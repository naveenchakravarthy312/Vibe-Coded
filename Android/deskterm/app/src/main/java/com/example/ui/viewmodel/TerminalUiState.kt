package com.example.ui.viewmodel

import com.example.data.model.CommandHistoryItem
import com.example.data.model.Snippet
import com.example.data.model.SshHost
import com.example.data.preferences.UserSettings
import com.example.ssh.ConnectionMetrics
import com.example.ssh.SshConnectionState

enum class ActiveSheet {
    NONE,
    HOSTS_LIST,
    HOST_EDIT,
    SNIPPETS,
    SETTINGS
}

data class TerminalUiState(
    val connectionState: SshConnectionState = SshConnectionState.Disconnected,
    val metrics: ConnectionMetrics = ConnectionMetrics(),
    val hosts: List<SshHost> = emptyList(),
    val snippets: List<Snippet> = emptyList(),
    val history: List<CommandHistoryItem> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val activeHost: SshHost? = null,
    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    val editingHost: SshHost? = null,
    val testConnectionResult: Result<String>? = null,
    val isTestingConnection: Boolean = false,
    val commandInput: String = "",
    val ctrlActive: Boolean = false,
    val altActive: Boolean = false,
    val historyIndex: Int = -1, // -1 means current input
    val terminalSearchQuery: String = "",
    val isSearchOpen: Boolean = false,
    val statusMessage: String? = null
)
