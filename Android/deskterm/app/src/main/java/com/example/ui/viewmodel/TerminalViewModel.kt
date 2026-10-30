package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.AuthType
import com.example.data.model.CommandHistoryItem
import com.example.data.model.Snippet
import com.example.data.model.SshHost
import com.example.data.preferences.AppPreferences
import com.example.ssh.SshClientManager
import com.example.ssh.SshConnectionState
import com.example.terminal.TerminalBuffer
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val hostDao = db.sshHostDao()
    private val snippetDao = db.snippetDao()
    private val preferences = AppPreferences(application)

    val terminalBuffer = TerminalBuffer()

    private val sshClient = SshClientManager { chunk ->
        terminalBuffer.append(chunk)
    }

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var hasAttemptedAutoConnect = false

    init {
        // Observe SSH connection state and metrics
        viewModelScope.launch {
            sshClient.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                when (state) {
                    is SshConnectionState.Connecting -> {
                        terminalBuffer.append("\u001B[33mConnecting to ${state.host.username}@${state.host.host}:${state.host.port}...\u001B[0m\n")
                    }
                    is SshConnectionState.Connected -> {
                        _uiState.update { it.copy(activeHost = state.host) }
                        hostDao.updateLastConnected(state.host.id, System.currentTimeMillis())
                    }
                    is SshConnectionState.Error -> {
                        terminalBuffer.append("\u001B[31m[Connection error] ${state.message}\u001B[0m\n")
                    }
                    SshConnectionState.Disconnected -> {
                    }
                }
            }
        }

        viewModelScope.launch {
            sshClient.metrics.collect { metrics ->
                _uiState.update { it.copy(metrics = metrics) }
            }
        }

        // Observe hosts
        viewModelScope.launch {
            hostDao.getAllHostsFlow().collect { hostsList ->
                // Clean up any old dummy host if present
                val dummy = hostsList.find { it.host == "192.168.1.100" && it.password.isEmpty() && it.lastConnectedAt == null }
                if (dummy != null) {
                    hostDao.deleteHost(dummy)
                    return@collect
                }

                _uiState.update { it.copy(hosts = hostsList) }

                // Check auto-connect on initial load
                if (!hasAttemptedAutoConnect) {
                    hasAttemptedAutoConnect = true
                    checkAndTriggerAutoConnect(hostsList)
                }
            }
        }

        // Observe snippets
        viewModelScope.launch {
            snippetDao.getAllSnippetsFlow().collect { snippetsList ->
                _uiState.update { it.copy(snippets = snippetsList) }
            }
        }

        // Observe history
        viewModelScope.launch {
            snippetDao.getRecentHistoryFlow().collect { historyList ->
                _uiState.update { it.copy(history = historyList) }
            }
        }

        // Observe user preferences
        viewModelScope.launch {
            preferences.settingsFlow.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    private fun checkAndTriggerAutoConnect(hosts: List<SshHost>) {
        viewModelScope.launch {
            val settings = preferences.settingsFlow.first()
            val validHosts = hosts.filter { it.host != "192.168.1.100" || it.password.isNotEmpty() }
            if (validHosts.isEmpty()) {
                // If no desktop host configured, automatically open the Host Edit form
                startEditingHost(null)
            } else if (settings.autoConnectEnabled) {
                val autoHost = validHosts.firstOrNull { it.isAutoConnect } ?: validHosts.firstOrNull()
                if (autoHost != null) {
                    connectToHost(autoHost)
                }
            }
        }
    }

    fun connectToHost(host: SshHost) {
        _uiState.update { it.copy(activeHost = host, activeSheet = ActiveSheet.NONE) }
        viewModelScope.launch {
            sshClient.connect(host)
        }
    }

    fun disconnect() {
        sshClient.disconnect()
        terminalBuffer.append("\u001B[33m[Disconnected]\u001B[0m\n")
    }

    fun reconnect() {
        val host = _uiState.value.activeHost ?: _uiState.value.hosts.firstOrNull { it.isAutoConnect } ?: _uiState.value.hosts.firstOrNull()
        if (host != null) {
            connectToHost(host)
        }
    }

    fun onCommandInputChanged(newInput: String) {
        _uiState.update { it.copy(commandInput = newInput) }
    }

    fun sendCurrentCommand() {
        val command = _uiState.value.commandInput
        if (command.isNotBlank()) {
            viewModelScope.launch {
                snippetDao.insertHistoryItem(CommandHistoryItem(command = command))
            }
        }
        sshClient.sendInput(command + "\n")
        _uiState.update {
            it.copy(
                commandInput = "",
                historyIndex = -1,
                ctrlActive = false
            )
        }
    }

    fun sendQuickCommand(command: String) {
        viewModelScope.launch {
            snippetDao.insertHistoryItem(CommandHistoryItem(command = command))
        }
        terminalBuffer.append("\u001B[36m> $command\u001B[0m\n")
        sshClient.sendInput(command + "\n")
        _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    }

    fun sendControlKey(keyName: String) {
        when (keyName.uppercase()) {
            "CTRL" -> {
                _uiState.update { it.copy(ctrlActive = !it.ctrlActive) }
            }
            "ESC" -> {
                sshClient.sendRawBytes(byteArrayOf(0x1B))
            }
            "TAB" -> {
                sshClient.sendRawBytes(byteArrayOf(0x09))
            }
            "UP" -> {
                // If in command input history mode
                val history = _uiState.value.history
                if (history.isNotEmpty()) {
                    val nextIndex = (_uiState.value.historyIndex + 1).coerceAtMost(history.size - 1)
                    _uiState.update {
                        it.copy(
                            historyIndex = nextIndex,
                            commandInput = history[nextIndex].command
                        )
                    }
                } else {
                    sshClient.sendRawBytes(byteArrayOf(0x1B, 0x5B, 0x41)) // ANSI Up
                }
            }
            "DOWN" -> {
                val history = _uiState.value.history
                val currentIndex = _uiState.value.historyIndex
                if (currentIndex > 0) {
                    val nextIndex = currentIndex - 1
                    _uiState.update {
                        it.copy(
                            historyIndex = nextIndex,
                            commandInput = history[nextIndex].command
                        )
                    }
                } else if (currentIndex == 0) {
                    _uiState.update {
                        it.copy(
                            historyIndex = -1,
                            commandInput = ""
                        )
                    }
                } else {
                    sshClient.sendRawBytes(byteArrayOf(0x1B, 0x5B, 0x42)) // ANSI Down
                }
            }
            "LEFT" -> {
                sshClient.sendRawBytes(byteArrayOf(0x1B, 0x5B, 0x44))
            }
            "RIGHT" -> {
                sshClient.sendRawBytes(byteArrayOf(0x1B, 0x5B, 0x43))
            }
            "CTRL+C" -> {
                sshClient.sendRawBytes(byteArrayOf(0x03))
                _uiState.update { it.copy(ctrlActive = false) }
            }
            "CTRL+D" -> {
                sshClient.sendRawBytes(byteArrayOf(0x04))
                _uiState.update { it.copy(ctrlActive = false) }
            }
            "CTRL+Z" -> {
                sshClient.sendRawBytes(byteArrayOf(0x1A))
                _uiState.update { it.copy(ctrlActive = false) }
            }
            "CTRL+L" -> {
                sshClient.sendRawBytes(byteArrayOf(0x0C))
                terminalBuffer.clear()
                _uiState.update { it.copy(ctrlActive = false) }
            }
            else -> {
                // Direct character injection
                sshClient.sendInput(keyName)
            }
        }
    }

    fun clearTerminal() {
        terminalBuffer.clear()
    }

    // Sheet Navigation
    fun openSheet(sheet: ActiveSheet) {
        _uiState.update { it.copy(activeSheet = sheet) }
    }

    fun closeSheet() {
        _uiState.update {
            it.copy(
                activeSheet = ActiveSheet.NONE,
                editingHost = null,
                testConnectionResult = null,
                isTestingConnection = false
            )
        }
    }

    // Host Management
    fun startEditingHost(host: SshHost?) {
        _uiState.update {
            it.copy(
                editingHost = host ?: SshHost(
                    name = "Desktop",
                    host = "",
                    port = 22,
                    username = "",
                    authType = AuthType.PASSWORD,
                    isAutoConnect = true
                ),
                activeSheet = ActiveSheet.HOST_EDIT,
                testConnectionResult = null,
                isTestingConnection = false
            )
        }
    }

    fun saveHost(host: SshHost) {
        viewModelScope.launch {
            val savedId = hostDao.saveHostWithAutoConnect(host)
            val updatedHost = host.copy(id = savedId)
            _uiState.update {
                it.copy(
                    activeSheet = ActiveSheet.NONE,
                    editingHost = null,
                    testConnectionResult = null,
                    activeHost = updatedHost
                )
            }
            connectToHost(updatedHost)
        }
    }

    fun deleteHost(host: SshHost) {
        viewModelScope.launch {
            if (_uiState.value.activeHost?.id == host.id) {
                disconnect()
            }
            hostDao.deleteHost(host)
        }
    }

    fun toggleAutoConnectHost(host: SshHost) {
        viewModelScope.launch {
            val newStatus = !host.isAutoConnect
            val updated = host.copy(isAutoConnect = newStatus)
            hostDao.saveHostWithAutoConnect(updated)
        }
    }

    fun testConnection(host: SshHost) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true, testConnectionResult = null) }
            val result = sshClient.testConnection(host)
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    testConnectionResult = result
                )
            }
        }
    }

    // Snippet Management
    fun saveSnippet(title: String, command: String, category: String) {
        viewModelScope.launch {
            snippetDao.insertSnippet(
                Snippet(title = title, command = command, category = category)
            )
        }
    }

    fun deleteSnippet(snippet: Snippet) {
        viewModelScope.launch {
            snippetDao.deleteSnippet(snippet)
        }
    }

    // Settings
    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun setFontSize(sizeSp: Int) {
        viewModelScope.launch { preferences.setFontSize(sizeSp) }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoConnectEnabled(enabled) }
    }

    fun setKeepAlive(seconds: Int) {
        viewModelScope.launch { preferences.setKeepAliveSeconds(seconds) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setHapticEnabled(enabled) }
    }

    fun setAutoScroll(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutoScroll(enabled) }
    }

    fun toggleAmoled() {
        val current = _uiState.value.settings.themeMode
        val next = if (current == AppThemeMode.AMOLED) AppThemeMode.DARK else AppThemeMode.AMOLED
        setThemeMode(next)
    }

    fun getExportText(): String = terminalBuffer.getFullText()

    override fun onCleared() {
        super.onCleared()
        sshClient.disconnect()
    }
}
