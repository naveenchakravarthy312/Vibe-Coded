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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.concurrent.atomic.AtomicLong

class SshClientManager(
    private val onOutputReceived: (String) -> Unit
) {
    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

    private val _metrics = MutableStateFlow(ConnectionMetrics())
    val metrics: StateFlow<ConnectionMetrics> = _metrics.asStateFlow()

    private val connectionMutex = Mutex()
    private val writeMutex = Mutex()
    private val connectionGeneration = AtomicLong(0L)

    @Volatile private var activeSession: Session? = null
    @Volatile private var activeChannel: ChannelShell? = null
    @Volatile private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var ptyCols: Int = 80
    @Volatile private var ptyRows: Int = 24
    @Volatile private var ptyWidthPx: Int = 640
    @Volatile private var ptyHeightPx: Int = 480

    suspend fun connect(host: SshHost, keepAliveSeconds: Int = 30) = withContext(Dispatchers.IO) {
        connectionMutex.withLock {
            val generation = connectionGeneration.incrementAndGet()
            disconnectInternal()

            if (host.port !in 1..65535) {
                _connectionState.value = SshConnectionState.Error(
                    message = "Invalid port ${host.port}. Port must be between 1 and 65535.",
                    canRetry = false,
                    host = host
                )
                return@withLock
            }

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
                if (keepAliveSeconds > 0) {
                    session.serverAliveInterval = keepAliveSeconds * 1000
                    session.serverAliveCountMax = 3
                }

                val startTime = System.currentTimeMillis()
                session.connect(15000)
                val handshakePing = System.currentTimeMillis() - startTime

                _connectionState.value = SshConnectionState.Connecting(host, "Opening terminal shell (xterm-256color)...")
                val channel = session.openChannel("shell") as ChannelShell
                channel.setPtyType("xterm-256color")
                channel.setPtySize(ptyCols, ptyRows, ptyWidthPx, ptyHeightPx)

                val inStream = channel.inputStream
                val outStream = channel.outputStream

                channel.connect(8000)

                activeSession = session
                activeChannel = channel
                outputStream = outStream

                _metrics.update { it.copy(pingMs = handshakePing) }
                _connectionState.value = SshConnectionState.Connected(host, System.currentTimeMillis())

                startReader(inStream, channel, generation)
            } catch (e: CancellationException) {
                disconnectInternal()
                throw e
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
    }

    fun updateKeepAlive(keepAliveSeconds: Int) {
        managerScope.launch {
            try {
                activeSession?.let { session ->
                    if (session.isConnected) {
                        session.serverAliveInterval = if (keepAliveSeconds > 0) keepAliveSeconds * 1000 else 0
                        session.serverAliveCountMax = 3
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun resizePty(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
        val safeCols = cols.coerceIn(20, 300)
        val safeRows = rows.coerceIn(8, 200)
        val safeW = widthPx.coerceAtLeast(1)
        val safeH = heightPx.coerceAtLeast(1)
        if (safeCols == ptyCols && safeRows == ptyRows) return

        ptyCols = safeCols
        ptyRows = safeRows
        ptyWidthPx = safeW
        ptyHeightPx = safeH

        managerScope.launch {
            try {
                activeChannel?.let { channel ->
                    if (channel.isConnected) {
                        channel.setPtySize(safeCols, safeRows, safeW, safeH)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun startReader(inputStream: InputStream, channel: ChannelShell, generation: Long) {
        readJob?.cancel()
        readJob = managerScope.launch {
            val reader = inputStream.reader(Charsets.UTF_8)
            val buffer = CharArray(4096)
            try {
                while (isActive && channel.isConnected && connectionGeneration.get() == generation) {
                    val count = reader.read(buffer)
                    if (count > 0) {
                        val text = String(buffer, 0, count)
                        onOutputReceived(text)
                        _metrics.update { it.copy(bytesReceived = it.bytesReceived + count) }
                    } else if (count == -1) {
                        break
                    }
                }
            } catch (e: Exception) {
                if (isActive && connectionGeneration.get() == generation) {
                    onOutputReceived("\n[Connection closed: ${e.message ?: "Stream end"}]\n")
                }
            } finally {
                withContext(NonCancellable) {
                    if (connectionGeneration.get() == generation && _connectionState.value is SshConnectionState.Connected) {
                        _connectionState.value = SshConnectionState.Disconnected
                    }
                }
            }
        }
    }

    fun sendInput(input: String) {
        sendRawBytes(input.toByteArray(Charsets.UTF_8))
    }

    fun sendRawBytes(bytes: ByteArray) {
        managerScope.launch {
            writeMutex.withLock {
                try {
                    outputStream?.let { stream ->
                        stream.write(bytes)
                        stream.flush()
                        _metrics.update { it.copy(bytesSent = it.bytesSent + bytes.size) }
                    }
                } catch (e: Exception) {
                    onOutputReceived("\n[Error writing to terminal: ${e.message}]\n")
                }
            }
        }
    }

    suspend fun testConnection(host: SshHost): Result<String> = withContext(Dispatchers.IO) {
        if (host.port !in 1..65535) {
            return@withContext Result.failure(IllegalArgumentException("Invalid port ${host.port}. Port must be between 1 and 65535."))
        }
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
            connectionMutex.withLock {
                connectionGeneration.incrementAndGet()
                disconnectInternal()
                _connectionState.value = SshConnectionState.Disconnected
            }
        }
    }

    fun shutdown() {
        connectionGeneration.incrementAndGet()
        disconnectInternal()
        managerScope.cancel()
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
                "Connection timed out: Check that the host IP/domain and port are accessible and not blocked by a firewall."
            raw.contains("UnknownHostException", ignoreCase = true) || raw.contains("No route to host", ignoreCase = true) ->
                "Unknown host: Cannot resolve host address. Ensure device is on the same local network or VPN."
            raw.contains("Connection refused", ignoreCase = true) ->
                "Connection refused: Ensure SSH service (sshd) is running on the target desktop."
            else -> raw
        }
    }
}
