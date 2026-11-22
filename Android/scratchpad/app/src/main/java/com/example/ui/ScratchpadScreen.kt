package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.FileStorageHelper
import com.example.ui.components.FindReplaceBar
import com.example.ui.components.FontSettingsDialog
import com.example.ui.components.SavedNotesSheet
import com.example.ui.components.SaveNoteDialog
import com.example.ui.components.ScratchpadAccessoryBar
import com.example.ui.components.ScratchpadEditor
import com.example.ui.components.ScratchpadTopBar
import com.example.ui.components.WipeConfirmDialog
import com.example.ui.theme.CanvasDark

@Composable
fun ScratchpadScreen(
    viewModel: ScratchpadViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Survives configuration changes and process recreation while the SAF picker is open
    var noteToExportContent by rememberSaveable { mutableStateOf<String?>(null) }
    var noteToExportName by rememberSaveable { mutableStateOf("scratchpad.txt") }

    // SAF Create Document launcher (Save to file on device storage)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val contentToSave = noteToExportContent ?: uiState.textFieldValue.text
            val success = FileStorageHelper.writeTextToUri(context, uri, contentToSave)
            if (success) {
                viewModel.onFileSavedToStorage(noteToExportName)
            }
        }
        noteToExportContent = null
    }

    // SAF Open Document launcher (Open text file from device storage)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = FileStorageHelper.readTextFromUri(context, uri)
            if (result != null) {
                viewModel.loadExternalFile(fileName = result.first, content = result.second)
            }
        }
    }

    // Handle back button smoothly
    BackHandler(
        enabled = uiState.showSavedNotesSheet || uiState.showFindReplace || uiState.isZenMode
    ) {
        when {
            uiState.showSavedNotesSheet -> viewModel.toggleSavedNotesSheet(false)
            uiState.showFindReplace -> viewModel.toggleFindReplace(false)
            uiState.isZenMode -> viewModel.setZenMode(false)
        }
    }

    // Show status snackbars with optional undo action
    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            val result = if (msg == "Note deleted" && uiState.lastDeletedNote != null) {
                snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Short
                )
            } else {
                snackbarHostState.showSnackbar(
                    message = msg,
                    duration = SnackbarDuration.Short
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreLastDeletedNote()
            }
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AnimatedVisibility(
                visible = !uiState.isZenMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    ScratchpadTopBar(
                        uiState = uiState,
                        onOpenSavedNotes = { viewModel.toggleSavedNotesSheet(true) },
                        onSaveNote = { viewModel.toggleSaveDialog(true) },
                        onSaveToFile = {
                            val defaultName = "${uiState.activeNoteTitle.replace("\\s+".toRegex(), "_")}.txt"
                            noteToExportContent = uiState.textFieldValue.text
                            noteToExportName = defaultName
                            createDocumentLauncher.launch(defaultName)
                        },
                        onOpenFile = {
                            openDocumentLauncher.launch(arrayOf("text/*", "text/plain", "*/*"))
                        },
                        onNewScratchpad = { viewModel.newScratchpad() },
                        onShareText = {
                            FileStorageHelper.shareText(
                                context = context,
                                title = uiState.activeNoteTitle,
                                text = uiState.textFieldValue.text
                            )
                        },
                        onCopyAll = {
                            FileStorageHelper.copyToClipboard(context, uiState.textFieldValue.text)
                            viewModel.showStatusMessage("Copied to clipboard")
                        },
                        onOpenFontSettings = { viewModel.toggleFontDialog(true) },
                        onClearScratchpad = { viewModel.toggleWipeConfirmDialog(true) }
                    )

                    AnimatedVisibility(
                        visible = uiState.showFindReplace,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        FindReplaceBar(
                            uiState = uiState,
                            onFindQueryChange = { viewModel.setFindQuery(it) },
                            onReplaceQueryChange = { viewModel.setReplaceQuery(it) },
                            onFindNext = { viewModel.findNext() },
                            onFindPrevious = { viewModel.findPrevious() },
                            onReplaceCurrent = { viewModel.replaceCurrent() },
                            onReplaceAll = { viewModel.replaceAll() },
                            onClose = { viewModel.toggleFindReplace(false) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !uiState.isZenMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ScratchpadAccessoryBar(
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onInsertTab = { viewModel.insertTab() },
                    onInsertTimestamp = { viewModel.insertTimestamp() },
                    onInsertChecklist = { viewModel.insertChecklist() },
                    onInsertBullet = { viewModel.insertBullet() },
                    onToggleFind = { viewModel.toggleFindReplace(!uiState.showFindReplace) },
                    onToggleZenMode = { viewModel.toggleZenMode() }
                )
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(CanvasDark)
    ) { innerPadding ->
        ScratchpadEditor(
            uiState = uiState,
            onTextChanged = { viewModel.onTextChanged(it) },
            onZoomFont = { viewModel.adjustFontSize(it) },
            onExitZenMode = { viewModel.setZenMode(false) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }

    // Modal Sheet for Saved Notes
    if (uiState.showSavedNotesSheet) {
        SavedNotesSheet(
            uiState = uiState,
            onDismiss = { viewModel.toggleSavedNotesSheet(false) },
            onNoteClick = { note -> viewModel.loadNote(note) },
            onDeleteNote = { note -> viewModel.deleteNote(note) },
            onTogglePin = { note -> viewModel.togglePin(note) },
            onExportNote = { note ->
                val safeName = "${note.title.replace("\\s+".toRegex(), "_")}.txt"
                noteToExportContent = note.content
                noteToExportName = safeName
                createDocumentLauncher.launch(safeName)
            },
            onNewScratchpad = { viewModel.newScratchpad() },
            onSearchQueryChange = { viewModel.setSearchQuery(it) }
        )
    }

    // Save Note Dialog
    if (uiState.showSaveDialog) {
        SaveNoteDialog(
            initialTitle = uiState.activeNoteTitle,
            onConfirm = { title -> viewModel.saveCurrentNote(title) },
            onDismiss = { viewModel.toggleSaveDialog(false) }
        )
    }

    // Wipe Confirmation Dialog
    if (uiState.showWipeConfirmDialog) {
        WipeConfirmDialog(
            onConfirm = { viewModel.clearScratchpad() },
            onDismiss = { viewModel.toggleWipeConfirmDialog(false) }
        )
    }

    // Font and Typography Settings Dialog
    if (uiState.showFontDialog) {
        FontSettingsDialog(
            fontSizeSp = uiState.fontSizeSp,
            isAmoledMode = uiState.isAmoledMode,
            isDynamicColor = uiState.isDynamicColor,
            onAdjustFontSize = { viewModel.adjustFontSize(it) },
            onResetFontSize = { viewModel.resetFontSize() },
            onToggleAmoledMode = { viewModel.toggleAmoledMode() },
            onToggleDynamicColor = { viewModel.toggleDynamicColor() },
            onDismiss = { viewModel.toggleFontDialog(false) }
        )
    }
}
