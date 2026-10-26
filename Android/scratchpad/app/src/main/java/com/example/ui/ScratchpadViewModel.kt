package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.NoteEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScratchpadViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val noteDao = db.noteDao()
    private val prefs = application.getSharedPreferences("scratchpad_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(ScratchpadUiState())
    val uiState: StateFlow<ScratchpadUiState> = _uiState.asStateFlow()

    // Undo / Redo history stacks
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()
    private var isUndoRedoAction = false

    init {
        // Restore saved font size and preferences
        val savedFontSize = prefs.getInt("font_size", 15)
        val savedMonospace = prefs.getBoolean("is_monospace", true)
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
                isMonospace = savedMonospace
            )
        }

        // Collect saved notes from Room database
        viewModelScope.launch {
            noteDao.getAllNotes().collectLatest { notes ->
                _uiState.update { it.copy(savedNotes = notes) }
            }
        }
    }

    fun onTextChanged(newValue: TextFieldValue) {
        val current = _uiState.value.textFieldValue
        if (!isUndoRedoAction && current.text != newValue.text) {
            if (undoStack.size >= 50) {
                undoStack.removeFirst()
            }
            undoStack.addLast(current)
            redoStack.clear()
        }

        _uiState.update {
            it.copy(
                textFieldValue = newValue,
                isModified = true
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
            isUndoRedoAction = true
            _uiState.update { it.copy(textFieldValue = previous, isModified = true) }
            prefs.edit().putString("draft_text", previous.text).apply()
            isUndoRedoAction = false
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeLast()
            undoStack.addLast(_uiState.value.textFieldValue)
            isUndoRedoAction = true
            _uiState.update { it.copy(textFieldValue = next, isModified = true) }
            prefs.edit().putString("draft_text", next.text).apply()
            isUndoRedoAction = false
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

        onTextChanged(newValue)
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

    fun toggleMonospace() {
        val next = !_uiState.value.isMonospace
        _uiState.update { it.copy(isMonospace = next) }
        prefs.edit().putBoolean("is_monospace", next).apply()
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
            undoStack.addLast(current)
            redoStack.clear()
        }
        _uiState.update {
            it.copy(
                textFieldValue = TextFieldValue(""),
                activeNoteId = null,
                activeNoteTitle = "Untitled",
                isModified = false,
                showWipeConfirmDialog = false,
                statusMessage = "Scratchpad cleared"
            )
        }
        prefs.edit().putString("draft_text", "").apply()
    }

    fun newScratchpad() {
        _uiState.update {
            it.copy(
                textFieldValue = TextFieldValue(""),
                activeNoteId = null,
                activeNoteTitle = "Untitled",
                isModified = false,
                showSavedNotesSheet = false,
                statusMessage = "New scratchpad ready"
            )
        }
        undoStack.clear()
        redoStack.clear()
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

            val note = NoteEntity(
                id = currentState.activeNoteId ?: 0L,
                title = resolvedTitle,
                content = content,
                updatedAt = System.currentTimeMillis(),
                wordCount = currentState.wordCount,
                charCount = currentState.charCount
            )

            val savedId = noteDao.insertNote(note)
            val finalId = if (currentState.activeNoteId == null || currentState.activeNoteId == 0L) savedId else currentState.activeNoteId

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
        val textValue = TextFieldValue(note.content, selection = TextRange(note.content.length))
        _uiState.update {
            it.copy(
                textFieldValue = textValue,
                activeNoteId = note.id,
                activeNoteTitle = note.title,
                isModified = false,
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
        val title = fileName.removeSuffix(".txt").removeSuffix(".md")
        val textValue = TextFieldValue(content, selection = TextRange(content.length))
        _uiState.update {
            it.copy(
                textFieldValue = textValue,
                activeNoteId = null,
                activeNoteTitle = title,
                isModified = true,
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

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            if (query.isBlank()) {
                noteDao.getAllNotes().collectLatest { notes ->
                    _uiState.update { it.copy(savedNotes = notes) }
                }
            } else {
                noteDao.searchNotes(query).collectLatest { notes ->
                    _uiState.update { it.copy(savedNotes = notes) }
                }
            }
        }
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
        _uiState.update {
            it.copy(
                findMatchesCount = matches.size,
                currentMatchIndex = if (matches.isNotEmpty()) 1 else 0
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
        val nextIdx = if (currentIdx >= matches.size) 1 else currentIdx + 1
        val match = matches[nextIdx - 1]

        _uiState.update {
            it.copy(
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

        if (selection.collapsed) {
            findNext()
            return
        }

        val selectedText = text.substring(selection.min, selection.max)
        if (selectedText.equals(query, ignoreCase = true)) {
            val newText = text.substring(0, selection.min) + replacement + text.substring(selection.max)
            val newCursor = selection.min + replacement.length
            onTextChanged(TextFieldValue(newText, selection = TextRange(newCursor)))
            findNext()
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
        val newText = text.replace(regex, replacement)
        onTextChanged(TextFieldValue(newText, selection = TextRange(0)))
        _uiState.update { it.copy(findMatchesCount = 0, currentMatchIndex = 0, statusMessage = "All occurrences replaced") }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
