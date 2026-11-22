package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.NoteEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ScratchpadViewModel(application: Application) : AndroidViewModel(application) {

    private enum class EditKind { NONE, INSERT, DELETE, OTHER }

    private val db = AppDatabase.getDatabase(application)
    private val noteDao = db.noteDao()
    private val prefs = application.getSharedPreferences("scratchpad_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ScratchpadUiState())
    val uiState: StateFlow<ScratchpadUiState> = _uiState.asStateFlow()

    // Undo / Redo history stacks with word-boundary & pause grouping
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var isUndoRedoAction = false
    private var lastEditTimeMs: Long = 0L
    private var lastEditKind: EditKind = EditKind.NONE
    private var lastInsertedWasWhitespace: Boolean = false
    private var notesCollectionJob: Job? = null

    internal var clockMs: () -> Long = { System.currentTimeMillis() }

    companion object {
        private const val MAX_UNDO_HISTORY = 100
        private const val UNDO_GROUP_WINDOW_MS = 750L
    }

    init {
        // Restore saved font size and preferences
        val savedFontSize = prefs.getInt("font_size", 15)
        val savedAmoled = prefs.getBoolean("is_amoled_mode", false)
        val savedDynamicColor = prefs.getBoolean("is_dynamic_color", true)
        val savedSystemTheme = prefs.getBoolean("use_system_theme", true)
        val draftText = prefs.getString("draft_text", "") ?: ""

        val initialValue = if (draftText.isNotEmpty()) {
            TextFieldValue(draftText, selection = TextRange(draftText.length))
        } else {
            val welcomeText = """# Ephemeral Scratchpad (Android)
A lightweight, distraction-free canvas for your phone.

• Distraction-free: full-bleed dark canvas with 24dp padding
• Monospace typography: crisp and comfortable
• File management: save notes locally or export to .txt files
• Quick tools: Undo/Redo, Tab stop, Timestamps, and Zen mode

Tap anywhere to begin typing…
""".trimIndent()
            TextFieldValue(welcomeText, selection = TextRange(welcomeText.length))
        }

        _uiState.update {
            it.copy(
                textFieldValue = initialValue,
                fontSizeSp = savedFontSize,
                isAmoledMode = savedAmoled,
                isDynamicColor = savedDynamicColor,
                useSystemTheme = savedSystemTheme
            )
        }

        // Collect saved notes from Room database via a single cancellable collector
        observeNotes("")
    }

    private fun observeNotes(query: String) {
        notesCollectionJob?.cancel()
        notesCollectionJob = viewModelScope.launch {
            val source = if (query.isBlank()) {
                noteDao.getAllNotes()
            } else {
                noteDao.searchNotes(query)
            }
            source.collectLatest { notes ->
                _uiState.update { it.copy(savedNotes = notes) }
            }
        }
    }

    private fun resetUndoGrouping() {
        lastEditKind = EditKind.NONE
        lastEditTimeMs = 0L
        lastInsertedWasWhitespace = false
    }

    private fun pushUndoSnapshot(snapshot: TextFieldValue) {
        if (undoStack.size >= MAX_UNDO_HISTORY) {
            undoStack.removeFirst()
        }
        undoStack.addLast(snapshot)
    }

    fun onTextChanged(newValue: TextFieldValue, forceUndoSnapshot: Boolean = false) {
        val current = _uiState.value.textFieldValue
        if (!isUndoRedoAction && current.text != newValue.text) {
            val now = clockMs()
            val delta = newValue.text.length - current.text.length
            val editKind = when {
                forceUndoSnapshot || abs(delta) > 1 -> EditKind.OTHER
                delta == 1 -> EditKind.INSERT
                delta == -1 -> EditKind.DELETE
                else -> EditKind.OTHER
            }

            val insertedChar: Char? = if (editKind == EditKind.INSERT) {
                val idx = (newValue.selection.start - 1).coerceIn(0, newValue.text.lastIndex)
                newValue.text[idx]
            } else {
                null
            }

            val pauseExceeded = lastEditTimeMs > 0L && (now - lastEditTimeMs) >= UNDO_GROUP_WINDOW_MS
            val startedNewWord = editKind == EditKind.INSERT &&
                lastInsertedWasWhitespace &&
                insertedChar != null &&
                !insertedChar.isWhitespace()
            val insertedNewline = editKind == EditKind.INSERT && insertedChar == '\n'

            val shouldPushSnapshot = undoStack.isEmpty() ||
                forceUndoSnapshot ||
                editKind == EditKind.OTHER ||
                lastEditKind != editKind ||
                pauseExceeded ||
                startedNewWord ||
                insertedNewline

            if (shouldPushSnapshot) {
                pushUndoSnapshot(current)
            }

            redoStack.clear()
            lastEditTimeMs = now
            lastEditKind = editKind
            lastInsertedWasWhitespace = editKind == EditKind.INSERT && insertedChar?.isWhitespace() == true
        }

        _uiState.update {
            it.copy(
                textFieldValue = newValue,
                isModified = true,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }

        // Save active draft
        prefs.edit().putString("draft_text", newValue.text).apply()

        // Update find matches if search is open
        if (_uiState.value.showFindReplace && _uiState.value.findQuery.isNotEmpty()) {
            updateFindMatches(_uiState.value.findQuery, newValue.text)
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeLast()
            redoStack.addLast(_uiState.value.textFieldValue)
            resetUndoGrouping()
            isUndoRedoAction = true
            _uiState.update {
                it.copy(
                    textFieldValue = previous,
                    isModified = true,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
            prefs.edit().putString("draft_text", previous.text).apply()
            isUndoRedoAction = false
            if (_uiState.value.showFindReplace && _uiState.value.findQuery.isNotEmpty()) {
                updateFindMatches(_uiState.value.findQuery, previous.text)
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeLast()
            pushUndoSnapshot(_uiState.value.textFieldValue)
            resetUndoGrouping()
            isUndoRedoAction = true
            _uiState.update {
                it.copy(
                    textFieldValue = next,
                    isModified = true,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
            prefs.edit().putString("draft_text", next.text).apply()
            isUndoRedoAction = false
            if (_uiState.value.showFindReplace && _uiState.value.findQuery.isNotEmpty()) {
                updateFindMatches(_uiState.value.findQuery, next.text)
            }
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun insertTextAtCursor(textToInsert: String) {
        val current = _uiState.value.textFieldValue
        val text = current.text
        val selection = current.selection
        val start = selection.min
        val end = selection.max

        val newText = text.substring(0, start) + textToInsert + text.substring(end)
        val newCursor = start + textToInsert.length
        val newValue = TextFieldValue(newText, selection = TextRange(newCursor))

        onTextChanged(newValue, forceUndoSnapshot = true)
    }

    fun insertTimestamp() {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val timestamp = "[${formatter.format(Date())}] "
        insertTextAtCursor(timestamp)
    }

    fun insertTab() {
        // Crisp 4-space tab stop as in Linux Python scratchpad
        insertTextAtCursor("    ")
    }

    fun insertChecklist() {
        insertTextAtCursor("- [ ] ")
    }

    fun insertBullet() {
        insertTextAtCursor("• ")
    }

    fun adjustFontSize(delta: Int) {
        val current = _uiState.value.fontSizeSp
        val newSize = (current + delta).coerceIn(10, 34)
        if (newSize != current) {
            _uiState.update { it.copy(fontSizeSp = newSize) }
            prefs.edit().putInt("font_size", newSize).apply()
        }
    }

    fun resetFontSize() {
        _uiState.update { it.copy(fontSizeSp = 15) }
        prefs.edit().putInt("font_size", 15).apply()
    }

    fun toggleAmoledMode() {
        val next = !_uiState.value.isAmoledMode
        _uiState.update { it.copy(isAmoledMode = next) }
        prefs.edit().putBoolean("is_amoled_mode", next).apply()
    }

    fun toggleDynamicColor() {
        val next = !_uiState.value.isDynamicColor
        _uiState.update { it.copy(isDynamicColor = next) }
        prefs.edit().putBoolean("is_dynamic_color", next).apply()
    }

    fun toggleSystemTheme() {
        val next = !_uiState.value.useSystemTheme
        _uiState.update { it.copy(useSystemTheme = next) }
        prefs.edit().putBoolean("use_system_theme", next).apply()
    }

    fun toggleZenMode() {
        _uiState.update { it.copy(isZenMode = !it.isZenMode) }
    }

    fun setZenMode(enabled: Boolean) {
        _uiState.update { it.copy(isZenMode = enabled) }
    }

    fun toggleSavedNotesSheet(show: Boolean) {
        _uiState.update { it.copy(showSavedNotesSheet = show) }
    }

    fun toggleSaveDialog(show: Boolean) {
        _uiState.update { it.copy(showSaveDialog = show) }
    }

    fun toggleFindReplace(show: Boolean) {
        _uiState.update {
            it.copy(showFindReplace = show)
        }
        if (show && _uiState.value.findQuery.isNotEmpty()) {
            updateFindMatches(_uiState.value.findQuery, _uiState.value.textFieldValue.text)
        }
    }

    fun toggleFontDialog(show: Boolean) {
        _uiState.update { it.copy(showFontDialog = show) }
    }

    fun toggleWipeConfirmDialog(show: Boolean) {
        _uiState.update { it.copy(showWipeConfirmDialog = show) }
    }

    fun clearScratchpad() {
        val current = _uiState.value.textFieldValue
        if (current.text.isNotEmpty()) {
            pushUndoSnapshot(current)
            redoStack.clear()
            resetUndoGrouping()
        }
        _uiState.update {
            it.copy(
                textFieldValue = TextFieldValue(""),
                activeNoteId = null,
                activeNoteTitle = "Untitled",
                isModified = false,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                showWipeConfirmDialog = false,
                statusMessage = "Scratchpad cleared"
            )
        }
        prefs.edit().putString("draft_text", "").apply()
    }

    fun newScratchpad() {
        undoStack.clear()
        redoStack.clear()
        resetUndoGrouping()
        _uiState.update {
            it.copy(
                textFieldValue = TextFieldValue(""),
                activeNoteId = null,
                activeNoteTitle = "Untitled",
                isModified = false,
                canUndo = false,
                canRedo = false,
                showSavedNotesSheet = false,
                statusMessage = "New scratchpad ready"
            )
        }
        prefs.edit().putString("draft_text", "").apply()
    }

    fun saveCurrentNote(customTitle: String? = null) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val content = currentState.textFieldValue.text

            // Auto-detect title from first non-empty line or custom title
            val resolvedTitle = when {
                !customTitle.isNullOrBlank() -> customTitle.trim()
                currentState.activeNoteTitle != "Untitled" && currentState.activeNoteTitle.isNotBlank() -> currentState.activeNoteTitle
                else -> {
                    val firstLine = content.lineSequence()
                        .map { it.trim().removePrefix("#").trim() }
                        .firstOrNull { it.isNotEmpty() }
                    firstLine?.take(30) ?: "Scratchpad ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date())}"
                }
            }

            val now = System.currentTimeMillis()
            val existing = currentState.activeNoteId?.let { noteDao.getNoteById(it) }
            val note = NoteEntity(
                id = existing?.id ?: 0L,
                title = resolvedTitle,
                content = content,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                isPinned = existing?.isPinned ?: false,
                wordCount = currentState.wordCount,
                charCount = currentState.charCount
            )

            val savedId = noteDao.insertNote(note)
            val finalId = if (existing == null) savedId else existing.id

            _uiState.update {
                it.copy(
                    activeNoteId = finalId,
                    activeNoteTitle = resolvedTitle,
                    isModified = false,
                    showSaveDialog = false,
                    statusMessage = "Saved \"$resolvedTitle\""
                )
            }
        }
    }

    fun loadNote(note: NoteEntity) {
        undoStack.clear()
        redoStack.clear()
        resetUndoGrouping()
        val textValue = TextFieldValue(note.content, selection = TextRange(note.content.length))
        _uiState.update {
            it.copy(
                textFieldValue = textValue,
                activeNoteId = note.id,
                activeNoteTitle = note.title,
                isModified = false,
                canUndo = false,
                canRedo = false,
                showSavedNotesSheet = false,
                statusMessage = "Loaded \"${note.title}\""
            )
        }
        prefs.edit().putString("draft_text", note.content).apply()
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            noteDao.deleteNote(note)
            _uiState.update {
                it.copy(
                    lastDeletedNote = note,
                    statusMessage = "Note deleted"
                )
            }
            if (_uiState.value.activeNoteId == note.id) {
                _uiState.update {
                    it.copy(
                        activeNoteId = null,
                        activeNoteTitle = "Untitled",
                        isModified = true
                    )
                }
            }
        }
    }

    fun restoreLastDeletedNote() {
        val noteToRestore = _uiState.value.lastDeletedNote ?: return
        viewModelScope.launch {
            noteDao.insertNote(noteToRestore)
            _uiState.update {
                it.copy(
                    lastDeletedNote = null,
                    statusMessage = "Restored \"${noteToRestore.title}\""
                )
            }
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            val updated = note.copy(isPinned = !note.isPinned)
            noteDao.updateNote(updated)
        }
    }

    fun loadExternalFile(fileName: String, content: String) {
        undoStack.clear()
        redoStack.clear()
        resetUndoGrouping()
        val title = fileName.removeSuffix(".txt").removeSuffix(".md")
        val textValue = TextFieldValue(content, selection = TextRange(content.length))
        _uiState.update {
            it.copy(
                textFieldValue = textValue,
                activeNoteId = null,
                activeNoteTitle = title,
                isModified = true,
                canUndo = false,
                canRedo = false,
                statusMessage = "Opened $fileName"
            )
        }
        prefs.edit().putString("draft_text", content).apply()
    }

    fun onFileSavedToStorage(fileName: String) {
        _uiState.update {
            it.copy(statusMessage = "Exported file to $fileName")
        }
    }

    fun showStatusMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        observeNotes(query)
    }

    // Find and Replace logic
    fun setFindQuery(query: String) {
        _uiState.update { it.copy(findQuery = query) }
        updateFindMatches(query, _uiState.value.textFieldValue.text)
    }

    fun setReplaceQuery(query: String) {
        _uiState.update { it.copy(replaceQuery = query) }
    }

    private fun updateFindMatches(query: String, text: String) {
        if (query.isEmpty() || text.isEmpty()) {
            _uiState.update { it.copy(findMatchesCount = 0, currentMatchIndex = 0) }
            return
        }
        val matches = Regex.escape(query).toRegex(RegexOption.IGNORE_CASE).findAll(text).toList()
        _uiState.update { state ->
            val preservedIdx = if (matches.isEmpty()) {
                0
            } else {
                state.currentMatchIndex.coerceIn(0, matches.size)
            }
            state.copy(
                findMatchesCount = matches.size,
                currentMatchIndex = preservedIdx
            )
        }
    }

    fun findNext() {
        val query = _uiState.value.findQuery
        val text = _uiState.value.textFieldValue.text
        if (query.isEmpty() || text.isEmpty()) return

        val regex = Regex.escape(query).toRegex(RegexOption.IGNORE_CASE)
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return

        val currentIdx = _uiState.value.currentMatchIndex
        val nextIdx = if (currentIdx < 1 || currentIdx >= matches.size) 1 else currentIdx + 1
        val match = matches[nextIdx - 1]

        _uiState.update {
            it.copy(
                findMatchesCount = matches.size,
                currentMatchIndex = nextIdx,
                textFieldValue = it.textFieldValue.copy(
                    selection = TextRange(match.range.first, match.range.last + 1)
                )
            )
        }
    }

    fun findPrevious() {
        val query = _uiState.value.findQuery
        val text = _uiState.value.textFieldValue.text
        if (query.isEmpty() || text.isEmpty()) return

        val regex = Regex.escape(query).toRegex(RegexOption.IGNORE_CASE)
        val matches = regex.findAll(text).toList()
        if (matches.isEmpty()) return

        val currentIdx = _uiState.value.currentMatchIndex
        val prevIdx = if (currentIdx <= 1) matches.size else currentIdx - 1
        val match = matches[prevIdx - 1]

        _uiState.update {
            it.copy(
                findMatchesCount = matches.size,
                currentMatchIndex = prevIdx,
                textFieldValue = it.textFieldValue.copy(
                    selection = TextRange(match.range.first, match.range.last + 1)
                )
            )
        }
    }

    fun replaceCurrent() {
        val query = _uiState.value.findQuery
        val replacement = _uiState.value.replaceQuery
        val current = _uiState.value.textFieldValue
        val text = current.text
        val selection = current.selection
        if (query.isEmpty() || text.isEmpty()) return

        if (selection.collapsed) {
            findNext()
            return
        }

        val selectedText = text.substring(selection.min, selection.max)
        if (selectedText.equals(query, ignoreCase = true)) {
            val newText = text.substring(0, selection.min) + replacement + text.substring(selection.max)
            val afterPos = selection.min + replacement.length
            onTextChanged(
                TextFieldValue(newText, selection = TextRange(afterPos)),
                forceUndoSnapshot = true
            )

            // Advance strictly past the replaced occurrence (wrapping to first match if none remain after)
            val matches = Regex.escape(query).toRegex(RegexOption.IGNORE_CASE).findAll(newText).toList()
            if (matches.isNotEmpty()) {
                val nextIndex0 = matches.indexOfFirst { it.range.first >= afterPos }.let {
                    if (it >= 0) it else 0
                }
                val match = matches[nextIndex0]
                _uiState.update {
                    it.copy(
                        findMatchesCount = matches.size,
                        currentMatchIndex = nextIndex0 + 1,
                        textFieldValue = it.textFieldValue.copy(
                            selection = TextRange(match.range.first, match.range.last + 1)
                        )
                    )
                }
            }
        } else {
            findNext()
        }
    }

    fun replaceAll() {
        val query = _uiState.value.findQuery
        val replacement = _uiState.value.replaceQuery
        val text = _uiState.value.textFieldValue.text
        if (query.isEmpty() || text.isEmpty()) return

        val regex = Regex.escape(query).toRegex(RegexOption.IGNORE_CASE)
        val newText = text.replace(regex, Regex.escapeReplacement(replacement))
        onTextChanged(TextFieldValue(newText, selection = TextRange(0)), forceUndoSnapshot = true)
        _uiState.update { it.copy(statusMessage = "All occurrences replaced") }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
