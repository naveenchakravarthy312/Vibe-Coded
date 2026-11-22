================================================================================
MEGAPAD — ULTRA-LIGHTWEIGHT HIGH-CAPACITY PLAIN-TEXT EDITOR
================================================================================

MegaPad is an ultra-minimalist, zero-chrome plain-text and markdown editor engineered
specifically to view, search, and edit massive documents with 10+ million words
(~50–100+ MB) without UI lag, tokenization freezes, or memory ballooning.

It maintains the exact zero-chrome Vibe-Coding design aesthetic:
- 100% full-bleed flat charcoal matte canvas (#141414, toggleable to #f2f2f0)
- Soft off-white typography (#e0e0e0) and muted slate selection (#323842)
- 24px internal viewport margins and crisp 2px blinking cursor
- Whisper-quiet live telemetry corner (#585858) with asynchronous word counting
- Memory footprint: ~150 MB baseline, ~250–320 MB while holding 10 million words

--------------------------------------------------------------------------------
KEY SHORTCUTS
--------------------------------------------------------------------------------

FILE OPERATIONS
  Ctrl + O             Open File (or drag and drop any file directly onto the canvas)
  Ctrl + S             Save File (saves in-place; opens Save As if untitled)
  Ctrl + Shift + S     Save As (save buffer to a new path)
  Ctrl + N             New Document (prompts if current document has unsaved edits)
  Ctrl + Shift + C     Copy All (copies entire text directly to system clipboard)

HIGH-PERFORMANCE FIND & REPLACE
  Ctrl + F             Toggle Find & Replace Bar
  Ctrl + H             Toggle Find & Replace Bar
  Enter                Find Next match (searches block-by-block in native C++)
  Shift + Enter        Find Previous match
  Enter (in replace)   Replace current match and advance
  Ctrl + Alt + Enter   Replace All occurrences (atomic edit block)
  Escape               Close Find Bar and return focus to text canvas

LAYOUT & VIEW MODES
  Alt + Z              Toggle Word Wrap (Widget Width Wrap <-> No Wrap)
                       *Tip: Use No Wrap for huge single-line data dumps or logs*
  Ctrl + I             Cycle Telemetry Detail:
                         Mode 0: 10,000,000 words
                         Mode 1: 10,000,000 words · 48.1M chars · 500k lines
                         Mode 2: 10,000,000w · 48.1Mc · 500kL · 13pt · wrap · 185MB RAM
  Ctrl + Shift + D     Toggle Dark Theme (#141414) / Light Theme (#f2f2f0)
  Ctrl + M             Toggle Font Family (Monospace <-> Clean Sans)
  F11 / Ctrl+Shift+F   Toggle Frameless Window Mode (zero title bar/borders)

ZOOM & TYPOGRAPHY
  Ctrl + =  /  Ctrl + +   Zoom In (+1pt)
  Ctrl + -                Zoom Out (-1pt)
  Ctrl + 0                Reset Font Size (13pt default)
  Ctrl + Mouse Wheel      Dynamic Font Zoom

QUICK INSERTS
  Ctrl + T             Insert Timestamp: [YYYY-MM-DD HH:MM] 
  Ctrl + Shift + L     Insert Checklist item: - [ ] 
  Ctrl + Shift + B     Insert Bullet point: • 

WINDOW & EXIT
  Alt + Left Click Drag   Move window from anywhere on canvas
  Escape                  Context-aware cancel (closes search/prompt, or prompts exit)
  Ctrl + Q                Fast Quit (prompts if unsaved changes, wipes memory on exit)

--------------------------------------------------------------------------------
HOW MEGAPAD HANDLES 10 MILLION WORDS WITHOUT HIGH RAM
--------------------------------------------------------------------------------

1. Zero Database Disk Thrashing:
   Unlike traditional scratchpads that serialize the entire document into SQLite
   draft tables on keystrokes, MegaPad works directly with in-memory buffers and
   atomic on-demand disk writes.

2. Non-Allocating Asynchronous Word Counter:
   Word counting is offloaded to a dedicated background QThread running a non-
   allocating regex iterator (`re.finditer`). It calculates counts without
   instantiating millions of Python `str` heap objects, and debounces requests so
   typing is always 100% fluid at 60/144fps.

3. Native C++ Piece-Table Search:
   Find and Replace directly queries Qt's internal `QTextDocument.find()` piece
   table without duplicating multi-megabyte strings into the Python heap.

4. Live Memory Telemetry:
   Pressing `Ctrl + I` twice activates Mode 2 telemetry, showing real-time resident
   set memory (RSS) so you can monitor RAM consumption right in the bottom corner.
