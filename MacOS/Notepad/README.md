================================================================================
SCRATCHPAD (macOS) — KEYBOARD SHORTCUTS & USAGE INSTRUCTIONS
================================================================================

Scratchpad is an ultra-minimalist, zero-chrome, distraction-free text and
markdown editor for macOS. The interface provides 100% full-canvas writing
space without toolbars, menu bars, or visible buttons. All functionality is
driven via keyboard shortcuts and native macOS gestures.

--------------------------------------------------------------------------------
INSTALLATION & LAUNCHING ON macOS
--------------------------------------------------------------------------------
1. Create a virtual environment and install PyQt6:
     python3 -m venv .venv
     .venv/bin/pip install -r requirements.txt

2. Launch via Finder, Dock, or Terminal:
   • Double-click `Scratchpad.app` (or drag it to `/Applications` or the Dock)
   • Double-click `Scratchpad.command`
   • Or launch from Terminal: `./ephemeral_pad.py [optional_file.md]`

3. Data & Preferences Location:
   • `~/Library/Application Support/ephemeral-scratchpad/scratchpad.db`

--------------------------------------------------------------------------------
FILE & SCRATCHPAD MANAGEMENT
--------------------------------------------------------------------------------
Cmd + N
  • New Scratchpad: Resets the editor to a fresh, empty buffer.

Cmd + O
  • Open File: Opens a native macOS file dialog to select and load any .txt,
    .md, or plain text file.
  • You can also drag-and-drop .txt / .md files directly into the window,
    onto the `Scratchpad.app` icon in Finder/Dock, or pass via Terminal.

Cmd + S
  • Save Note / File:
    - If an external .txt / .md file is open, saves changes directly to it.
    - If a note from the Saved Notes Library is open, updates it in place.
    - Otherwise, opens an inline "save as" prompt to name and save the note
      into the internal database.

Cmd + Shift + S
  • Save As File: Opens a file dialog to export/save the current buffer as a
    new .txt or .md file anywhere on your Mac.

Cmd + L  /  Cmd + Shift + O
  • Saved Notes Library: Toggles the zero-chrome library viewer.
    - Type any characters to instantly filter/search saved notes.
    - Up / Down arrow keys: Navigate note list.
    - Return / Enter: Open/load the selected note into the editor.
    - Cmd + P: Pin / unpin the selected note (pinned notes stay at top).
    - Cmd + E: Export the selected note to a .txt or .md file.
    - Cmd + Delete (Cmd + Backspace)  or  Fn + Delete  or  Cmd + D:
      Delete the selected note.
    - Cmd + Z: Undo and restore the last deleted note.
    - Escape: Return to the editor canvas.

--------------------------------------------------------------------------------
EDITING & TEXT MANIPULATION
--------------------------------------------------------------------------------
Tab
  • Native Tab key: Inserts a standard 4-space indentation.

Cmd + Z
  • Undo: Reverts your recent text edits.

Cmd + Shift + Z  /  Cmd + Y
  • Redo: Reapplies undone edits.

Cmd + W  /  Cmd + Shift + Delete
  • Clear / Wipe Buffer: Instantly clears the entire text buffer.
    Can be undone immediately with Cmd + Z.

Cmd + Shift + C
  • Copy All: Copies the entire scratchpad text to your macOS clipboard.

Cmd + T
  • Insert Timestamp: Inserts `[YYYY-MM-DD HH:MM] ` at the cursor position.

Cmd + Shift + L
  • Insert Checklist Item: Inserts `- [ ] ` at the cursor position.

Cmd + Shift + B
  • Insert Bullet Point: Inserts `• ` at the cursor position.

--------------------------------------------------------------------------------
FIND & REPLACE
--------------------------------------------------------------------------------
Cmd + F  /  Cmd + H  /  Cmd + Option + F
  • Find & Replace: Toggles the minimal inline search/replace bar at the bottom.
  • Inside Find input:
    - Return: Jump to next match.
    - Shift + Return: Jump to previous match.
    - Tab: Move focus to the Replace input.
    - Cmd + Return: Replace current match and jump to next.
    - Escape: Close the Find & Replace bar and return to editor.
  • Inside Replace input:
    - Cmd + Return: Replace current match.
    - Cmd + Option + Return: Replace all matches across the document.
    - Escape: Close the Find & Replace bar and return to editor.

--------------------------------------------------------------------------------
TYPOGRAPHY, THEME & DISPLAY
--------------------------------------------------------------------------------
Cmd + =  /  Cmd + +  /  Cmd + Scroll Up  /  Trackpad Pinch Out
  • Increase Font Size: Scales typography up by 1pt.

Cmd + -  /  Cmd + Scroll Down  /  Trackpad Pinch In
  • Decrease Font Size: Scales typography down by 1pt.

Cmd + 0
  • Reset Font Size: Resets typography to default (13pt).

Cmd + M
  • Toggle Font Family: Switches between Monospace (JetBrains Mono / SF Mono /
    Menlo) and Sans-Serif (Roboto / SF Pro Text).

Cmd + Shift + D
  • Toggle Surface Theme: Cycles between Matte Dark (#141414) and
    Soft Light (#f2f2f0).

Cmd + I  /  Click Bottom-Right Counter
  • Cycle Metadata Stats:
    - Mode 1: Word count (e.g. `12 words`)
    - Mode 2: Words and characters (e.g. `12 words · 64 chars`)
    - Mode 3: Detailed (`12w · 64c · 3L · 13pt`)

--------------------------------------------------------------------------------
WINDOW MANAGEMENT & SYSTEM SHORTCUTS
--------------------------------------------------------------------------------
Ctrl + Cmd + F  /  Cmd + Shift + F  /  F11
  • Toggle Frameless Mode: Removes macOS window titlebar and borders for
    complete distraction-free editing.
  • Window Movement:
    - Option + Left Click Drag: Move window from anywhere on canvas.
    - Margin Drag: In frameless mode, dragging within 24px of the edge moves
      the window.

Cmd + /  /  Cmd + ?  /  F1
  • Shortcuts Help Sheet: Toggles the zero-chrome in-app shortcut reference.

Escape  /  Cmd + Q
  • Context-aware Escape / Exit:
    - If the Saved Notes Library, Help view, Save prompt, or Find & Replace
      bar is open: Closes that overlay and returns to the editor.
    - If in normal editing mode: Saves draft state, wipes RAM buffer, and
      immediately exits the app.
================================================================================
