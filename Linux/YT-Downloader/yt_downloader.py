#!/usr/bin/env python3
"""
YT-DLP Desktop Downloader — Zero-chrome, buttonless Linux desktop GUI wrapper for yt-dlp.

Engineered following the Desktop Design System & Style Specification (Linux)
for the Vibe-Coding ecosystem.

Key Principles:
- Pure Flat Charcoal Canvas (#141414) with zero buttons, headers, card boxes, or blue accents
- Uniform Monospace typography across the entire interface
- 100% Keyboard-driven workflow (Enter, Esc, F1, Ctrl+B, Ctrl+O, Ctrl+L, Ctrl+K, Ctrl+P, Ctrl+N, Ctrl+S)
- Native yt-dlp Python engine inside a dedicated QThread (DownloadWorker)
- Smart Playlist Sync with track numbering and two-pass collision-safe renaming
- Media-aware automatic clipboard URL detection
"""

from __future__ import annotations

import datetime
import os
from pathlib import Path
import re
import shutil
import sys
from typing import Any
import urllib.parse
import unicodedata
import uuid

# ---------------------------------------------------------------------------
# Self-Healing Local Virtualenv Interpreter Re-execution
# ---------------------------------------------------------------------------
try:
    from PyQt6.QtCore import (  # type: ignore[import-not-found,reportMissingImports]
        QEvent,
        Qt,
        QThread,
        QTimer,
        QUrl,
        pyqtSignal,
    )
    from PyQt6.QtGui import (  # type: ignore[import-not-found,reportMissingImports]
        QCloseEvent,
        QColor,
        QDesktopServices,
        QFont,
        QFontDatabase,
        QIcon,
        QKeySequence,
        QPainter,
        QPixmap,
        QShortcut,
        QStandardItemModel,
        QTextCursor,
    )
    from PyQt6.QtWidgets import (  # type: ignore[import-not-found,reportMissingImports]
        QApplication,
        QCheckBox,
        QComboBox,
        QFileDialog,
        QHBoxLayout,
        QLabel,
        QLineEdit,
        QMainWindow,
        QPlainTextEdit,
        QProgressBar,
        QVBoxLayout,
        QWidget,
    )
    import yt_dlp
    from yt_dlp.utils import DownloadCancelled
except ModuleNotFoundError:
    # Automatically re-exec using the project's local .venv interpreter if launched directly
    local_venv = Path(__file__).resolve().parent / ".venv"
    local_python = local_venv / "bin" / "python3"
    if local_python.exists() and Path(sys.prefix).resolve() != local_venv.resolve():
        os.execv(str(local_python), [str(local_python), *sys.argv])
    raise


# ---------------------------------------------------------------------------
# Flat Charcoal Palette (Strictly Dark, Zero Blue)
# ---------------------------------------------------------------------------
PALETTE: dict[str, str] = {
    "canvas": "#141414",
    "fg": "#e0e0e0",
    "fg_subtle": "#8a8f9d",
    "sel_bg": "#323842",
    "sel_fg": "#ffffff",
    "meta": "#585858",
    "active": "#e0e0e0",
    "btn_bg": "#21242c",
    "btn_hover": "#2c313d",
    "btn_pressed": "#181a20",
    "btn_border": "#2c313d",
    "input_bg": "#17181c",
    "input_border": "#282b33",
    "input_focus_border": "#4a5568",
    "danger": "#ef4444",
    "console_bg": "#0c0d0f",
    "progress_bg": "#21242b",
    "progress_chunk": "#e0e0e0",
    "checkbox_bg": "#17181c",
    "checkbox_border": "#333742",
}

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

PADDING_PX = 24
UI_FONT_SIZE = 13
DEFAULT_DESTINATION = "/mnt/BA5EF38D5EF340A5/Media/Music/"

SHORTCUTS_HELP_TEXT = (
    "Enter    Start download          Ctrl+L   Toggle logs\n"
    "Esc      Cancel / close / quit   Ctrl+K   Clear logs\n"
    "Ctrl+B   Browse destination      Ctrl+P   Toggle playlist\n"
    "Ctrl+O   Open destination        Ctrl+N   Toggle track numbers\n"
    "F1       Toggle shortcuts        Ctrl+S   Toggle sync mode"
)

FORMAT_OPTIONS = [
    "Best Available (Video + Audio merged)",
    "1080p MP4",
    "720p MP4",
    "Best Audio (Native / No Re-encode)",
    "Audio Only (MP3 320kbps)",
    "Audio Only (FLAC)",
]

FFMPEG_TRANSCODE_FORMATS = {
    "Audio Only (MP3 320kbps)",
    "Audio Only (FLAC)",
}

MEDIA_URL_DOMAINS = (
    "youtube.com",
    "youtu.be",
    "soundcloud.com",
    "bandcamp.com",
    "vimeo.com",
    "twitch.tv",
    "dailymotion.com",
    "tiktok.com",
    "instagram.com",
    "twitter.com",
    "x.com",
    "reddit.com",
    "redd.it",
    "bilibili.com",
    "nicovideo.jp",
    "mixcloud.com",
    "facebook.com",
    "fb.watch",
    "rumble.com",
    "odysee.com",
    "bitchute.com",
    "streamable.com",
)

ANSI_ESCAPE_RE = re.compile(r"\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])")


def strip_ansi(text: str) -> str:
    """Remove ANSI escape codes from logs and progress metrics."""
    return ANSI_ESCAPE_RE.sub("", text)


SUPPORTED_EXTENSIONS = {".mp3", ".m4a", ".opus", ".flac", ".webm", ".ogg", ".wav", ".mp4", ".mkv"}


def normalize_title(title: str) -> str:
    """
    Normalize title for robust playlist matching across all languages:
    1. Strip leading track numbers and delimiters (e.g. '01 - ', '5. ').
    2. Strip trailing bracketed YouTube video IDs (e.g. ' [wRENuDrnXcs]').
    3. Normalize Unicode via NFKC (decomposes fullwidth punctuation/letters).
    4. Strip noise symbols, punctuation, whitespace, and underscores,
       retaining ALL Unicode letters (\\p{L}) and digits (\\p{N}).
    """
    t = title.replace('ㅣ', ' ')
    t = re.sub(r'^\d+\s*[-._\s]*', '', t)
    t = re.sub(r'\s*\[[a-zA-Z0-9_-]{11}\]\s*$', '', t)
    t = unicodedata.normalize('NFKC', t).lower()
    return re.sub(r'[^\w]|_', '', t)


def get_title_core_tokens(norm_title: str) -> str:
    """
    Remove non-distinguishing connector words from normalized title
    to prevent false-positive matches (such as two different songs colliding on 'feat').
    """
    t = norm_title
    for stop in ("featuring", "feat", "ft", "official", "musicvideo", "video", "lyrics", "cover", "mv", "remix"):
        t = t.replace(stop, "")
    return t


def get_primary_title(title: str) -> str:
    """
    Extract the core song title prior to featured artists, cover tags, or delimiters.
    E.g. 'Melt (メルト)  ㅣ아카네 リゼ 【Cover】' -> 'meltメルト'
    """
    t = re.sub(r'^\d+\s*[-._\s]*', '', title)
    t = re.sub(r'\s*\[[a-zA-Z0-9_-]{11}\]\s*$', '', t)
    # Split by common artist/feature/cover delimiters
    part = re.split(r'[\(\[【\|ㅣ/／⧸]', t)[0].strip()
    return normalize_title(part)


def compress_indices(indices: list[int]) -> str:
    """
    Compress a list of 1-based playlist indices into a range string:
    e.g. [3, 7, 12, 13, 14, 15] -> "3,7,12-15"
    """
    if not indices:
        return ""
    sorted_idx = sorted(set(indices))
    ranges: list[str] = []
    start = sorted_idx[0]
    prev = start
    for x in sorted_idx[1:]:
        if x == prev + 1:
            prev = x
        else:
            if start == prev:
                ranges.append(str(start))
            else:
                ranges.append(f"{start}-{prev}")
            start = x
            prev = x
    if start == prev:
        ranges.append(str(start))
    else:
        ranges.append(f"{start}-{prev}")
    return ",".join(ranges)


def resolve_system_font(family_list: list[str]) -> str:
    available = set(QFontDatabase.families())
    for f in family_list:
        if f in available:
            return f
    return family_list[-1]


def check_ffmpeg_available() -> tuple[bool, str]:
    """Check if ffmpeg and ffprobe are present on system PATH."""
    ffmpeg_bin = shutil.which("ffmpeg")
    ffprobe_bin = shutil.which("ffprobe")
    if ffmpeg_bin and ffprobe_bin:
        return True, f"ffmpeg available: {ffmpeg_bin}"
    elif ffmpeg_bin:
        return False, "ffmpeg found, but ffprobe is missing from PATH"
    return False, "ffmpeg not found on PATH. Audio conversion (MP3/FLAC) and muxing require ffmpeg."


def clean_url(url: str) -> str:
    """Strip surrounding whitespace and accidental quotes from a URL string."""
    return url.strip().strip('"').strip("'").strip()


def is_valid_url(url: str) -> bool:
    """Validate whether input is a properly formatted HTTP/HTTPS URL."""
    url = clean_url(url)
    if not url:
        return False
    try:
        p = urllib.parse.urlparse(url)
        return (
            p.scheme in ("http", "https")
            and bool(p.netloc)
            and ("." in p.netloc or p.netloc == "localhost")
        )
    except Exception:
        return False


def is_media_url(url: str) -> bool:
    """Check if URL belongs to a recognized media hosting domain for clipboard auto-fill."""
    url = clean_url(url)
    if not is_valid_url(url):
        return False
    try:
        p = urllib.parse.urlparse(url)
        host = (p.hostname or "").lower()
        return any(host == d or host.endswith("." + d) for d in MEDIA_URL_DOMAINS)
    except Exception:
        return False


def normalize_playlist_url(url: str) -> str:
    """
    Convert video-in-playlist URLs (e.g. youtube.com/watch?v=...&list=PL...)
    into canonical playlist URLs (https://www.youtube.com/playlist?list=PL...)
    so flat extraction and playlist downloads target the full playlist rather than a single item.
    """
    url = clean_url(url)
    try:
        p = urllib.parse.urlparse(url)
        host = (p.hostname or "").lower()
        is_yt = host in ("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")
        if is_yt and p.path != "/playlist":
            qs = urllib.parse.parse_qs(p.query)
            list_id = (qs.get("list") or [None])[0]
            # Do not rewrite dynamic YouTube Mix/Radio queues (RD...) which require a seed video v=
            if list_id and not list_id.startswith("RD"):
                return f"https://www.youtube.com/playlist?list={list_id}"
    except Exception:
        pass
    return url


def load_app_icon() -> QIcon:
    """Load desktop application icon."""
    for candidate in [
        Path(__file__).resolve().parent / "icon.png",
        Path(__file__).resolve().parent / "icon.svg",
    ]:
        if candidate.is_file() and candidate.stat().st_size > 0:
            return QIcon(str(candidate))

    # Monochrome zero-chrome fallback icon
    pix = QPixmap(64, 64)
    pix.fill(QColor(0, 0, 0, 0))
    p = QPainter(pix)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    p.setBrush(QColor("#141414"))
    p.setPen(Qt.PenStyle.NoPen)
    p.drawRoundedRect(4, 4, 56, 56, 12, 12)
    p.setPen(QColor("#e0e0e0"))
    p.drawText(pix.rect(), int(Qt.AlignmentFlag.AlignCenter), "YT")
    p.end()
    return QIcon(pix)


# ---------------------------------------------------------------------------
# Custom Logger for yt-dlp to Pipe into Qt Console
# ---------------------------------------------------------------------------
class YtDlpQtLogger:
    """Redirects yt-dlp internal logging directly to PyQt signals."""

    def __init__(self, log_signal: Any) -> None:
        self.log_signal = log_signal

    def debug(self, msg: str) -> None:
        clean = strip_ansi(msg).strip()
        if clean:
            self.log_signal.emit("debug", clean)

    def info(self, msg: str) -> None:
        clean = strip_ansi(msg).strip()
        if clean:
            self.log_signal.emit("info", clean)

    def warning(self, msg: str) -> None:
        clean = strip_ansi(msg).strip()
        if clean:
            self.log_signal.emit("warning", clean)

    def error(self, msg: str) -> None:
        clean = strip_ansi(msg).strip()
        if clean:
            self.log_signal.emit("error", clean)


# ---------------------------------------------------------------------------
# Dedicated Background Download Worker (QThread)
# ---------------------------------------------------------------------------
class DownloadWorker(QThread):
    """
    Dedicated QThread worker executing yt_dlp extraction, download,
    and post-processing pipelines asynchronously without blocking the Qt UI.
    """

    progressUpdated = pyqtSignal(float, str, str, str)  # percent, speed, eta, status_message
    logEmitted = pyqtSignal(str, str)                    # level (info/warn/err/debug), text
    metadataLoaded = pyqtSignal(dict)                   # media info dictionary
    downloadFinished = pyqtSignal(bool, str, str)        # success, message, target_filepath

    def __init__(
        self,
        url: str,
        output_dir: str,
        format_choice: str,
        download_playlist: bool,
        number_playlist: bool,
        sync_mode: bool = False,
        parent: Any = None,
    ) -> None:
        super().__init__(parent)
        self.url = clean_url(url)
        self.output_dir = output_dir
        self.format_choice = format_choice
        self.download_playlist = download_playlist
        self.number_playlist = number_playlist
        self.sync_mode = sync_mode
        self._is_cancelled = False
        self._last_filename: str | None = None
        self._title: str = ""

    def cancel(self) -> None:
        """Mark cancellation flag so hooks immediately raise DownloadCancelled."""
        self._is_cancelled = True

    def _progress_hook(self, d: dict) -> None:
        if self._is_cancelled:
            raise DownloadCancelled("Download cancelled by user.")

        status = d.get("status")
        if status == "downloading":
            downloaded = d.get("downloaded_bytes", 0)
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0

            # Percentage calculation (strip ANSI sequences from fallback string)
            if total > 0:
                percent = (downloaded / total) * 100.0
            else:
                percent_str = strip_ansi(d.get("_percent_str", "")).strip().rstrip("%")
                try:
                    percent = float(percent_str)
                except ValueError:
                    percent = 0.0

            # Transfer speed (in MB/s)
            speed_val = d.get("speed")
            if speed_val is not None:
                speed_str = f"{speed_val / (1024 * 1024):.2f} MB/s"
            else:
                speed_str = strip_ansi(d.get("_speed_str", "")).strip() or "-- MB/s"

            # Estimated Time Remaining (ETA)
            eta_val = d.get("eta")
            if eta_val is not None:
                mins, secs = divmod(int(eta_val), 60)
                hours, mins = divmod(mins, 60)
                if hours > 0:
                    eta_str = f"{hours:02d}:{mins:02d}:{secs:02d}"
                else:
                    eta_str = f"{mins:02d}:{secs:02d}"
            else:
                eta_str = strip_ansi(d.get("_eta_str", "")).strip() or "--:--"

            # Target filename tracking
            filename = d.get("filename")
            if filename:
                self._last_filename = filename

            # Playlist progress indicator if available
            info_dict = d.get("info_dict") or {}
            pl_idx = info_dict.get("playlist_index") or d.get("playlist_index")
            pl_count = info_dict.get("n_entries") or d.get("playlist_count")

            if pl_idx and pl_count:
                status_msg = f"Downloading item {pl_idx} of {pl_count}…"
            elif pl_idx:
                status_msg = f"Downloading playlist item {pl_idx}…"
            else:
                status_msg = "Downloading media…"

            self.progressUpdated.emit(percent, speed_str, eta_str, status_msg)

        elif status == "finished":
            filename = d.get("filename")
            if filename:
                self._last_filename = filename
            self.progressUpdated.emit(100.0, "", "00:00", "Download complete. Merging / Post-processing…")

    def _postprocessor_hook(self, d: dict) -> None:
        if self._is_cancelled:
            raise DownloadCancelled("Download cancelled by user.")

        status = d.get("status")
        pp_name = d.get("postprocessor", "Post-processor")
        if status == "started":
            self.progressUpdated.emit(100.0, "", "", f"Processing ({pp_name})…")
            self.logEmitted.emit("info", f"[postprocess] Started {pp_name}")
        elif status == "finished":
            self.progressUpdated.emit(100.0, "", "", f"Finished {pp_name}")
            self.logEmitted.emit("info", f"[postprocess] Finished {pp_name}")

    def _build_ydl_opts(self, dest_path: Path, logger: YtDlpQtLogger) -> dict[str, Any]:
        has_ffmpeg, _ = check_ffmpeg_available()

        ydl_opts: dict[str, Any] = {
            "paths": {"home": str(dest_path)},
            "progress_hooks": [self._progress_hook],
            "postprocessor_hooks": [self._postprocessor_hook],
            "logger": logger,
            "no_color": True,
            "continuedl": True,
            "windowsfilenames": False,
            "restrictfilenames": False,
            "overwrites": False,
        }

        if self.format_choice in FFMPEG_TRANSCODE_FORMATS and not has_ffmpeg:
            raise RuntimeError(
                f"FFmpeg is required for '{self.format_choice}'. "
                "Install ffmpeg or select 'Best Audio (Native / No Re-encode)'."
            )

        if self.format_choice == "Best Available (Video + Audio merged)":
            if has_ffmpeg:
                ydl_opts["format"] = "bestvideo*+bestaudio/best"
                ydl_opts["merge_output_format"] = "mp4"
            else:
                ydl_opts["format"] = "best[ext=mp4]/best"
        elif self.format_choice == "1080p MP4":
            if has_ffmpeg:
                ydl_opts["format"] = (
                    "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/"
                    "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best"
                )
                ydl_opts["merge_output_format"] = "mp4"
            else:
                ydl_opts["format"] = "best[height<=1080][ext=mp4]/best[height<=1080]/best"
        elif self.format_choice == "720p MP4":
            if has_ffmpeg:
                ydl_opts["format"] = (
                    "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/"
                    "bestvideo[height<=720]+bestaudio/best[height<=720]/best"
                )
                ydl_opts["merge_output_format"] = "mp4"
            else:
                ydl_opts["format"] = "best[height<=720][ext=mp4]/best[height<=720]/best"
        elif self.format_choice == "Best Audio (Native / No Re-encode)":
            ydl_opts["format"] = "bestaudio/best"
        elif self.format_choice == "Audio Only (MP3 320kbps)":
            ydl_opts["format"] = "bestaudio/best"
            ydl_opts["postprocessors"] = [
                {
                    "key": "FFmpegExtractAudio",
                    "preferredcodec": "mp3",
                    "preferredquality": "320",
                }
            ]
        elif self.format_choice == "Audio Only (FLAC)":
            ydl_opts["format"] = "bestaudio/best"
            ydl_opts["postprocessors"] = [
                {
                    "key": "FFmpegExtractAudio",
                    "preferredcodec": "flac",
                }
            ]

        return ydl_opts

    @staticmethod
    def _match_files_by_title(
        files: list[Path],
        playlist_items: list[dict[str, Any]],
        matched_file_to_item: dict[Path, dict[str, Any]],
        matched_item_indices: set[int],
    ) -> None:
        """Run Pass B (full normalized title) and Pass C (primary title) against unmatched files."""
        # Pass B: Match by Full Normalized Title (all languages)
        for f in files:
            if f in matched_file_to_item:
                continue
            clean_name = re.sub(r'^\d+\s*[-._\s]*', '', f.stem)
            clean_name_no_id = re.sub(r'\s*\[[a-zA-Z0-9_-]{11}\]$', '', clean_name)
            clean_norm = normalize_title(clean_name_no_id)
            clean_core = get_title_core_tokens(clean_norm)

            if not clean_norm:
                continue

            for item in playlist_items:
                if item["index"] in matched_item_indices:
                    continue
                # Ensure match is on distinctive content, not solely a stop word like 'feat'
                if clean_norm in item["norm_titles"]:
                    if len(clean_core) >= 2 or any(ord(c) > 127 for c in clean_norm):
                        matched_file_to_item[f] = item
                        matched_item_indices.add(item["index"])
                        break

        # Pass C: Match by Primary Title (before feature/cover/artist delimiters)
        for f in files:
            if f in matched_file_to_item:
                continue
            clean_name = re.sub(r'^\d+\s*[-._\s]*', '', f.stem)
            clean_name_no_id = re.sub(r'\s*\[[a-zA-Z0-9_-]{11}\]$', '', clean_name)
            clean_primary = get_primary_title(clean_name_no_id)

            if not clean_primary or (len(clean_primary) < 2 and not any(ord(c) > 127 for c in clean_primary)):
                continue

            for item in playlist_items:
                if item["index"] in matched_item_indices:
                    continue
                if clean_primary in item["primary_titles"]:
                    matched_file_to_item[f] = item
                    matched_item_indices.add(item["index"])
                    break

    def _run_sync(self, dest_path: Path, logger: YtDlpQtLogger) -> None:
        """
        Smart Playlist Sync Engine:
        1. Extract playlist metadata without downloading (extract_flat + dump_single_json).
        2. Scan and match local files (by ID, then normalized title).
        3. Prune removed songs (delete orphan files).
        4. Collision-safe two-pass renaming.
        5. Selective download for missing songs via playlist_items.
        """
        sync_url = normalize_playlist_url(self.url)
        if sync_url != self.url:
            self.logEmitted.emit("info", f"[sync:1] Normalized video+playlist URL to playlist: {sync_url}")

        # Step 1: Extract Playlist Metadata Without Downloading (Single Pass)
        self.logEmitted.emit("info", f"[sync:1] Fetching playlist structure: {sync_url}")
        self.progressUpdated.emit(0.0, "-- MB/s", "--:--", "Sync: Extracting playlist metadata…")

        extract_opts = {
            "extract_flat": True,
            "dump_single_json": True,
            "noplaylist": False,
            "quiet": True,
            "no_warnings": True,
            "logger": logger,
            "extractor_args": {"youtube": {"lang": ["ja", "en"]}},
            "http_headers": {"Accept-Language": "ja,en;q=0.9"},
        }

        try:
            with yt_dlp.YoutubeDL(extract_opts) as ydl:
                if self._is_cancelled:
                    raise DownloadCancelled("Sync cancelled before extraction.")
                info = ydl.extract_info(sync_url, download=False)
        except DownloadCancelled:
            raise
        except Exception as e:
            raise RuntimeError(f"Failed to fetch playlist metadata: {e}")

        if not info:
            raise RuntimeError("Could not retrieve playlist information.")

        entries_raw = info.get("entries")
        if entries_raw is None and info.get("_type") != "playlist":
            self.logEmitted.emit("warning", "[sync] Target is a single video, not a playlist. Proceeding with standard download.")
            self._run_standard_download(dest_path, logger)
            return

        entries = [e for e in (entries_raw or []) if e]
        if not entries:
            raise RuntimeError("Playlist is empty or contains no accessible entries.")

        playlist_title = info.get("title") or "Playlist"
        self._title = playlist_title
        self.metadataLoaded.emit(info)
        self.logEmitted.emit("info", f"[sync:1] Loaded '{playlist_title}' with {len(entries)} track(s).")

        playlist_items: list[dict[str, Any]] = []
        for idx, entry in enumerate(entries, 1):
            vid = entry.get("id") or ""
            title = entry.get("title") or f"Track {idx}"
            alt_title = entry.get("alt_title") or ""

            raw_titles = {t for t in [title, alt_title] if t}
            norm_titles = {normalize_title(t) for t in raw_titles if normalize_title(t)}
            core_tokens = {get_title_core_tokens(nt) for nt in norm_titles if get_title_core_tokens(nt)}
            primary_titles = {get_primary_title(t) for t in raw_titles if get_primary_title(t)}

            playlist_items.append({
                "index": idx,
                "id": vid,
                "title": title,
                "clean_title": yt_dlp.utils.sanitize_filename(title),
                "norm_titles": norm_titles,
                "core_tokens": core_tokens,
                "primary_titles": primary_titles,
                "entry": entry,
            })

        if self._is_cancelled:
            raise DownloadCancelled("Sync cancelled after metadata extraction.")

        # Step 2: Scan and Match Local Files
        self.progressUpdated.emit(10.0, "-- MB/s", "--:--", "Sync: Scanning local files…")
        local_files = [
            p for p in dest_path.iterdir()
            if p.is_file() and p.suffix.lower() in SUPPORTED_EXTENSIONS and not p.name.startswith("__tmp_sync_")
        ]

        matched_file_to_item: dict[Path, dict[str, Any]] = {}
        matched_item_indices: set[int] = set()

        id_to_item = {item["id"]: item for item in playlist_items if item["id"]}

        # Pass A: Match by YouTube Video ID
        for f in local_files:
            id_matches = re.findall(r'[\[\(]([a-zA-Z0-9_-]{11})[\]\)]', f.stem)
            matched = None
            for vid in id_matches:
                if vid in id_to_item and id_to_item[vid]["index"] not in matched_item_indices:
                    matched = id_to_item[vid]
                    break
            if not matched:
                for vid, item in id_to_item.items():
                    if item["index"] not in matched_item_indices and vid in f.stem:
                        matched = item
                        break
            if matched:
                matched_file_to_item[f] = matched
                matched_item_indices.add(matched["index"])

        # Pass B & C: Match by Normalized and Primary Title
        self._match_files_by_title(local_files, playlist_items, matched_file_to_item, matched_item_indices)

        orphans = [f for f in local_files if f not in matched_file_to_item]
        missing_items = [item for item in playlist_items if item["index"] not in matched_item_indices]

        # Conditional localized fallback: only query default English titles if there are
        # both unmatched local files (orphans) and unmatched playlist items (missing_items)
        if orphans and missing_items:
            if self._is_cancelled:
                raise DownloadCancelled("Sync cancelled before fallback title check.")
            try:
                def_opts = {
                    "extract_flat": True,
                    "dump_single_json": True,
                    "noplaylist": False,
                    "quiet": True,
                    "no_warnings": True,
                }
                with yt_dlp.YoutubeDL(def_opts) as ydl_def:
                    info_def = ydl_def.extract_info(sync_url, download=False)
                    for e in (info_def.get("entries") or []):
                        if not e:
                            continue
                        vid = e.get("id") or ""
                        t_def = e.get("title") or ""
                        if vid and t_def and vid in id_to_item:
                            item = id_to_item[vid]
                            nt = normalize_title(t_def)
                            if nt:
                                item["norm_titles"].add(nt)
                                ct = get_title_core_tokens(nt)
                                if ct:
                                    item["core_tokens"].add(ct)
                            pt = get_primary_title(t_def)
                            if pt:
                                item["primary_titles"].add(pt)
                if self._is_cancelled:
                    raise DownloadCancelled("Sync cancelled after fallback title check.")
                self._match_files_by_title(orphans, playlist_items, matched_file_to_item, matched_item_indices)
                orphans = [f for f in local_files if f not in matched_file_to_item]
                missing_items = [item for item in playlist_items if item["index"] not in matched_item_indices]
            except DownloadCancelled:
                raise
            except Exception:
                pass

        self.logEmitted.emit(
            "info",
            f"[sync:2] Match summary: {len(matched_file_to_item)} matched, {len(orphans)} unlisted/orphan, {len(missing_items)} missing."
        )

        if self._is_cancelled:
            raise DownloadCancelled("Sync cancelled after local scan.")

        # Step 3: Prune Removed Songs
        if orphans:
            self.progressUpdated.emit(20.0, "-- MB/s", "--:--", f"Sync: Pruning {len(orphans)} unlisted file(s)…")
            for orphan in orphans:
                if self._is_cancelled:
                    raise DownloadCancelled("Sync cancelled during pruning.")
                try:
                    orphan.unlink()
                    self.logEmitted.emit("warning", f"[sync:prune] Deleted unlisted local file: {orphan.name}")
                except Exception as e:
                    self.logEmitted.emit("error", f"[sync:prune] Could not delete {orphan.name}: {e}")
        else:
            self.logEmitted.emit("info", "[sync:3] No unlisted files to prune.")

        if self._is_cancelled:
            raise DownloadCancelled("Sync cancelled after pruning.")

        # Step 4: Collision-Safe Renaming (Two-Pass)
        rename_plan: list[tuple[Path, Path, str]] = []
        for f, item in matched_file_to_item.items():
            new_idx = item["index"]
            ext = f.suffix
            clean_name = re.sub(r'^\d+\s*[-._\s]*', '', f.stem)
            clean_name_no_id = re.sub(r'\s*\[[a-zA-Z0-9_-]{11}\]\s*$', '', clean_name)

            # Preserve or include [video_id] to match Step 5 format
            if item["id"]:
                title_part = f"{clean_name_no_id} [{item['id']}]"
            else:
                title_part = clean_name_no_id

            target_filename = f"{new_idx:02d} - {title_part}{ext}"
            target_path = f.parent / target_filename
            if f.name != target_filename:
                rename_plan.append((f, target_path, f.name))

        if rename_plan:
            self.progressUpdated.emit(35.0, "-- MB/s", "--:--", f"Sync: Re-indexing {len(rename_plan)} track(s)…")
            self.logEmitted.emit("info", f"[sync:4] Re-indexing {len(rename_plan)} file(s) via two-pass collision-safe rename...")

            temp_renames: list[tuple[Path, Path, Path]] = []
            completed_pass2: list[tuple[Path, Path]] = []
            try:
                # Pass 1: Rename files to unique temporary names
                for current_file, final_target, old_name in rename_plan:
                    if self._is_cancelled:
                        raise DownloadCancelled("Sync cancelled during re-indexing.")
                    tmp_name = f"__tmp_sync_{uuid.uuid4().hex[:8]}_{current_file.name}"
                    tmp_path = current_file.parent / tmp_name
                    current_file.rename(tmp_path)
                    temp_renames.append((tmp_path, final_target, current_file))

                # Pass 2: Rename temporary files to their final destination
                for tmp_path, final_target, orig_file in temp_renames:
                    if self._is_cancelled:
                        raise DownloadCancelled("Sync cancelled during re-indexing.")
                    tmp_path.rename(final_target)
                    completed_pass2.append((final_target, tmp_path))
                    self.logEmitted.emit("info", f"[sync:4] Renamed: {orig_file.name} -> {final_target.name}")
            except Exception:
                # Collision-safe two-stage rollback: first revert Pass 2 targets back to temp, then temp back to orig
                for final_target, tmp_path in reversed(completed_pass2):
                    if final_target.exists() and not tmp_path.exists():
                        try:
                            final_target.rename(tmp_path)
                        except Exception:
                            pass
                for tmp_path, _, orig_file in reversed(temp_renames):
                    if tmp_path.exists() and not orig_file.exists():
                        try:
                            tmp_path.rename(orig_file)
                        except Exception:
                            pass
                raise
        else:
            self.logEmitted.emit("info", "[sync:4] All matched files are already correctly indexed.")

        if self._is_cancelled:
            raise DownloadCancelled("Sync cancelled after re-indexing.")

        # Step 5: Selective Download for Missing Items
        if not missing_items:
            up_to_date_msg = "Playlist is already up to date. Re-indexed existing tracks."
            self.logEmitted.emit("info", f"[sync:5] {up_to_date_msg}")
            self.progressUpdated.emit(100.0, "", "00:00", up_to_date_msg)
            self.downloadFinished.emit(True, up_to_date_msg, str(dest_path))
            return

        missing_indices = [item["index"] for item in missing_items]
        missing_indices_str = compress_indices(missing_indices)
        self.logEmitted.emit(
            "info",
            f"[sync:5] Downloading {len(missing_items)} missing track(s) (indices: {missing_indices_str})..."
        )
        self.progressUpdated.emit(40.0, "-- MB/s", "--:--", f"Sync: Downloading {len(missing_items)} missing song(s)…")

        # Selective download configuration (directory passed via 'paths' in _build_ydl_opts)
        ydl_sync_opts = self._build_ydl_opts(dest_path, logger)
        ydl_sync_opts["noplaylist"] = False
        ydl_sync_opts["playlist_items"] = missing_indices_str
        ydl_sync_opts["outtmpl"] = "%(playlist_index)02d - %(title)s [%(id)s].%(ext)s"

        with yt_dlp.YoutubeDL(ydl_sync_opts) as ydl:
            ret = ydl.download([sync_url])
            if ret != 0:
                raise RuntimeError(f"yt-dlp exited with non-zero code {ret} during sync download.")

        success_msg = f"Playlist synced! {len(missing_items)} missing track(s) downloaded."
        self.logEmitted.emit("info", f"[sync] {success_msg}")
        self.downloadFinished.emit(True, success_msg, str(dest_path))

    def _run_standard_download(self, dest_path: Path, logger: YtDlpQtLogger) -> None:
        """Standard download mode for single items or standard playlists."""
        target_url = normalize_playlist_url(self.url) if self.download_playlist else self.url

        ydl_opts = self._build_ydl_opts(dest_path, logger)
        if self.download_playlist and self.number_playlist:
            ydl_opts["outtmpl"] = "%(playlist_index&{:02d} - |)s%(title)s.%(ext)s"
        else:
            ydl_opts["outtmpl"] = "%(title)s.%(ext)s"
        ydl_opts["noplaylist"] = not self.download_playlist

        self.logEmitted.emit("info", f"[init] Target: {target_url}")
        self.logEmitted.emit("info", f"[format] Profile: {self.format_choice}")
        self.logEmitted.emit("info", f"[playlist] Download playlist: {self.download_playlist} (numbering: {self.number_playlist})")
        self.logEmitted.emit("info", f"[destination] Destination: {dest_path}")

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            if self._is_cancelled:
                raise DownloadCancelled("Cancelled before extraction.")

            self.progressUpdated.emit(0.0, "-- MB/s", "--:--", "Extracting media metadata…")
            info = ydl.extract_info(target_url, download=False, process=not self.download_playlist)
            if info:
                self._title = info.get("title", "")
                self.metadataLoaded.emit(info)
                self.logEmitted.emit("info", f"[meta] Title: {self._title}")

            if self._is_cancelled:
                raise DownloadCancelled("Cancelled before payload download.")

            ret = ydl.download([target_url])
            if ret != 0:
                raise RuntimeError(f"yt-dlp exited with non-zero return code: {ret}")

        final_file = self._last_filename or str(dest_path)
        self.downloadFinished.emit(True, "Download and processing completed successfully!", final_file)

    def run(self) -> None:
        dest_path = Path(self.output_dir)
        try:
            dest_path.mkdir(parents=True, exist_ok=True)
        except Exception as e:
            self.logEmitted.emit("error", f"Could not create destination directory: {e}")
            self.downloadFinished.emit(False, f"Cannot create destination path: {e}", "")
            return

        logger = YtDlpQtLogger(self.logEmitted)

        try:
            if self.sync_mode:
                self._run_sync(dest_path, logger)
            else:
                self._run_standard_download(dest_path, logger)
        except DownloadCancelled as e:
            self.logEmitted.emit("warning", f"[cancelled] {e}")
            self.downloadFinished.emit(False, "Download cancelled by user.", "")
        except Exception as e:
            err_msg = str(e)
            self.logEmitted.emit("error", f"[error] {err_msg}")
            self.downloadFinished.emit(False, f"Download failed: {err_msg}", "")


# ---------------------------------------------------------------------------
# Main Window Interface
# ---------------------------------------------------------------------------
class YtDownloaderWindow(QMainWindow):
    """
    Main Desktop Window implementing zero-chrome, buttonless Linux styling,
    uniform monospace typography, and 100% keyboard-driven control.
    """

    def __init__(self) -> None:
        super().__init__()
        self._logs_expanded = False
        self._help_expanded = False
        self._log_line_count = 0
        self._has_ffmpeg = True
        self._last_clipboard_url = ""
        self._url_completed = False

        self._active_worker: DownloadWorker | None = None
        self._last_completed_target: str = ""
        self._transient_status: str = ""
        self._idle_status_text: str = "Idle"

        self.setWindowTitle("YT-DLP Downloader")
        self.setWindowIcon(load_app_icon())
        self.resize(680, 260)
        self.setMinimumSize(460, 220)

        self._build_ui()
        self._setup_shortcuts()
        self._apply_theme()

        # Startup system dependency checks
        self._check_system_ffmpeg()

        # Clipboard detection setup
        self._setup_clipboard_monitor()

    # -----------------------------------------------------------------------
    # UI Construction (Pure Flat Canvas, Zero Buttons, Zero Headers)
    # -----------------------------------------------------------------------
    def _build_ui(self) -> None:
        root = QWidget(self)
        root_layout = QVBoxLayout(root)
        root_layout.setContentsMargins(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        root_layout.setSpacing(12)
        root_layout.setAlignment(Qt.AlignmentFlag.AlignTop)

        # -------------------------------------------------------------------
        # Input 1: Media URL (No header, no placeholder, no buttons)
        # -------------------------------------------------------------------
        self.txt_url = QLineEdit(root)
        self.txt_url.setFixedHeight(34)
        self.txt_url.textChanged.connect(self._on_url_input_changed)
        self.txt_url.returnPressed.connect(self.start_download)
        root_layout.addWidget(self.txt_url)

        self.lbl_url_validation = QLabel("", root)
        self.lbl_url_validation.setObjectName("validationLabel")
        self.lbl_url_validation.hide()
        root_layout.addWidget(self.lbl_url_validation)

        # -------------------------------------------------------------------
        # Input 2: Output Destination (Ctrl+B browse, Ctrl+O open folder)
        # -------------------------------------------------------------------
        default_dir = DEFAULT_DESTINATION if Path(DEFAULT_DESTINATION).exists() else str(Path.home() / "Music")
        self.txt_path = QLineEdit(default_dir, root)
        self.txt_path.setFixedHeight(34)
        root_layout.addWidget(self.txt_path)

        # -------------------------------------------------------------------
        # Input 3: Download Format Selector
        # -------------------------------------------------------------------
        self.combo_format = QComboBox(root)
        self.combo_format.setFixedHeight(34)
        for fmt in FORMAT_OPTIONS:
            self.combo_format.addItem(fmt)
        root_layout.addWidget(self.combo_format)

        self.lbl_ffmpeg_warning = QLabel("", root)
        self.lbl_ffmpeg_warning.setObjectName("validationLabel")
        self.lbl_ffmpeg_warning.setWordWrap(True)
        self.lbl_ffmpeg_warning.hide()
        root_layout.addWidget(self.lbl_ffmpeg_warning)

        # -------------------------------------------------------------------
        # Options Row: Concise Checkboxes (Playlist, Number tracks, Sync)
        # -------------------------------------------------------------------
        options_row = QHBoxLayout()
        options_row.setContentsMargins(0, 2, 0, 2)
        options_row.setSpacing(24)

        self.chk_playlist = QCheckBox("Playlist", root)
        self.chk_playlist.setToolTip("Download entire playlist (Ctrl+P)")
        self.chk_playlist.toggled.connect(self._on_playlist_toggled)

        self.chk_numbering = QCheckBox("Number tracks", root)
        self.chk_numbering.setToolTip("Prefix playlist items with track index (Ctrl+N)")
        self.chk_numbering.setChecked(True)
        self.chk_numbering.setEnabled(False)

        self.chk_sync = QCheckBox("Sync", root)
        self.chk_sync.setToolTip("Smart Playlist Sync: re-index existing tracks, delete unlisted songs, download missing (Ctrl+S)")
        self.chk_sync.setChecked(False)
        self.chk_sync.toggled.connect(self._on_sync_toggled)

        options_row.addWidget(self.chk_playlist)
        options_row.addWidget(self.chk_numbering)
        options_row.addWidget(self.chk_sync)
        options_row.addStretch(1)
        root_layout.addLayout(options_row)

        # -------------------------------------------------------------------
        # Metadata Strip (Flat text, shown during extraction/download)
        # -------------------------------------------------------------------
        self.lbl_meta_text = QLabel("", root)
        self.lbl_meta_text.setObjectName("metaContent")
        self.lbl_meta_text.setWordWrap(True)
        self.lbl_meta_text.hide()
        root_layout.addWidget(self.lbl_meta_text)

        # -------------------------------------------------------------------
        # Progress Bar & Single Unified Telemetry Row
        # -------------------------------------------------------------------
        progress_box = QWidget(root)
        progress_layout = QVBoxLayout(progress_box)
        progress_layout.setContentsMargins(0, 4, 0, 0)
        progress_layout.setSpacing(6)

        self.progress_bar = QProgressBar(progress_box)
        self.progress_bar.setFixedHeight(6)
        self.progress_bar.setRange(0, 1000)
        self.progress_bar.setValue(0)
        self.progress_bar.setTextVisible(False)
        progress_layout.addWidget(self.progress_bar)

        metrics_row = QHBoxLayout()
        metrics_row.setContentsMargins(0, 0, 0, 0)

        self.lbl_progress_status = QLabel("Idle", progress_box)
        self.lbl_progress_status.setObjectName("statusLabel")

        self.lbl_progress_speed = QLabel("", progress_box)
        self.lbl_progress_speed.setObjectName("metricLabel")

        self.lbl_progress_eta = QLabel("", progress_box)
        self.lbl_progress_eta.setObjectName("metricLabel")

        self.lbl_progress_percent = QLabel("0.0%", progress_box)
        self.lbl_progress_percent.setObjectName("percentLabel")

        metrics_row.addWidget(self.lbl_progress_status, 2)
        metrics_row.addWidget(self.lbl_progress_speed, 1, Qt.AlignmentFlag.AlignCenter)
        metrics_row.addWidget(self.lbl_progress_eta, 1, Qt.AlignmentFlag.AlignCenter)
        metrics_row.addWidget(self.lbl_progress_percent, 1, Qt.AlignmentFlag.AlignRight)
        progress_layout.addLayout(metrics_row)

        root_layout.addWidget(progress_box, 0)

        # -------------------------------------------------------------------
        # F1 Inline Keyboard Shortcuts Reference (Hidden by default)
        # -------------------------------------------------------------------
        self.lbl_shortcuts_help = QLabel(SHORTCUTS_HELP_TEXT, root)
        self.lbl_shortcuts_help.setObjectName("shortcutsHelp")
        self.lbl_shortcuts_help.hide()
        root_layout.addWidget(self.lbl_shortcuts_help, 0)

        # -------------------------------------------------------------------
        # Collapsible Text Console for yt-dlp Log Output (Toggled via Ctrl+L)
        # -------------------------------------------------------------------
        self.txt_console = QPlainTextEdit(root)
        self.txt_console.setObjectName("logConsole")
        self.txt_console.setReadOnly(True)
        self.txt_console.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap)
        self.txt_console.hide()
        root_layout.addWidget(self.txt_console, 1)

        self.setCentralWidget(root)

        # Transient status timer
        self._transient_timer = QTimer(self)
        self._transient_timer.setSingleShot(True)
        self._transient_timer.timeout.connect(self._clear_transient_status)

    # -----------------------------------------------------------------------
    # Theme & Styling (Uniform Monospace Font, Pure Dark Charcoal, Zero Boxes)
    # -----------------------------------------------------------------------
    def _apply_theme(self) -> None:
        p = PALETTE
        mono_family = resolve_system_font(PREFERRED_MONO_FONTS)

        font_ui = QFont(mono_family, UI_FONT_SIZE)
        font_meta = QFont(mono_family, 10)
        font_mono_console = QFont(mono_family, 11)

        self.txt_url.setFont(font_ui)
        self.lbl_url_validation.setFont(font_meta)
        self.txt_path.setFont(font_ui)
        self.combo_format.setFont(font_ui)
        self.lbl_ffmpeg_warning.setFont(font_meta)

        self.chk_playlist.setFont(font_ui)
        self.chk_numbering.setFont(font_ui)
        self.chk_sync.setFont(font_ui)

        self.lbl_meta_text.setFont(font_ui)

        self.lbl_progress_status.setFont(font_ui)
        self.lbl_progress_speed.setFont(font_meta)
        self.lbl_progress_eta.setFont(font_meta)
        self.lbl_progress_percent.setFont(QFont(mono_family, UI_FONT_SIZE, QFont.Weight.Bold))

        self.lbl_shortcuts_help.setFont(font_meta)
        self.txt_console.setFont(font_mono_console)

        self.setStyleSheet(
            f"""
            QMainWindow, QWidget {{
                background-color: {p['canvas']};
                color: {p['fg']};
                border: none;
            }}
            QLabel#validationLabel {{
                color: {p['danger']};
            }}
            QLabel#statusLabel {{
                color: {p['fg']};
            }}
            QLabel#metricLabel {{
                color: {p['fg_subtle']};
            }}
            QLabel#percentLabel {{
                color: {p['fg']};
            }}
            QLabel#metaContent {{
                color: {p['fg']};
                padding: 4px 0;
            }}
            QLabel#shortcutsHelp {{
                color: {p['fg_subtle']};
                padding: 6px 0;
            }}
            QLineEdit {{
                background-color: {p['input_bg']};
                color: {p['fg']};
                border: 1px solid {p['input_border']};
                border-radius: 4px;
                padding: 4px 10px;
                selection-background-color: {p['sel_bg']};
                selection-color: {p['sel_fg']};
            }}
            QLineEdit:focus {{
                border: 1px solid {p['input_focus_border']};
            }}
            QComboBox {{
                background-color: {p['input_bg']};
                color: {p['fg']};
                border: 1px solid {p['input_border']};
                border-radius: 4px;
                padding: 4px 10px;
            }}
            QComboBox:focus {{
                border: 1px solid {p['input_focus_border']};
            }}
            QComboBox::drop-down {{
                border: none;
                width: 24px;
            }}
            QComboBox QAbstractItemView {{
                background-color: {p['input_bg']};
                color: {p['fg']};
                border: 1px solid {p['input_border']};
                selection-background-color: {p['sel_bg']};
                selection-color: {p['sel_fg']};
                padding: 4px;
            }}
            /* Checkboxes */
            QCheckBox {{
                color: {p['fg']};
                background-color: transparent;
                spacing: 8px;
            }}
            QCheckBox:hover {{
                color: #ffffff;
            }}
            QCheckBox:disabled {{
                color: {p['meta']};
            }}
            QCheckBox::indicator {{
                width: 14px;
                height: 14px;
                background-color: {p['checkbox_bg']};
                border: 1px solid {p['checkbox_border']};
                border-radius: 3px;
            }}
            QCheckBox::indicator:hover {{
                border: 1px solid {p['input_focus_border']};
            }}
            QCheckBox::indicator:checked {{
                background-color: {p['fg']};
                border: 2px solid {p['checkbox_bg']};
                border-radius: 3px;
            }}
            QCheckBox::indicator:disabled {{
                background-color: {p['checkbox_bg']};
                border: 1px solid {p['input_border']};
            }}
            QCheckBox::indicator:checked:disabled {{
                background-color: {p['meta']};
                border: 2px solid {p['checkbox_bg']};
                border-radius: 3px;
            }}
            /* Monochrome Progress Bar */
            QProgressBar {{
                background-color: {p['progress_bg']};
                border: none;
                border-radius: 3px;
            }}
            QProgressBar::chunk {{
                background-color: {p['progress_chunk']};
                border-radius: 3px;
            }}
            /* Console */
            QPlainTextEdit#logConsole {{
                background-color: {p['console_bg']};
                color: {p['fg_subtle']};
                border: 1px solid {p['input_border']};
                border-radius: 4px;
                padding: 8px;
                selection-background-color: {p['sel_bg']};
                selection-color: {p['sel_fg']};
            }}
            """
        )

    # -----------------------------------------------------------------------
    # System Dependency & Clipboard Checks
    # -----------------------------------------------------------------------
    def _check_system_ffmpeg(self) -> None:
        self._has_ffmpeg, msg = check_ffmpeg_available()
        if not self._has_ffmpeg:
            self._append_console_log("warning", f"[dependency] {msg}")
            self.lbl_ffmpeg_warning.setText(
                "⚠ ffmpeg not found on PATH — MP3/FLAC conversion unavailable; using native streams."
            )
            self.lbl_ffmpeg_warning.show()

            # Disable transcode-only profiles in combobox when FFmpeg is absent
            model = self.combo_format.model()
            if isinstance(model, QStandardItemModel):
                for idx in range(self.combo_format.count()):
                    if self.combo_format.itemText(idx) in FFMPEG_TRANSCODE_FORMATS:
                        item = model.item(idx)
                        if item is not None:
                            item.setEnabled(False)
            if self.combo_format.currentText() in FFMPEG_TRANSCODE_FORMATS:
                native_idx = self.combo_format.findText("Best Audio (Native / No Re-encode)")
                if native_idx >= 0:
                    self.combo_format.setCurrentIndex(native_idx)
        else:
            self._append_console_log("info", f"[dependency] {msg}")
            self.lbl_ffmpeg_warning.hide()

    def _setup_clipboard_monitor(self) -> None:
        clipboard = QApplication.clipboard()
        if clipboard is not None:
            clipboard.dataChanged.connect(self._on_clipboard_changed)
        # Check immediately on launch
        self._check_clipboard_for_url()

    def _on_clipboard_changed(self) -> None:
        self._check_clipboard_for_url()

    def _check_clipboard_for_url(self) -> None:
        clipboard = QApplication.clipboard()
        if clipboard is None:
            return
        text = clean_url(clipboard.text())
        if not text or text == self._last_clipboard_url:
            return
        if is_media_url(text):
            # Auto-populate if URL input is empty or holds a previously finished download
            if not self.txt_url.text().strip() or self._url_completed:
                self._last_clipboard_url = text
                self.txt_url.setText(text)
                self._url_completed = False
                self.show_transient_status("auto-detected media URL from clipboard")

    def _on_url_input_changed(self, text: str) -> None:
        self._url_completed = False
        if self.lbl_url_validation.isVisible():
            self.lbl_url_validation.hide()

        # Auto-detect if URL points to a playlist
        if "list=" in text or "/playlist" in text:
            if not self.chk_playlist.isChecked():
                self.chk_playlist.setChecked(True)
                self.show_transient_status("playlist detected")

    def _on_playlist_toggled(self, checked: bool) -> None:
        if not checked and self.chk_sync.isChecked():
            self.chk_sync.setChecked(False)
        self.chk_numbering.setEnabled(checked and not self.chk_sync.isChecked())
        self.show_transient_status("playlist download enabled" if checked else "single item download")

    def _on_sync_toggled(self, checked: bool) -> None:
        if checked:
            if not self.chk_playlist.isChecked():
                self.chk_playlist.setChecked(True)
            self.chk_numbering.setChecked(True)
            self.chk_numbering.setEnabled(False)
            self.show_transient_status("sync mode enabled (deletes unlisted files)")
        else:
            self.chk_numbering.setEnabled(self.chk_playlist.isChecked())
            self.show_transient_status("sync mode disabled")

    def _toggle_numbering_shortcut(self) -> None:
        if self.chk_numbering.isEnabled():
            self.chk_numbering.setChecked(not self.chk_numbering.isChecked())

    def _validate_input_url(self) -> bool:
        url = clean_url(self.txt_url.text())
        if not url:
            self.lbl_url_validation.setText("Please enter a media URL.")
            self.lbl_url_validation.show()
            return False
        if not is_valid_url(url):
            self.lbl_url_validation.setText("Invalid URL. Must be http:// or https://")
            self.lbl_url_validation.show()
            return False
        self.lbl_url_validation.hide()
        return True

    # -----------------------------------------------------------------------
    # Directory Picker & Actions (Ctrl+B / Ctrl+O)
    # -----------------------------------------------------------------------
    def browse_destination(self) -> None:
        current = self.txt_path.text().strip() or str(Path.home())
        chosen = QFileDialog.getExistingDirectory(self, "Select Output Destination", current)
        if chosen:
            self.txt_path.setText(chosen)

    def open_destination_folder(self) -> None:
        target = self._last_completed_target or self.txt_path.text().strip()
        path = Path(target)
        folder = path if path.is_dir() else path.parent

        if folder.exists():
            QDesktopServices.openUrl(QUrl.fromLocalFile(str(folder)))
            self.show_transient_status(f"opened: {folder.name}")
        else:
            self.show_transient_status("destination folder does not exist yet")

    # -----------------------------------------------------------------------
    # Download Execution & Lifecycle
    # -----------------------------------------------------------------------
    def start_download(self) -> None:
        if self._active_worker and self._active_worker.isRunning():
            return
        if self.combo_format.view().isVisible():
            return

        if not self._validate_input_url():
            return

        url = clean_url(self.txt_url.text())
        dest_dir = self.txt_path.text().strip()
        if not dest_dir:
            self.lbl_url_validation.setText("Please select an output destination directory.")
            self.lbl_url_validation.show()
            return

        fmt_choice = self.combo_format.currentText()
        if fmt_choice in FFMPEG_TRANSCODE_FORMATS and not self._has_ffmpeg:
            self.lbl_url_validation.setText(
                f"FFmpeg is required for '{fmt_choice}'. Select 'Best Audio (Native / No Re-encode)'."
            )
            self.lbl_url_validation.show()
            return

        dl_playlist = self.chk_playlist.isChecked()
        num_playlist = self.chk_numbering.isChecked()
        sync_mode = self.chk_sync.isChecked()

        # UI state transitions
        self._transient_timer.stop()
        self._transient_status = ""
        self.progress_bar.setValue(0)
        self.lbl_progress_percent.setText("0.0%")
        self.lbl_progress_status.setText("Connecting…")
        self.lbl_progress_speed.setText("-- MB/s")
        self.lbl_progress_eta.setText("ETA: --:--")

        self.lbl_meta_text.hide()
        self.lbl_meta_text.setText("")

        # Instantiate background worker
        self._active_worker = DownloadWorker(
            url=url,
            output_dir=dest_dir,
            format_choice=fmt_choice,
            download_playlist=dl_playlist,
            number_playlist=num_playlist,
            sync_mode=sync_mode,
            parent=self,
        )
        self._active_worker.progressUpdated.connect(self._on_worker_progress)
        self._active_worker.logEmitted.connect(self._append_console_log)
        self._active_worker.metadataLoaded.connect(self._on_metadata_loaded)
        self._active_worker.downloadFinished.connect(self._on_download_finished)
        self._active_worker.start()

    def cancel_download(self) -> None:
        if self._active_worker and self._active_worker.isRunning():
            self.lbl_progress_status.setText("Cancelling download…")
            self._active_worker.cancel()

    def _on_worker_progress(self, percent: float, speed_str: str, eta_str: str, status_msg: str) -> None:
        int_val = int(max(0.0, min(100.0, percent)) * 10)
        self.progress_bar.setValue(int_val)
        self.lbl_progress_percent.setText(f"{percent:.1f}%")

        if speed_str:
            self.lbl_progress_speed.setText(speed_str)
        if eta_str:
            self.lbl_progress_eta.setText(f"ETA: {eta_str}" if not eta_str.startswith("ETA") else eta_str)
        if status_msg:
            self.lbl_progress_status.setText(status_msg)

    def _on_metadata_loaded(self, info: dict) -> None:
        title = info.get("title", "")
        duration = info.get("duration")
        uploader = info.get("uploader") or info.get("channel", "")

        dur_str = ""
        if duration:
            m, s = divmod(int(duration), 60)
            h, m = divmod(m, 60)
            dur_str = f" · {h:02d}:{m:02d}:{s:02d}" if h > 0 else f" · {m:02d}:{s:02d}"

        channel_str = f" · {uploader}" if uploader else ""
        if title:
            self.lbl_meta_text.setText(f"{title}{channel_str}{dur_str}")
            self.lbl_meta_text.show()

    def _on_download_finished(self, success: bool, message: str, target_path: str) -> None:
        self._last_completed_target = target_path
        self._active_worker = None
        self._transient_timer.stop()
        self._transient_status = ""

        if success:
            self._url_completed = True
            self.txt_url.selectAll()
            self.progress_bar.setValue(1000)
            self.lbl_progress_percent.setText("100.0%")
            self._idle_status_text = f"✓ {message}"
            self.lbl_progress_status.setText(self._idle_status_text)
            self.lbl_progress_speed.setText("")
            self.lbl_progress_eta.setText("Finished")
        else:
            self._idle_status_text = f"✕ {message}"
            self.lbl_progress_status.setText(self._idle_status_text)
            self.lbl_progress_speed.setText("")
            self.lbl_progress_eta.setText("")

    # -----------------------------------------------------------------------
    # Console Logs & F1 Shortcuts Reference
    # -----------------------------------------------------------------------
    def toggle_shortcuts_help(self) -> None:
        self._help_expanded = not self._help_expanded
        self.lbl_shortcuts_help.setVisible(self._help_expanded)
        self._adjust_window_height()

    def toggle_console_expanded(self) -> None:
        self._logs_expanded = not self._logs_expanded
        self.txt_console.setVisible(self._logs_expanded)
        self._adjust_window_height()

    def _adjust_window_height(self) -> None:
        target_h = 260
        if self._help_expanded:
            target_h += 130
        if self._logs_expanded:
            target_h += 230
        if self.height() < target_h or not (self._help_expanded or self._logs_expanded):
            self.resize(self.width(), target_h)

    def _append_console_log(self, level: str, msg: str) -> None:
        timestamp = datetime.datetime.now().strftime("%H:%M:%S")
        prefix = f"[{timestamp}] [{level.upper()}] "
        line = f"{prefix}{msg}"

        self._log_line_count += 1
        self.txt_console.appendPlainText(line)
        self.txt_console.moveCursor(QTextCursor.MoveOperation.End)

    def clear_console_logs(self) -> None:
        self.txt_console.clear()
        self._log_line_count = 0
        self.show_transient_status("logs cleared")

    # -----------------------------------------------------------------------
    # Transient Status Routing (Unified into lbl_progress_status)
    # -----------------------------------------------------------------------
    def show_transient_status(self, msg: str) -> None:
        self._transient_status = msg
        if not (self._active_worker and self._active_worker.isRunning()):
            self.lbl_progress_status.setText(msg)
        self._transient_timer.start(2800)

    def _clear_transient_status(self) -> None:
        self._transient_status = ""
        if not (self._active_worker and self._active_worker.isRunning()):
            self.lbl_progress_status.setText(self._idle_status_text)

    # -----------------------------------------------------------------------
    # Shortcuts & Event Handling
    # -----------------------------------------------------------------------
    def _setup_shortcuts(self) -> None:
        def bind(keys: str, callback: Any) -> None:
            QShortcut(QKeySequence(keys), self, callback)

        bind("F1", self.toggle_shortcuts_help)
        bind("Return", self.start_download)
        bind("Enter", self.start_download)
        bind("Escape", self._handle_escape)

        bind("Ctrl+B", self.browse_destination)
        bind("Ctrl+O", self.open_destination_folder)
        bind("Ctrl+L", self.toggle_console_expanded)
        bind("Ctrl+K", self.clear_console_logs)
        bind("Ctrl+Shift+L", self.clear_console_logs)

        bind("Ctrl+P", lambda: self.chk_playlist.setChecked(not self.chk_playlist.isChecked()))
        bind("Ctrl+N", self._toggle_numbering_shortcut)
        bind("Ctrl+S", lambda: self.chk_sync.setChecked(not self.chk_sync.isChecked()))

        bind("Ctrl+Q", self.close)

    def _handle_escape(self) -> None:
        if self._help_expanded:
            self.toggle_shortcuts_help()
            return
        if self._logs_expanded:
            self.toggle_console_expanded()
            return
        if self._active_worker and self._active_worker.isRunning():
            self.cancel_download()
            return
        self.close()

    def changeEvent(self, event: QEvent) -> None:
        if event.type() == QEvent.Type.ActivationChange and self.isActiveWindow():
            self._check_clipboard_for_url()
        super().changeEvent(event)

    def closeEvent(self, event: QCloseEvent) -> None:
        if self._active_worker and self._active_worker.isRunning():
            self._active_worker.cancel()
            try:
                self._active_worker.progressUpdated.disconnect()
                self._active_worker.logEmitted.disconnect()
                self._active_worker.metadataLoaded.disconnect()
                self._active_worker.downloadFinished.disconnect()
            except Exception:
                pass
            if not self._active_worker.wait(1500):
                self._active_worker.terminate()
                self._active_worker.wait(500)
        super().closeEvent(event)


# ---------------------------------------------------------------------------
# Entry Point
# ---------------------------------------------------------------------------
def main() -> None:
    os.environ.setdefault("QT_AUTO_SCREEN_SCALE_FACTOR", "1")

    app = QApplication(sys.argv)
    app.setApplicationName("YT-DLP Downloader")
    app.setOrganizationName("Vibe-Coding")
    app.setWindowIcon(load_app_icon())

    window = YtDownloaderWindow()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
