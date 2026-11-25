#!/usr/bin/env python3
"""Unit tests for VibeWatch core engine, SQLite WAL heartbeat merging, and setup generators."""

from __future__ import annotations

import json
import os
import sqlite3
import tempfile
import time
import unittest
from datetime import datetime
from pathlib import Path

import setup as vw_setup
from vibewatch_core import (
    ActivityDatabase,
    CategoryEngine,
    compute_analytics,
    date_range_bounds,
    format_duration,
    normalize_app_binary,
    resolve_pid_binary,
)


class VibeWatchCoreTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmpdir = tempfile.TemporaryDirectory()
        self.root = Path(self.tmpdir.name)
        self.db = ActivityDatabase(self.root / "activity.db")
        self.cats = CategoryEngine(self.root / "categories.json")

    def tearDown(self) -> None:
        self.tmpdir.cleanup()

    def test_wal_mode_and_index_created(self) -> None:
        conn = sqlite3.connect(str(self.root / "activity.db"))
        mode = conn.execute("PRAGMA journal_mode;").fetchone()[0]
        indexes = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='index';").fetchall()]
        conn.close()
        self.assertEqual(mode.lower(), "wal")
        self.assertIn("idx_events_ts", indexes)

    def test_heartbeat_merge_within_pulsetime_and_split_after(self) -> None:
        t0 = 1700000000.0
        a1, id1 = self.db.record_heartbeat(t0, "zen-bin", "zen", "Docs — Zen Browser", False)
        a2, id2 = self.db.record_heartbeat(t0 + 2.0, "zen-bin", "zen", "Docs — Zen Browser", False)
        a3, id3 = self.db.record_heartbeat(t0 + 4.5, "zen-bin", "zen", "Docs — Zen Browser", False)
        # Gap of 6.0s exceeds 5.0s pulsetime -> must insert new row
        a4, id4 = self.db.record_heartbeat(t0 + 10.5, "zen-bin", "zen", "Docs — Zen Browser", False)
        # AFK state change -> must insert new row even within 5.0s
        a5, id5 = self.db.record_heartbeat(t0 + 12.5, "zen-bin", "zen", "Docs — Zen Browser", True)

        self.assertEqual(a1, "inserted")
        self.assertEqual((a2, id2), ("merged", id1))
        self.assertEqual((a3, id3), ("merged", id1))
        self.assertEqual(a4, "inserted")
        self.assertNotEqual(id4, id1)
        self.assertEqual(a5, "inserted")
        self.assertNotEqual(id5, id4)

        events = self.db.query_events(t0 - 1, t0 + 20)
        self.assertEqual(len(events), 3)
        self.assertAlmostEqual(events[0].duration, 4.5)
        self.assertTrue(events[2].is_afk)

    def test_smart_app_grouping_and_title_expansion(self) -> None:
        base = datetime(2026, 11, 22, 10, 0, 0).timestamp()
        # War Thunder dynamic window titles should group under single app_binary
        self.db.record_heartbeat(base, "aces", "aces", "War Thunder (Vulkan, 64bit) - In battle", False)
        self.db.record_heartbeat(base + 300, "aces", "aces", "War Thunder (Vulkan, 64bit) - In battle", False, pulsetime=400)
        self.db.record_heartbeat(base + 302, "aces", "aces", "War Thunder (Vulkan, 64bit) - Waiting for game", False)
        self.db.record_heartbeat(base + 422, "aces", "aces", "War Thunder (Vulkan, 64bit) - Waiting for game", False, pulsetime=200)

        # Zen Browser tabs grouped under zen-bin
        self.db.record_heartbeat(base + 500, "zen-bin", "zen", "KDE Plasma 6 Docs — Zen Browser", False)
        self.db.record_heartbeat(base + 1100, "zen-bin", "zen", "KDE Plasma 6 Docs — Zen Browser", False, pulsetime=700)

        start, end = date_range_bounds(datetime(2026, 11, 22), "day")
        snap = compute_analytics(self.db.query_events(start, end), self.cats, include_afk=False)

        app_map = {a.app_binary: a for a in snap.apps}
        self.assertIn("War Thunder", app_map)
        self.assertIn("zen-bin", app_map)
        self.assertEqual(len(app_map["War Thunder"].titles), 2)
        self.assertAlmostEqual(app_map["War Thunder"].duration, 420.0)
        self.assertEqual(app_map["War Thunder"].category, "Gaming")
        self.assertEqual(app_map["zen-bin"].category, "Browsing")

    def test_categories_hot_reload_on_disk_modification(self) -> None:
        self.assertEqual(self.cats.categorize("blender", "blender", "Scene.blend")[0], "Uncategorized")
        raw = json.loads(self.cats.read_raw_json())
        raw["categories"].insert(0, {"name": "3D Art", "color": "#B48EAD", "pattern": "blender|krita"})
        # Ensure mtime advances reliably
        time.sleep(0.02)
        self.cats.save_raw_json(json.dumps(raw, indent=2))
        cat, color = self.cats.categorize("blender", "blender", "Scene.blend")
        self.assertEqual(cat, "3D Art")
        self.assertEqual(color, "#B48EAD")

    def test_proc_pid_resolution_never_unknown(self) -> None:
        fake_proc = self.root / "proc"
        (fake_proc / "4242").mkdir(parents=True)
        (fake_proc / "4242" / "comm").write_text("zen-bin\n", encoding="utf-8")
        (fake_proc / "4242" / "cmdline").write_bytes(b"/opt/zen/zen-bin\x00--new-window\x00")
        self.assertEqual(resolve_pid_binary(4242, "", "Zen Browser", proc_root=fake_proc), "zen-bin")

        (fake_proc / "9999").mkdir(parents=True)
        (fake_proc / "9999" / "comm").write_text("reaper\n", encoding="utf-8")
        (fake_proc / "9999" / "cmdline").write_bytes(b"reaper\x00SteamLaunch\x00steam_app_1172620\x00")
        self.assertEqual(resolve_pid_binary(9999, "steam_app_1172620", "", proc_root=fake_proc), "Sea of Thieves")
        self.assertEqual(normalize_app_binary("org.kde.konsole"), "konsole")
        self.assertEqual(normalize_app_binary("com.mitchellh.ghostty"), "ghostty")
        self.assertEqual(normalize_app_binary("script.py"), "script.py")
        self.assertEqual(normalize_app_binary("runner.sh"), "runner.sh")

    def test_category_save_validates_hex_color(self) -> None:
        raw = json.loads(self.cats.read_raw_json())
        raw["categories"][0]["color"] = "not-a-hex"
        with self.assertRaises(ValueError):
            self.cats.save_raw_json(json.dumps(raw))

    def test_kwin_bridge_script_dedup_and_service_name(self) -> None:
        import daemon as vw_daemon
        script = vw_daemon.build_kwin_bridge_script("com.naveen.VibeWatch.Receiver.Probe99")
        self.assertIn("com.naveen.VibeWatch.Receiver.Probe99", script)
        self.assertIn("hookedWindows", script)

    def test_activitywatch_import_merge_and_idempotency(self) -> None:
        import import_aw

        win_export = {
            "buckets": {
                "win": {
                    "type": "currentwindow",
                    "events": [
                        {"timestamp": "2026-11-22T10:00:00+00:00", "duration": 120.0, "data": {"app": "unknown", "title": "unknown"}},
                        {"timestamp": "2026-11-22T10:02:00+00:00", "duration": 60.0, "data": {"app": "steam_app_517630", "title": "Just Cause 4"}},
                    ],
                }
            }
        }
        web_export = {
            "buckets": {
                "web": {
                    "type": "web.tab.current",
                    "events": [
                        {"timestamp": "2026-11-22T10:00:00+00:00", "duration": 90.0, "data": {"title": "Docs", "url": "https://kde.org/docs"}},
                    ],
                }
            }
        }
        afk_export = {
            "buckets": {
                "afk": {
                    "type": "afkstatus",
                    "events": [
                        {"timestamp": "2026-11-22T10:00:00+00:00", "duration": 150.0, "data": {"status": "not-afk"}},
                        {"timestamp": "2026-11-22T10:02:30+00:00", "duration": 30.0, "data": {"status": "afk"}},
                    ],
                }
            }
        }

        p_win = self.root / "win.json"
        p_web = self.root / "web.json"
        p_afk = self.root / "afk.json"
        p_win.write_text(json.dumps(win_export), encoding="utf-8")
        p_web.write_text(json.dumps(web_export), encoding="utf-8")
        p_afk.write_text(json.dumps(afk_export), encoding="utf-8")

        db_path = self.root / "import.db"
        res1 = import_aw.import_activitywatch_files([p_win, p_web, p_afk], db_path=db_path)
        self.assertEqual(res1["rows_inserted"], 4)
        self.assertAlmostEqual(res1["active_seconds"], 150.0, places=2)
        self.assertAlmostEqual(res1["afk_seconds"], 30.0, places=2)

        # Second run must be idempotent
        res2 = import_aw.import_activitywatch_files([p_win, p_web, p_afk], db_path=db_path)
        self.assertEqual(res2["rows_inserted"], 0)

        imported_db = ActivityDatabase(db_path)
        evs = imported_db.query_events(0, 9999999999)
        self.assertEqual([e.app_binary for e in evs], ["zen-bin", "wayland-app", "Just Cause 4", "afk"])
        self.assertFalse(evs[2].is_afk)
        self.assertTrue(evs[3].is_afk)

    def test_duration_formatting_and_setup_renderers(self) -> None:
        self.assertEqual(format_duration(0), "0m")
        self.assertEqual(format_duration(35), "< 1m")
        self.assertEqual(format_duration(2700), "45m")
        self.assertEqual(format_duration(3 * 3600 + 42 * 60), "3h 42m")

        unit = vw_setup.render_systemd_unit(self.root)
        desktop = vw_setup.render_desktop_entry(self.root)
        self.assertIn("plasma-kwin_wayland.service", unit)
        self.assertIn("daemon.py", unit)
        self.assertIn("StartupWMClass=VibeWatch", desktop)
        self.assertIn("dashboard.py", desktop)


if __name__ == "__main__":
    unittest.main()
