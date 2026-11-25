#!/usr/bin/env python3
"""
VibeWatch Core — Shared SQLite WAL database, heartbeat merging, /proc binary
resolution, smart application grouping, and live-reloading categories.json engine.
"""

from __future__ import annotations

import contextlib
import json
import os
import re
import sqlite3
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple

try:
    import psutil  # type: ignore
except ImportError:  # pragma: no cover - optional fallback in non-venv tests
    psutil = None


APP_DIR = Path(__file__).resolve().parent
DATA_DIR = Path(os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share")) / "vibewatch"
CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "vibewatch"
DEFAULT_DB_PATH = DATA_DIR / "activity.db"
DEFAULT_CATEGORIES_PATH = CONFIG_DIR / "categories.json"
DEFAULT_STATUS_PATH = DATA_DIR / "daemon_status.json"

POLL_INTERVAL_SECONDS = 2.0
HEARTBEAT_PULSETIME_SECONDS = 5.0
AFK_THRESHOLD_MS = 180_000  # 3 minutes


DEFAULT_CATEGORIES: List[Dict[str, str]] = [
    {
        "name": "Coding",
        "color": "#88C0D0",
        "pattern": r"code|antigravity|agy|cursor|nvim|lazygit|pycharm|android-studio|qtcreator",
    },
    {
        "name": "Browsing",
        "color": "#826cb6",
        "pattern": r"zen|firefox|brave|chromium|chrome",
    },
    {
        "name": "Gaming",
        "color": "#D08770",
        "pattern": r"steam_app_|war thunder|aces|beamng|ksp|minecraft|heroic|prism",
    },
    {
        "name": "Terminal",
        "color": "#98C379",
        "pattern": r"konsole|ghostty|kitty|alacritty|zsh|bash",
    },
    {
        "name": "Media",
        "color": "#EBCB8B",
        "pattern": r"vlc|elisa|mpv|spotify|youtube|music_player",
    },
    {
        "name": "Uncategorized",
        "color": "#6C7086",
        "pattern": r".*",
    },
]

# Known friendly process display names while preserving underlying binary grouping
FRIENDLY_APP_ALIASES: Dict[str, str] = {
    "aces": "War Thunder",
    "steam_app_236390": "War Thunder",
    "steam_app_1172620": "Sea of Thieves",
    "steam_app_517630": "Just Cause 4",
    "steam_app_621830": "WRC 7",
    "steam_app_2357570": "Overwatch",
    "steam_app_1273400": "ConSim",
    "steam_app_3240220": "GTA V",
}


def ensure_venv(script_path: Optional[Path] = None) -> None:
    """
    Self-healing .venv check: if a local `.venv/bin/python` exists alongside
    VibeWatch and the current interpreter is outside that virtual environment,
    seamlessly re-exec into the local venv Python.
    """
    if os.environ.get("VIBEWATCH_SKIP_VENV") == "1":
        return

    base_dir = (Path(script_path).resolve().parent if script_path else APP_DIR)
    venv_dir = base_dir / ".venv"
    venv_python = venv_dir / "bin" / "python"

    if not venv_python.exists():
        return

    try:
        current_prefix = Path(sys.prefix).resolve()
        target_prefix = venv_dir.resolve()
        if current_prefix == target_prefix:
            return
    except OSError:
        return

    os.execv(str(venv_python), [str(venv_python), *sys.argv])


def format_duration(seconds: float) -> str:
    """Format seconds into concise desktop telemetry strings (e.g., `3h 42m`, `48m`, `< 1m`)."""
    total = max(0, int(round(seconds)))
    if total == 0:
        return "0m"
    if total < 60:
        return "< 1m"
    hours, rem = divmod(total, 3600)
    minutes = rem // 60
    if hours > 0:
        return f"{hours}h {minutes:02d}m" if minutes > 0 else f"{hours}h 00m"
    return f"{minutes}m"


def resolve_pid_binary(
    pid: Optional[int],
    resource_class: str = "",
    caption: str = "",
    proc_root: Path = Path("/proc"),
) -> str:
    """
    Resolve the true Linux executable name for a window PID via `/proc/<pid>/comm`
    with `/proc/<pid>/cmdline` and `psutil` fallbacks. Prevents Wayland-native
    browsers (e.g., `zen-bin`) or Proton/Steam games (`aces`, `steam_app_*`) from
    registering as `"unknown"`.
    """
    comm = ""
    cmdline_parts: List[str] = []

    if pid and pid > 0:
        comm_path = proc_root / str(pid) / "comm"
        cmdline_path = proc_root / str(pid) / "cmdline"

        try:
            if comm_path.exists():
                comm = comm_path.read_text(encoding="utf-8", errors="ignore").strip()
        except OSError:
            pass

        try:
            if cmdline_path.exists():
                raw = cmdline_path.read_bytes()
                cmdline_parts = [
                    part.decode("utf-8", errors="ignore")
                    for part in raw.split(b"\x00")
                    if part
                ]
        except OSError:
            pass

        if not comm and psutil is not None:
            try:
                proc = psutil.Process(pid)
                comm = proc.name() or ""
                if not cmdline_parts:
                    cmdline_parts = proc.cmdline()
            except Exception:
                pass

    # Detect Steam Proton / Linux runtime app IDs in cmdline or resource_class
    joined_cmd = " ".join(cmdline_parts)
    steam_match = re.search(r"(steam_app_\d+)", f"{resource_class} {joined_cmd}", re.IGNORECASE)
    if steam_match:
        app_id = steam_match.group(1).lower()
        return FRIENDLY_APP_ALIASES.get(app_id, app_id)

    # Unwrap generic interpreters/launchers when cmdline gives a clearer binary
    if comm in {"", "MainThread", "ThreadPoolForeg", "wine64-preloader", "reaper", "bwrap"} and cmdline_parts:
        for arg in cmdline_parts:
            if arg.startswith("-"):
                continue
            base = Path(arg.replace("\\", "/")).name
            if base and base.lower() not in {"sh", "bash", "env", "python", "python3", "bwrap", "reaper"}:
                comm = base
                break

    # Fall back cleanly to KWin resourceClass before ever returning a generic label
    candidate = comm or resource_class.strip()
    if not candidate and caption:
        # Derive a stable token from the last segment of caption (e.g., "file.py — Kate")
        segments = [seg.strip() for seg in re.split(r"\s+[\-—|·]\s+", caption) if seg.strip()]
        candidate = segments[-1] if segments else caption[:32].strip()

    candidate = normalize_app_binary(candidate, resource_class=resource_class, caption=caption)
    return candidate or "desktop"


def normalize_app_binary(app_binary: str, resource_class: str = "", caption: str = "") -> str:
    """
    Normalize process names so dynamic titles or flatpak/desktop-entry prefixes
    group cleanly into one top-level application row.
    """
    raw = (app_binary or "").strip()
    cls = (resource_class or "").strip()

    # Strip reverse-DNS prefixes from Wayland app_ids (e.g. "org.kde.konsole" -> "konsole")
    if not raw.lower().endswith(".exe") and not raw.startswith("steam_app_"):
        parts = raw.split(".")
        if len(parts) >= 3 or (
            len(parts) == 2 and parts[0].lower() in {"org", "com", "net", "io", "dev", "app", "de", "uk", "fr", "me"}
        ):
            tail = parts[-1]
            if tail and len(tail) >= 2:
                raw = tail

    lower = raw.lower()
    if lower in FRIENDLY_APP_ALIASES:
        return FRIENDLY_APP_ALIASES[lower]
    if lower.endswith(".exe"):
        stem = raw[:-4]
        if stem.lower() in FRIENDLY_APP_ALIASES:
            return FRIENDLY_APP_ALIASES[stem.lower()]
        return stem

    # Check if binary or caption clearly indicates War Thunder via aces launcher
    if lower in {"aces", "dagor"} or "war thunder" in lower or "war thunder" in caption.lower():
        return "War Thunder"

    if not raw and cls:
        return cls.split(".")[-1] if "." in cls else cls

    return raw or "desktop"


@dataclass
class CategoryRule:
    name: str
    color: str
    pattern: str
    compiled: re.Pattern[str] = field(init=False, repr=False)

    def __post_init__(self) -> None:
        try:
            self.compiled = re.compile(self.pattern, re.IGNORECASE)
        except re.error:
            self.compiled = re.compile(re.escape(self.pattern), re.IGNORECASE)


class CategoryEngine:
    """
    Manages `~/.config/vibewatch/categories.json` with auto-creation on first run
    and automatic mtime-based hot-reloading whenever edited on disk.
    """

    def __init__(self, path: Path = DEFAULT_CATEGORIES_PATH) -> None:
        self.path = Path(path)
        self._last_mtime: float = -1.0
        self.rules: List[CategoryRule] = []
        self.ensure_default_file()
        self.reload_if_changed(force=True)

    def ensure_default_file(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            payload = {"categories": DEFAULT_CATEGORIES}
            self.path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    def reload_if_changed(self, force: bool = False) -> bool:
        """Reload rules from disk if mtime changed. Returns True if reloaded."""
        try:
            stat = self.path.stat()
            mtime = stat.st_mtime
        except OSError:
            self.ensure_default_file()
            mtime = self.path.stat().st_mtime
            force = True

        if not force and mtime == self._last_mtime:
            return False

        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
            entries = raw.get("categories", raw) if isinstance(raw, dict) else raw
            parsed: List[CategoryRule] = []
            for item in entries:
                if not isinstance(item, dict):
                    continue
                name = str(item.get("name", "")).strip()
                color = str(item.get("color", "#6C7086")).strip()
                pattern = str(item.get("pattern", "")).strip()
                if name and pattern:
                    parsed.append(CategoryRule(name=name, color=color, pattern=pattern))
            if not any(r.name.lower() == "uncategorized" for r in parsed):
                parsed.append(CategoryRule(name="Uncategorized", color="#6C7086", pattern=r".*"))
            self.rules = parsed
            self._last_mtime = mtime
            return True
        except (OSError, json.JSONDecodeError, TypeError):
            if not self.rules:
                self.rules = [CategoryRule(**item) for item in DEFAULT_CATEGORIES]
            return False

    def save_raw_json(self, json_text: str) -> None:
        """Validate and persist updated categories.json text, then reload immediately."""
        parsed = json.loads(json_text)
        entries = parsed.get("categories", parsed) if isinstance(parsed, dict) else parsed
        if not isinstance(entries, list) or not entries:
            raise ValueError("categories.json must contain a non-empty 'categories' array.")
        for idx, item in enumerate(entries):
            if not isinstance(item, dict):
                raise ValueError(f"Category entry #{idx + 1} must be an object.")
            for req in ("name", "color", "pattern"):
                if not str(item.get(req, "")).strip():
                    raise ValueError(f"Category entry #{idx + 1} is missing '{req}'.")
            color_val = str(item["color"]).strip()
            if not re.fullmatch(r"#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})", color_val):
                raise ValueError(f"Category '{item['name']}' has invalid hex color '{color_val}'.")
            # Validate regex syntax
            re.compile(str(item["pattern"]), re.IGNORECASE)

        self.path.parent.mkdir(parents=True, exist_ok=True)
        normalized = {"categories": entries}
        self.path.write_text(json.dumps(normalized, indent=2) + "\n", encoding="utf-8")
        self.reload_if_changed(force=True)

    def read_raw_json(self) -> str:
        self.ensure_default_file()
        return self.path.read_text(encoding="utf-8")

    def categorize(self, app_binary: str, wm_class: str, window_title: str) -> Tuple[str, str]:
        """Return `(category_name, hex_color)` matched case-insensitively across all fields."""
        self.reload_if_changed()
        haystack = f"{app_binary} {wm_class} {window_title}"
        fallback: Tuple[str, str] = ("Uncategorized", "#6C7086")
        for rule in self.rules:
            if rule.name.lower() == "uncategorized":
                fallback = (rule.name, rule.color)
                continue
            if rule.compiled.search(haystack):
                return (rule.name, rule.color)
        return fallback

    def color_for(self, category_name: str) -> str:
        for rule in self.rules:
            if rule.name == category_name:
                return rule.color
        return "#6C7086"


@dataclass
class ActivityEvent:
    id: int
    start_ts: float
    end_ts: float
    app_binary: str
    wm_class: str
    window_title: str
    is_afk: bool

    @property
    def duration(self) -> float:
        return max(0.0, self.end_ts - self.start_ts)


class ActivityDatabase:
    """
    Lightweight SQLite WAL database shared between `daemon.py` and `dashboard.py`.
    Implements heartbeat-merged writes (pulsetime = 5.0s) to prevent database bloat.
    """

    def __init__(self, db_path: Path = DEFAULT_DB_PATH) -> None:
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_schema()

    def _connect(self, readonly: bool = False) -> sqlite3.Connection:
        if readonly and self.db_path.exists():
            uri = f"{self.db_path.resolve().as_uri()}?mode=ro"
            conn = sqlite3.connect(uri, uri=True, timeout=5.0)
        else:
            conn = sqlite3.connect(str(self.db_path), timeout=5.0)
        conn.row_factory = sqlite3.Row
        if not readonly:
            conn.execute("PRAGMA journal_mode=WAL;")
            conn.execute("PRAGMA synchronous=NORMAL;")
            conn.execute("PRAGMA busy_timeout=5000;")
        return conn

    def _init_schema(self) -> None:
        with contextlib.closing(self._connect(readonly=False)) as conn, conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    start_ts REAL NOT NULL,
                    end_ts REAL NOT NULL,
                    app_binary TEXT NOT NULL,
                    wm_class TEXT NOT NULL,
                    window_title TEXT NOT NULL,
                    is_afk INTEGER NOT NULL DEFAULT 0
                );
                """
            )
            conn.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_events_ts
                ON events (start_ts, end_ts);
                """
            )

    def record_heartbeat(
        self,
        now: float,
        app_binary: str,
        wm_class: str,
        window_title: str,
        is_afk: bool,
        pulsetime: float = HEARTBEAT_PULSETIME_SECONDS,
    ) -> Tuple[str, int]:
        """
        Heartbeat merge logic:
        - Fetch most recent row in `events`.
        - If `(app_binary, wm_class, window_title, is_afk)` matches AND
          `(now - last.end_ts) <= pulsetime`, UPDATE `end_ts = now`.
        - Otherwise INSERT a new row with `start_ts = now, end_ts = now`.
        Returns `("merged" | "inserted", row_id)`.
        """
        afk_int = 1 if is_afk else 0
        norm_binary = normalize_app_binary(app_binary, wm_class, window_title)
        clean_class = (wm_class or "").strip()
        clean_title = (window_title or "").strip()

        with contextlib.closing(self._connect(readonly=False)) as conn, conn:
            row = conn.execute(
                """
                SELECT id, start_ts, end_ts, app_binary, wm_class, window_title, is_afk
                FROM events
                ORDER BY id DESC
                LIMIT 1;
                """
            ).fetchone()

            if (
                row is not None
                and row["app_binary"] == norm_binary
                and row["wm_class"] == clean_class
                and row["window_title"] == clean_title
                and row["is_afk"] == afk_int
                and 0.0 <= (now - float(row["end_ts"])) <= pulsetime
            ):
                conn.execute(
                    "UPDATE events SET end_ts = ? WHERE id = ?;",
                    (float(now), int(row["id"])),
                )
                return ("merged", int(row["id"]))

            cursor = conn.execute(
                """
                INSERT INTO events (start_ts, end_ts, app_binary, wm_class, window_title, is_afk)
                VALUES (?, ?, ?, ?, ?, ?);
                """,
                (float(now), float(now), norm_binary, clean_class, clean_title, afk_int),
            )
            return ("inserted", int(cursor.lastrowid))

    def query_events(self, start_ts: float, end_ts: float) -> List[ActivityEvent]:
        """Fetch and clamp all events intersecting `[start_ts, end_ts]`."""
        with contextlib.closing(self._connect(readonly=True)) as conn:
            rows = conn.execute(
                """
                SELECT id, start_ts, end_ts, app_binary, wm_class, window_title, is_afk
                FROM events
                WHERE end_ts >= ? AND start_ts <= ?
                ORDER BY start_ts ASC;
                """,
                (float(start_ts), float(end_ts)),
            ).fetchall()

        events: List[ActivityEvent] = []
        for r in rows:
            clamped_start = max(float(r["start_ts"]), float(start_ts))
            clamped_end = min(float(r["end_ts"]), float(end_ts))
            if clamped_end < clamped_start:
                continue
            events.append(
                ActivityEvent(
                    id=int(r["id"]),
                    start_ts=clamped_start,
                    end_ts=clamped_end,
                    app_binary=str(r["app_binary"]),
                    wm_class=str(r["wm_class"]),
                    window_title=str(r["window_title"]),
                    is_afk=bool(r["is_afk"]),
                )
            )
        return events

    def latest_event(self) -> Optional[ActivityEvent]:
        with contextlib.closing(self._connect(readonly=True)) as conn:
            r = conn.execute(
                """
                SELECT id, start_ts, end_ts, app_binary, wm_class, window_title, is_afk
                FROM events
                ORDER BY id DESC
                LIMIT 1;
                """
            ).fetchone()
        if r is None:
            return None
        return ActivityEvent(
            id=int(r["id"]),
            start_ts=float(r["start_ts"]),
            end_ts=float(r["end_ts"]),
            app_binary=str(r["app_binary"]),
            wm_class=str(r["wm_class"]),
            window_title=str(r["window_title"]),
            is_afk=bool(r["is_afk"]),
        )

    def bulk_insert_events(
        self, rows: Sequence[Tuple[float, float, str, str, str, bool]]
    ) -> int:
        """Atomically insert pre-merged `(start_ts, end_ts, app_binary, wm_class, window_title, is_afk)` rows."""
        payload = [
            (
                float(start_ts),
                float(end_ts),
                normalize_app_binary(app_binary, wm_class, window_title),
                (wm_class or "").strip(),
                (window_title or "").strip(),
                1 if is_afk else 0,
            )
            for start_ts, end_ts, app_binary, wm_class, window_title, is_afk in rows
            if end_ts > start_ts
        ]
        if not payload:
            return 0
        with contextlib.closing(self._connect(readonly=False)) as conn, conn:
            conn.executemany(
                """
                INSERT INTO events (start_ts, end_ts, app_binary, wm_class, window_title, is_afk)
                VALUES (?, ?, ?, ?, ?, ?);
                """,
                payload,
            )
        return len(payload)


@dataclass
class TitleBreakdown:
    window_title: str
    duration: float
    percentage: float


@dataclass
class AppBreakdown:
    app_binary: str
    category: str
    color: str
    duration: float
    percentage: float
    titles: List[TitleBreakdown]


@dataclass
class CategoryBreakdown:
    category: str
    color: str
    duration: float
    percentage: float


@dataclass
class TimelineSegment:
    start_ts: float
    end_ts: float
    app_binary: str
    window_title: str
    category: str
    color: str
    is_afk: bool


@dataclass
class AnalyticsSnapshot:
    active_seconds: float
    afk_seconds: float
    total_tracked_seconds: float
    top_category: str
    top_category_color: str
    top_app: str
    categories: List[CategoryBreakdown]
    apps: List[AppBreakdown]
    timeline: List[TimelineSegment]


def compute_analytics(
    events: Sequence[ActivityEvent],
    categorizer: CategoryEngine,
    include_afk: bool = False,
    category_filter: Optional[str] = None,
) -> AnalyticsSnapshot:
    """
    Aggregate events into KPIs, Top Categories, Smart-Grouped Top Applications
    (with expandable per-`window_title` durations), and 24-hour timeline segments.
    """
    categorizer.reload_if_changed()

    active_seconds = 0.0
    afk_seconds = 0.0
    cat_totals: Dict[str, float] = {}
    cat_colors: Dict[str, str] = {}
    app_totals: Dict[str, float] = {}
    app_category_votes: Dict[str, Dict[str, float]] = {}
    app_title_totals: Dict[str, Dict[str, float]] = {}
    timeline: List[TimelineSegment] = []

    for ev in events:
        dur = ev.duration
        if dur <= 0.0:
            continue

        if ev.is_afk:
            afk_seconds += dur
            timeline.append(
                TimelineSegment(
                    start_ts=ev.start_ts,
                    end_ts=ev.end_ts,
                    app_binary=ev.app_binary,
                    window_title=ev.window_title or "Away / Screen Locked",
                    category="AFK",
                    color="#2A2A2E",
                    is_afk=True,
                )
            )
            if not include_afk:
                continue
            cat_name, cat_color = ("AFK", "#585858")
        else:
            active_seconds += dur
            cat_name, cat_color = categorizer.categorize(
                ev.app_binary, ev.wm_class, ev.window_title
            )
            timeline.append(
                TimelineSegment(
                    start_ts=ev.start_ts,
                    end_ts=ev.end_ts,
                    app_binary=ev.app_binary,
                    window_title=ev.window_title,
                    category=cat_name,
                    color=cat_color,
                    is_afk=False,
                )
            )

        cat_totals[cat_name] = cat_totals.get(cat_name, 0.0) + dur
        cat_colors[cat_name] = cat_color

        if category_filter and cat_name != category_filter:
            continue

        app_key = "AFK / Locked" if ev.is_afk else ev.app_binary
        app_totals[app_key] = app_totals.get(app_key, 0.0) + dur

        votes = app_category_votes.setdefault(app_key, {})
        votes[cat_name] = votes.get(cat_name, 0.0) + dur

        title_key = ev.window_title or ("Idle / Locked" if ev.is_afk else app_key)
        titles = app_title_totals.setdefault(app_key, {})
        titles[title_key] = titles.get(title_key, 0.0) + dur

    total_tracked = active_seconds + afk_seconds if include_afk else active_seconds
    cat_denom = sum(cat_totals.values()) or 1.0
    categories = [
        CategoryBreakdown(
            category=name,
            color=cat_colors[name],
            duration=dur,
            percentage=(dur / cat_denom) * 100.0,
        )
        for name, dur in sorted(cat_totals.items(), key=lambda kv: kv[1], reverse=True)
    ]

    app_denom = sum(app_totals.values()) or 1.0
    apps: List[AppBreakdown] = []
    for app_name, dur in sorted(app_totals.items(), key=lambda kv: kv[1], reverse=True):
        dominant_cat = max(
            app_category_votes[app_name].items(), key=lambda kv: kv[1]
        )[0]
        title_map = app_title_totals.get(app_name, {})
        title_denom = dur or 1.0
        titles = [
            TitleBreakdown(
                window_title=t_name,
                duration=t_dur,
                percentage=(t_dur / title_denom) * 100.0,
            )
            for t_name, t_dur in sorted(title_map.items(), key=lambda kv: kv[1], reverse=True)
        ]
        apps.append(
            AppBreakdown(
                app_binary=app_name,
                category=dominant_cat,
                color=cat_colors.get(dominant_cat, "#6C7086"),
                duration=dur,
                percentage=(dur / app_denom) * 100.0,
                titles=titles,
            )
        )

    non_afk_cats = [c for c in categories if c.category != "AFK"]
    top_cat = non_afk_cats[0].category if non_afk_cats else "None"
    top_cat_color = non_afk_cats[0].color if non_afk_cats else "#585858"
    non_afk_apps = [a for a in apps if a.app_binary != "AFK / Locked"]
    top_app = non_afk_apps[0].app_binary if non_afk_apps else "None"

    return AnalyticsSnapshot(
        active_seconds=active_seconds,
        afk_seconds=afk_seconds,
        total_tracked_seconds=total_tracked,
        top_category=top_cat,
        top_category_color=top_cat_color,
        top_app=top_app,
        categories=categories,
        apps=apps,
        timeline=timeline,
    )


def date_range_bounds(anchor_date: datetime, range_mode: str) -> Tuple[float, float]:
    """Compute local Unix epoch `[start_ts, end_ts]` for `day`, `7d`, or `30d`."""
    day_start = anchor_date.replace(hour=0, minute=0, second=0, microsecond=0)
    day_end = day_start + timedelta(days=1)
    if range_mode == "7d":
        start = day_start - timedelta(days=6)
    elif range_mode == "30d":
        start = day_start - timedelta(days=29)
    else:
        start = day_start
    return (start.timestamp(), day_end.timestamp())


def write_daemon_status(
    state: str,
    active_app: str,
    backend: str,
    is_afk: bool,
    status_path: Path = DEFAULT_STATUS_PATH,
) -> None:
    """Persist lightweight daemon status snapshot for dashboard footer readout."""
    status_path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "pid": os.getpid(),
        "state": state,
        "active_app": active_app,
        "backend": backend,
        "is_afk": is_afk,
        "updated_ts": time.time(),
    }
    tmp = status_path.with_suffix(".tmp")
    tmp.write_text(json.dumps(payload), encoding="utf-8")
    tmp.replace(status_path)


def read_daemon_status(status_path: Path = DEFAULT_STATUS_PATH) -> Dict[str, Any]:
    """Read daemon health snapshot and verify freshness (`<= 10s`)."""
    if not status_path.exists():
        return {"running": False, "active_app": "idle", "backend": "none", "is_afk": False}
    try:
        data = json.loads(status_path.read_text(encoding="utf-8"))
        age = time.time() - float(data.get("updated_ts", 0.0))
        running = age <= 10.0 and data.get("state") == "running"
        return {
            "running": running,
            "active_app": str(data.get("active_app", "idle")),
            "backend": str(data.get("backend", "wayland-kwin")),
            "is_afk": bool(data.get("is_afk", False)),
            "age": age,
        }
    except (OSError, json.JSONDecodeError, ValueError):
        return {"running": False, "active_app": "idle", "backend": "none", "is_afk": False}
