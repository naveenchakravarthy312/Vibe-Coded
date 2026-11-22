# YT-DLP Desktop Downloader (Linux / Wayland & KDE Plasma)

A clean, modern, zero-chrome desktop GUI wrapper for `yt-dlp` on Linux built with Python and PyQt6. Designed according to the **Desktop Design System & Style Specification (Linux)** (`DESKTOP-STYLE.md`) in the Vibe-Coding ecosystem.

---

## Key Principles & Design Specifications

1. **Pure Flat Charcoal Canvas (`#141414`):**
   - 100% flat matte charcoal surface with zero card enclosures, gradients, or blue accents.
   - High-legibility soft off-white typography (`#e0e0e0`) and whisper-quiet metadata (`#585858`).
   - Strict `24px` internal viewport margins along all four edges.

2. **Zero-Button, 100% Keyboard-Driven Controls:**
   - **Zero Buttons & Zero Headers:** 100% of the canvas surface is dedicated directly to functional inputs, concise checkboxes (`Playlist`, `Number tracks`, `Sync`), and progress telemetry.
   - **Uniform Monospace Typography:** Uses the system's preferred monospace stack (`JetBrains Mono`, `Fira Code`, `Cascadia Code`, `monospace`) uniformly across every control.
   - **Inline `F1` Shortcuts Reference:** Press `F1` at any time to toggle the inline keyboard shortcut cheat sheet.
   - **Media-Aware Clipboard Detection:** Silently detects links from recognized media platforms (YouTube, SoundCloud, Bandcamp, Vimeo, Twitch, etc.) and auto-fills empty or completed inputs without pasting unrelated web links.

3. **Smart Playlist Sync Engine:**
   - **Sync (`Ctrl + S`):** Optional safety-controlled synchronization mode that keeps local directories perfectly aligned with YouTube playlists.
   - **Video-in-Playlist URL Normalization:** Automatically normalizes `watch?v=...&list=PL...` links into canonical playlist URLs (`playlist?list=PL...`) before flat extraction.
   - **Single-Pass Flat Metadata Extraction:** Uses `extract_flat` and `dump_single_json` to fetch the complete playlist structure in ~1 second without downloading payloads, with on-demand localized fallback only when unmatched files remain.
   - **Intelligent Local Matching:** Scans target folder for media files (`.mp3`, `.m4a`, `.opus`, `.flac`, `.webm`, `.ogg`, `.wav`, `.mp4`, `.mkv`), matching against the playlist by YouTube video ID first, with a fallback to Unicode NFKC-normalized titles.
   - **Orphan Pruning:** Safely deletes local media files that no longer exist in the playlist, logging each pruned file to the console.
   - **Collision-Safe Two-Pass Renaming:** Re-numbers existing files to their current playlist index (`01 - Title [id].ext`) via a two-pass temporary renaming step (`__tmp_sync_<uuid>_*`) with two-stage rollback protection.
   - **Selective Download:** Only downloads genuinely missing songs via yt-dlp `playlist_items` ranges (e.g., `3,7,12-15`). If all songs are present, skips download entirely and notifies: `"Playlist is already up to date. Re-indexed existing tracks."`
   - **Safety Controls:** The sync checkbox is disabled by default to protect against unintended deletions. Enabling sync automatically enables playlist downloading and locks track numbering.

4. **Playlist Downloads & Order Preservation:**
   - **Playlist (`Ctrl + P`):** Toggleable option to download complete playlists (`--yes-playlist` / `noplaylist: False`). Automatically detects `list=` parameters in pasted URLs.
   - **Number tracks (`Ctrl + N`):** Option to prefix filenames with their track index (e.g., `01 - First Song.mp3`, `02 - Second Song.mp3`), preserving their playback sequence when sorted alphabetically in file managers.

5. **Native Python Engine & Non-Blocking Worker (`QThread`):**
   - Directly drives `yt_dlp.YoutubeDL` in Python rather than piping shell subprocesses.
   - All network extraction, downloads, audio conversion (MP3/FLAC), and FFmpeg muxing run asynchronously in a dedicated `DownloadWorker` thread.
   - Clean cancellation via `yt_dlp.utils.DownloadCancelled` and graceful thread shutdown on window close (`closeEvent`).

6. **Feedback & Telemetry:**
   - Flat monochrome progress bar with sub-pixel precision (`0.0%` to `100.0%`).
   - Single unified status row displaying transfer status, speed (`MB/s`), dynamic `ETA`, and percentage.
   - Collapsible log console (`Ctrl + L`) and log clear shortcut (`Ctrl + K`).

---

## Supported Format Profiles

1. `Best Available (Video + Audio merged)`
2. `1080p MP4`
3. `720p MP4`
4. `Best Audio (Native / No Re-encode)` — *Fast, zero generation loss, works without FFmpeg*
5. `Audio Only (MP3 320kbps)`
6. `Audio Only (FLAC)`

---

## Keyboard Shortcuts

| Shortcut | Action | Behavior |
| :--- | :--- | :--- |
| **`F1`** | Shortcuts Help | Toggles inline keyboard shortcuts reference |
| **`Enter` / `Return`** | Start Download | Initiates extraction and download |
| **`Escape`** | Cancel / Close / Exit | Closes help/logs, cancels active download, or exits |
| **`Ctrl + B`** | Browse Destination | Opens directory picker for output path |
| **`Ctrl + O`** | Open Destination | Opens target download folder in file manager |
| **`Ctrl + L`** | Toggle Logs | Shows or hides the `yt-dlp` log console |
| **`Ctrl + K`** | Clear Logs | Clears all lines in the log console |
| **`Ctrl + P`** | Toggle Playlist | Toggles playlist download mode |
| **`Ctrl + N`** | Toggle Numbering | Toggles track index prefixing (`01 - Title`) |
| **`Ctrl + S`** | Toggle Sync Mode | Toggles Smart Playlist Sync mode |
| **`Ctrl + Q`** | Fast Exit | Gracefully stops active worker and closes process |

---

## Running the Application

```bash
cd Linux/YT-Downloader

# Direct execution (automatically re-executes into local .venv if needed):
./yt_downloader.py

# Or explicitly with Python:
python3 yt_downloader.py
```
