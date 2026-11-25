#!/usr/bin/env python3
"""
ActivityWatch -> VibeWatch Importer (`import_aw.py`)

Merges ActivityWatch JSON bucket exports (`currentwindow`, `web.tab.current`,
`afkstatus`) via an exact sweep-line into non-overlapping VibeWatch SQLite WAL
events, subtracting any intervals already present in `activity.db` so imports
are idempotent and never double-count live VibeWatch telemetry.

Usage:
  .venv/bin/python import_aw.py file1.json file2.json ...
  .venv/bin/python import_aw.py --dry-run file1.json ...
"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple
from urllib.parse import urlparse

from vibewatch_core import (
    DEFAULT_DB_PATH,
    HEARTBEAT_PULSETIME_SECONDS,
    ActivityDatabase,
    format_duration,
    normalize_app_binary,
)


@dataclass(frozen=True)
class AWInterval:
    kind: str  # "afk" | "not_afk" | "window" | "web" | "existing"
    start_ts: float
    end_ts: float
    app: str = ""
    title: str = ""
    url: str = ""
    order: int = 0


def _parse_iso_ts(raw: str) -> float:
    text = raw.strip()
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    return datetime.fromisoformat(text).timestamp()


def _format_web_title(title: str, url: str) -> str:
    clean_title = (title or "").strip()
    clean_url = (url or "").strip()
    host = ""
    if clean_url:
        try:
            host = urlparse(clean_url).netloc
            if host.startswith("www."):
                host = host[4:]
        except ValueError:
            host = ""
    if clean_title and host and host.lower() not in clean_title.lower():
        return f"{clean_title} — {host}"
    return clean_title or host or clean_url or "Browser Tab"


def load_aw_intervals(paths: Sequence[Path]) -> Tuple[List[AWInterval], Dict[str, int]]:
    """Load ActivityWatch bucket JSON files into normalized intervals."""
    intervals: List[AWInterval] = []
    counts: Dict[str, int] = {"currentwindow": 0, "web.tab.current": 0, "afkstatus": 0}
    order = 0

    for path in paths:
        raw = json.loads(Path(path).read_text(encoding="utf-8"))
        if isinstance(raw, dict) and isinstance(raw.get("buckets"), dict):
            buckets = list(raw["buckets"].values())
        elif isinstance(raw, dict) and "events" in raw:
            buckets = [raw]
        else:
            raise ValueError(f"Unrecognized ActivityWatch export format: {path}")

        for bucket in buckets:
            btype = str(bucket.get("type", "")).strip()
            events = bucket.get("events", [])
            if not isinstance(events, list):
                continue

            for ev in events:
                try:
                    dur = float(ev.get("duration", 0.0) or 0.0)
                    if dur <= 0.0:
                        continue
                    t0 = _parse_iso_ts(str(ev["timestamp"]))
                except (KeyError, TypeError, ValueError):
                    continue
                t1 = t0 + dur
                data = ev.get("data") or {}
                order += 1

                if btype == "afkstatus":
                    status = str(data.get("status", "")).strip().lower()
                    kind = "afk" if status == "afk" else "not_afk"
                    intervals.append(AWInterval(kind=kind, start_ts=t0, end_ts=t1, order=order))
                    counts["afkstatus"] += 1
                elif btype == "currentwindow":
                    app = str(data.get("app", "")).strip()
                    title = str(data.get("title", "")).strip()
                    intervals.append(
                        AWInterval(kind="window", start_ts=t0, end_ts=t1, app=app, title=title, order=order)
                    )
                    counts["currentwindow"] += 1
                elif btype == "web.tab.current":
                    title = str(data.get("title", "")).strip()
                    url = str(data.get("url", "")).strip()
                    intervals.append(
                        AWInterval(kind="web", start_ts=t0, end_ts=t1, title=title, url=url, order=order)
                    )
                    counts["web.tab.current"] += 1

    return intervals, counts


def merge_aw_intervals(
    aw_intervals: Sequence[AWInterval],
    existing_intervals: Sequence[Tuple[float, float]] = (),
    merge_gap: float = HEARTBEAT_PULSETIME_SECONDS,
) -> List[Tuple[float, float, str, str, str, bool]]:
    """
    Sweep-line merge of ActivityWatch intervals into non-overlapping VibeWatch rows:
      1. Existing `activity.db` intervals are skipped (idempotent & non-overlapping).
      2. `afkstatus == "afk"` overrides active window/web events (`is_afk=True`).
      3. Known `currentwindow` (`app` not `'unknown'`) takes precedence for active time.
      4. `web.tab.current` recovers Wayland `'unknown'` browser activity as `zen-bin`.
      5. Remaining `not-afk` + `'unknown'` window periods map to `wayland-app`.
    """
    all_items: List[AWInterval] = list(aw_intervals)
    for idx, (s, e) in enumerate(existing_intervals):
        if e > s:
            all_items.append(AWInterval(kind="existing", start_ts=float(s), end_ts=float(e), order=-idx - 1))

    if not all_items:
        return []

    events: List[Tuple[float, int, AWInterval]] = []
    for item in all_items:
        if item.end_ts <= item.start_ts:
            continue
        events.append((item.start_ts, 1, item))
        events.append((item.end_ts, -1, item))
    events.sort(key=lambda x: (x[0], x[1]))

    active_existing: Dict[int, AWInterval] = {}
    active_afk: Dict[int, AWInterval] = {}
    active_not_afk: Dict[int, AWInterval] = {}
    active_window: Dict[int, AWInterval] = {}
    active_web: Dict[int, AWInterval] = {}

    buckets_map = {
        "existing": active_existing,
        "afk": active_afk,
        "not_afk": active_not_afk,
        "window": active_window,
        "web": active_web,
    }

    merged: List[Tuple[float, float, str, str, str, bool]] = []
    n = len(events)
    i = 0

    def _pick_latest(items: Iterable[AWInterval]) -> Optional[AWInterval]:
        best: Optional[AWInterval] = None
        for it in items:
            if best is None or (it.start_ts, it.order) > (best.start_ts, best.order):
                best = it
        return best

    while i < n:
        t = events[i][0]
        while i < n and events[i][0] == t:
            _, delta, item = events[i]
            target = buckets_map[item.kind]
            key = id(item)
            if delta > 0:
                target[key] = item
            else:
                target.pop(key, None)
            i += 1

        if i >= n:
            break
        next_t = events[i][0]
        if next_t - t <= 0.01:
            continue

        if active_existing:
            continue

        resolved: Optional[Tuple[str, str, str, bool]] = None

        if active_afk:
            resolved = ("afk", "org.freedesktop.ScreenSaver", "Screen Locked / Away", True)
        else:
            known_win = _pick_latest(
                w for w in active_window.values() if w.app and w.app.lower() != "unknown"
            )
            if known_win is not None:
                norm_app = normalize_app_binary(known_win.app, known_win.app, known_win.title)
                if norm_app.lower() == "zen" and active_web:
                    tab = _pick_latest(active_web.values())
                    title = _format_web_title(tab.title, tab.url) if tab else known_win.title
                    resolved = ("zen-bin", "zen", title, False)
                else:
                    title = known_win.title if known_win.title.lower() != "unknown" else norm_app
                    resolved = (norm_app, known_win.app, title, False)
            elif active_web and (active_window or active_not_afk):
                tab = _pick_latest(active_web.values())
                if tab is not None:
                    resolved = ("zen-bin", "zen", _format_web_title(tab.title, tab.url), False)
            elif active_window or active_not_afk:
                resolved = ("wayland-app", "wayland", "Native Wayland App (ActivityWatch)", False)

        if resolved is None:
            continue

        app_bin, wm_class, win_title, is_afk = resolved
        if merged:
            prev_s, prev_e, prev_app, prev_cls, prev_title, prev_afk = merged[-1]
            if (
                prev_app == app_bin
                and prev_cls == wm_class
                and prev_title == win_title
                and prev_afk == is_afk
                and 0.0 <= (t - prev_e) <= merge_gap
            ):
                merged[-1] = (prev_s, next_t, prev_app, prev_cls, prev_title, prev_afk)
                continue

        merged.append((t, next_t, app_bin, wm_class, win_title, is_afk))

    return merged


def import_activitywatch_files(
    paths: Sequence[Path],
    db_path: Path = DEFAULT_DB_PATH,
    dry_run: bool = False,
) -> Dict[str, Any]:
    """Import one or more ActivityWatch bucket JSON files into VibeWatch SQLite DB."""
    aw_intervals, bucket_counts = load_aw_intervals(paths)
    db = ActivityDatabase(db_path=db_path)

    if aw_intervals:
        min_ts = min(iv.start_ts for iv in aw_intervals)
        max_ts = max(iv.end_ts for iv in aw_intervals)
        existing = [
            (ev.start_ts, ev.end_ts) for ev in db.query_events(min_ts - 1.0, max_ts + 1.0)
        ]
    else:
        min_ts = max_ts = 0.0
        existing = []

    rows = merge_aw_intervals(aw_intervals, existing_intervals=existing)
    inserted = 0 if dry_run else db.bulk_insert_events(rows)

    active_sec = sum(e - s for s, e, *_rest, is_afk in rows if not is_afk)
    afk_sec = sum(e - s for s, e, *_rest, is_afk in rows if is_afk)

    return {
        "bucket_events": bucket_counts,
        "rows_prepared": len(rows),
        "rows_inserted": inserted,
        "active_seconds": active_sec,
        "afk_seconds": afk_sec,
        "range_start": datetime.fromtimestamp(min_ts).isoformat() if min_ts else None,
        "range_end": datetime.fromtimestamp(max_ts).isoformat() if max_ts else None,
        "dry_run": dry_run,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Import ActivityWatch bucket JSON exports into VibeWatch.")
    parser.add_argument("files", nargs="+", type=Path, help="ActivityWatch export JSON files.")
    parser.add_argument("--db", type=Path, default=DEFAULT_DB_PATH, help="Target VibeWatch SQLite DB path.")
    parser.add_argument("--dry-run", action="store_true", help="Compute summary without writing to SQLite.")
    args = parser.parse_args()

    summary = import_activitywatch_files(args.files, db_path=args.db, dry_run=args.dry_run)
    print(
        json.dumps(
            {
                **summary,
                "active_human": format_duration(summary["active_seconds"]),
                "afk_human": format_duration(summary["afk_seconds"]),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
