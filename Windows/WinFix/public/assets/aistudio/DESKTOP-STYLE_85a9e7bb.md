# Desktop Design System & Style Specification (Linux)

This document defines the zero-chrome, distraction-free desktop styling and architecture guidelines for Linux applications in the **Vibe-Coding** ecosystem (built with Python, PyQt6, or Tkinter for Wayland and KDE Plasma / X11).

---

## 1. Core Principles

* **Zero Chrome:** No native menu bars, toolbars, bevels, or button boxes. 100% of the window surface is dedicated to the functional canvas.
* **Pure Flat Charcoal Canvas:** Matte `#141414` background without gradients, dividers, or drop-shadows.
* **Keyboard-First:** Full keyboard navigation, inline controls, and instant exits without touching the mouse.
* **Whisper-Quiet Telemetry:** Peripheral indicators (counters, hotkeys, status) sit unobtrusively in the bottom-right corner in low-contrast typography.
* **Instant Lifecycle:** Rapid in-memory execution and immediate memory-wipe on exit (`Escape` / `Ctrl+Q`).

---

## 2. Color Palettes

### Primary Dark Palette (`PALETTE_DARK`)

```python
PALETTE_DARK = {
    "canvas": "#141414",       # Matte Dark Canvas (100% flat charcoal, zero borders)
    "fg": "#e0e0e0",           # Soft Off-White Foreground (high legibility, no glare)
    "sel_bg": "#323842",       # Muted Slate Selection background
    "sel_fg": "#ffffff",       # Muted Slate Selection text
    "meta": "#585858",         # Whisper-Quiet Metadata (bottom-right counters/status)
    "active": "#e0e0e0",       # Crisp active element / input state
    "match_bg": "#242932",     # Inactive find/replace match highlight
}
```

### Secondary Light Palette (`PALETTE_LIGHT`) — *Toggleable via `Ctrl+Shift+D`*

```python
PALETTE_LIGHT = {
    "canvas": "#f2f2f0",       # Warm, soft off-white surface
    "fg": "#1e1e1e",           # Deep charcoal text
    "sel_bg": "#323842",       # Slate selection background
    "sel_fg": "#ffffff",       # White selection text
    "meta": "#888888",         # Soft gray metadata
    "active": "#141414",       # Crisp dark active state
    "match_bg": "#dcdcdc",     # Inactive find match highlight
}
```

---

## 3. Typography & Caret Specs

### Monospace Detection Stack
Applications automatically probe and load the highest-priority installed monospace font:

```python
PREFERRED_MONO_FONTS = [
    "JetBrains Mono",
    "Fira Code",
    "Cascadia Code",
    "Iosevka",
    "Source Code Pro",
    "Ubuntu Mono",
    "DejaVu Sans Mono",
    "monospace",
]
```

### Sans-Serif Fallback (UI / Overlays)
```python
PREFERRED_SANS_FONTS = [
    "Roboto",
    "Inter",
    "Cantarell",
    "Ubuntu",
    "Noto Sans",
    "DejaVu Sans",
    "sans-serif",
]
```

### Sizing & Dynamic Scaling
* **Default Size:** `13pt`
* **Scale Range:** Min `8pt` — Max `48pt`
* **Shortcut Zoom:**
  * `Ctrl + =` / `Ctrl + +`: Scale up (+1pt)
  * `Ctrl + -`: Scale down (-1pt)
  * `Ctrl + 0`: Reset to default (13pt)
  * `Ctrl + Mouse Wheel Up/Down`: Zoom in / out

### Caret & Text Input
* **Cursor Width:** `2px` crisp solid bar.
* **Blink Timing:** `1000ms` cycle (`500ms` on / `500ms` off).
* **Sanitized Paste:** Strips rich-text styling and carriage returns (`\r`) before inserting into active text fields.

---

## 4. Layout Geometry & Window Management

```
+-----------------------------------------------------------+
| [24px Viewport Margin]                                    |
|                                                           |
|                     INTERACTIVE CANVAS                    |
|                                                           |
|                                                           |
|                                                           |
| [24px Viewport Margin]       [idle · 0 clicks · F6 #585858]|
+-----------------------------------------------------------+
```

* **Viewport Margin:** Strict `24px` internal padding along all four edges of the window canvas.
* **Frameless Toggle (`F11` or `Ctrl+Shift+F`):** Strips system title bar and borders completely.
* **Canvas Drag Movement:** 
  * `Alt + Left Click Drag`: Move window from anywhere on the canvas.
  * Dragging inside the `24px` outer viewport margin moves the window when frameless.
* **Edge Resize Margins:** An `8px` perimeter border area triggers diagonal/horizontal/vertical resize handles when frameless.
* **Status Anchor:** Bottom-right corner reserved for minimal, unbordered metadata strings (e.g., `12 words` or `running · 1,420 clicks · F6`).

---

## 5. Keyboard Navigation & Shortcuts

| Shortcut | Action | Behavior |
| :--- | :--- | :--- |
| **`Escape`** | Context-Aware Close / Exit | Closes active search bar, popup, or overlay; exits immediately if at root buffer. |
| **`Ctrl + Q`** | Fast Exit / Wipe | Clears in-memory buffer and terminates process. |
| **`Ctrl + F` / `Ctrl + H`** | Find & Replace Bar | Toggles inline bottom search/replace strip (no external popups). |
| **`Ctrl + Shift + D`** | Toggle Dark/Light Theme | Switches between `#141414` matte dark and `#f2f2f0` light canvas. |
| **`Ctrl + M`** | Toggle Font Family | Flips between Monospace (`JetBrains Mono`) and Sans (`Roboto`). |
| **`Ctrl + I`** | Cycle Metadata Detail | Cycles bottom-right counter detail (`words` $\to$ `words & chars` $\to$ `full stats`). |
| **`Ctrl + T`** | Insert Timestamp | Inserts `[YYYY-MM-DD HH:MM] ` at caret. |
| **`Ctrl + Shift + L`**| Checklist Item | Inserts `- [ ] ` at caret. |
| **`Ctrl + Shift + B`**| Bullet Point | Inserts `• ` at caret. |
| **`Ctrl + Shift + C`**| Copy All | Copies active buffer directly to clipboard. |

---

## 6. Linux System Integration

* **Wayland & KDE Plasma Native:** Input simulation and global hotkeys prioritize Linux kernel `uinput` (`python-evdev`) and XDG Desktop Portals before falling back to X11 `libXtst`.
* **Self-Healing Environment:** Executables auto-detect and re-exec into local `.venv` environments if launched without virtualenv activation.
* **Desktop Launcher (`.desktop`):**
  * Placed in `~/.local/share/applications/` or project folder.
  * Specifies `StartupWMClass` matching the window title.
  * Links to a crisp local `icon.png` / `icon.svg`.
