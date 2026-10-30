package com.example.ui

import androidx.compose.ui.text.input.TextFieldValue
import com.example.data.NoteEntity

data class ScratchpadUiState(
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val activeNoteId: Long? = null,
    val activeNoteTitle: String = "Untitled",
    val isModified: Boolean = false,
    val fontSizeSp: Int = 15,
    val isMonospace: Boolean = true,
    val isAmoledMode: Boolean = false,
    val isDynamicColor: Boolean = true,
    val useSystemTheme: Boolean = true,
    val isZenMode: Boolean = false,
    val showSavedNotesSheet: Boolean = false,
    val showSaveDialog: Boolean = false,
    val showFindReplace: Boolean = false,
    val showFontDialog: Boolean = false,
    val showWipeConfirmDialog: Boolean = false,
    val searchQuery: String = "",
    val findQuery: String = "",
    val replaceQuery: String = "",
    val findMatchesCount: Int = 0,
    val currentMatchIndex: Int = 0,
    val savedNotes: List<NoteEntity> = emptyList(),
    val statusMessage: String? = null,
    val lastDeletedNote: NoteEntity? = null
) {
    val wordCount: Int
        get() {
            val text = textFieldValue.text.trim()
            if (text.isEmpty()) return 0
            return text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        }

    val charCount: Int
        get() = textFieldValue.text.length

    val lineCount: Int
        get() {
            if (textFieldValue.text.isEmpty()) return 1
            return textFieldValue.text.count { it == '\n' } + 1
        }
}
