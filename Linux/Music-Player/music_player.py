#!/usr/bin/env python3
"""
Minimal Music Player — Ultra-minimalist, zero-chrome, keyboard-driven offline
music player for Linux (PyQt6 + PyQt6-Multimedia + mutagen).

Engineered as a sibling to Scratchpad in the Vibe-Coding Linux Desktop Design System:
- Zero cover art support (never extracted, decoded, cached, or given layout space)
- Zero clickable UI buttons (100% keyboard-driven)
- Main canvas displays ONLY: Track Name, Artist, razor-thin 2px flat progress bar
  with elapsed MM:SS, and total Duration MM:SS (+ whisper-quiet bottom-right telemetry)
- Hidden shortcuts: F1 is the exclusive way to view the keyboard shortcuts overlay
- Scratchpad-style SQLite Saved Playlists Library (Ctrl+L), inline 'save as' prompt
  (Ctrl+S), .m3u/.m3u8 import & export, live searchable track switcher (L or /),
  undo support (Ctrl+Z), and automatic session & preference persistence
- Pure flat matte charcoal (#141414) dark palette & soft off-white (#f2f2f0) light palette
- Strict 24px internal viewport padding, frameless window drag & 8px edge resize
"""

from __future__ import annotations

import ctypes
import ctypes.util
import datetime
import glob
import json
import os
import random
import re
import select
import sqlite3
import struct
import sys
import threading
import time
import urllib.parse
from dataclasses import dataclass
from pathlib import Path
from typing import Any

# ---------------------------------------------------------------------------
# Self-Healing Local .venv Bootstrap
# ---------------------------------------------------------------------------
_APP_DIR = Path(__file__).resolve().parent
_VENV_DIR = _APP_DIR / ".venv"
_VENV_PYTHON = _VENV_DIR / "bin" / "python"
if not _VENV_PYTHON.exists():
    _VENV_PYTHON = _VENV_DIR / "bin" / "python3"

if (
    _VENV_PYTHON.exists()
    and Path(sys.prefix).resolve() != _VENV_DIR.resolve()
    and os.environ.get("_MINIMAL_MUSIC_PLAYER_VENV") != "1"
):
    os.environ["_MINIMAL_MUSIC_PLAYER_VENV"] = "1"
    os.execv(str(_VENV_PYTHON), [str(_VENV_PYTHON), *sys.argv])

import mutagen  # type: ignore[import-not-found,reportMissingImports]
from PyQt6.QtCore import (  # type: ignore[import-not-found,reportMissingImports]
    QEvent,
    QMetaType,
    QObject,
    QPoint,
    QRect,
    Qt,
    QThread,
    QTimer,
    QUrl,
    pyqtClassInfo,
    pyqtProperty,
    pyqtSignal,
    pyqtSlot,
)
from PyQt6.QtGui import (  # type: ignore[import-not-found,reportMissingImports]
    QColor,
    QFont,
    QFontDatabase,
    QFontMetrics,
    QIcon,
    QPainter,
)
from PyQt6.QtMultimedia import (  # type: ignore[import-not-found,reportMissingImports]
    QAudio,
    QAudioOutput,
    QMediaPlayer,
)
from PyQt6.QtWidgets import (  # type: ignore[import-not-found,reportMissingImports]
    QAbstractItemView,
    QApplication,
    QFileDialog,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMainWindow,
    QScrollArea,
    QSizePolicy,
    QStackedWidget,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

try:
    from PyQt6.QtDBus import (  # type: ignore[import-not-found,reportMissingImports]
        QDBusAbstractAdaptor,
        QDBusArgument,
        QDBusConnection,
        QDBusMessage,
        QDBusObjectPath,
    )
    _HAS_QTDBUS = True
except ImportError:
    _HAS_QTDBUS = False


# ---------------------------------------------------------------------------
# Color Palettes (Desktop Design System — Exact Match with Scratchpad)
# ---------------------------------------------------------------------------
PALETTE_DARK: dict[str, str] = {
    "canvas": "#141414",   # 100% flat matte charcoal, zero borders/gradients/shadows
    "fg": "#e0e0e0",       # Track title and primary text
    "sel_bg": "#323842",   # Active highlight background in overlays
    "sel_fg": "#ffffff",   # Active highlight foreground in overlays
    "meta": "#585858",     # Artist subtitle, MM:SS timers, unfilled progress track, telemetry
    "active": "#e0e0e0",   # Filled progress bar segment
}

PALETTE_LIGHT: dict[str, str] = {
    "canvas": "#f2f2f0",
    "fg": "#1e1e1e",
    "sel_bg": "#323842",
    "sel_fg": "#ffffff",
    "meta": "#888888",
    "active": "#141414",
}


# ---------------------------------------------------------------------------
# Typography, Geometry & Audio Constants
# ---------------------------------------------------------------------------
APP_TITLE = "Minimal Music Player"
DESKTOP_FILE_NAME = "minimal-music-player"
DATA_DIR = Path.home() / ".local" / "share" / "minimal-music-player"
DB_PATH = DATA_DIR / "player.db"
ICON_CANDIDATES = ("icon.svg", "icon.png", "icon.ico")

PREFERRED_MONO_FONTS: list[str] = [
    "JetBrains Mono",
    "Fira Code",
    "Cascadia Code",
    "Iosevka",
    "Source Code Pro",
    "Ubuntu Mono",
    "DejaVu Sans Mono",
    "monospace",
]

PREFERRED_SANS_FONTS: list[str] = [
    "Roboto",
    "Inter",
    "Cantarell",
    "Ubuntu",
    "Noto Sans",
    "DejaVu Sans",
    "sans-serif",
]

DEFAULT_FONT_SIZE = 13
MIN_FONT_SIZE = 8
MAX_FONT_SIZE = 48

PADDING_PX = 24
RESIZE_MARGIN_PX = 8

SUPPORTED_AUDIO_EXTENSIONS: frozenset[str] = frozenset(
    {".mp3", ".flac", ".ogg", ".opus", ".m4a", ".wav"}
)
SUPPORTED_PLAYLIST_EXTENSIONS: frozenset[str] = frozenset({".m3u", ".m3u8"})

SEEK_STEP_MS = 5_000
SEEK_LARGE_STEP_MS = 15_000
PREV_RESTART_THRESHOLD_MS = 3_000
VOLUME_STEP_PCT = 5
DEFAULT_VOLUME_PCT = 85

REPEAT_MODES: tuple[str, ...] = ("Off", "All", "One")

SHORTCUT_TABLE: list[tuple[str, str]] = [
    ("play / pause", "space / media play"),
    ("seek ±5s / ±15s", "left / right  (shift ±15s)"),
    ("next track", "n / down / media next"),
    ("previous track", "p / up / media prev  (restarts >3s)"),
    ("volume ±5%", "= / +  and  -"),
    ("mute / unmute", "m"),
    ("toggle shuffle", "s"),
    ("cycle repeat", "r  (off -> all -> one)"),
    ("toggle playlist", "h"),
    ("open folder", "o / ctrl+o"),
    ("open .m3u/.m3u8", "ctrl+shift+o"),
    ("append files", "a / ctrl+a"),
    ("track switcher", "l / /  (filter · ↑↓ · enter · del · ctrl+↑↓)"),
    ("saved playlists", "ctrl+l  (filter · enter · ctrl+p · ctrl+e · del)"),
    ("save playlist", "ctrl+s  (inline save as)"),
    ("export .m3u/.m3u8", "ctrl+shift+s"),
    ("clear queue", "ctrl+n / ctrl+w  (ctrl+z undo)"),
    ("cycle telemetry", "ctrl+i"),
    ("font size", "ctrl+= / ctrl+- / ctrl+0"),
    ("font family", "ctrl+m  (mono / sans)"),
    ("surface theme", "ctrl+shift+d  (dark / light)"),
    ("fullscreen / frameless", "f11 / ctrl+shift+f"),
    ("shortcuts overlay", "f1"),
    ("close / quit", "esc / ctrl+q"),
]


# ---------------------------------------------------------------------------
# Font Probing & Formatting Helpers
# ---------------------------------------------------------------------------


def probe_font_family(candidates: list[str], fallback: str) -> str:
    """Probe installed system fonts and return the first exact available match."""
    installed = QFontDatabase.families()
    lookup = {fam.lower(): fam for fam in installed}
    for candidate in candidates:
        matched = lookup.get(candidate.lower())
        if matched:
            return matched
    return fallback


def make_font(
    family: str,
    point_size: int,
    *,
    bold: bool = False,
    monospace: bool = False,
) -> QFont:
    font = QFont(family, point_size)
    font.setWeight(QFont.Weight.Bold if bold else QFont.Weight.Normal)
    if monospace:
        font.setStyleHint(QFont.StyleHint.Monospace)
        font.setFixedPitch(True)
    else:
        font.setStyleHint(QFont.StyleHint.SansSerif)
    return font


def load_app_icon() -> QIcon:
    for name in ICON_CANDIDATES:
        candidate = _APP_DIR / name
        if candidate.is_file():
            icon = QIcon(str(candidate))
            if not icon.isNull():
                return icon
    return QIcon()


def format_mm_ss(ms: int) -> str:
    """Format milliseconds strictly as MM:SS."""
    total_seconds = max(0, int(ms // 1000))
    minutes = total_seconds // 60
    seconds = total_seconds % 60
    return f"{minutes:02d}:{seconds:02d}"


def format_timestamp(epoch_ms: int) -> str:
    dt = datetime.datetime.fromtimestamp(epoch_ms / 1000.0)
    return dt.strftime("%b %-d, %H:%M")


def natural_sort_key(path: Path) -> list[Any]:
    """Natural sort key so track 2 precedes track 10."""
    parts = re.split(r"(\d+)", str(path).lower())
    return [int(p) if p.isdigit() else p for p in parts]


def clean_filename_title(path: Path) -> str:
    """Produce a clean fallback track title from an audio filename."""
    stem = path.stem
    stem = re.sub(r"\s*\[[A-Za-z0-9_-]{11}\]\s*$", "", stem)
    stem = re.sub(r"^\d{1,3}\s*[-._)]\s+", "", stem)
    stem = stem.replace("_", " ")
    stem = re.sub(r"\s+", " ", stem).strip()
    return stem or path.stem


def auto_generate_playlist_name(tracks: list[Track], fallback_dir: str = "") -> str:
    """Auto-name a playlist from common parent folder or timestamp (like Scratchpad)."""
    if tracks:
        parents = {t.path.parent.name for t in tracks if t.path.parent.name}
        if len(parents) == 1:
            folder_name = next(iter(parents)).strip()
            if folder_name:
                return folder_name[:32]
    if fallback_dir:
        name = Path(fallback_dir).name.strip()
        if name:
            return name[:32]
    now_str = datetime.datetime.now().strftime("%b %-d, %H:%M")
    return f"Playlist {now_str}"


# ---------------------------------------------------------------------------
# Track & Saved Playlist Models + SQLite Repository (Scratchpad Pattern)
# ---------------------------------------------------------------------------
@dataclass
class Track:
    path: Path
    title: str
    artist: str = "Unknown Artist"
    duration_ms: int = 0
    metadata_loaded: bool = False


@dataclass
class SavedPlaylist:
    id: int
    name: str
    track_paths: list[str]
    track_count: int
    is_pinned: bool
    created_at: int  # epoch ms
    updated_at: int  # epoch ms


class PlaylistRepository:
    """SQLite persistence for Saved Playlists Library, active session, and preferences."""

    def __init__(self, db_path: Path | None = None) -> None:
        self.db_path = db_path if db_path is not None else DB_PATH
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA journal_mode=WAL;")
        self._conn.execute("PRAGMA synchronous=NORMAL;")
        self._init_db()

    def _init_db(self) -> None:
        with self._conn:
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS playlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    tracks_json TEXT NOT NULL,
                    track_count INTEGER NOT NULL DEFAULT 0,
                    is_pinned INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """
            )
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS preferences (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """
            )

    def close(self) -> None:
        try:
            self._conn.close()
        except Exception:
            pass

    def get_pref(self, key: str, default: str = "") -> str:
        row = self._conn.execute(
            "SELECT value FROM preferences WHERE key = ?", (key,)
        ).fetchone()
        return str(row["value"]) if row else default

    def set_pref(self, key: str, value: str) -> None:
        with self._conn:
            self._conn.execute(
                "INSERT OR REPLACE INTO preferences (key, value) VALUES (?, ?)",
                (key, str(value)),
            )

    def set_prefs(self, mapping: dict[str, str]) -> None:
        with self._conn:
            self._conn.executemany(
                "INSERT OR REPLACE INTO preferences (key, value) VALUES (?, ?)",
                [(k, str(v)) for k, v in mapping.items()],
            )

    @staticmethod
    def _row_to_playlist(row: sqlite3.Row) -> SavedPlaylist:
        try:
            paths = json.loads(str(row["tracks_json"]))
            if not isinstance(paths, list):
                paths = []
        except Exception:
            paths = []
        clean_paths = [str(p) for p in paths]
        return SavedPlaylist(
            id=int(row["id"]),
            name=str(row["name"]),
            track_paths=clean_paths,
            track_count=len(clean_paths),
            is_pinned=bool(row["is_pinned"]),
            created_at=int(row["created_at"]),
            updated_at=int(row["updated_at"]),
        )

    def list_playlists(self, search_query: str = "") -> list[SavedPlaylist]:
        query = search_query.strip()
        if query:
            like = f"%{query}%"
            rows = self._conn.execute(
                """
                SELECT * FROM playlists
                WHERE name LIKE ? OR tracks_json LIKE ?
                ORDER BY is_pinned DESC, updated_at DESC
                """,
                (like, like),
            ).fetchall()
        else:
            rows = self._conn.execute(
                "SELECT * FROM playlists ORDER BY is_pinned DESC, updated_at DESC"
            ).fetchall()
        return [self._row_to_playlist(r) for r in rows]

    def get_playlist_by_id(self, playlist_id: int) -> SavedPlaylist | None:
        row = self._conn.execute(
            "SELECT * FROM playlists WHERE id = ?", (playlist_id,)
        ).fetchone()
        return self._row_to_playlist(row) if row else None

    def upsert_playlist(
        self,
        name: str,
        track_paths: list[str],
        playlist_id: int | None = None,
        created_at: int | None = None,
        is_pinned: bool = False,
    ) -> int:
        now_ms = int(datetime.datetime.now().timestamp() * 1000)
        encoded = json.dumps(track_paths, ensure_ascii=False)
        count = len(track_paths)
        existing = self.get_playlist_by_id(playlist_id) if playlist_id is not None else None

        with self._conn:
            if existing is not None:
                self._conn.execute(
                    """
                    UPDATE playlists
                    SET name = ?, tracks_json = ?, track_count = ?,
                        is_pinned = ?, updated_at = ?
                    WHERE id = ?
                    """,
                    (name, encoded, count, int(existing.is_pinned), now_ms, existing.id),
                )
                return existing.id
            created = created_at if created_at is not None else now_ms
            cur = self._conn.execute(
                """
                INSERT INTO playlists (name, tracks_json, track_count, is_pinned, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (name, encoded, count, int(is_pinned), created, now_ms),
            )
            return int(cur.lastrowid or 0)

    def restore_playlist(self, playlist: SavedPlaylist) -> int:
        encoded = json.dumps(playlist.track_paths, ensure_ascii=False)
        with self._conn:
            cur = self._conn.execute(
                """
                INSERT OR REPLACE INTO playlists (id, name, tracks_json, track_count, is_pinned, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    playlist.id,
                    playlist.name,
                    encoded,
                    len(playlist.track_paths),
                    int(playlist.is_pinned),
                    playlist.created_at,
                    playlist.updated_at,
                ),
            )
            return int(cur.lastrowid or playlist.id)

    def toggle_pin(self, playlist: SavedPlaylist) -> None:
        with self._conn:
            self._conn.execute(
                "UPDATE playlists SET is_pinned = ? WHERE id = ?",
                (0 if playlist.is_pinned else 1, playlist.id),
            )

    def delete_playlist(self, playlist_id: int) -> None:
        with self._conn:
            self._conn.execute("DELETE FROM playlists WHERE id = ?", (playlist_id,))


# ---------------------------------------------------------------------------
# Zero-Cover-Art Metadata Scanner & M3U / M3U8 Playlist I/O
# ---------------------------------------------------------------------------
def read_track_metadata(path: Path) -> tuple[str, str, int]:
    """
    Read strictly text title, artist, and duration via mutagen (easy=True).
    Zero cover art frames (APIC/covr/pictures) are ever accessed or decoded.
    """
    fallback_title = clean_filename_title(path)
    title = fallback_title
    artist = "Unknown Artist"
    duration_ms = 0

    try:
        audio = mutagen.File(str(path), easy=True)
        if audio is not None:
            raw_title = audio.get("title")
            if raw_title and isinstance(raw_title, list) and str(raw_title[0]).strip():
                title = str(raw_title[0]).strip()

            raw_artist = audio.get("artist") or audio.get("albumartist")
            if raw_artist and isinstance(raw_artist, list) and str(raw_artist[0]).strip():
                artist = str(raw_artist[0]).strip()

            info = getattr(audio, "info", None)
            length = getattr(info, "length", 0.0) if info is not None else 0.0
            if length and length > 0:
                duration_ms = int(round(length * 1000))
    except Exception:
        pass

    return title, artist, duration_ms


def parse_m3u_playlist(m3u_path: Path) -> list[Path]:
    """Parse an .m3u or .m3u8 playlist preserving explicit track order."""
    tracks: list[Path] = []
    seen: set[Path] = set()
    base_dir = m3u_path.expanduser().resolve().parent

    try:
        lines = m3u_path.read_text(encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return tracks

    for raw in lines:
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.lower().startswith("file://"):
            parsed = urllib.parse.urlparse(line)
            candidate = Path(urllib.parse.unquote(parsed.path))
        else:
            candidate = Path(line)
            if not candidate.is_absolute():
                candidate = base_dir / candidate
        try:
            resolved = candidate.expanduser().resolve()
        except OSError:
            continue
        if (
            resolved.is_file()
            and resolved.suffix.lower() in SUPPORTED_AUDIO_EXTENSIONS
            and resolved not in seen
        ):
            seen.add(resolved)
            tracks.append(resolved)

    return tracks


def write_m3u_playlist(m3u_path: Path, tracks: list[Track]) -> None:
    """Write a standard UTF-8 #EXTM3U playlist file."""
    lines = ["#EXTM3U"]
    for track in tracks:
        secs = max(-1, int(track.duration_ms // 1000)) if track.duration_ms > 0 else -1
        lines.append(f"#EXTINF:{secs},{track.artist} - {track.title}")
        lines.append(str(track.path))
    m3u_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def scan_audio_targets(targets: list[Path]) -> list[Path]:
    """
    Collect supported audio files recursively from directories, direct files,
    or .m3u/.m3u8 playlists. Explicit playlist order is preserved; directory
    scans are naturally sorted.
    """
    discovered: list[Path] = []
    seen: set[Path] = set()

    for target in targets:
        try:
            resolved = target.expanduser().resolve()
        except OSError:
            continue

        if resolved.is_file():
            ext = resolved.suffix.lower()
            if ext in SUPPORTED_PLAYLIST_EXTENSIONS:
                for p in parse_m3u_playlist(resolved):
                    if p not in seen:
                        seen.add(p)
                        discovered.append(p)
            elif ext in SUPPORTED_AUDIO_EXTENSIONS and resolved not in seen:
                seen.add(resolved)
                discovered.append(resolved)
        elif resolved.is_dir():
            dir_files: list[Path] = []
            for root, dirs, files in os.walk(resolved):
                dirs[:] = sorted(
                    (d for d in dirs if not d.startswith(".")),
                    key=lambda s: s.lower(),
                )
                root_path = Path(root)
                for fname in files:
                    if fname.startswith("."):
                        continue
                    fpath = root_path / fname
                    if fpath.suffix.lower() in SUPPORTED_AUDIO_EXTENSIONS:
                        if fpath not in seen:
                            seen.add(fpath)
                            dir_files.append(fpath)
            dir_files.sort(key=natural_sort_key)
            discovered.extend(dir_files)

    return discovered


class MetadataWorker(QThread):
    """Background reader for track text metadata & duration (zero cover art)."""

    batchReady = pyqtSignal(list)  # list of (path_str, title, artist, duration_ms)

    def __init__(self, tracks: list[Track], start_index: int = 0) -> None:
        super().__init__()
        indexed: list[tuple[int, Path]] = [
            (i, t.path)
            for i, t in enumerate(tracks)
            if not t.metadata_loaded
        ]
        if start_index > 0:
            indexed.sort(key=lambda pair: (pair[0] < start_index, pair[0]))
        self._paths: list[Path] = [p for _, p in indexed]

    def run(self) -> None:
        batch: list[tuple[str, str, str, int]] = []
        for path in self._paths:
            if self.isInterruptionRequested():
                return
            title, artist, duration_ms = read_track_metadata(path)
            batch.append((str(path), title, artist, duration_ms))
            if len(batch) >= 24:
                self.batchReady.emit(batch)
                batch = []
        if batch and not self.isInterruptionRequested():
            self.batchReady.emit(batch)


# ---------------------------------------------------------------------------
# Global Unfocused Media Key Engine (MPRIS2 D-Bus + Kernel evdev + X11)
# ---------------------------------------------------------------------------
EV_KEY = 0x01
_INPUT_EVENT_STRUCT = struct.Struct("llHHi")

# Linux kernel input-event-codes.h hardware media keycodes
EVDEV_MEDIA_KEY_MAP: dict[int, str] = {
    164: "play_pause",  # KEY_PLAYPAUSE
    200: "play",        # KEY_PLAYCD
    207: "play",        # KEY_PLAY
    201: "pause",       # KEY_PAUSECD
    166: "stop",        # KEY_STOPCD
    128: "stop",        # KEY_STOP
    163: "next",        # KEY_NEXTSONG
    165: "previous",    # KEY_PREVIOUSSONG
    168: "seek_back",   # KEY_REWIND
    208: "seek_fwd",    # KEY_FASTFORWARD
}

# X11 XF86Audio* keysyms (AudioPlay placed after AudioPause in case both share a keycode)
X11_MEDIA_KEYSYMS: tuple[tuple[int, str], ...] = (
    (0x1008FF31, "pause"),       # XF86XK_AudioPause
    (0x1008FF14, "play_pause"),  # XF86XK_AudioPlay
    (0x1008FF15, "stop"),        # XF86XK_AudioStop
    (0x1008FF16, "previous"),    # XF86XK_AudioPrev
    (0x1008FF17, "next"),        # XF86XK_AudioNext
    (0x1008FF3E, "seek_back"),   # XF86XK_AudioRewind
    (0x1008FF97, "seek_fwd"),    # XF86XK_AudioForward
)


def _device_supports_media_keys(event_dev_path: str) -> bool:
    """Check /sys/class/input/eventN/device/capabilities/key bitmask for media keys."""
    name = os.path.basename(event_dev_path)
    cap_path = Path("/sys/class/input") / name / "device" / "capabilities" / "key"
    try:
        raw = cap_path.read_text(encoding="utf-8").strip()
    except OSError:
        return False
    if not raw:
        return False
    mask = 0
    for word in raw.split():
        try:
            mask = (mask << 64) | int(word, 16)
        except ValueError:
            return False
    return any(bool(mask & (1 << code)) for code in (163, 164, 165, 166, 200, 201, 207))


class GlobalMediaKeyListener(QObject):
    """
    Captures hardware media keys globally even when the window is unfocused or minimized:
      1. Direct kernel /dev/input/event* reader (works on Wayland, X11, and virtual terminals).
      2. X11 / XWayland XQueryKeymap fallback when kernel device nodes are root-only.
    """

    actionTriggered = pyqtSignal(str)

    def __init__(self, parent: QObject | None = None, *, enable_threads: bool = True) -> None:
        super().__init__(parent)
        self._stop_event = threading.Event()
        self.active_backends: list[str] = []
        self._threads: list[threading.Thread] = []

        if enable_threads:
            t_evdev = threading.Thread(target=self._evdev_loop, daemon=True)
            t_evdev.start()
            self._threads.append(t_evdev)

            if os.environ.get("DISPLAY") and os.environ.get("QT_QPA_PLATFORM") != "offscreen":
                t_x11 = threading.Thread(target=self._x11_keymap_loop, daemon=True)
                t_x11.start()
                self._threads.append(t_x11)

    def stop(self) -> None:
        self._stop_event.set()
        for t in self._threads:
            if t.is_alive():
                t.join(timeout=0.35)
        self._threads.clear()

    def _evdev_loop(self) -> None:
        fds: list[int] = []
        for path in sorted(glob.glob("/dev/input/event*")):
            if not _device_supports_media_keys(path):
                continue
            try:
                fd = os.open(path, os.O_RDONLY | os.O_NONBLOCK)
                fds.append(fd)
            except OSError:
                continue

        if not fds:
            return

        self.active_backends.append("evdev")
        rec_size = _INPUT_EVENT_STRUCT.size
        try:
            while not self._stop_event.is_set() and fds:
                try:
                    ready, _, _ = select.select(fds, [], [], 0.15)
                except OSError:
                    break
                for fd in ready:
                    try:
                        data = os.read(fd, rec_size * 16)
                    except BlockingIOError:
                        continue
                    except OSError:
                        fds.remove(fd)
                        try:
                            os.close(fd)
                        except OSError:
                            pass
                        continue

                    offset = 0
                    while offset + rec_size <= len(data):
                        _sec, _usec, ev_type, ev_code, ev_value = _INPUT_EVENT_STRUCT.unpack_from(
                            data, offset
                        )
                        offset += rec_size
                        # ev_value == 1 is initial key down (ignores release 0 & autorepeat 2)
                        if ev_type == EV_KEY and ev_value == 1:
                            action = EVDEV_MEDIA_KEY_MAP.get(ev_code)
                            if action is not None:
                                self.actionTriggered.emit(action)
        finally:
            for fd in fds:
                try:
                    os.close(fd)
                except OSError:
                    pass

    def _x11_keymap_loop(self) -> None:
        x11 = None
        disp = None
        try:
            x11_path = ctypes.util.find_library("X11")
            if not x11_path:
                return
            x11 = ctypes.cdll.LoadLibrary(x11_path)
            x11.XOpenDisplay.restype = ctypes.c_void_p
            x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
            x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
            x11.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
            x11.XKeysymToKeycode.restype = ctypes.c_ubyte
            x11.XQueryKeymap.argtypes = [ctypes.c_void_p, ctypes.c_char * 32]

            disp = x11.XOpenDisplay(None)
            if not disp:
                return

            kc_to_action: dict[int, str] = {}
            for keysym, action in X11_MEDIA_KEYSYMS:
                kc = int(x11.XKeysymToKeycode(disp, keysym))
                if kc > 0:
                    kc_to_action[kc] = action

            if not kc_to_action:
                return

            self.active_backends.append("x11")
            keymap = (ctypes.c_char * 32)()
            was_pressed: dict[int, bool] = {kc: False for kc in kc_to_action}

            while not self._stop_event.is_set():
                x11.XQueryKeymap(disp, keymap)
                for kc, action in kc_to_action.items():
                    byte_idx = kc >> 3
                    bit_mask = 1 << (kc & 7)
                    raw_byte = keymap[byte_idx]
                    b_val = raw_byte[0] if isinstance(raw_byte, (bytes, bytearray)) else ord(raw_byte)
                    is_pressed = bool(b_val & bit_mask)
                    if is_pressed and not was_pressed[kc]:
                        self.actionTriggered.emit(action)
                    was_pressed[kc] = is_pressed
                time.sleep(0.03)
        except Exception:
            pass
        finally:
            if disp and x11:
                try:
                    x11.XCloseDisplay(disp)
                except Exception:
                    pass


if _HAS_QTDBUS:

    def _dbus_string_list(items: list[str]) -> QDBusArgument:
        """Encode a Python list[str] strictly as D-Bus signature 'as' (QStringList)."""
        arg = QDBusArgument()
        arg.beginArray(QMetaType.Type.QString.value)
        for item in items:
            arg.add(str(item))
        arg.endArray()
        return arg

    def _dbus_int64(value: int) -> QDBusArgument:
        """Encode an integer strictly as D-Bus signature 'x' (int64 / qlonglong)."""
        arg = QDBusArgument()
        arg.add(int(value), QMetaType.Type.LongLong.value)
        return arg

    def _to_dbus_metadata(meta: dict[str, Any]) -> dict[str, Any]:
        """Convert mpris_metadata() dict to strict MPRIS2 D-Bus wire types."""
        out: dict[str, Any] = {}
        for k, v in meta.items():
            if k == "mpris:length":
                out[k] = _dbus_int64(int(v))
            elif k == "xesam:artist" and isinstance(v, list):
                out[k] = _dbus_string_list([str(s) for s in v])
            else:
                out[k] = v
        return out

    @pyqtClassInfo("D-Bus Interface", "org.mpris.MediaPlayer2")
    class MprisRootAdaptor(QDBusAbstractAdaptor):
        """org.mpris.MediaPlayer2 root interface adaptor for KDE Plasma & KDE Connect."""

        def __init__(self, host: QObject, window: MinimalMusicPlayerWindow) -> None:
            super().__init__(host)
            self._window = window

        @pyqtSlot()
        def Raise(self) -> None:
            self._window.showNormal()
            self._window.raise_()
            self._window.activateWindow()

        @pyqtSlot()
        def Quit(self) -> None:
            self._window.instant_quit()

        @pyqtProperty(bool)
        def CanQuit(self) -> bool:
            return True

        @pyqtProperty(bool)
        def CanRaise(self) -> bool:
            return True

        @pyqtProperty(bool)
        def CanSetFullscreen(self) -> bool:
            return True

        @pyqtProperty(bool)
        def Fullscreen(self) -> bool:
            return self._window.isFullScreen()

        @Fullscreen.setter
        def Fullscreen(self, value: bool) -> None:
            if bool(value) != self._window.isFullScreen():
                self._window.toggle_fullscreen()

        @pyqtProperty(bool)
        def HasTrackList(self) -> bool:
            return False

        @pyqtProperty(str)
        def Identity(self) -> str:
            return APP_TITLE

        @pyqtProperty(str)
        def DesktopEntry(self) -> str:
            return DESKTOP_FILE_NAME

        @pyqtProperty("QStringList")
        def SupportedUriSchemes(self) -> list[str]:
            return ["file"]

        @pyqtProperty("QStringList")
        def SupportedMimeTypes(self) -> list[str]:
            return [
                "audio/mpeg",
                "audio/flac",
                "audio/ogg",
                "audio/opus",
                "audio/mp4",
                "audio/x-wav",
                "audio/x-mpegurl",
                "application/vnd.apple.mpegurl",
            ]

    @pyqtClassInfo("D-Bus Interface", "org.mpris.MediaPlayer2.Player")
    class MprisPlayerAdaptor(QDBusAbstractAdaptor):
        """org.mpris.MediaPlayer2.Player adaptor for KDE Plasma, KDE Connect & media keys."""

        Seeked = pyqtSignal("qlonglong")

        def __init__(self, host: QObject, window: MinimalMusicPlayerWindow) -> None:
            super().__init__(host)
            self._window = window

        @pyqtSlot()
        def Next(self) -> None:
            self._window.dispatch_media_action("next")

        @pyqtSlot()
        def Previous(self) -> None:
            self._window.dispatch_media_action("previous")

        @pyqtSlot()
        def Pause(self) -> None:
            self._window.dispatch_media_action("pause")

        @pyqtSlot()
        def PlayPause(self) -> None:
            self._window.dispatch_media_action("play_pause")

        @pyqtSlot()
        def Stop(self) -> None:
            self._window.dispatch_media_action("stop")

        @pyqtSlot()
        def Play(self) -> None:
            self._window.dispatch_media_action("play")

        @pyqtSlot("qlonglong")
        def Seek(self, offset_us: int) -> None:
            self._window.seek_relative(int(offset_us // 1000))

        @pyqtSlot(QDBusObjectPath, "qlonglong")
        def SetPosition(self, track_id: QDBusObjectPath, position_us: int) -> None:
            self._window.mpris_set_position(track_id.path(), int(position_us))

        @pyqtSlot(str)
        def OpenUri(self, uri: str) -> None:
            self._window.mpris_open_uri(uri)

        @pyqtProperty(str)
        def PlaybackStatus(self) -> str:
            return self._window.mpris_playback_status()

        @pyqtProperty(str)
        def LoopStatus(self) -> str:
            return self._window.mpris_loop_status()

        @LoopStatus.setter
        def LoopStatus(self, value: str) -> None:
            self._window.mpris_set_loop_status(value)

        @pyqtProperty(float)
        def Rate(self) -> float:
            return 1.0

        @Rate.setter
        def Rate(self, _value: float) -> None:
            pass

        @pyqtProperty(float)
        def MinimumRate(self) -> float:
            return 1.0

        @pyqtProperty(float)
        def MaximumRate(self) -> float:
            return 1.0

        @pyqtProperty(bool)
        def Shuffle(self) -> bool:
            return self._window._shuffle

        @Shuffle.setter
        def Shuffle(self, value: bool) -> None:
            self._window.mpris_set_shuffle(bool(value))

        @pyqtProperty("QVariantMap")
        def Metadata(self) -> dict[str, Any]:
            return _to_dbus_metadata(self._window.mpris_metadata())

        @pyqtProperty(float)
        def Volume(self) -> float:
            return 0.0 if self._window._muted else (self._window._volume_pct / 100.0)

        @Volume.setter
        def Volume(self, value: float) -> None:
            self._window.mpris_set_volume(float(value))

        @pyqtProperty("qlonglong")
        def Position(self) -> int:
            return int(max(0, self._window._last_position_ms) * 1000)

        @pyqtProperty(bool)
        def CanGoNext(self) -> bool:
            return bool(self._window._tracks)

        @pyqtProperty(bool)
        def CanGoPrevious(self) -> bool:
            return bool(self._window._tracks)

        @pyqtProperty(bool)
        def CanPlay(self) -> bool:
            return bool(self._window._tracks)

        @pyqtProperty(bool)
        def CanPause(self) -> bool:
            return bool(self._window._tracks)

        @pyqtProperty(bool)
        def CanSeek(self) -> bool:
            return bool(self._window._tracks)

        @pyqtProperty(bool)
        def CanControl(self) -> bool:
            return True


class MprisService(QObject):
    """Registers Minimal Music Player on the desktop session D-Bus for KDE Plasma & KDE Connect."""

    OBJECT_PATH = "/org/mpris/MediaPlayer2"
    BASE_SERVICE = "org.mpris.MediaPlayer2.minimal_music_player"

    def __init__(self, window: MinimalMusicPlayerWindow) -> None:
        super().__init__(window)
        self._window = window
        self.registered = False
        self.service_name = ""
        self._root_adaptor: Any = None
        self._player_adaptor: Any = None

        if not _HAS_QTDBUS:
            return

        try:
            bus = QDBusConnection.sessionBus()
            if not bus.isConnected():
                return

            self._root_adaptor = MprisRootAdaptor(self, window)
            self._player_adaptor = MprisPlayerAdaptor(self, window)

            if not bus.registerObject(
                self.OBJECT_PATH,
                self,
                QDBusConnection.RegisterOption.ExportAdaptors,
            ):
                return

            for candidate in (
                self.BASE_SERVICE,
                f"{self.BASE_SERVICE}.instance{os.getpid()}",
            ):
                if bus.registerService(candidate):
                    self.service_name = candidate
                    self.registered = True
                    break
        except Exception:
            self.registered = False

    def emit_properties_changed(
        self,
        changed: dict[str, Any],
        *,
        interface: str = "org.mpris.MediaPlayer2.Player",
    ) -> None:
        if not self.registered or not _HAS_QTDBUS or not changed:
            return
        try:
            bus = QDBusConnection.sessionBus()
            if not bus.isConnected():
                return
            payload: dict[str, Any] = {}
            for k, v in changed.items():
                if k == "Metadata" and isinstance(v, dict):
                    payload[k] = _to_dbus_metadata(v)
                else:
                    payload[k] = v
            msg = QDBusMessage.createSignal(
                self.OBJECT_PATH,
                "org.freedesktop.DBus.Properties",
                "PropertiesChanged",
            )
            msg.setArguments(
                [
                    interface,
                    payload,
                    _dbus_string_list([]),
                ]
            )
            bus.send(msg)
        except Exception:
            pass

    def emit_seeked(self, position_us: int) -> None:
        if not self.registered or not _HAS_QTDBUS or self._player_adaptor is None:
            return
        try:
            self._player_adaptor.Seeked.emit(int(max(0, position_us)))
        except Exception:
            pass

    def unregister(self) -> None:
        if not self.registered or not _HAS_QTDBUS:
            return
        self.registered = False
        try:
            bus = QDBusConnection.sessionBus()
            if bus.isConnected():
                bus.unregisterObject(self.OBJECT_PATH)
                if self.service_name:
                    bus.unregisterService(self.service_name)
        except Exception:
            pass


# ---------------------------------------------------------------------------
# Zero-Chrome Widgets (Scratchpad Aesthetic)
# ---------------------------------------------------------------------------
class FlatProgressBar(QWidget):
    """
    Razor-thin 2px flat progress bar.
    Zero borders, zero gradients, zero draggable thumb/knob.
    """

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self._ratio: float = 0.0
        self._track_color = QColor(PALETTE_DARK["meta"])
        self._fill_color = QColor(PALETTE_DARK["active"])
        self.setFixedHeight(2)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents, True)

    def set_progress(self, position_ms: int, duration_ms: int) -> None:
        if duration_ms > 0:
            ratio = max(0.0, min(1.0, position_ms / float(duration_ms)))
        else:
            ratio = 0.0
        if abs(ratio - self._ratio) >= 1e-4 or (ratio in (0.0, 1.0) and ratio != self._ratio):
            self._ratio = ratio
            self.update()

    def set_colors(self, track_hex: str, fill_hex: str) -> None:
        self._track_color = QColor(track_hex)
        self._fill_color = QColor(fill_hex)
        self.update()

    def paintEvent(self, a0: Any) -> None:
        painter = QPainter(self)
        painter.setPen(Qt.PenStyle.NoPen)

        w = self.width()
        h = self.height()
        if w <= 0 or h <= 0:
            return

        painter.fillRect(0, 0, w, h, self._track_color)
        fill_w = int(round(w * self._ratio))
        if fill_w > 0:
            painter.fillRect(0, 0, min(w, fill_w), h, self._fill_color)


class ZeroChromePlaylistTable(QTableWidget):
    """
    Zero-chrome 4-column table (#, Title, Artist, Duration) that aligns every
    column to the exact pixel regardless of CJK/Japanese/fullwidth character widths.
    Zero borders, zero gridlines, zero headers, zero scrollbars.
    """

    trackActivated = pyqtSignal(int)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(0, 4, parent)
        self.setMouseTracking(True)
        self.setFrameShape(QFrame.Shape.NoFrame)
        self.setShowGrid(False)
        self.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        self.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.setTextElideMode(Qt.TextElideMode.ElideRight)
        self.setWordWrap(False)
        self.setCornerButtonEnabled(False)
        self.setCursor(Qt.CursorShape.PointingHandCursor)

        h_header = self.horizontalHeader()
        if h_header is not None:
            h_header.setVisible(False)
            h_header.setMinimumSectionSize(24)

        v_header = self.verticalHeader()
        if v_header is not None:
            v_header.setVisible(False)
            v_header.setMinimumSectionSize(20)
            v_header.setDefaultSectionSize(28)

        self._palette: dict[str, str] = PALETTE_DARK
        self._row_font: QFont = QFont()
        self._mono_font: QFont = QFont()

        self.cellClicked.connect(self._on_cell_clicked)

    def _on_cell_clicked(self, row: int, _col: int) -> None:
        item = self.item(row, 0)
        if item is not None:
            track_idx = item.data(Qt.ItemDataRole.UserRole)
            if isinstance(track_idx, int):
                self.trackActivated.emit(track_idx)

    def apply_style(
        self,
        palette: dict[str, str],
        row_font: QFont,
        mono_font: QFont,
    ) -> None:
        self._palette = palette
        self._row_font = row_font
        self._mono_font = mono_font

        fm = QFontMetrics(row_font)
        row_h = max(26, fm.height() + 10)
        v_header = self.verticalHeader()
        if v_header is not None:
            v_header.setDefaultSectionSize(row_h)

        self.setStyleSheet(
            f"""
            QTableWidget {{
                background-color: {palette['canvas']};
                color: {palette['meta']};
                border: none;
                outline: none;
                gridline-color: transparent;
            }}
            QTableWidget::item {{
                padding: 0px 6px;
                border: none;
            }}
            """
        )
        self._recompute_column_widths()

    def _recompute_column_widths(self) -> None:
        vp = self.viewport()
        avail_w = vp.width() if (vp is not None and vp.width() > 0) else self.width()
        if avail_w <= 0:
            avail_w = 640

        fm_mono = QFontMetrics(self._mono_font)
        col0_w = max(54, fm_mono.horizontalAdvance("▸ 999 ") + 16)
        col3_w = max(62, fm_mono.horizontalAdvance(" 999:59") + 16)
        rem = max(120, avail_w - col0_w - col3_w)
        col1_w = int(rem * 0.62)
        col2_w = max(60, rem - col1_w)

        self.setColumnWidth(0, col0_w)
        self.setColumnWidth(1, col1_w)
        self.setColumnWidth(2, col2_w)
        self.setColumnWidth(3, col3_w)

    def populate_rows(
        self,
        tracks: list[Track],
        indices: list[int],
        playing_index: int,
        selected_row: int,
        *,
        use_cursor_marker: bool = False,
    ) -> None:
        total_all = len(tracks)
        total_rows = len(indices)
        self.setRowCount(total_rows)

        if total_rows == 0:
            return

        bold_row_font = QFont(self._row_font)
        bold_row_font.setBold(True)
        bold_mono_font = QFont(self._mono_font)
        bold_mono_font.setBold(True)

        sel_bg = QColor(self._palette["sel_bg"])
        sel_fg = QColor(self._palette["sel_fg"])
        canvas_bg = QColor(self._palette["canvas"])
        fg_color = QColor(self._palette["fg"])
        meta_color = QColor(self._palette["meta"])

        idx_width = max(2, len(str(total_all)))

        for row, track_idx in enumerate(indices):
            track = tracks[track_idx]
            is_sel = row == selected_row
            is_playing = track_idx == playing_index

            if use_cursor_marker:
                marker = "›" if is_sel else ("▸" if is_playing else " ")
            else:
                marker = "▸" if is_playing else " "

            idx_text = f"{marker} {track_idx + 1:0{idx_width}d}"
            dur_text = format_mm_ss(track.duration_ms) if track.duration_ms > 0 else "--:--"

            bg = sel_bg if is_sel else canvas_bg
            fg = sel_fg if is_sel else (fg_color if is_playing else meta_color)
            f_text = bold_row_font if is_sel else self._row_font
            f_mono = bold_mono_font if is_sel else self._mono_font

            cells = (
                (idx_text, f_mono, Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter),
                (track.title, f_text, Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter),
                (track.artist, f_text, Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter),
                (dur_text, f_mono, Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter),
            )

            for col, (text, font, align) in enumerate(cells):
                item = self.item(row, col)
                if item is None:
                    item = QTableWidgetItem()
                    self.setItem(row, col, item)
                item.setText(text)
                item.setFont(font)
                item.setTextAlignment(align)
                item.setBackground(bg)
                item.setForeground(fg)
                if col == 0:
                    item.setData(Qt.ItemDataRole.UserRole, track_idx)

        self._recompute_column_widths()

        if 0 <= selected_row < total_rows:
            anchor_item = self.item(selected_row, 0)
            if anchor_item is not None:
                self.scrollToItem(
                    anchor_item, QAbstractItemView.ScrollHint.EnsureVisible
                )

    def resizeEvent(self, a0: Any) -> None:
        super().resizeEvent(a0)
        self._recompute_column_widths()

    def showEvent(self, a0: Any) -> None:
        super().showEvent(a0)
        self._recompute_column_widths()


class PlaybackCanvas(QWidget):
    """
    Main playback canvas displaying the Now Playing header (Track Name, Artist,
    razor-thin 2px flat progress bar, Elapsed & Duration MM:SS) at the top and
    the aligned 4-column Playlist Table directly below (hideable via 'H').
    Zero cover art space, zero buttons, zero shortcut hints.
    """

    trackActivated = pyqtSignal(int)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setMouseTracking(True)

        self._tracks: list[Track] = []
        self._playing_index: int = -1
        self._playlist_visible: bool = True
        self._palette: dict[str, str] = PALETTE_DARK

        self._outer_layout = QVBoxLayout(self)
        self._outer_layout.setContentsMargins(
            PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX
        )
        self._outer_layout.setSpacing(0)

        # Index 0: top stretch (active when playlist is hidden via 'H')
        self._outer_layout.addStretch(0)

        # Index 1: Now Playing header block
        self.center_block = QWidget(self)
        self.center_block.setMouseTracking(True)
        self.center_block.setSizePolicy(
            QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Minimum
        )

        block_layout = QVBoxLayout(self.center_block)
        block_layout.setContentsMargins(0, 0, 0, 0)
        block_layout.setSpacing(0)

        # 1. Track Name
        self.title_label = QLabel("No Track Loaded", self.center_block)
        self.title_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.title_label.setWordWrap(True)
        self.title_label.setMouseTracking(True)
        self.title_label.setTextFormat(Qt.TextFormat.PlainText)
        block_layout.addWidget(self.title_label)

        block_layout.addSpacing(4)

        # 2. Artist
        self.artist_label = QLabel("—", self.center_block)
        self.artist_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.artist_label.setWordWrap(True)
        self.artist_label.setMouseTracking(True)
        self.artist_label.setTextFormat(Qt.TextFormat.PlainText)
        block_layout.addWidget(self.artist_label)

        block_layout.addSpacing(16)

        # 3. Razor-thin 2px Flat Progress Bar (no thumb/knob)
        self.progress_bar = FlatProgressBar(self.center_block)
        block_layout.addWidget(self.progress_bar)

        block_layout.addSpacing(8)

        # 3b & 4. Elapsed / Duration MM:SS Readout
        time_row = QHBoxLayout()
        time_row.setContentsMargins(0, 0, 0, 0)
        time_row.setSpacing(0)

        self.elapsed_label = QLabel("00:00", self.center_block)
        self.elapsed_label.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.elapsed_label.setMouseTracking(True)
        self.elapsed_label.setTextFormat(Qt.TextFormat.PlainText)

        self.duration_label = QLabel("00:00", self.center_block)
        self.duration_label.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
        self.duration_label.setMouseTracking(True)
        self.duration_label.setTextFormat(Qt.TextFormat.PlainText)

        time_row.addWidget(self.elapsed_label)
        time_row.addStretch(1)
        time_row.addWidget(self.duration_label)
        block_layout.addLayout(time_row)

        self._outer_layout.addWidget(self.center_block, 0)

        # Index 2: gap between header and playlist table
        self.playlist_gap = QWidget(self)
        self.playlist_gap.setFixedHeight(14)
        self._outer_layout.addWidget(self.playlist_gap, 0)

        # Index 3: Live 4-Column Playlist Table directly on the main canvas
        self.playlist_table = ZeroChromePlaylistTable(self)
        self.playlist_table.trackActivated.connect(self.trackActivated.emit)
        self._outer_layout.addWidget(self.playlist_table, 1)

        # Index 4: bottom clearance / stretch (expands when playlist is hidden)
        self.bottom_clearance = QWidget(self)
        self.bottom_clearance.setFixedHeight(18)
        self._outer_layout.addWidget(self.bottom_clearance, 0)
        self._outer_layout.addStretch(0)

        # Index 6: Scratchpad-style zero-chrome inline 'save as' prompt (hidden unless Ctrl+S)
        self.save_bar = QWidget(self)
        self.save_bar.setVisible(False)
        self.save_bar.setMouseTracking(True)
        save_layout = QHBoxLayout(self.save_bar)
        save_layout.setContentsMargins(0, 4, 0, 0)
        save_layout.setSpacing(8)

        self.lbl_save_key = QLabel("save as", self.save_bar)
        self.lbl_save_key.setTextFormat(Qt.TextFormat.PlainText)
        save_layout.addWidget(self.lbl_save_key)

        self.save_name_input = QLineEdit(self.save_bar)
        self.save_name_input.setFrame(False)
        self.save_name_input.setPlaceholderText("playlist name…")
        save_layout.addWidget(self.save_name_input, 1)

        self._outer_layout.addWidget(self.save_bar, 0)

    def set_playlist_visible(self, visible: bool) -> None:
        self._playlist_visible = visible
        self.playlist_gap.setVisible(visible)
        self.playlist_table.setVisible(visible)
        self.bottom_clearance.setVisible(visible)
        self._outer_layout.setStretch(0, 0 if visible else 1)
        self._outer_layout.setStretch(3, 1 if visible else 0)
        self._outer_layout.setStretch(5, 0 if visible else 1)

    def is_playlist_visible(self) -> bool:
        return self._playlist_visible

    def set_track_info(self, title: str, artist: str, duration_ms: int) -> None:
        self.title_label.setText(title)
        self.artist_label.setText(artist)
        self.duration_label.setText(format_mm_ss(duration_ms))

    def set_time(self, position_ms: int, duration_ms: int) -> None:
        self.elapsed_label.setText(format_mm_ss(position_ms))
        self.duration_label.setText(format_mm_ss(duration_ms))
        self.progress_bar.set_progress(position_ms, duration_ms)

    def sync_playlist(self, tracks: list[Track], playing_index: int) -> None:
        """Render the current playlist queue in the aligned 4-column table."""
        self._tracks = tracks
        self._playing_index = playing_index
        indices = list(range(len(tracks)))
        self.playlist_table.populate_rows(
            tracks,
            indices,
            playing_index=playing_index,
            selected_row=playing_index,
            use_cursor_marker=False,
        )

    def apply_style(
        self,
        palette: dict[str, str],
        title_font: QFont,
        artist_font: QFont,
        timer_font: QFont,
        list_font: QFont,
        prompt_font: QFont,
    ) -> None:
        self._palette = palette

        self.setStyleSheet(f"background-color: {palette['canvas']}; border: none;")
        self.center_block.setStyleSheet("background: transparent; border: none;")
        self.playlist_gap.setStyleSheet("background: transparent; border: none;")
        self.bottom_clearance.setStyleSheet("background: transparent; border: none;")
        self.save_bar.setStyleSheet("background: transparent; border: none;")

        self.title_label.setFont(title_font)
        self.title_label.setStyleSheet(
            f"color: {palette['fg']}; background: transparent; border: none;"
        )

        self.artist_label.setFont(artist_font)
        self.artist_label.setStyleSheet(
            f"color: {palette['meta']}; background: transparent; border: none;"
        )

        self.elapsed_label.setFont(timer_font)
        self.elapsed_label.setStyleSheet(
            f"color: {palette['meta']}; background: transparent; border: none;"
        )

        self.duration_label.setFont(timer_font)
        self.duration_label.setStyleSheet(
            f"color: {palette['meta']}; background: transparent; border: none;"
        )

        self.lbl_save_key.setFont(prompt_font)
        self.lbl_save_key.setStyleSheet(
            f"color: {palette['meta']}; background: transparent; border: none;"
        )

        self.save_name_input.setFont(prompt_font)
        self.save_name_input.setStyleSheet(
            f"""
            QLineEdit {{
                background-color: {palette['canvas']};
                color: {palette['fg']};
                selection-background-color: {palette['sel_bg']};
                selection-color: {palette['sel_fg']};
                border: none;
            }}
            """
        )

        self.progress_bar.set_colors(palette["meta"], palette["active"])
        self.playlist_table.apply_style(palette, list_font, timer_font)
        self.sync_playlist(self._tracks, self._playing_index)


class TrackSwitcherOverlay(QWidget):
    """
    Scratchpad-style zero-chrome searchable track switcher overlay (L or /).
    Uses ZeroChromePlaylistTable so columns (#, Title, Artist, Duration) align
    pixel-perfectly across all tracks.
    """

    def __init__(
        self,
        on_activate_track,
        on_remove_track,
        on_reorder_track,
        on_undo_remove,
        on_close,
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self.setMouseTracking(True)
        self._on_activate_track = on_activate_track
        self._on_remove_track = on_remove_track
        self._on_reorder_track = on_reorder_track
        self._on_undo_remove = on_undo_remove
        self._on_close = on_close

        self._all_tracks: list[Track] = []
        self._filtered_indices: list[int] = []
        self._playing_index: int = -1
        self._selected_pos: int = 0
        self._palette: dict[str, str] = PALETTE_DARK
        self._row_font: QFont = QFont()
        self._meta_font: QFont = QFont()

        layout = QVBoxLayout(self)
        layout.setContentsMargins(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        layout.setSpacing(6)

        search_row = QHBoxLayout()
        search_row.setContentsMargins(0, 0, 0, 6)
        search_row.setSpacing(0)

        self.lbl_search_key = QLabel(f"{'search':<15}", self)
        self.lbl_search_key.setTextFormat(Qt.TextFormat.PlainText)
        self.lbl_search_key.setMouseTracking(True)
        search_row.addWidget(self.lbl_search_key)

        self.search_input = QLineEdit(self)
        self.search_input.setFrame(False)
        self.search_input.setPlaceholderText("filter tracks…")
        self.search_input.textChanged.connect(self._on_search_changed)
        self.search_input.installEventFilter(self)
        search_row.addWidget(self.search_input, 1)
        layout.addLayout(search_row)

        self.table = ZeroChromePlaylistTable(self)
        self.table.trackActivated.connect(self._on_activate_track)
        layout.addWidget(self.table, 1)

        self._empty_lbl = QLabel("no tracks", self)
        self._empty_lbl.setTextFormat(Qt.TextFormat.PlainText)
        self._empty_lbl.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignTop)
        self._empty_lbl.hide()
        layout.addWidget(self._empty_lbl, 1)

        self.count_label = QLabel("", self)
        self.count_label.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
        self.count_label.setTextFormat(Qt.TextFormat.PlainText)
        self.count_label.setMouseTracking(True)
        layout.addWidget(self.count_label)

    def open_view(self, tracks: list[Track], playing_index: int) -> None:
        self._all_tracks = tracks
        self._playing_index = playing_index
        self.search_input.blockSignals(True)
        self.search_input.clear()
        self.search_input.blockSignals(False)
        self._selected_pos = playing_index if 0 <= playing_index < len(tracks) else 0
        self.refresh_list()
        self.search_input.setFocus()

    def sync_tracks(
        self,
        tracks: list[Track],
        playing_index: int,
        keep_selected_pos: int | None = None,
    ) -> None:
        self._all_tracks = tracks
        self._playing_index = playing_index
        if keep_selected_pos is not None:
            self._selected_pos = keep_selected_pos
        self.refresh_list()

    def _on_search_changed(self) -> None:
        self._selected_pos = 0
        self.refresh_list()

    def _compute_filtered_indices(self) -> list[int]:
        q = self.search_input.text().strip().lower()
        if not q:
            return list(range(len(self._all_tracks)))
        return [
            idx
            for idx, tr in enumerate(self._all_tracks)
            if q in tr.title.lower() or q in tr.artist.lower()
        ]

    def selected_track_index(self) -> int | None:
        if 0 <= self._selected_pos < len(self._filtered_indices):
            return self._filtered_indices[self._selected_pos]
        return None

    def refresh_list(self) -> None:
        self._filtered_indices = self._compute_filtered_indices()
        total_all = len(self._all_tracks)
        total_shown = len(self._filtered_indices)

        if total_all == 0:
            self.count_label.setText("0 tracks")
        elif total_shown != total_all:
            self.count_label.setText(f"{total_shown}/{total_all} tracks")
        else:
            self.count_label.setText("1 track" if total_all == 1 else f"{total_all} tracks")

        if total_shown == 0:
            self._selected_pos = 0
            self.table.setRowCount(0)
            self.table.hide()
            self._empty_lbl.setFont(self._row_font)
            self._empty_lbl.setStyleSheet(
                f"color: {self._palette['meta']}; background: transparent; padding: 3px 6px;"
            )
            self._empty_lbl.show()
            return

        self._empty_lbl.hide()
        self.table.show()
        self._selected_pos = max(0, min(self._selected_pos, total_shown - 1))
        self.table.populate_rows(
            self._all_tracks,
            self._filtered_indices,
            playing_index=self._playing_index,
            selected_row=self._selected_pos,
            use_cursor_marker=True,
        )

    def move_selection(self, delta: int) -> None:
        if not self._filtered_indices:
            return
        self._selected_pos = (self._selected_pos + delta) % len(self._filtered_indices)
        self.refresh_list()

    def jump_edge(self, to_end: bool) -> None:
        if not self._filtered_indices:
            return
        self._selected_pos = len(self._filtered_indices) - 1 if to_end else 0
        self.refresh_list()

    def apply_style(
        self,
        palette: dict[str, str],
        row_font: QFont,
        meta_font: QFont,
    ) -> None:
        self._palette = palette
        self._row_font = row_font
        self._meta_font = meta_font

        self.lbl_search_key.setFont(row_font)
        self.search_input.setFont(row_font)
        self.count_label.setFont(meta_font)
        self.table.apply_style(palette, row_font, meta_font)
        self.setStyleSheet(
            f"""
            QWidget {{
                background-color: {palette['canvas']};
                color: {palette['fg']};
                border: none;
            }}
            QLabel {{
                color: {palette['meta']};
                background: transparent;
            }}
            QLineEdit {{
                background-color: {palette['canvas']};
                color: {palette['fg']};
                selection-background-color: {palette['sel_bg']};
                selection-color: {palette['sel_fg']};
                border: none;
            }}
            """
        )
        self.refresh_list()

    def eventFilter(self, a0: Any, a1: Any) -> bool:
        watched, event = a0, a1
        if watched is self.search_input and event is not None and event.type() == QEvent.Type.KeyPress:
            key = event.key()
            mods = event.modifiers()
            ctrl = bool(mods & Qt.KeyboardModifier.ControlModifier)

            if key == Qt.Key.Key_Down:
                if ctrl and not self.search_input.text().strip():
                    idx = self.selected_track_index()
                    if idx is not None:
                        self._on_reorder_track(idx, 1)
                else:
                    self.move_selection(1)
                return True
            if key == Qt.Key.Key_Up:
                if ctrl and not self.search_input.text().strip():
                    idx = self.selected_track_index()
                    if idx is not None:
                        self._on_reorder_track(idx, -1)
                else:
                    self.move_selection(-1)
                return True
            if key == Qt.Key.Key_PageDown:
                self.move_selection(10)
                return True
            if key == Qt.Key.Key_PageUp:
                self.move_selection(-10)
                return True
            if key == Qt.Key.Key_Home and ctrl:
                self.jump_edge(to_end=False)
                return True
            if key == Qt.Key.Key_End and ctrl:
                self.jump_edge(to_end=True)
                return True
            if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                idx = self.selected_track_index()
                if idx is not None:
                    self._on_activate_track(idx)
                return True
            if key == Qt.Key.Key_Escape:
                self._on_close()
                return True
            if key == Qt.Key.Key_Delete or (ctrl and key == Qt.Key.Key_D):
                idx = self.selected_track_index()
                if idx is not None:
                    self._on_remove_track(idx, self._selected_pos)
                return True
            if ctrl and key == Qt.Key.Key_Z:
                if self._on_undo_remove():
                    return True
        return super().eventFilter(watched, event)


class SavedPlaylistsView(QWidget):
    """
    Scratchpad-style zero-chrome Saved Playlists Library (Ctrl+L):
    - 100% flat canvas, 24px margins, zero borders/buttons/scrollbars
    - Live filter search bar at top ('search          <flat entry>')
    - Keyboard-driven: Up/Down navigate, Enter load & play, Ctrl+P pin/unpin,
      Ctrl+E export to .m3u/.m3u8, Del/Ctrl+D delete, Ctrl+Z undo delete, Esc close.
    """

    def __init__(
        self,
        repo: PlaylistRepository,
        on_select_playlist,
        on_export_playlist,
        on_delete_playlist,
        on_undo_delete,
        on_close,
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self.setMouseTracking(True)
        self.repo = repo
        self._on_select_playlist = on_select_playlist
        self._on_export_playlist = on_export_playlist
        self._on_delete_playlist = on_delete_playlist
        self._on_undo_delete = on_undo_delete
        self._on_close = on_close

        self._playlists: list[SavedPlaylist] = []
        self._selected_idx: int = 0
        self._active_playlist_id: int | None = None
        self._row_labels: list[QLabel] = []
        self._palette: dict[str, str] = PALETTE_DARK
        self._row_font: QFont = QFont()
        self._meta_font: QFont = QFont()

        layout = QVBoxLayout(self)
        layout.setContentsMargins(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        layout.setSpacing(6)

        search_row = QHBoxLayout()
        search_row.setContentsMargins(0, 0, 0, 6)
        search_row.setSpacing(0)

        self.lbl_search_key = QLabel(f"{'playlists':<15}", self)
        self.lbl_search_key.setTextFormat(Qt.TextFormat.PlainText)
        self.lbl_search_key.setMouseTracking(True)
        search_row.addWidget(self.lbl_search_key)

        self.search_input = QLineEdit(self)
        self.search_input.setFrame(False)
        self.search_input.setPlaceholderText("filter playlists…")
        self.search_input.textChanged.connect(self._on_search_changed)
        self.search_input.installEventFilter(self)
        search_row.addWidget(self.search_input, 1)
        layout.addLayout(search_row)

        self.scroll_area = QScrollArea(self)
        self.scroll_area.setWidgetResizable(True)
        self.scroll_area.setFrameShape(QFrame.Shape.NoFrame)
        self.scroll_area.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.scroll_area.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.scroll_area.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.scroll_area.setMouseTracking(True)

        self.rows_widget = QWidget()
        self.rows_widget.setMouseTracking(True)
        self.rows_layout = QVBoxLayout(self.rows_widget)
        self.rows_layout.setContentsMargins(0, 0, 0, 0)
        self.rows_layout.setSpacing(3)
        self.scroll_area.setWidget(self.rows_widget)
        layout.addWidget(self.scroll_area, 1)

        self.count_label = QLabel("", self)
        self.count_label.setAlignment(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter)
        self.count_label.setTextFormat(Qt.TextFormat.PlainText)
        self.count_label.setMouseTracking(True)
        layout.addWidget(self.count_label)

        self._empty_lbl = QLabel("no saved playlists", self.rows_widget)
        self._empty_lbl.setTextFormat(Qt.TextFormat.PlainText)
        self._empty_lbl.hide()
        self.rows_layout.addWidget(self._empty_lbl)
        self.rows_layout.addStretch(1)

    def open_view(self, active_playlist_id: int | None) -> None:
        self._active_playlist_id = active_playlist_id
        self.search_input.blockSignals(True)
        self.search_input.clear()
        self.search_input.blockSignals(False)
        self._selected_idx = 0
        self.refresh_list()
        if active_playlist_id is not None:
            for idx, pl in enumerate(self._playlists):
                if pl.id == active_playlist_id:
                    self._selected_idx = idx
                    self.refresh_list()
                    break
        self.search_input.setFocus()

    def _on_search_changed(self) -> None:
        self._selected_idx = 0
        self.refresh_list()

    def refresh_list(self) -> None:
        self._playlists = self.repo.list_playlists(self.search_input.text())
        total = len(self._playlists)
        self.count_label.setText("1 playlist" if total == 1 else f"{total} playlists")

        if not self._playlists:
            self._selected_idx = 0
            for lbl in self._row_labels:
                lbl.hide()
            self._empty_lbl.setFont(self._row_font)
            self._empty_lbl.setStyleSheet(
                f"color: {self._palette['meta']}; background: transparent; padding: 3px 6px;"
            )
            self._empty_lbl.show()
            return

        self._empty_lbl.hide()
        self._selected_idx = max(0, min(self._selected_idx, total - 1))

        bold_font = QFont(self._row_font)
        bold_font.setBold(True)

        while len(self._row_labels) < total:
            lbl = QLabel(self.rows_widget)
            lbl.setTextFormat(Qt.TextFormat.PlainText)
            lbl.setMouseTracking(True)
            insert_at = max(0, self.rows_layout.count() - 1)
            self.rows_layout.insertWidget(insert_at, lbl)
            self._row_labels.append(lbl)

        for idx, pl in enumerate(self._playlists):
            is_sel = idx == self._selected_idx
            is_active = self._active_playlist_id is not None and pl.id == self._active_playlist_id
            lbl = self._row_labels[idx]
            lbl.setFont(bold_font if is_sel else self._row_font)
            if is_sel:
                lbl.setStyleSheet(
                    f"background-color: {self._palette['sel_bg']}; "
                    f"color: {self._palette['sel_fg']}; padding: 3px 6px; border: none;"
                )
            elif is_active:
                lbl.setStyleSheet(
                    f"background: transparent; color: {self._palette['fg']}; "
                    f"padding: 3px 6px; border: none;"
                )
            else:
                lbl.setStyleSheet(
                    f"background: transparent; color: {self._palette['meta']}; "
                    f"padding: 3px 6px; border: none;"
                )

            marker = "›" if is_sel else ("▸" if is_active else " ")
            pin_tag = "* " if pl.is_pinned else ""
            name_str = f"{pin_tag}{pl.name}"
            if len(name_str) > 30:
                name_str = name_str[:29] + "…"
            tr_word = "track" if pl.track_count == 1 else "tracks"
            meta_str = f"{pl.track_count} {tr_word} · {format_timestamp(pl.updated_at)}"
            lbl.setText(f"{marker} {name_str:<32}  {meta_str}")
            lbl.show()

        for idx in range(total, len(self._row_labels)):
            self._row_labels[idx].hide()

        active_lbl = self._row_labels[self._selected_idx]
        self.scroll_area.ensureWidgetVisible(active_lbl, 0, 24)

    def apply_style(
        self,
        palette: dict[str, str],
        row_font: QFont,
        meta_font: QFont,
    ) -> None:
        self._palette = palette
        self._row_font = row_font
        self._meta_font = meta_font

        self.lbl_search_key.setFont(row_font)
        self.search_input.setFont(row_font)
        self.count_label.setFont(meta_font)
        self.setStyleSheet(
            f"""
            QWidget {{
                background-color: {palette['canvas']};
                color: {palette['fg']};
                border: none;
            }}
            QLabel {{
                color: {palette['meta']};
                background: transparent;
            }}
            QLineEdit {{
                background-color: {palette['canvas']};
                color: {palette['fg']};
                selection-background-color: {palette['sel_bg']};
                selection-color: {palette['sel_fg']};
                border: none;
            }}
            """
        )
        self.refresh_list()

    def eventFilter(self, a0: Any, a1: Any) -> bool:
        watched, event = a0, a1
        if watched is self.search_input and event is not None and event.type() == QEvent.Type.KeyPress:
            key = event.key()
            mods = event.modifiers()
            ctrl = bool(mods & Qt.KeyboardModifier.ControlModifier)

            if key == Qt.Key.Key_Down:
                if self._playlists:
                    self._selected_idx = (self._selected_idx + 1) % len(self._playlists)
                    self.refresh_list()
                return True
            if key == Qt.Key.Key_Up:
                if self._playlists:
                    self._selected_idx = (self._selected_idx - 1) % len(self._playlists)
                    self.refresh_list()
                return True
            if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                if self._playlists:
                    self._on_select_playlist(self._playlists[self._selected_idx])
                return True
            if key == Qt.Key.Key_Escape:
                self._on_close()
                return True
            if ctrl and key == Qt.Key.Key_P:
                if self._playlists:
                    self.repo.toggle_pin(self._playlists[self._selected_idx])
                    self.refresh_list()
                return True
            if ctrl and key == Qt.Key.Key_E:
                if self._playlists:
                    self._on_export_playlist(self._playlists[self._selected_idx])
                return True
            if key == Qt.Key.Key_Delete or (ctrl and key == Qt.Key.Key_D):
                if self._playlists:
                    self._on_delete_playlist(self._playlists[self._selected_idx])
                    self.refresh_list()
                return True
            if ctrl and key == Qt.Key.Key_Z:
                if self._on_undo_delete():
                    self.refresh_list()
                    return True
        return super().eventFilter(watched, event)


class ShortcutsHelpOverlay(QWidget):
    """
    Centered Scratchpad-style two-column monospace keyboard shortcuts overlay.
    Toggled exclusively via F1 (dismissed via F1 or Escape).
    """

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setMouseTracking(True)

        outer = QVBoxLayout(self)
        outer.setContentsMargins(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        outer.setSpacing(0)
        outer.addStretch(1)

        row_wrap = QHBoxLayout()
        row_wrap.setContentsMargins(0, 0, 0, 0)
        row_wrap.addStretch(1)

        self.table_container = QWidget(self)
        self.table_container.setMouseTracking(True)
        grid = QGridLayout(self.table_container)
        grid.setContentsMargins(0, 0, 0, 0)
        grid.setHorizontalSpacing(24)
        grid.setVerticalSpacing(3)

        self.header_label = QLabel("KEYBOARD SHORTCUTS", self.table_container)
        self.header_label.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.header_label.setMouseTracking(True)
        self.header_label.setTextFormat(Qt.TextFormat.PlainText)
        grid.addWidget(self.header_label, 0, 0, 1, 2)

        self.action_labels: list[QLabel] = []
        self.key_labels: list[QLabel] = []

        for row_idx, (action_str, shortcut_str) in enumerate(SHORTCUT_TABLE, start=1):
            a_lbl = QLabel(action_str, self.table_container)
            a_lbl.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
            a_lbl.setMouseTracking(True)
            a_lbl.setTextFormat(Qt.TextFormat.PlainText)

            k_lbl = QLabel(shortcut_str, self.table_container)
            k_lbl.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
            k_lbl.setMouseTracking(True)
            k_lbl.setTextFormat(Qt.TextFormat.PlainText)

            grid.addWidget(a_lbl, row_idx, 0)
            grid.addWidget(k_lbl, row_idx, 1)
            self.action_labels.append(a_lbl)
            self.key_labels.append(k_lbl)

        row_wrap.addWidget(self.table_container)
        row_wrap.addStretch(1)
        outer.addLayout(row_wrap)
        outer.addStretch(1)

    def apply_style(
        self,
        palette: dict[str, str],
        mono_font: QFont,
        header_font: QFont,
    ) -> None:
        self.setStyleSheet(f"background-color: {palette['canvas']}; border: none;")
        self.table_container.setStyleSheet("background: transparent; border: none;")

        self.header_label.setFont(header_font)
        self.header_label.setStyleSheet(
            f"background-color: {palette['sel_bg']}; color: {palette['sel_fg']}; "
            f"padding: 3px 8px; border: none;"
        )

        for a_lbl in self.action_labels:
            a_lbl.setFont(mono_font)
            a_lbl.setStyleSheet(
                f"color: {palette['meta']}; background: transparent; border: none;"
            )
        for k_lbl in self.key_labels:
            k_lbl.setFont(mono_font)
            k_lbl.setStyleSheet(
                f"color: {palette['fg']}; background: transparent; border: none;"
            )


# ---------------------------------------------------------------------------
# Main Application Window
# ---------------------------------------------------------------------------
class MinimalMusicPlayerWindow(QMainWindow):
    """
    Zero-chrome, keyboard-driven offline music player with Scratchpad-style
    playlist library, inline prompts, transient status whispers, and persistence.
    """

    VIEW_PLAYER = 0
    VIEW_SWITCHER = 1
    VIEW_PLAYLISTS = 2
    VIEW_HELP = 3

    def __init__(
        self,
        initial_targets: list[Path] | None = None,
        start_frameless: bool = False,
        db_path: Path | None = None,
    ) -> None:
        super().__init__()

        self.repo = PlaylistRepository(db_path=db_path)

        # Typography stacks probed from installed system fonts
        self._mono_family = probe_font_family(PREFERRED_MONO_FONTS, "monospace")
        self._sans_family = probe_font_family(PREFERRED_SANS_FONTS, "sans-serif")

        # Load persisted typography, palette, and audio preferences (Scratchpad style)
        try:
            saved_size = int(self.repo.get_pref("font_size", str(DEFAULT_FONT_SIZE)))
            self._font_size = max(MIN_FONT_SIZE, min(MAX_FONT_SIZE, saved_size))
        except ValueError:
            self._font_size = DEFAULT_FONT_SIZE

        self._use_monospace_primary = self.repo.get_pref("is_monospace", "0") == "1"
        self._is_dark = self.repo.get_pref("is_dark", "1") != "0"

        try:
            saved_vol = int(self.repo.get_pref("volume_pct", str(DEFAULT_VOLUME_PCT)))
            self._volume_pct = max(0, min(100, saved_vol))
        except ValueError:
            self._volume_pct = DEFAULT_VOLUME_PCT

        self._muted: bool = self.repo.get_pref("muted", "0") == "1"
        self._shuffle: bool = self.repo.get_pref("shuffle", "0") == "1"
        try:
            saved_rep = int(self.repo.get_pref("repeat_mode_idx", "0"))
            self._repeat_mode_idx = saved_rep % len(REPEAT_MODES)
        except ValueError:
            self._repeat_mode_idx = 0

        self._last_folder: str = self.repo.get_pref("last_folder", str(Path.home()))
        self._show_playlist: bool = self.repo.get_pref("show_playlist", "1") != "0"
        self._telemetry_mode: int = 0

        # Window chrome / drag-resize state
        self._frameless = start_frameless
        self._was_frameless_before_fs = start_frameless
        self._manual_drag_offset: QPoint | None = None
        self._manual_resize_edges: Qt.Edge = Qt.Edge(0)
        self._manual_resize_origin_pos: QPoint | None = None
        self._manual_resize_origin_geom: QRect | None = None
        self._has_closed = False

        # Library, playlist & playback state
        self._tracks: list[Track] = []
        self._current_index: int = -1
        self._mpris_track_gen: int = 0
        self._shuffle_order: list[int] = []
        self._shuffle_pos: int = -1
        self._error_flag: bool = False
        self._last_position_ms: int = 0
        self._metadata_worker: MetadataWorker | None = None

        # Active playlist metadata & undo state (mirroring Scratchpad)
        self._active_playlist_id: int | None = None
        self._active_playlist_name: str = ""
        self._active_m3u_path: Path | None = None
        self._playlist_modified: bool = False
        self._show_save_prompt: bool = False
        self._last_deleted_playlist: SavedPlaylist | None = None
        self._last_removed_track: tuple[int, Track] | None = None
        self._last_cleared_queue: tuple[list[Track], int, int | None, str, Path | None] | None = None
        self._transient_status: str = ""

        # Configure window flags & geometry
        self.setWindowTitle(APP_TITLE)
        if self._frameless:
            self.setWindowFlags(
                Qt.WindowType.Window | Qt.WindowType.FramelessWindowHint
            )
        self.setMinimumSize(380, 260)
        self.resize(760, 500)
        self.setAcceptDrops(True)
        self.setMouseTracking(True)
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)

        # Stacked zero-chrome views
        self.stack = QStackedWidget(self)
        self.stack.setMouseTracking(True)
        self.setCentralWidget(self.stack)

        self.player_canvas = PlaybackCanvas(self.stack)
        self.player_canvas.set_playlist_visible(self._show_playlist)
        self.player_canvas.save_name_input.installEventFilter(self)
        self.player_canvas.trackActivated.connect(
            lambda idx: self._load_track_at(idx, auto_play=True)
        )

        self.switcher_overlay = TrackSwitcherOverlay(
            on_activate_track=self._on_switcher_track_activated,
            on_remove_track=self._remove_track_at,
            on_reorder_track=self._reorder_track,
            on_undo_remove=self.undo_remove_track,
            on_close=self._return_to_player,
            parent=self.stack,
        )
        self.playlists_view = SavedPlaylistsView(
            repo=self.repo,
            on_select_playlist=self._on_playlist_selected_from_library,
            on_export_playlist=self.export_saved_playlist_to_m3u,
            on_delete_playlist=self.delete_saved_playlist,
            on_undo_delete=self.undo_delete_playlist,
            on_close=self._return_to_player,
            parent=self.stack,
        )
        self.help_overlay = ShortcutsHelpOverlay(self.stack)

        self.stack.addWidget(self.player_canvas)     # 0
        self.stack.addWidget(self.switcher_overlay)  # 1
        self.stack.addWidget(self.playlists_view)    # 2
        self.stack.addWidget(self.help_overlay)      # 3

        # Whisper-quiet bottom-right telemetry (never shows keybinding hints)
        self.telemetry_label = QLabel("", self)
        self.telemetry_label.setAlignment(
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        )
        self.telemetry_label.setTextFormat(Qt.TextFormat.PlainText)
        self.telemetry_label.setAttribute(
            Qt.WidgetAttribute.WA_TransparentForMouseEvents, True
        )
        self.telemetry_label.raise_()

        # Transient status timer (Scratchpad 2.2s whisper fade)
        self._status_timer = QTimer(self)
        self._status_timer.setSingleShot(True)
        self._status_timer.timeout.connect(self._clear_transient_status)

        # Audio engine (QMediaPlayer + QAudioOutput)
        self.audio_output = QAudioOutput(self)
        self.media_player = QMediaPlayer(self)
        self.media_player.setAudioOutput(self.audio_output)
        self._apply_audio_volume()

        self.media_player.positionChanged.connect(self._on_position_changed)
        self.media_player.durationChanged.connect(self._on_duration_changed)
        self.media_player.playbackStateChanged.connect(self._on_playback_state_changed)
        self.media_player.mediaStatusChanged.connect(self._on_media_status_changed)
        self.media_player.errorOccurred.connect(self._on_media_error)

        # Global unfocused media key listeners (Linux MPRIS2 D-Bus + kernel /dev/input + X11)
        self._last_media_action_ts: dict[str, float] = {}
        self.mpris_service = MprisService(self)
        self.global_media_keys = GlobalMediaKeyListener(self)
        self.global_media_keys.actionTriggered.connect(self.dispatch_media_action)

        # Application-level event filter for frameless drag/resize, wheel zoom & global Ctrl keys
        app = QApplication.instance()
        if app is not None:
            app.installEventFilter(self)

        self._apply_theme_and_typography(persist=False)

        # Load CLI targets if supplied, else restore last session queue (paused)
        if initial_targets:
            self.load_targets(initial_targets, auto_play=True)
        else:
            self._restore_last_session()

        self._update_telemetry()

    # -----------------------------------------------------------------------
    # Palette & Typography Application
    # -----------------------------------------------------------------------
    @property
    def palette_colors(self) -> dict[str, str]:
        return PALETTE_DARK if self._is_dark else PALETTE_LIGHT

    def _apply_theme_and_typography(self, *, persist: bool = True) -> None:
        pal = self.palette_colors
        base = self._font_size
        primary_family = self._mono_family if self._use_monospace_primary else self._sans_family

        title_pt = max(base + 4, int(round(base * 1.45)))
        artist_pt = base
        timer_pt = max(MIN_FONT_SIZE, base - 2)
        telemetry_pt = max(MIN_FONT_SIZE, base - 3)
        help_pt = max(MIN_FONT_SIZE, base - 2)
        list_pt = max(MIN_FONT_SIZE, base - 1)

        title_font = make_font(
            primary_family, title_pt, bold=True, monospace=self._use_monospace_primary
        )
        artist_font = make_font(
            primary_family, artist_pt, bold=False, monospace=self._use_monospace_primary
        )
        timer_font = make_font(self._mono_family, timer_pt, monospace=True)
        telemetry_font = make_font(self._mono_family, telemetry_pt, monospace=True)
        help_font = make_font(self._mono_family, help_pt, monospace=True)
        help_header_font = make_font(self._mono_family, help_pt, bold=True, monospace=True)
        list_font = make_font(
            primary_family, list_pt, bold=False, monospace=self._use_monospace_primary
        )
        prompt_font = make_font(self._mono_family, timer_pt, monospace=True)

        self.setStyleSheet(
            f"QMainWindow {{ background-color: {pal['canvas']}; border: none; }}"
        )
        self.stack.setStyleSheet(
            f"QStackedWidget {{ background-color: {pal['canvas']}; border: none; }}"
        )

        self.player_canvas.apply_style(
            pal, title_font, artist_font, timer_font, list_font, prompt_font
        )
        self.switcher_overlay.apply_style(pal, list_font, timer_font)
        self.playlists_view.apply_style(pal, list_font, timer_font)
        self.help_overlay.apply_style(pal, help_font, help_header_font)

        self.telemetry_label.setFont(telemetry_font)
        self.telemetry_label.setStyleSheet(
            f"color: {pal['meta']}; background: transparent; border: none;"
        )

        if persist:
            self.repo.set_prefs(
                {
                    "font_size": str(self._font_size),
                    "is_monospace": "1" if self._use_monospace_primary else "0",
                    "is_dark": "1" if self._is_dark else "0",
                }
            )

        self._position_telemetry()

    def adjust_font_size(self, delta: int) -> None:
        new_size = max(MIN_FONT_SIZE, min(MAX_FONT_SIZE, self._font_size + delta))
        if new_size != self._font_size:
            self._font_size = new_size
            self._apply_theme_and_typography()

    def reset_font_size(self) -> None:
        self._font_size = DEFAULT_FONT_SIZE
        self._apply_theme_and_typography()
        self.show_status(f"reset {DEFAULT_FONT_SIZE}pt")

    def toggle_font_family(self) -> None:
        self._use_monospace_primary = not self._use_monospace_primary
        self._apply_theme_and_typography()
        mode_name = "monospace" if self._use_monospace_primary else "sans-serif"
        self.show_status(f"font: {mode_name}")

    def toggle_theme(self) -> None:
        self._is_dark = not self._is_dark
        self._apply_theme_and_typography()
        self.show_status("matte dark" if self._is_dark else "soft light")

    def toggle_playlist_visibility(self) -> None:
        self._show_playlist = not self._show_playlist
        self.player_canvas.set_playlist_visible(self._show_playlist)
        self.repo.set_pref("show_playlist", "1" if self._show_playlist else "0")

    def cycle_telemetry_mode(self) -> None:
        self._telemetry_mode = (self._telemetry_mode + 1) % 3
        self._update_telemetry()

    # -----------------------------------------------------------------------
    # Whisper-Quiet Bottom-Right Telemetry (Scratchpad Pattern, Zero Key Hints)
    # -----------------------------------------------------------------------
    def show_status(self, msg: str) -> None:
        self._transient_status = msg
        self._update_telemetry()
        self._status_timer.start(2200)

    def _clear_transient_status(self) -> None:
        self._transient_status = ""
        self._update_telemetry()

    def _update_telemetry(self) -> None:
        total = len(self._tracks)
        if total == 0:
            state_str = "idle"
            index_str = "00/00"
        else:
            pb_state = self.media_player.playbackState()
            if self._error_flag:
                state_str = "error"
            elif pb_state == QMediaPlayer.PlaybackState.PlayingState:
                state_str = "playing"
            elif pb_state == QMediaPlayer.PlaybackState.PausedState:
                state_str = "paused"
            else:
                state_str = "stopped"
            width = max(2, len(str(total)))
            cur_num = (self._current_index + 1) if self._current_index >= 0 else 0
            index_str = f"{cur_num:0{width}d}/{total:0{width}d}"

        vol_str = "muted" if self._muted else f"{self._volume_pct}%"

        parts: list[str] = []
        if self._transient_status:
            parts.append(self._transient_status)
        elif self._active_playlist_name and self._telemetry_mode >= 1:
            mod = "*" if self._playlist_modified else ""
            parts.append(f"{self._active_playlist_name.lower()}{mod}")

        parts.extend([state_str, index_str, vol_str])

        if self._shuffle:
            parts.append("shuffle")
        repeat_mode = REPEAT_MODES[self._repeat_mode_idx]
        if repeat_mode != "Off":
            parts.append(f"repeat {repeat_mode.lower()}")
        if self._telemetry_mode == 2:
            parts.append(f"{self._font_size}pt")

        self.telemetry_label.setText(" · ".join(parts))

        if self._active_playlist_name:
            mod = "*" if self._playlist_modified else ""
            self.setWindowTitle(f"{self._active_playlist_name}{mod} — {APP_TITLE}")
        else:
            self.setWindowTitle(APP_TITLE)

        self._position_telemetry()

    def _position_telemetry(self) -> None:
        visible = (
            self.stack.currentIndex() == self.VIEW_PLAYER
            and not self._show_save_prompt
        )
        self.telemetry_label.setVisible(visible)
        if not visible:
            return
        self.telemetry_label.adjustSize()
        x = max(PADDING_PX, self.width() - self.telemetry_label.width() - PADDING_PX)
        y = max(PADDING_PX, self.height() - self.telemetry_label.height() - 12)
        self.telemetry_label.move(x, y)
        self.telemetry_label.raise_()

    # -----------------------------------------------------------------------
    # Session Persistence (Scratchpad Draft-Style Auto-Restore)
    # -----------------------------------------------------------------------
    def _save_session_state(self) -> None:
        self.repo.set_prefs(
            {
                "volume_pct": str(self._volume_pct),
                "muted": "1" if self._muted else "0",
                "shuffle": "1" if self._shuffle else "0",
                "repeat_mode_idx": str(self._repeat_mode_idx),
                "last_folder": self._last_folder,
                "session_tracks": json.dumps([str(t.path) for t in self._tracks], ensure_ascii=False),
                "session_index": str(self._current_index),
                "session_playlist_id": (
                    str(self._active_playlist_id) if self._active_playlist_id is not None else ""
                ),
                "session_playlist_name": self._active_playlist_name,
                "session_m3u_path": str(self._active_m3u_path) if self._active_m3u_path else "",
                "session_modified": "1" if self._playlist_modified else "0",
            }
        )

    def _restore_last_session(self) -> None:
        raw_tracks = self.repo.get_pref("session_tracks", "")
        if not raw_tracks:
            return
        try:
            paths_list = json.loads(raw_tracks)
            if not isinstance(paths_list, list):
                return
        except Exception:
            return

        existing = [
            Path(p)
            for p in paths_list
            if Path(p).is_file() and Path(p).suffix.lower() in SUPPORTED_AUDIO_EXTENSIONS
        ]
        if not existing:
            return

        saved_id = self.repo.get_pref("session_playlist_id", "")
        saved_name = self.repo.get_pref("session_playlist_name", "")
        saved_m3u = self.repo.get_pref("session_m3u_path", "")
        saved_mod = self.repo.get_pref("session_modified", "0") == "1"
        try:
            saved_idx = int(self.repo.get_pref("session_index", "0"))
        except ValueError:
            saved_idx = 0

        self._active_playlist_id = int(saved_id) if saved_id.isdigit() else None
        self._active_playlist_name = saved_name
        self._active_m3u_path = Path(saved_m3u) if saved_m3u and Path(saved_m3u).is_file() else None
        self._playlist_modified = saved_mod

        start_idx = max(0, min(saved_idx, len(existing) - 1))
        self._set_queue_paths(existing, start_index=start_idx, auto_play=False)

    # -----------------------------------------------------------------------
    # Library, Folder & Playlist Loading / Editing
    # -----------------------------------------------------------------------
    def _stop_metadata_worker(self) -> None:
        if self._metadata_worker is not None:
            if self._metadata_worker.isRunning():
                self._metadata_worker.requestInterruption()
                self._metadata_worker.quit()
                self._metadata_worker.wait(1000)
            self._metadata_worker.deleteLater()
            self._metadata_worker = None

    def open_folder_dialog(self) -> None:
        folder = QFileDialog.getExistingDirectory(
            self,
            "Open Music Folder",
            self._last_folder,
            QFileDialog.Option.ShowDirsOnly,
        )
        if folder:
            self._last_folder = folder
            self.load_targets([Path(folder)], auto_play=True)
        self.activateWindow()
        self.setFocus()

    def open_playlist_file_dialog(self) -> None:
        start_dir = (
            str(self._active_m3u_path.parent)
            if self._active_m3u_path is not None
            else self._last_folder
        )
        file_path, _ = QFileDialog.getOpenFileName(
            self,
            "Open Playlist (.m3u / .m3u8)",
            start_dir,
            "Playlists (*.m3u *.m3u8);;All Files (*)",
        )
        if file_path:
            self.load_m3u_file(Path(file_path), auto_play=True)
        self.activateWindow()
        self.setFocus()

    def append_files_dialog(self) -> None:
        files, _ = QFileDialog.getOpenFileNames(
            self,
            "Append Audio Files to Playlist",
            self._last_folder,
            "Audio Files (*.mp3 *.flac *.ogg *.opus *.m4a *.wav);;Playlists (*.m3u *.m3u8);;All Files (*)",
        )
        if files:
            self.append_targets([Path(f) for f in files])
        self.activateWindow()
        self.setFocus()

    def load_m3u_file(self, m3u_path: Path, auto_play: bool = True) -> None:
        resolved = m3u_path.expanduser().resolve()
        paths = parse_m3u_playlist(resolved)
        if not paths:
            self.show_status(f"empty playlist {resolved.name}")
            return
        self._last_folder = str(resolved.parent)
        self._active_m3u_path = resolved
        self._active_playlist_id = None
        self._active_playlist_name = resolved.stem or resolved.name
        self._playlist_modified = False
        self._set_queue_paths(paths, start_index=0, auto_play=auto_play)
        self._return_to_player()
        self.show_status(f"opened {resolved.name}")

    def load_targets(self, targets: list[Path], auto_play: bool = True) -> None:
        if len(targets) == 1:
            single = targets[0].expanduser().resolve()
            if single.is_file() and single.suffix.lower() in SUPPORTED_PLAYLIST_EXTENSIONS:
                self.load_m3u_file(single, auto_play=auto_play)
                return

        files = scan_audio_targets(targets)
        if not files:
            self.show_status("no audio files found")
            return

        first_target = targets[0].expanduser().resolve()
        self._last_folder = str(first_target if first_target.is_dir() else first_target.parent)
        self._active_playlist_id = None
        self._active_m3u_path = None
        self._active_playlist_name = ""
        self._playlist_modified = False

        self._set_queue_paths(files, start_index=0, auto_play=auto_play)
        if self.stack.currentIndex() == self.VIEW_PLAYLISTS:
            self._return_to_player()

    def append_targets(self, targets: list[Path]) -> None:
        new_files = scan_audio_targets(targets)
        if not new_files:
            return

        if not self._tracks:
            self.load_targets(targets, auto_play=True)
            return

        was_count = len(self._tracks)
        for p in new_files:
            self._tracks.append(Track(path=p, title=clean_filename_title(p)))

        if self._shuffle:
            extra_indices = list(range(was_count, len(self._tracks)))
            random.shuffle(extra_indices)
            self._shuffle_order.extend(extra_indices)

        self._playlist_modified = True
        self._start_metadata_worker(start_index=was_count)

        self.player_canvas.sync_playlist(self._tracks, self._current_index)
        if self.stack.currentIndex() == self.VIEW_SWITCHER:
            self.switcher_overlay.sync_tracks(self._tracks, self._current_index)

        self._save_session_state()
        added = len(new_files)
        self.show_status(f"added {added} {'track' if added == 1 else 'tracks'}")

    def _set_queue_paths(
        self,
        paths: list[Path],
        *,
        start_index: int = 0,
        auto_play: bool = True,
    ) -> None:
        self._stop_metadata_worker()
        self.media_player.stop()

        self._tracks = [Track(path=p, title=clean_filename_title(p)) for p in paths]
        self._current_index = max(0, min(start_index, len(self._tracks) - 1))
        self._error_flag = False
        self._last_position_ms = 0

        self._ensure_track_metadata(self._current_index)

        if self._shuffle:
            self._rebuild_shuffle_order(anchor_index=self._current_index)
        else:
            self._shuffle_order.clear()
            self._shuffle_pos = -1

        if len(self._tracks) > 1:
            self._start_metadata_worker(start_index=self._current_index)

        if self.stack.currentIndex() == self.VIEW_SWITCHER:
            self.switcher_overlay.sync_tracks(self._tracks, self._current_index)

        self._load_track_at(self._current_index, auto_play=auto_play)
        self._save_session_state()

    def _start_metadata_worker(self, start_index: int = 0) -> None:
        self._stop_metadata_worker()
        worker = MetadataWorker(self._tracks, start_index=start_index)
        worker.batchReady.connect(self._on_metadata_batch_ready)
        self._metadata_worker = worker
        worker.start()

    def _ensure_track_metadata(self, index: int) -> None:
        if not (0 <= index < len(self._tracks)):
            return
        track = self._tracks[index]
        if not track.metadata_loaded:
            title, artist, duration_ms = read_track_metadata(track.path)
            track.title = title
            track.artist = artist
            if duration_ms > 0:
                track.duration_ms = duration_ms
            track.metadata_loaded = True

    def _on_metadata_batch_ready(self, batch: list[tuple[str, str, str, int]]) -> None:
        by_path = {p: (t, a, d) for p, t, a, d in batch}
        cur_updated = False
        any_updated = False

        for idx, track in enumerate(self._tracks):
            info = by_path.get(str(track.path))
            if info is not None:
                title, artist, duration_ms = info
                track.title = title
                track.artist = artist
                if duration_ms > 0 and track.duration_ms <= 0:
                    track.duration_ms = duration_ms
                track.metadata_loaded = True
                any_updated = True
                if idx == self._current_index:
                    cur_updated = True

        if cur_updated and 0 <= self._current_index < len(self._tracks):
            cur = self._tracks[self._current_index]
            self.player_canvas.set_track_info(cur.title, cur.artist, cur.duration_ms)
            self._notify_mpris_state()

        if any_updated:
            self.player_canvas.sync_playlist(self._tracks, self._current_index)
            if self.stack.currentIndex() == self.VIEW_SWITCHER:
                self.switcher_overlay.refresh_list()

    def clear_queue(self) -> None:
        """Clear active queue (Ctrl+N / Ctrl+W) with Ctrl+Z undo support."""
        if not self._tracks and not self._active_playlist_name:
            return
        self._last_cleared_queue = (
            list(self._tracks),
            self._current_index,
            self._active_playlist_id,
            self._active_playlist_name,
            self._active_m3u_path,
        )
        self._stop_metadata_worker()
        self.media_player.stop()
        self.media_player.setSource(QUrl())
        self._tracks = []
        self._current_index = -1
        self._shuffle_order.clear()
        self._shuffle_pos = -1
        self._last_position_ms = 0
        self._error_flag = False
        self._active_playlist_id = None
        self._active_playlist_name = ""
        self._active_m3u_path = None
        self._playlist_modified = False

        self.player_canvas.set_track_info("No Track Loaded", "—", 0)
        self.player_canvas.set_time(0, 0)
        self.player_canvas.sync_playlist(self._tracks, self._current_index)
        self._save_session_state()
        self._return_to_player()
        self.show_status("cleared queue")
        self._notify_mpris_state()

    def undo_clear_queue(self) -> bool:
        if self._last_cleared_queue is None:
            return False
        tracks, cur_idx, pl_id, pl_name, m3u_path = self._last_cleared_queue
        self._last_cleared_queue = None
        self._tracks = tracks
        self._active_playlist_id = pl_id
        self._active_playlist_name = pl_name
        self._active_m3u_path = m3u_path
        self._playlist_modified = False
        if self._tracks:
            target = max(0, min(cur_idx, len(self._tracks) - 1))
            self._load_track_at(target, auto_play=False)
        else:
            self.player_canvas.sync_playlist(self._tracks, self._current_index)
        self._save_session_state()
        self.show_status("restored queue")
        return True

    def _remove_track_at(self, track_index: int, selected_pos: int) -> None:
        if not (0 <= track_index < len(self._tracks)):
            return
        removed = self._tracks.pop(track_index)
        self._last_removed_track = (track_index, removed)
        self._playlist_modified = True

        was_playing = self.media_player.playbackState() == QMediaPlayer.PlaybackState.PlayingState

        if not self._tracks:
            self.media_player.stop()
            self.media_player.setSource(QUrl())
            self._current_index = -1
            self._last_position_ms = 0
            self.player_canvas.set_track_info("No Track Loaded", "—", 0)
            self.player_canvas.set_time(0, 0)
        elif track_index == self._current_index:
            new_idx = min(track_index, len(self._tracks) - 1)
            self._load_track_at(new_idx, auto_play=was_playing)
        elif track_index < self._current_index:
            self._current_index -= 1

        if self._shuffle:
            self._rebuild_shuffle_order(anchor_index=max(0, self._current_index))

        self.player_canvas.sync_playlist(self._tracks, self._current_index)
        self.switcher_overlay.sync_tracks(
            self._tracks,
            self._current_index,
            keep_selected_pos=max(0, min(selected_pos, len(self._tracks) - 1)),
        )
        self._save_session_state()
        self._update_telemetry()
        self._notify_mpris_state()

    def undo_remove_track(self) -> bool:
        if self._last_removed_track is None:
            return False
        idx, track = self._last_removed_track
        self._last_removed_track = None
        insert_at = max(0, min(idx, len(self._tracks)))
        self._tracks.insert(insert_at, track)
        if self._current_index < 0:
            self._load_track_at(insert_at, auto_play=False)
        elif insert_at <= self._current_index:
            self._current_index += 1
        if self._shuffle:
            self._rebuild_shuffle_order(anchor_index=max(0, self._current_index))
        self._playlist_modified = True
        self.player_canvas.sync_playlist(self._tracks, self._current_index)
        self.switcher_overlay.sync_tracks(
            self._tracks, self._current_index, keep_selected_pos=insert_at
        )
        self._save_session_state()
        self._update_telemetry()
        return True

    def _reorder_track(self, track_index: int, delta: int) -> None:
        target = track_index + delta
        if not (0 <= track_index < len(self._tracks) and 0 <= target < len(self._tracks)):
            return
        self._tracks[track_index], self._tracks[target] = (
            self._tracks[target],
            self._tracks[track_index],
        )
        if self._current_index == track_index:
            self._current_index = target
        elif self._current_index == target:
            self._current_index = track_index

        self._playlist_modified = True
        self.player_canvas.sync_playlist(self._tracks, self._current_index)
        self.switcher_overlay.sync_tracks(
            self._tracks, self._current_index, keep_selected_pos=target
        )
        self._save_session_state()
        self._update_telemetry()

    # -----------------------------------------------------------------------
    # Saved Playlists Library & Save / Export (Scratchpad Pattern)
    # -----------------------------------------------------------------------
    def handle_save_playlist(self) -> None:
        """
        Save shortcut (Ctrl+S), mirroring Scratchpad:
        - If an external .m3u/.m3u8 file is active, writes directly to that file.
        - If an existing saved library playlist is active, updates it immediately.
        - Otherwise opens the minimal zero-chrome inline 'save as' prompt.
        """
        if not self._tracks:
            self.show_status("nothing to save")
            return

        if self._active_m3u_path is not None:
            try:
                write_m3u_playlist(self._active_m3u_path, self._tracks)
                self._playlist_modified = False
                self._save_session_state()
                self.show_status(f"saved {self._active_m3u_path.name}")
                return
            except OSError:
                self.show_status("failed to save playlist")
                return

        if self._active_playlist_id is not None:
            self.save_current_playlist(self._active_playlist_name)
            return

        self._open_save_prompt()

    def _open_save_prompt(self) -> None:
        if self.stack.currentIndex() != self.VIEW_PLAYER:
            self.stack.setCurrentIndex(self.VIEW_PLAYER)
        self._show_save_prompt = True
        self.player_canvas.save_bar.setVisible(True)
        if self._active_playlist_name:
            self.player_canvas.save_name_input.setText(self._active_playlist_name)
            self.player_canvas.save_name_input.selectAll()
        else:
            self.player_canvas.save_name_input.clear()
        self.player_canvas.save_name_input.setFocus()
        self._position_telemetry()

    def _close_save_prompt(self) -> None:
        self._show_save_prompt = False
        self.player_canvas.save_bar.setVisible(False)
        self.setFocus()
        self._position_telemetry()

    def save_current_playlist(self, custom_name: str = "") -> None:
        if not self._tracks:
            self._close_save_prompt()
            return
        final_name = (
            custom_name.strip()
            or self._active_playlist_name.strip()
            or auto_generate_playlist_name(self._tracks, self._last_folder)
        )
        paths = [str(t.path) for t in self._tracks]
        pl_id = self.repo.upsert_playlist(
            name=final_name,
            track_paths=paths,
            playlist_id=self._active_playlist_id,
        )
        self._active_playlist_id = pl_id
        self._active_playlist_name = final_name
        self._active_m3u_path = None
        self._playlist_modified = False
        self._close_save_prompt()
        self._save_session_state()
        self.show_status(f'saved "{final_name}"')

    def export_current_playlist_dialog(self) -> None:
        if not self._tracks:
            self.show_status("nothing to export")
            return
        suggested = (
            self._active_playlist_name
            or auto_generate_playlist_name(self._tracks, self._last_folder)
        )
        safe_stem = (
            "".join(c if c.isalnum() or c in "-_ ." else "_" for c in suggested).strip()
            or "playlist"
        )
        if not safe_stem.lower().endswith((".m3u", ".m3u8")):
            safe_stem += ".m3u8"
        default_path = (
            str(self._active_m3u_path)
            if self._active_m3u_path is not None
            else str(Path(self._last_folder) / safe_stem)
        )
        file_path, selected_filter = QFileDialog.getSaveFileName(
            self,
            "Export Playlist (.m3u8 / .m3u)",
            default_path,
            "UTF-8 Playlist (*.m3u8);;M3U Playlist (*.m3u);;All Files (*)",
        )
        if not file_path:
            self.setFocus()
            return
        target = Path(file_path)
        if not target.suffix:
            target = target.with_suffix(".m3u" if "M3U Playlist" in selected_filter else ".m3u8")
        try:
            write_m3u_playlist(target, self._tracks)
            self._active_m3u_path = target
            self._active_playlist_id = None
            self._active_playlist_name = target.stem or target.name
            self._playlist_modified = False
            self._save_session_state()
            self.show_status(f"exported {target.name}")
        except OSError:
            self.show_status("export failed")
        self.setFocus()

    def export_saved_playlist_to_m3u(self, playlist: SavedPlaylist) -> None:
        safe_stem = (
            "".join(c if c.isalnum() or c in "-_ ." else "_" for c in playlist.name).strip()
            or "playlist"
        )
        if not safe_stem.lower().endswith((".m3u", ".m3u8")):
            safe_stem += ".m3u8"
        default_path = str(Path(self._last_folder) / safe_stem)
        file_path, selected_filter = QFileDialog.getSaveFileName(
            self,
            "Export Saved Playlist (.m3u8 / .m3u)",
            default_path,
            "UTF-8 Playlist (*.m3u8);;M3U Playlist (*.m3u);;All Files (*)",
        )
        if not file_path:
            return
        target = Path(file_path)
        if not target.suffix:
            target = target.with_suffix(".m3u" if "M3U Playlist" in selected_filter else ".m3u8")
        tracks = [Track(path=Path(p), title=clean_filename_title(Path(p))) for p in playlist.track_paths]
        try:
            write_m3u_playlist(target, tracks)
            self.show_status(f"exported {target.name}")
        except OSError:
            self.show_status("export failed")

    def _on_playlist_selected_from_library(self, playlist: SavedPlaylist) -> None:
        existing = [
            Path(p)
            for p in playlist.track_paths
            if Path(p).is_file() and Path(p).suffix.lower() in SUPPORTED_AUDIO_EXTENSIONS
        ]
        if not existing:
            self.show_status("no available tracks in playlist")
            self._return_to_player()
            return
        self._active_playlist_id = playlist.id
        self._active_playlist_name = playlist.name
        self._active_m3u_path = None
        self._playlist_modified = False
        self._set_queue_paths(existing, start_index=0, auto_play=True)
        self._return_to_player()
        self.show_status(f'loaded "{playlist.name}"')

    def delete_saved_playlist(self, playlist: SavedPlaylist) -> None:
        self._last_deleted_playlist = playlist
        self.repo.delete_playlist(playlist.id)
        if self._active_playlist_id == playlist.id:
            self._active_playlist_id = None
            self._active_playlist_name = ""
            self._playlist_modified = False
            self._save_session_state()
        self.show_status(f'deleted "{playlist.name}"')

    def undo_delete_playlist(self) -> bool:
        if self._last_deleted_playlist is None:
            return False
        restored = self._last_deleted_playlist
        self._last_deleted_playlist = None
        self.repo.restore_playlist(restored)
        self.show_status(f'restored "{restored.name}"')
        return True

    # -----------------------------------------------------------------------
    # Playback Controls
    # -----------------------------------------------------------------------
    def _load_track_at(self, index: int, auto_play: bool = True) -> None:
        if not (0 <= index < len(self._tracks)):
            return

        self._current_index = index
        self._mpris_track_gen += 1
        self._error_flag = False
        self._last_position_ms = 0
        self._ensure_track_metadata(index)
        track = self._tracks[index]

        if self._shuffle:
            if index in self._shuffle_order:
                self._shuffle_pos = self._shuffle_order.index(index)
            else:
                self._rebuild_shuffle_order(anchor_index=index)

        self.player_canvas.set_track_info(track.title, track.artist, track.duration_ms)
        self.player_canvas.set_time(0, track.duration_ms)
        self.player_canvas.sync_playlist(self._tracks, self._current_index)

        self.media_player.setSource(QUrl.fromLocalFile(str(track.path)))
        if auto_play:
            self.media_player.play()
        self._update_telemetry()
        self._notify_mpris_state()

    def mpris_track_id(self) -> str:
        if not (0 <= self._current_index < len(self._tracks)):
            return "/org/mpris/MediaPlayer2/NoTrack"
        return f"/org/mpris/MediaPlayer2/Track/{self._mpris_track_gen}_{self._current_index}"

    def mpris_playback_status(self) -> str:
        state = self.media_player.playbackState()
        if state == QMediaPlayer.PlaybackState.PlayingState:
            return "Playing"
        if state == QMediaPlayer.PlaybackState.PausedState:
            return "Paused"
        return "Stopped"

    def mpris_loop_status(self) -> str:
        mode = REPEAT_MODES[self._repeat_mode_idx]
        if mode == "All":
            return "Playlist"
        if mode == "One":
            return "Track"
        return "None"

    def mpris_metadata(self) -> dict[str, Any]:
        if not (0 <= self._current_index < len(self._tracks)):
            return {}
        track = self._tracks[self._current_index]
        dur_ms = track.duration_ms if track.duration_ms > 0 else self._effective_duration_ms()
        meta: dict[str, Any] = {
            "xesam:title": track.title,
            "xesam:artist": [track.artist],
            "xesam:url": QUrl.fromLocalFile(str(track.path)).toString(),
            "mpris:length": int(max(0, dur_ms) * 1000),
        }
        if _HAS_QTDBUS:
            meta["mpris:trackid"] = QDBusObjectPath(self.mpris_track_id())
        return meta

    def _notify_mpris_state(self) -> None:
        if hasattr(self, "mpris_service"):
            has_tracks = bool(self._tracks)
            self.mpris_service.emit_properties_changed(
                {
                    "PlaybackStatus": self.mpris_playback_status(),
                    "Metadata": self.mpris_metadata(),
                    "LoopStatus": self.mpris_loop_status(),
                    "Shuffle": self._shuffle,
                    "Volume": 0.0 if self._muted else (self._volume_pct / 100.0),
                    "CanPlay": has_tracks,
                    "CanPause": has_tracks,
                    "CanGoNext": has_tracks,
                    "CanGoPrevious": has_tracks,
                    "CanSeek": has_tracks,
                }
            )

    def mpris_set_volume(self, volume: float) -> None:
        clamped = max(0.0, min(1.0, float(volume)))
        pct = int(round(clamped * 100.0))
        self._volume_pct = pct
        self._muted = pct == 0
        self._apply_audio_volume()
        self._update_telemetry()
        if hasattr(self, "mpris_service"):
            self.mpris_service.emit_properties_changed(
                {"Volume": 0.0 if self._muted else (self._volume_pct / 100.0)}
            )

    def mpris_set_shuffle(self, enabled: bool) -> None:
        if bool(enabled) != self._shuffle:
            self.toggle_shuffle()

    def mpris_set_loop_status(self, loop_status: str) -> None:
        mapping = {"None": 0, "Playlist": 1, "Track": 2}
        if loop_status in mapping and self._repeat_mode_idx != mapping[loop_status]:
            self._repeat_mode_idx = mapping[loop_status]
            self._update_telemetry()
            if hasattr(self, "mpris_service"):
                self.mpris_service.emit_properties_changed(
                    {"LoopStatus": self.mpris_loop_status()}
                )

    def mpris_set_position(self, track_id_path: str, position_us: int) -> None:
        if not self._tracks or self._current_index < 0:
            return
        if track_id_path and track_id_path != self.mpris_track_id():
            return
        self.seek_to(int(position_us // 1000))

    def mpris_open_uri(self, uri: str) -> None:
        url = QUrl(uri)
        local_str = url.toLocalFile() if url.isLocalFile() else uri
        if local_str:
            path = Path(local_str).expanduser()
            if path.exists():
                self.load_targets([path], auto_play=True)

    def dispatch_media_action(self, action: str, *, debounce_sec: float = 0.22) -> bool:
        """
        Unified debounced dispatcher for hardware/global media keys across MPRIS2 D-Bus,
        Linux kernel /dev/input/event*, X11 XQueryKeymap, and focused Qt QKeyEvents.
        """
        now = time.monotonic()
        bucket = "play_state" if action in ("play_pause", "play", "pause") else action
        last_ts = self._last_media_action_ts.get(bucket, -10.0)
        if debounce_sec > 0 and (now - last_ts) < debounce_sec:
            return False
        self._last_media_action_ts[bucket] = now

        if action == "play_pause":
            self.toggle_play_pause()
            return True
        if action == "play":
            if self.media_player.playbackState() != QMediaPlayer.PlaybackState.PlayingState:
                self.toggle_play_pause()
            return True
        if action == "pause":
            if self.media_player.playbackState() == QMediaPlayer.PlaybackState.PlayingState:
                self.media_player.pause()
                self._update_telemetry()
                self._notify_mpris_state()
            return True
        if action == "stop":
            self.media_player.stop()
            self._last_position_ms = 0
            self.player_canvas.set_time(0, self._effective_duration_ms())
            self._update_telemetry()
            self._notify_mpris_state()
            return True
        if action == "next":
            self.next_track()
            return True
        if action == "previous":
            self.previous_track()
            return True
        if action == "seek_fwd":
            self.seek_relative(SEEK_STEP_MS)
            return True
        if action == "seek_back":
            self.seek_relative(-SEEK_STEP_MS)
            return True
        return False

    def toggle_play_pause(self) -> None:
        if not self._tracks:
            return
        state = self.media_player.playbackState()
        if state == QMediaPlayer.PlaybackState.PlayingState:
            self.media_player.pause()
        else:
            if self._current_index < 0:
                self._load_track_at(0, auto_play=True)
            else:
                self.media_player.play()
        self._update_telemetry()
        self._notify_mpris_state()

    def seek_to(self, position_ms: int) -> None:
        if not self._tracks or self._current_index < 0:
            return
        duration = self._effective_duration_ms()
        target = max(0, int(position_ms))
        if duration > 0:
            target = min(target, max(0, duration - 100))
        self._last_position_ms = target
        self.media_player.setPosition(target)
        self.player_canvas.set_time(target, duration)
        if hasattr(self, "mpris_service"):
            self.mpris_service.emit_seeked(int(target * 1000))

    def seek_relative(self, delta_ms: int) -> None:
        if not self._tracks or self._current_index < 0:
            return
        self.seek_to(self._last_position_ms + delta_ms)

    def _effective_duration_ms(self) -> int:
        dur = self.media_player.duration()
        if dur > 0:
            return dur
        if 0 <= self._current_index < len(self._tracks):
            return self._tracks[self._current_index].duration_ms
        return 0

    def _rebuild_shuffle_order(self, anchor_index: int | None = None) -> None:
        n = len(self._tracks)
        if n == 0:
            self._shuffle_order = []
            self._shuffle_pos = -1
            return
        indices = list(range(n))
        if anchor_index is not None and 0 <= anchor_index < n:
            indices.remove(anchor_index)
            random.shuffle(indices)
            self._shuffle_order = [anchor_index, *indices]
            self._shuffle_pos = 0
        else:
            random.shuffle(indices)
            self._shuffle_order = indices
            self._shuffle_pos = 0

    def next_track(self, *, from_end_of_media: bool = False) -> None:
        n = len(self._tracks)
        if n == 0:
            return

        repeat_mode = REPEAT_MODES[self._repeat_mode_idx]

        if from_end_of_media and repeat_mode == "One":
            self._last_position_ms = 0
            self.media_player.setPosition(0)
            self.media_player.play()
            if hasattr(self, "mpris_service"):
                self.mpris_service.emit_seeked(0)
            return

        if self._shuffle:
            if len(self._shuffle_order) != n:
                self._rebuild_shuffle_order(anchor_index=max(0, self._current_index))
            next_pos = self._shuffle_pos + 1
            if next_pos >= n:
                if from_end_of_media and repeat_mode == "Off":
                    self._last_position_ms = 0
                    self.media_player.stop()
                    self.player_canvas.set_time(0, self._effective_duration_ms())
                    self._update_telemetry()
                    self._notify_mpris_state()
                    return
                self._rebuild_shuffle_order()
                next_pos = 0
            self._shuffle_pos = next_pos
            self._load_track_at(self._shuffle_order[next_pos], auto_play=True)
            return

        next_idx = self._current_index + 1
        if next_idx >= n:
            if from_end_of_media and repeat_mode == "Off":
                self._last_position_ms = 0
                self.media_player.stop()
                self.player_canvas.set_time(0, self._effective_duration_ms())
                self._update_telemetry()
                self._notify_mpris_state()
                return
            next_idx = 0

        self._load_track_at(next_idx, auto_play=True)

    def previous_track(self) -> None:
        n = len(self._tracks)
        if n == 0:
            return

        if max(self.media_player.position(), self._last_position_ms) > PREV_RESTART_THRESHOLD_MS:
            self._last_position_ms = 0
            self.media_player.setPosition(0)
            self.player_canvas.set_time(0, self._effective_duration_ms())
            if hasattr(self, "mpris_service"):
                self.mpris_service.emit_seeked(0)
            return

        if self._shuffle:
            if len(self._shuffle_order) != n:
                self._rebuild_shuffle_order(anchor_index=max(0, self._current_index))
            prev_pos = (self._shuffle_pos - 1) % n
            self._shuffle_pos = prev_pos
            self._load_track_at(self._shuffle_order[prev_pos], auto_play=True)
            return

        prev_idx = (self._current_index - 1) % n
        self._load_track_at(prev_idx, auto_play=True)

    def adjust_volume(self, delta_pct: int) -> None:
        new_vol = max(0, min(100, self._volume_pct + delta_pct))
        if self._muted and delta_pct > 0:
            self._muted = False
        self._volume_pct = new_vol
        self._apply_audio_volume()
        self._update_telemetry()
        if hasattr(self, "mpris_service"):
            self.mpris_service.emit_properties_changed(
                {"Volume": 0.0 if self._muted else (self._volume_pct / 100.0)}
            )

    def toggle_mute(self) -> None:
        self._muted = not self._muted
        self._apply_audio_volume()
        self._update_telemetry()
        if hasattr(self, "mpris_service"):
            self.mpris_service.emit_properties_changed(
                {"Volume": 0.0 if self._muted else (self._volume_pct / 100.0)}
            )

    def _apply_audio_volume(self) -> None:
        linear = QAudio.convertVolume(
            self._volume_pct / 100.0,
            QAudio.VolumeScale.LogarithmicVolumeScale,
            QAudio.VolumeScale.LinearVolumeScale,
        )
        self.audio_output.setVolume(float(linear))
        self.audio_output.setMuted(self._muted)

    def toggle_shuffle(self) -> None:
        self._shuffle = not self._shuffle
        if self._shuffle and self._tracks:
            anchor = self._current_index if self._current_index >= 0 else 0
            self._rebuild_shuffle_order(anchor_index=anchor)
        else:
            self._shuffle_order.clear()
            self._shuffle_pos = -1
        self._update_telemetry()
        if hasattr(self, "mpris_service"):
            self.mpris_service.emit_properties_changed({"Shuffle": self._shuffle})

    def cycle_repeat(self) -> None:
        self._repeat_mode_idx = (self._repeat_mode_idx + 1) % len(REPEAT_MODES)
        self._update_telemetry()
        if hasattr(self, "mpris_service"):
            self.mpris_service.emit_properties_changed(
                {"LoopStatus": self.mpris_loop_status()}
            )

    # -----------------------------------------------------------------------
    # Media Player Signals
    # -----------------------------------------------------------------------
    def _on_position_changed(self, position_ms: int) -> None:
        self._last_position_ms = position_ms
        self.player_canvas.set_time(position_ms, self._effective_duration_ms())

    def _on_duration_changed(self, duration_ms: int) -> None:
        if duration_ms > 0 and 0 <= self._current_index < len(self._tracks):
            self._tracks[self._current_index].duration_ms = duration_ms
            self.player_canvas.sync_playlist(self._tracks, self._current_index)
            self._notify_mpris_state()
        self.player_canvas.set_time(self._last_position_ms, self._effective_duration_ms())

    def _on_playback_state_changed(self, _state: QMediaPlayer.PlaybackState) -> None:
        self._update_telemetry()
        self._notify_mpris_state()

    def _on_media_status_changed(self, status: QMediaPlayer.MediaStatus) -> None:
        if status == QMediaPlayer.MediaStatus.EndOfMedia:
            self.next_track(from_end_of_media=True)

    def _on_media_error(self, _error: QMediaPlayer.Error, _error_string: str) -> None:
        self._error_flag = True
        self._update_telemetry()

    # -----------------------------------------------------------------------
    # Overlays (F1 Help, L / Track Switcher, Ctrl+L Saved Playlists)
    # -----------------------------------------------------------------------
    def _return_to_player(self) -> None:
        self.stack.setCurrentIndex(self.VIEW_PLAYER)
        self._position_telemetry()
        self.setFocus()

    def toggle_help_overlay(self) -> None:
        if self._show_save_prompt:
            self._close_save_prompt()
        if self.stack.currentIndex() == self.VIEW_HELP:
            self._return_to_player()
        else:
            self.stack.setCurrentIndex(self.VIEW_HELP)
            self._position_telemetry()
            self.setFocus()

    def toggle_track_switcher(self) -> None:
        if self._show_save_prompt:
            self._close_save_prompt()
        if self.stack.currentIndex() == self.VIEW_SWITCHER:
            self._return_to_player()
        else:
            self.switcher_overlay.open_view(self._tracks, self._current_index)
            self.stack.setCurrentIndex(self.VIEW_SWITCHER)
            self._position_telemetry()

    def toggle_saved_playlists(self) -> None:
        if self._show_save_prompt:
            self._close_save_prompt()
        if self.stack.currentIndex() == self.VIEW_PLAYLISTS:
            self._return_to_player()
        else:
            self.playlists_view.open_view(self._active_playlist_id)
            self.stack.setCurrentIndex(self.VIEW_PLAYLISTS)
            self._position_telemetry()

    def _on_switcher_track_activated(self, index: int) -> None:
        self._return_to_player()
        self._load_track_at(index, auto_play=True)

    def handle_escape(self) -> None:
        if self._show_save_prompt:
            self._close_save_prompt()
            return
        if self.stack.currentIndex() != self.VIEW_PLAYER:
            self._return_to_player()
            return
        self.instant_quit()

    # -----------------------------------------------------------------------
    # Window Mode (Fullscreen & Frameless) + Edge Resize / Drag
    # -----------------------------------------------------------------------
    def toggle_fullscreen(self) -> None:
        if self.isFullScreen():
            self.showNormal()
            if self._was_frameless_before_fs != self._frameless:
                self._set_frameless_flag(self._was_frameless_before_fs)
        else:
            self._was_frameless_before_fs = self._frameless
            self.showFullScreen()
        self._position_telemetry()
        self.setFocus()
        if hasattr(self, "mpris_service"):
            self.mpris_service.emit_properties_changed(
                {"Fullscreen": self.isFullScreen()},
                interface="org.mpris.MediaPlayer2",
            )

    def toggle_frameless(self) -> None:
        if self.isFullScreen():
            self.showNormal()
        self._set_frameless_flag(not self._frameless)

    def _set_frameless_flag(self, enable: bool) -> None:
        self._frameless = enable
        geom = self.geometry()
        if self._frameless:
            self.setWindowFlags(
                Qt.WindowType.Window | Qt.WindowType.FramelessWindowHint
            )
        else:
            self.setWindowFlags(Qt.WindowType.Window)
        self.setGeometry(geom)
        self.show()
        self.unsetCursor()
        self._position_telemetry()
        self.setFocus()

    def _detect_resize_edges(self, pos: QPoint) -> Qt.Edge:
        edges = Qt.Edge(0)
        if pos.x() <= RESIZE_MARGIN_PX:
            edges |= Qt.Edge.LeftEdge
        elif pos.x() >= self.width() - RESIZE_MARGIN_PX:
            edges |= Qt.Edge.RightEdge

        if pos.y() <= RESIZE_MARGIN_PX:
            edges |= Qt.Edge.TopEdge
        elif pos.y() >= self.height() - RESIZE_MARGIN_PX:
            edges |= Qt.Edge.BottomEdge

        return edges

    def _update_resize_cursor(self, pos: QPoint) -> None:
        if not self._frameless or self.isFullScreen():
            self.unsetCursor()
            return

        edges = self._detect_resize_edges(pos)
        if edges == (Qt.Edge.LeftEdge | Qt.Edge.TopEdge) or edges == (
            Qt.Edge.RightEdge | Qt.Edge.BottomEdge
        ):
            self.setCursor(Qt.CursorShape.SizeFDiagCursor)
        elif edges == (Qt.Edge.RightEdge | Qt.Edge.TopEdge) or edges == (
            Qt.Edge.LeftEdge | Qt.Edge.BottomEdge
        ):
            self.setCursor(Qt.CursorShape.SizeBDiagCursor)
        elif edges in (Qt.Edge.LeftEdge, Qt.Edge.RightEdge):
            self.setCursor(Qt.CursorShape.SizeHorCursor)
        elif edges in (Qt.Edge.TopEdge, Qt.Edge.BottomEdge):
            self.setCursor(Qt.CursorShape.SizeVerCursor)
        else:
            self.unsetCursor()

    def _is_in_viewport_margin(self, pos: QPoint) -> bool:
        return (
            pos.x() < PADDING_PX
            or pos.x() > self.width() - PADDING_PX
            or pos.y() < PADDING_PX
            or pos.y() > self.height() - PADDING_PX
        )

    def _apply_manual_resize(self, global_pos: QPoint) -> None:
        if self._manual_resize_origin_pos is None or self._manual_resize_origin_geom is None:
            return
        delta = global_pos - self._manual_resize_origin_pos
        rect = QRect(self._manual_resize_origin_geom)
        edges = self._manual_resize_edges
        min_w, min_h = self.minimumWidth(), self.minimumHeight()

        if edges & Qt.Edge.LeftEdge:
            new_left = min(rect.right() - min_w + 1, rect.left() + delta.x())
            rect.setLeft(new_left)
        if edges & Qt.Edge.RightEdge:
            new_right = max(rect.left() + min_w - 1, rect.right() + delta.x())
            rect.setRight(new_right)
        if edges & Qt.Edge.TopEdge:
            new_top = min(rect.bottom() - min_h + 1, rect.top() + delta.y())
            rect.setTop(new_top)
        if edges & Qt.Edge.BottomEdge:
            new_bottom = max(rect.top() + min_h - 1, rect.bottom() + delta.y())
            rect.setBottom(new_bottom)

        self.setGeometry(rect)

    # -----------------------------------------------------------------------
    # Global Shortcut Dispatch (Works Even When Inline Search / Save Input Focused)
    # -----------------------------------------------------------------------
    def _handle_global_key(self, key: int, mods: Qt.KeyboardModifier) -> bool:
        # Hardware media keys (work in any view)
        if key in (Qt.Key.Key_MediaTogglePlayPause, Qt.Key.Key_MediaPlay):
            self.dispatch_media_action("play_pause")
            return True
        if key == Qt.Key.Key_MediaPause:
            self.dispatch_media_action("pause")
            return True
        if key == Qt.Key.Key_MediaStop:
            self.dispatch_media_action("stop")
            return True
        if key == Qt.Key.Key_MediaNext:
            self.dispatch_media_action("next")
            return True
        if key == Qt.Key.Key_MediaPrevious:
            self.dispatch_media_action("previous")
            return True
        if key == Qt.Key.Key_AudioForward:
            self.dispatch_media_action("seek_fwd")
            return True
        if key == Qt.Key.Key_AudioRewind:
            self.dispatch_media_action("seek_back")
            return True

        ctrl = bool(mods & Qt.KeyboardModifier.ControlModifier)
        shift = bool(mods & Qt.KeyboardModifier.ShiftModifier)
        alt = bool(mods & Qt.KeyboardModifier.AltModifier)

        if alt and not ctrl:
            return False

        if ctrl and key == Qt.Key.Key_Q:
            self.instant_quit()
            return True

        if key == Qt.Key.Key_F1 and not ctrl:
            self.toggle_help_overlay()
            return True

        if key == Qt.Key.Key_F11 and not ctrl:
            self.toggle_fullscreen()
            return True

        if ctrl and shift and key == Qt.Key.Key_F:
            self.toggle_frameless()
            return True

        if ctrl and shift and key == Qt.Key.Key_D:
            self.toggle_theme()
            return True

        if ctrl and not shift and key == Qt.Key.Key_M:
            self.toggle_font_family()
            return True

        if ctrl and key in (Qt.Key.Key_Equal, Qt.Key.Key_Plus):
            self.adjust_font_size(1)
            return True

        if ctrl and key == Qt.Key.Key_Minus:
            self.adjust_font_size(-1)
            return True

        if ctrl and not shift and key == Qt.Key.Key_0:
            self.reset_font_size()
            return True

        if ctrl and not shift and key == Qt.Key.Key_I:
            self.cycle_telemetry_mode()
            return True

        if ctrl and not shift and key == Qt.Key.Key_L:
            self.toggle_saved_playlists()
            return True

        if ctrl and shift and key == Qt.Key.Key_O:
            self.open_playlist_file_dialog()
            return True

        if ctrl and not shift and key == Qt.Key.Key_O:
            self.open_folder_dialog()
            return True

        if ctrl and shift and key == Qt.Key.Key_S:
            self.export_current_playlist_dialog()
            return True

        if ctrl and not shift and key == Qt.Key.Key_S:
            self.handle_save_playlist()
            return True

        if ctrl and not shift and key in (Qt.Key.Key_N, Qt.Key.Key_W):
            self.clear_queue()
            return True

        return False

    def keyPressEvent(self, a0: Any) -> None:
        event = a0
        if event is None:
            return
        key = event.key()
        mods = event.modifiers()
        ctrl = bool(mods & Qt.KeyboardModifier.ControlModifier)
        shift = bool(mods & Qt.KeyboardModifier.ShiftModifier)

        if self._handle_global_key(key, mods):
            event.accept()
            return

        if key == Qt.Key.Key_Escape and not ctrl:
            self.handle_escape()
            event.accept()
            return

        view = self.stack.currentIndex()
        if view == self.VIEW_HELP:
            event.accept()
            return

        if view == self.VIEW_SWITCHER:
            if not ctrl:
                if key == Qt.Key.Key_Down:
                    self.switcher_overlay.move_selection(1)
                    event.accept()
                    return
                if key == Qt.Key.Key_Up:
                    self.switcher_overlay.move_selection(-1)
                    event.accept()
                    return
                if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                    idx = self.switcher_overlay.selected_track_index()
                    if idx is not None:
                        self._on_switcher_track_activated(idx)
                    event.accept()
                    return
            event.accept()
            return

        if view == self.VIEW_PLAYLISTS:
            event.accept()
            return

        # --- Root Playback Canvas Shortcuts ---
        if ctrl and not shift:
            if key == Qt.Key.Key_A:
                self.append_files_dialog()
                event.accept()
                return
            if key == Qt.Key.Key_Down:
                if 0 <= self._current_index < len(self._tracks):
                    self._reorder_track(self._current_index, 1)
                event.accept()
                return
            if key == Qt.Key.Key_Up:
                if 0 <= self._current_index < len(self._tracks):
                    self._reorder_track(self._current_index, -1)
                event.accept()
                return
            if key == Qt.Key.Key_D:
                if 0 <= self._current_index < len(self._tracks):
                    self._remove_track_at(self._current_index, self._current_index)
                event.accept()
                return
            if key == Qt.Key.Key_Z:
                if not self.undo_remove_track():
                    self.undo_clear_queue()
                event.accept()
                return

        if not ctrl:
            if key == Qt.Key.Key_Delete:
                if 0 <= self._current_index < len(self._tracks):
                    self._remove_track_at(self._current_index, self._current_index)
                event.accept()
                return
            if key == Qt.Key.Key_Space:
                self.toggle_play_pause()
                event.accept()
                return
            if key == Qt.Key.Key_Right:
                self.seek_relative(SEEK_LARGE_STEP_MS if shift else SEEK_STEP_MS)
                event.accept()
                return
            if key == Qt.Key.Key_Left:
                self.seek_relative(-(SEEK_LARGE_STEP_MS if shift else SEEK_STEP_MS))
                event.accept()
                return
            if key in (Qt.Key.Key_N, Qt.Key.Key_Down):
                self.next_track()
                event.accept()
                return
            if key in (Qt.Key.Key_P, Qt.Key.Key_Up):
                self.previous_track()
                event.accept()
                return
            if key in (Qt.Key.Key_Equal, Qt.Key.Key_Plus):
                self.adjust_volume(VOLUME_STEP_PCT)
                event.accept()
                return
            if key == Qt.Key.Key_Minus:
                self.adjust_volume(-VOLUME_STEP_PCT)
                event.accept()
                return
            if key == Qt.Key.Key_M:
                self.toggle_mute()
                event.accept()
                return
            if key == Qt.Key.Key_S:
                self.toggle_shuffle()
                event.accept()
                return
            if key == Qt.Key.Key_R:
                self.cycle_repeat()
                event.accept()
                return
            if key == Qt.Key.Key_H:
                self.toggle_playlist_visibility()
                event.accept()
                return
            if key == Qt.Key.Key_O:
                self.open_folder_dialog()
                event.accept()
                return
            if key == Qt.Key.Key_A:
                self.append_files_dialog()
                event.accept()
                return
            if key in (Qt.Key.Key_L, Qt.Key.Key_Slash):
                self.toggle_track_switcher()
                event.accept()
                return

        super().keyPressEvent(event)

    # -----------------------------------------------------------------------
    # Application Event Filter (Inline Save Prompt, Global Keys, Drag & Resize)
    # -----------------------------------------------------------------------
    def eventFilter(self, a0: Any, a1: Any) -> bool:
        watched, event = a0, a1
        if event is None or not isinstance(watched, QWidget):
            return super().eventFilter(watched, event)

        if watched.window() is not self:
            return super().eventFilter(watched, event)

        etype = event.type()

        if etype == QEvent.Type.KeyPress:
            key = event.key()
            mods = event.modifiers()

            # Inline Save Playlist prompt (Scratchpad style)
            if watched is self.player_canvas.save_name_input:
                if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                    self.save_current_playlist(self.player_canvas.save_name_input.text())
                    return True
                if key == Qt.Key.Key_Escape:
                    self._close_save_prompt()
                    return True

            # Ensure global shortcuts work even when a child QLineEdit has focus
            if watched is not self:
                if self._handle_global_key(key, mods):
                    return True

        # Ctrl + Mouse Wheel dynamic font zoom
        if etype == QEvent.Type.Wheel:
            if event.modifiers() & Qt.KeyboardModifier.ControlModifier:
                delta = event.angleDelta().y()
                if delta > 0:
                    self.adjust_font_size(1)
                elif delta < 0:
                    self.adjust_font_size(-1)
                return True

        # Hover cursor feedback along 8px outer resize perimeter when frameless
        if etype == QEvent.Type.MouseMove:
            global_pos = event.globalPosition().toPoint()
            if self._manual_drag_offset is not None:
                self.move(global_pos - self._manual_drag_offset)
                return True
            if self._manual_resize_edges != Qt.Edge(0):
                self._apply_manual_resize(global_pos)
                return True
            win_pos = self.mapFromGlobal(global_pos)
            self._update_resize_cursor(win_pos)
            return False

        if etype == QEvent.Type.MouseButtonPress:
            if event.button() == Qt.MouseButton.LeftButton:
                global_pos = event.globalPosition().toPoint()
                win_pos = self.mapFromGlobal(global_pos)
                win_handle = self.windowHandle()

                if (
                    event.modifiers() & Qt.KeyboardModifier.AltModifier
                ) and not self.isFullScreen():
                    if win_handle is not None and win_handle.startSystemMove():
                        return True
                    self._manual_drag_offset = global_pos - self.frameGeometry().topLeft()
                    return True

                if self._frameless and not self.isFullScreen():
                    edges = self._detect_resize_edges(win_pos)
                    if edges != Qt.Edge(0):
                        if win_handle is not None and win_handle.startSystemResize(edges):
                            return True
                        self._manual_resize_edges = edges
                        self._manual_resize_origin_pos = global_pos
                        self._manual_resize_origin_geom = QRect(self.geometry())
                        return True

                    if self._is_in_viewport_margin(win_pos):
                        if win_handle is not None and win_handle.startSystemMove():
                            return True
                        self._manual_drag_offset = global_pos - self.frameGeometry().topLeft()
                        return True

        if etype == QEvent.Type.MouseButtonRelease:
            if self._manual_drag_offset is not None or self._manual_resize_edges != Qt.Edge(0):
                self._manual_drag_offset = None
                self._manual_resize_edges = Qt.Edge(0)
                self._manual_resize_origin_pos = None
                self._manual_resize_origin_geom = None
                return True

        return super().eventFilter(watched, event)

    # -----------------------------------------------------------------------
    # Drag & Drop Audio Files, Folders & .m3u/.m3u8 Playlists
    # -----------------------------------------------------------------------
    def dragEnterEvent(self, a0: Any) -> None:
        event = a0
        if event is not None and event.mimeData() is not None and event.mimeData().hasUrls():
            event.acceptProposedAction()
            return
        super().dragEnterEvent(event)

    def dropEvent(self, a0: Any) -> None:
        event = a0
        if event is not None and event.mimeData() is not None and event.mimeData().hasUrls():
            paths: list[Path] = []
            for url in event.mimeData().urls():
                if url.isLocalFile():
                    paths.append(Path(url.toLocalFile()))
            if paths:
                self.load_targets(paths, auto_play=True)
                event.acceptProposedAction()
                return
        super().dropEvent(event)

    # -----------------------------------------------------------------------
    # Window Events & Clean Audio Teardown
    # -----------------------------------------------------------------------
    def resizeEvent(self, a0: Any) -> None:
        super().resizeEvent(a0)
        self._position_telemetry()

    def showEvent(self, a0: Any) -> None:
        super().showEvent(a0)
        self._position_telemetry()
        self.setFocus()

    def _cleanup_audio(self) -> None:
        if self._has_closed:
            return
        self._has_closed = True
        self._status_timer.stop()
        if hasattr(self, "global_media_keys"):
            self.global_media_keys.stop()
        if hasattr(self, "mpris_service"):
            self.mpris_service.unregister()
        self._save_session_state()
        self._stop_metadata_worker()
        for sig, slot in (
            (self.media_player.positionChanged, self._on_position_changed),
            (self.media_player.durationChanged, self._on_duration_changed),
            (self.media_player.playbackStateChanged, self._on_playback_state_changed),
            (self.media_player.mediaStatusChanged, self._on_media_status_changed),
            (self.media_player.errorOccurred, self._on_media_error),
        ):
            try:
                sig.disconnect(slot)
            except TypeError:
                pass
        self.media_player.stop()
        self.media_player.setSource(QUrl())
        self.media_player.setAudioOutput(None)
        self.audio_output.setMuted(True)
        self.repo.close()

    def instant_quit(self) -> None:
        self._cleanup_audio()
        self.close()
        QApplication.quit()

    def closeEvent(self, a0: Any) -> None:
        self._cleanup_audio()
        if a0 is not None:
            a0.accept()


# ---------------------------------------------------------------------------
# Entry Point
# ---------------------------------------------------------------------------
def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv

    QApplication.setDesktopSettingsAware(True)
    app = QApplication([sys.argv[0], *args])
    app.setApplicationName(APP_TITLE)
    app.setDesktopFileName(DESKTOP_FILE_NAME)
    app.setWindowIcon(load_app_icon())
    app.setCursorFlashTime(1000)

    start_frameless = "--frameless" in args or "-f" in args
    targets = [
        Path(a)
        for a in args
        if not a.startswith("-") and Path(a).expanduser().exists()
    ]

    window = MinimalMusicPlayerWindow(
        initial_targets=targets,
        start_frameless=start_frameless,
    )
    window.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
