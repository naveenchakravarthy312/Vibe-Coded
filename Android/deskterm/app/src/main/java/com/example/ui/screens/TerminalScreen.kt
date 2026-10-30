package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.ui.components.*
import com.example.ui.theme.AppThemeMode
import com.example.ui.viewmodel.ActiveSheet
import com.example.ui.viewmodel.TerminalViewModel

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(viewModel.getExportText().toByteArray(Charsets.UTF_8))
                }
            } catch (_: Exception) {}
        }
    }

    // Handle back button to dismiss bottom sheets if active
    BackHandler(enabled = uiState.activeSheet != ActiveSheet.NONE) {
        viewModel.closeSheet()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        topBar = {
            TerminalTopBar(
                activeHost = uiState.activeHost,
                connectionState = uiState.connectionState,
                onHostsClick = { viewModel.openSheet(ActiveSheet.HOSTS_LIST) },
                onSnippetsClick = { viewModel.openSheet(ActiveSheet.SNIPPETS) },
                onExportClick = {
                    exportLauncher.launch("deskterm-session-${System.currentTimeMillis()}.log")
                },
                onSettingsClick = { viewModel.openSheet(ActiveSheet.SETTINGS) },
                onClearClick = { viewModel.clearTerminal() },
                onReconnectClick = { viewModel.reconnect() },
                onDisconnectClick = { viewModel.disconnect() }
            )
        },
        bottomBar = {
            TerminalAccessoryBar(
                commandInput = uiState.commandInput,
                onCommandInputChanged = { viewModel.onCommandInputChanged(it) },
                onSendCommand = { viewModel.sendCurrentCommand() },
                onSendKey = { viewModel.sendControlKey(it) },
                ctrlActive = uiState.ctrlActive,
                metrics = uiState.metrics,
                hapticEnabled = uiState.settings.hapticEnabled,
                isAmoled = uiState.settings.themeMode == AppThemeMode.AMOLED,
                onToggleAmoled = { viewModel.toggleAmoled() },
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
                    .testTag("terminal_accessory_bar")
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        TerminalCanvas(
            rawTextFlow = viewModel.terminalBuffer.rawTextFlow,
            fontSizeSp = uiState.settings.fontSizeSp,
            autoScrollEnabled = uiState.settings.autoScroll,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }

    // Modal Bottom Sheets
    when (uiState.activeSheet) {
        ActiveSheet.HOSTS_LIST -> {
            HostsBottomSheet(
                hosts = uiState.hosts,
                activeHost = uiState.activeHost,
                connectionState = uiState.connectionState,
                onHostSelect = { host -> viewModel.connectToHost(host) },
                onEditHost = { host -> viewModel.startEditingHost(host) },
                onAddNewHost = { viewModel.startEditingHost(null) },
                onToggleAutoConnect = { host -> viewModel.toggleAutoConnectHost(host) },
                onDeleteHost = { host -> viewModel.deleteHost(host) },
                onDismiss = { viewModel.closeSheet() }
            )
        }

        ActiveSheet.HOST_EDIT -> {
            uiState.editingHost?.let { host ->
                HostEditSheet(
                    host = host,
                    isTesting = uiState.isTestingConnection,
                    testResult = uiState.testConnectionResult,
                    onSave = { savedHost -> viewModel.saveHost(savedHost) },
                    onTestConnection = { testHost -> viewModel.testConnection(testHost) },
                    onDismiss = { viewModel.closeSheet() }
                )
            }
        }

        ActiveSheet.SNIPPETS -> {
            SnippetsBottomSheet(
                snippets = uiState.snippets,
                isConnected = uiState.connectionState is com.example.ssh.SshConnectionState.Connected,
                onRunSnippet = { cmd -> viewModel.sendQuickCommand(cmd) },
                onAddSnippet = { title, cmd, cat -> viewModel.saveSnippet(title, cmd, cat) },
                onDeleteSnippet = { snip -> viewModel.deleteSnippet(snip) },
                onDismiss = { viewModel.closeSheet() }
            )
        }

        ActiveSheet.SETTINGS -> {
            SettingsBottomSheet(
                settings = uiState.settings,
                onThemeModeChange = { mode -> viewModel.setThemeMode(mode) },
                onDynamicColorChange = { dyn -> viewModel.setDynamicColor(dyn) },
                onAutoConnectChange = { auto -> viewModel.setAutoConnectEnabled(auto) },
                onFontSizeChange = { sz -> viewModel.setFontSize(sz) },
                onAutoScrollChange = { sc -> viewModel.setAutoScroll(sc) },
                onHapticChange = { hp -> viewModel.setHapticEnabled(hp) },
                onExportSessionLog = {
                    exportLauncher.launch("deskterm-session-${System.currentTimeMillis()}.log")
                },
                onDismiss = { viewModel.closeSheet() }
            )
        }

        ActiveSheet.NONE -> {
            // No sheet open
        }
    }
}
