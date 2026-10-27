================================================================================
SCRATCHPAD — KEYBOARD SHORTCUTS & USAGE INSTRUCTIONS
================================================================================

Scratchpad is an ultra-minimalist, zero-chrome, distraction-free text and
markdown editor for Linux. The interface provides 100% full-canvas writing
space without toolbars, menu bars, or visible buttons. All functionality is
driven via keyboard shortcuts.

--------------------------------------------------------------------------------
FILE & SCRATCHPAD MANAGEMENT
--------------------------------------------------------------------------------
Ctrl + N
  • New Scratchpad: Resets the editor to a fresh, empty buffer.

Ctrl + O
  • Open File: Opens a system file dialog to select and load any .txt, .md,
    or plain text file.
  • You can also drag-and-drop .txt / .md files directly into the window,
    or launch via terminal: `./ephemeral_pad.py notes.md`

Ctrl + S
  • Save Note / File:
    - If an external .txt / .md file is open, saves changes directly to it.
    - If a note from the Saved Notes Library is open, updates it in place.
    - Otherwise, opens an inline "save as" prompt to name and save the note
      into the internal database.

Ctrl + Shift + S
  • Save As File: Opens a file dialog to export/save the current buffer as a
    new .txt or .md file anywhere on your filesystem.

Ctrl + L  /  Ctrl + Shift + O
  • Saved Notes Library: Toggles the zero-chrome library viewer.
    - Type any characters to instantly filter/search saved notes.
    - Up / Down arrow keys: Navigate note list.
    - Enter: Open/load the selected note into the editor.
    - Ctrl + P: Pin / unpin the selected note (pinned notes stay at top).
    - Ctrl + E: Export the selected note to a .txt or .md file.
    - Delete  or  Ctrl + D: Delete the selected note.
    - Ctrl + Z: Undo and restore the last deleted note.
    - Escape: Return to the editor canvas.

--------------------------------------------------------------------------------
EDITING & TEXT MANIPULATION
--------------------------------------------------------------------------------
Tab
  • Native Tab key: Inserts a standard 4-space indentation.

Ctrl + Z
  • Undo: Reverts your recent text edits.

Ctrl + Y  /  Ctrl + Shift + Z
  • Redo: Reapplies undone edits.

Ctrl + W  /  Ctrl + Shift + Backspace
  • Clear / Wipe Buffer: Instantly clears the entire text buffer.
    Can be undone immediately with Ctrl + Z.

Ctrl + Shift + C
  • Copy All: Copies the entire scratchpad text to your system clipboard.

Ctrl + T
  • Insert Timestamp: Inserts `[YYYY-MM-DD HH:MM] ` at the cursor position.

Ctrl + Shift + L
  • Insert Checklist Item: Inserts `- [ ] ` at the cursor position.

Ctrl + Shift + B
  • Insert Bullet Point: Inserts `• ` at the cursor position.

--------------------------------------------------------------------------------
FIND & REPLACE
--------------------------------------------------------------------------------
Ctrl + F  /  Ctrl + H
  • Find & Replace: Toggles the minimal inline search/replace bar at the bottom.
  • Inside Find input:
    - Enter: Jump to next match.
    - Shift + Enter: Jump to previous match.
    - Tab: Move focus to the Replace input.
    - Ctrl + Enter: Replace current match and jump to next.
    - Escape: Close the Find & Replace bar and return to editor.
  • Inside Replace input:
    - Ctrl + Enter: Replace current match.
    - Ctrl + Alt + Enter: Replace all matches across the document.
    - Escape: Close the Find & Replace bar and return to editor.

--------------------------------------------------------------------------------
TYPOGRAPHY, THEME & DISPLAY
--------------------------------------------------------------------------------
Ctrl + =  /  Ctrl + +  /  Ctrl + Mouse Wheel Up
  • Increase Font Size: Scales typography up by 1pt.

Ctrl + -  /  Ctrl + Mouse Wheel Down
  • Decrease Font Size: Scales typography down by 1pt.

Ctrl + 0
  • Reset Font Size: Resets typography to default (13pt).

Ctrl + M
  • Toggle Font Family: Switches between Monospace (JetBrains Mono) and
    Sans-Serif (Roboto / system sans).

Ctrl + Shift + D
  • Toggle Surface Theme: Cycles between Matte Dark (#141414) and
    Soft Light (#f2f2f0).

Ctrl + I  /  Click Bottom-Right Counter
  • Cycle Metadata Stats:
    - Mode 1: Word count (e.g. `12 words`)
    - Mode 2: Words and characters (e.g. `12 words · 64 chars`)
    - Mode 3: Detailed (`12w · 64c · 3L · 13pt`)

--------------------------------------------------------------------------------
WINDOW MANAGEMENT & SYSTEM SHORTCUTS
--------------------------------------------------------------------------------
F11  /  Ctrl + Shift + F
  • Toggle Frameless Mode: Removes system window borders and titlebar for
    complete distraction-free editing.
  • Window Movement / Resizing:
    - Alt + Left Click Drag: Move window from anywhere on canvas.
    - Margin Drag: In frameless mode, dragging within 24px of the edge moves
      the window, and dragging on the border edges resizes it.

F1  /  Ctrl + /
  • Shortcuts Help Sheet: Toggles the zero-chrome in-app shortcut reference.

Escape  /  Ctrl + Q
  • Context-aware Escape / Exit:
    - If the Saved Notes Library, Help view, Save prompt, or Find & Replace
      bar is open: Closes that overlay and returns to the editor.
    - If in normal editing mode: Wipes memory and immediately exits the app.
================================================================================
