# Minimal Music Player (macOS)

Ultra-minimalist, zero-chrome, keyboard-driven offline music player for **macOS** built with Python, `PyQt6`, `PyQt6-Multimedia`, and `mutagen` — engineered as a sibling to Scratchpad (`MacOS/Notepad`) in the Vibe-Coding Desktop Design System.

## Design Constraints & Architecture

- **Zero Cover Art:** Never extracts, decodes, caches, or reserves space for album artwork.
- **Zero UI Buttons:** No clickable controls, toolbars, or native macOS menu bars (`AA_DontUseNativeMenuBar`).
- **Minimal Canvas:** Displays only Track Name, Artist, a razor-thin `2px` flat progress bar with elapsed `MM:SS`, total duration `MM:SS`, a hideable 4-column aligned playlist table (`H`), and whisper-quiet bottom-right telemetry with transient 2.2s status notifications.
- **Hidden Shortcuts:** No keybinding hints on the main screen. Press `F1` to toggle the centered keyboard shortcuts overlay.
- **Playlists & Persistence:** SQLite Saved Playlists Library (`Cmd+L`), zero-chrome inline `save as` prompt (`Cmd+S`), `.m3u` / `.m3u8` import & export, live searchable Track Switcher with queue editing & reordering, `Cmd+Z` undo, and automatic session/preference restore (`~/Library/Application Support/minimal-music-player/player.db`).
- **Native macOS Media Keys (Unfocused):** Native `MediaPlayer.framework` (`MPRemoteCommandCenter` + `MPNowPlayingInfoCenter`) + CoreGraphics `CGEventTap` (`NX_SYSDEFINED` `F7`/`F8`/`F9`, Touch Bar, AirPods, and Control Center) with zero third-party dependencies.
- **Finder & Dock Integration:** Drag audio files, folders, or `.m3u`/`.m3u8` playlists onto `Minimal Music Player.app`, the Dock icon, or the player canvas (`QFileOpenEvent`).

## Setup & Launching on macOS

1. Create a virtual environment and install dependencies:
   ```bash
   python3 -m venv .venv
   .venv/bin/pip install -r requirements.txt
   chmod +x music_player.py "Minimal Music Player.command" "Minimal Music Player.app/Contents/MacOS/MinimalMusicPlayer"
   ```

2. Launch via Finder, Dock, or Terminal:
   - Double-click `Minimal Music Player.app` (or install a copy into `~/Applications` via `python3 music_player.py --install-macos-app`)
   - Double-click `Minimal Music Player.command`
   - Or launch from Terminal: `python3 music_player.py [/path/to/music_or_playlist]`

`music_player.py` automatically detects and `os.execv`s into the local `.venv/bin/python3` if launched outside the virtual environment.

## Keyboard Shortcuts (`F1` in-app)

*(Both `Cmd` `⌘` and physical `Ctrl` `⌃` work for modifier shortcuts.)*

### Playback & Queue
| Shortcut | Action |
| :--- | :--- |
| `Space` / `F8` (`Media Play/Pause`) | Play / Pause (works globally even when unfocused) |
| `Left` / `Right` | Seek backward / forward 5 seconds (`Shift` for 15 seconds) |
| `N` / `Down` / `F9` (`Media Next`) | Next track (works globally even when unfocused) |
| `P` / `Up` / `F7` (`Media Prev`) | Previous track (restarts if >3s; works globally when unfocused) |
| `=` / `+` and `-` | Volume up / down 5% |
| `M` | Mute / Unmute |
| `S` | Toggle Shuffle mode |
| `R` | Cycle Repeat mode (`Off` → `All` → `One`) |
| `H` | Show / hide the 4-column aligned playlist table on the main canvas |

### Playlists & Files
| Shortcut | Action |
| :--- | :--- |
| `O` / `Cmd+O` | Open music folder (`.mp3`, `.flac`, `.ogg`, `.opus`, `.m4a`, `.wav`) |
| `Cmd+Shift+O` | Open `.m3u` / `.m3u8` playlist file |
| `A` / `Cmd+A` | Append audio files to current queue |
| `L` / `/` | Open live-searchable track switcher (`Up`/`Down`, `Return` play, `Fn+Del`/`Cmd+Backspace`/`Cmd+D` remove, `Cmd+Up`/`Down` reorder, `Cmd+Z` undo, `Escape` close) |
| `Cmd+L` | Toggle Saved Playlists Library (`Up`/`Down`, `Return` load, `Cmd+P` pin, `Cmd+E` export `.m3u8`, `Fn+Del`/`Cmd+Backspace`/`Cmd+D` delete, `Cmd+Z` undo, `Escape` close) |
| `Cmd+S` | Save current queue as playlist (inline zero-chrome `save as` prompt, or updates active saved/M3U playlist) |
| `Cmd+Shift+S` | Export current queue to `.m3u8` / `.m3u` file |
| `Cmd+N` / `Cmd+W` | Clear active queue (`Cmd+Z` to undo) |

### Typography, Theme & Window
| Shortcut | Action |
| :--- | :--- |
| `F1` | Toggle centered keyboard shortcuts overlay |
| `Cmd+I` | Cycle bottom-right telemetry detail |
| `Cmd+Shift+D` | Toggle Dark (`#141414`) / Light (`#f2f2f0`) palette |
| `Cmd+M` | Toggle Monospace (`SF Mono`/`Menlo`/`JetBrains Mono`) / Sans-Serif (`SF Pro Text`/`Helvetica Neue`/`Roboto`) |
| `Cmd+=` / `Cmd+-` / `Cmd+0` / `Pinch` | Scale font size (`8pt`–`48pt`, default `13pt`; `Cmd+Wheel` & trackpad pinch supported) |
| `F11` / `Ctrl+Cmd+F` / `Cmd+Shift+F` | Toggle Fullscreen / Frameless window mode |
| `Escape` | Context-aware close (dismisses inline prompt or overlay; exits at root view) |
| `Cmd+Q` | Immediate exit and clean audio teardown |

## Window Movement & Resizing (Frameless)

- `Option + Left Click Drag` anywhere on the canvas moves the window.
- Dragging inside the `24px` outer viewport margin moves the window when frameless.
- Hovering/dragging the `8px` outer perimeter resizes the window when frameless.
