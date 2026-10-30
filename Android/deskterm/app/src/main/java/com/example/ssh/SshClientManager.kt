package com.example.ssh

import com.example.data.model.AuthType
import com.example.data.model.SshHost
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

class SshClientManager(
    private val onOutputReceived: (String) -> Unit
) {
    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

    private val _metrics = MutableStateFlow(ConnectionMetrics())
    val metrics: StateFlow<ConnectionMetrics> = _metrics.asStateFlow()

    private var activeSession: Session? = null
    private var activeChannel: ChannelShell? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun connect(host: SshHost) = withContext(Dispatchers.IO) {
        disconnect()

        _connectionState.value = SshConnectionState.Connecting(host, "Resolving host ${host.host}:${host.port}...")
        _metrics.value = ConnectionMetrics()

        try {
            val jsch = JSch()

            if (host.authType == AuthType.PRIVATE_KEY && host.privateKey.isNotBlank()) {
                _connectionState.value = SshConnectionState.Connecting(host, "Loading SSH private key...")
                val keyBytes = host.privateKey.trim().toByteArray(Charsets.UTF_8)
                val passBytes = if (host.passphrase.isNotBlank()) {
                    host.passphrase.toByteArray(Charsets.UTF_8)
                } else null
                jsch.addIdentity("key_${host.id}", keyBytes, null, passBytes)
            }

            _connectionState.value = SshConnectionState.Connecting(host, "Connecting to ${host.username}@${host.host}...")
            val session = jsch.getSession(host.username, host.host, host.port)

            if (host.authType == AuthType.PASSWORD && host.password.isNotBlank()) {
                session.setPassword(host.password)
            }

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", if (host.authType == AuthType.PASSWORD) "password,keyboard-interactive" else "publickey,password")
            }
            session.setConfig(config)
            session.timeout = 15000

            val startTime = System.currentTimeMillis()
            session.connect(15000)
            val handshakePing = System.currentTimeMillis() - startTime

            _connectionState.value = SshConnectionState.Connecting(host, "Opening terminal shell (xterm-256color)...")
            val channel = session.openChannel("shell") as ChannelShell
            channel.setPtyType("xterm-256color")

            val inStream = channel.inputStream
            val outStream = channel.outputStream

            channel.connect(8000)

            activeSession = session
            activeChannel = channel
            outputStream = outStream

            _metrics.value = _metrics.value.copy(pingMs = handshakePing)
            _connectionState.value = SshConnectionState.Connected(host, System.currentTimeMillis())

            startReader(inStream, channel)

        } catch (e: Exception) {
            val errorMsg = e.message ?: "Failed to connect to ${host.host}"
            _connectionState.value = SshConnectionState.Error(
                message = formatErrorMessage(errorMsg),
                canRetry = true,
                host = host
            )
            disconnectInternal()
        }
    }

    private fun startReader(inputStream: InputStream, channel: ChannelShell) {
        readJob?.cancel()
        readJob = managerScope.launch {
            val buffer = ByteArray(4096)
            try {
                while (isActive && channel.isConnected) {
                    val count = inputStream.read(buffer)
                    if (count > 0) {
                        val text = String(buffer, 0, count, Charsets.UTF_8)
                        onOutputReceived(text)
                        _metrics.value = _metrics.value.copy(
                            bytesReceived = _metrics.value.bytesReceived + count
                        )
                    } else if (count == -1) {
                        break
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    onOutputReceived("\n[Connection closed: ${e.message ?: "Stream end"}]\n")
                }
            } finally {
                withContext(NonCancellable) {
                    if (_connectionState.value is SshConnectionState.Connected) {
                        _connectionState.value = SshConnectionState.Disconnected
                    }
                }
            }
        }
    }

    fun sendInput(input: String) {
        managerScope.launch {
            try {
                outputStream?.let { stream ->
                    val bytes = input.toByteArray(Charsets.UTF_8)
                    stream.write(bytes)
                    stream.flush()
                    _metrics.value = _metrics.value.copy(
                        bytesSent = _metrics.value.bytesSent + bytes.size
                    )
                }
            } catch (e: Exception) {
                onOutputReceived("\n[Error writing to terminal: ${e.message}]\n")
            }
        }
    }

    fun sendRawBytes(bytes: ByteArray) {
        managerScope.launch {
            try {
                outputStream?.let { stream ->
                    stream.write(bytes)
                    stream.flush()
                    _metrics.value = _metrics.value.copy(
                        bytesSent = _metrics.value.bytesSent + bytes.size
                    )
                }
            } catch (e: Exception) {
                onOutputReceived("\n[Error sending control byte: ${e.message}]\n")
            }
        }
    }

    suspend fun testConnection(host: SshHost): Result<String> = withContext(Dispatchers.IO) {
        var testSession: Session? = null
        try {
            val jsch = JSch()
            if (host.authType == AuthType.PRIVATE_KEY && host.privateKey.isNotBlank()) {
                val keyBytes = host.privateKey.trim().toByteArray(Charsets.UTF_8)
                val passBytes = if (host.passphrase.isNotBlank()) host.passphrase.toByteArray(Charsets.UTF_8) else null
                jsch.addIdentity("test_key_${System.currentTimeMillis()}", keyBytes, null, passBytes)
            }

            val session = jsch.getSession(host.username, host.host, host.port)
            if (host.authType == AuthType.PASSWORD && host.password.isNotBlank()) {
                session.setPassword(host.password)
            }
            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", if (host.authType == AuthType.PASSWORD) "password,keyboard-interactive" else "publickey,password")
            }
            session.setConfig(config)
            session.timeout = 8000

            val startTime = System.currentTimeMillis()
            session.connect(8000)
            val duration = System.currentTimeMillis() - startTime
            val serverVersion = session.serverVersion ?: "SSH-2.0"

            testSession = session
            Result.success("Success! Handshake in ${duration}ms. Server banner: $serverVersion")
        } catch (e: Exception) {
            Result.failure(Exception(formatErrorMessage(e.message ?: "Authentication or network failure")))
        } finally {
            try {
                testSession?.disconnect()
            } catch (_: Exception) {}
        }
    }

    fun disconnect() {
        managerScope.launch {
            disconnectInternal()
            _connectionState.value = SshConnectionState.Disconnected
        }
    }

    private fun disconnectInternal() {
        try {
            readJob?.cancel()
            readJob = null
            outputStream?.close()
            outputStream = null
            activeChannel?.disconnect()
            activeChannel = null
            activeSession?.disconnect()
            activeSession = null
        } catch (_: Exception) {}
    }

    private fun formatErrorMessage(raw: String): String {
        return when {
            raw.contains("Auth fail", ignoreCase = true) ->
                "Authentication failed: Invalid username, password, or SSH key."
            raw.contains("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true) ->
                "Connection timed out: Check that the host IP/domain and port 22 are accessible and not blocked by a firewall."
            raw.contains("UnknownHostException", ignoreCase = true) || raw.contains("No route to host", ignoreCase = true) ->
                "Unknown host: Cannot resolve host address. Ensure device is on the same local network or VPN."
            raw.contains("Connection refused", ignoreCase = true) ->
                "Connection refused: Ensure SSH service (sshd) is running on the target desktop."
            else -> raw
        }
    }
}
