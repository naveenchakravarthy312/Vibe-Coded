# Minimal Music Player (Linux)

Ultra-minimalist, zero-chrome, keyboard-driven offline music player for **Linux** (Wayland & X11) built with Python, `PyQt6`, `PyQt6-Multimedia`, and `mutagen` — engineered as a sibling to Scratchpad in the Vibe-Coding Linux Desktop Design System. *(For the separate native macOS version, see [`../../MacOS/Music-Player/`](../../MacOS/Music-Player/README.md).)*

## Design Constraints & Scratchpad Architecture

- **Zero Cover Art:** Never extracts, decodes, caches, or reserves space for album artwork.
- **Zero UI Buttons:** No clickable controls or menu bars.
- **Minimal Canvas:** Displays only Track Name, Artist, a razor-thin `2px` flat progress bar with elapsed `MM:SS`, total duration `MM:SS`, a hideable 4-column aligned playlist table (`H`), and whisper-quiet bottom-right telemetry with transient 2.2s status notifications.
- **Hidden Shortcuts:** No keybinding hints on the main screen. Press `F1` to toggle the centered keyboard shortcuts overlay.
- **Playlists & Persistence:** SQLite Saved Playlists Library (`Ctrl+L`), zero-chrome inline `save as` prompt (`Ctrl+S`), `.m3u` / `.m3u8` import & export, live searchable Track Switcher with queue editing & reordering, `Ctrl+Z` undo, and automatic session/preference restore (`~/.local/share/minimal-music-player/player.db`).
- **Global Unfocused Media Keys:** MPRIS2 D-Bus service (`org.mpris.MediaPlayer2.minimal_music_player`) + direct Linux kernel `/dev/input/event*` reader + X11 `XQueryKeymap` fallback.

## Setup

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
chmod +x music_player.py
```

`music_player.py` automatically detects and `os.execv`s into the local `.venv/bin/python` if launched outside the virtual environment.

## Usage

```bash
# Launch and auto-restore last session (or press O / Ctrl+O to choose a folder)
python3 music_player.py

# Launch directly with a music folder, audio file(s), or .m3u / .m3u8 playlist
python3 music_player.py /path/to/music
python3 music_player.py /path/to/chill.m3u8
```

## Keyboard Shortcuts (`F1` in-app)

### Playback & Queue
| Shortcut | Action |
| :--- | :--- |
| `Space` / `Media Play/Pause` | Play / Pause (works globally even when unfocused) |
| `Right` / `Left` | Seek forward / backward 5 seconds (`Shift` for 15 seconds) |
| `N` / `Down` / `Media Next` | Next track (works globally even when unfocused) |
| `P` / `Up` / `Media Prev` | Previous track (restarts if >3s; works globally when unfocused) |
| `=` / `+` and `-` | Volume up / down 5% |
| `M` | Mute / Unmute |
| `S` | Toggle Shuffle mode |
| `R` | Cycle Repeat mode (`Off` → `All` → `One`) |
| `H` | Show / hide the 4-column aligned playlist table on the main canvas |

### Playlists & Files (Scratchpad Flow)
| Shortcut | Action |
| :--- | :--- |
| `O` / `Ctrl+O` | Open music folder (`.mp3`, `.flac`, `.ogg`, `.opus`, `.m4a`, `.wav`) |
| `Ctrl+Shift+O` | Open `.m3u` / `.m3u8` playlist file |
| `A` / `Ctrl+A` | Append audio files to current queue |
| `L` / `/` | Open live-searchable track switcher (`Up`/`Down`, `Enter` play, `Del`/`Ctrl+D` remove, `Ctrl+Up`/`Down` reorder, `Ctrl+Z` undo, `Escape` close) |
| `Ctrl+L` | Toggle Saved Playlists Library (`Up`/`Down`, `Enter` load, `Ctrl+P` pin, `Ctrl+E` export `.m3u8`, `Del`/`Ctrl+D` delete, `Ctrl+Z` undo, `Escape` close) |
| `Ctrl+S` | Save current queue as playlist (inline zero-chrome `save as` prompt, or updates active saved/M3U playlist) |
| `Ctrl+Shift+S` | Export current queue to `.m3u8` / `.m3u` file |
| `Ctrl+N` / `Ctrl+W` | Clear active queue (`Ctrl+Z` to undo) |

### Typography, Theme & Window
| Shortcut | Action |
| :--- | :--- |
| `F1` | Toggle centered keyboard shortcuts overlay |
| `Ctrl+I` | Cycle bottom-right telemetry detail |
| `Ctrl+Shift+D` | Toggle Dark (`#141414`) / Light (`#f2f2f0`) palette |
| `Ctrl+M` | Toggle Monospace / Sans-Serif typography |
| `Ctrl+=` / `Ctrl+-` / `Ctrl+0` | Scale font size (`8pt`–`48pt`, default `13pt`; `Ctrl+Wheel` supported) |
| `F11` / `Ctrl+Shift+F` | Toggle Fullscreen / Frameless window mode |
| `Escape` | Context-aware close (dismisses inline prompt or overlay; exits at root view) |
| `Ctrl+Q` | Immediate exit and clean audio teardown |

## Window Movement & Resizing (Frameless)

- `Alt + Left Click Drag` anywhere on the canvas moves the window.
- Dragging inside the `24px` outer viewport margin moves the window when frameless.
- Hovering/dragging the `8px` outer perimeter resizes the window when frameless.

