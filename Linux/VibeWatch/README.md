# VibeWatch

Lightweight, native Linux activity and screen-time tracker in Python and PyQt6, tailored specifically for **KDE Plasma 6 (Wayland)** with a zero-chrome desktop aesthetic.

## Two-Part Architecture

1. **`daemon.py` (Headless Background Watcher)** — Runs via `systemd --user`, polling active window and AFK/lock state every `2.0s` into `~/.local/share/vibewatch/activity.db` (`WAL` mode) with heartbeat merging (`pulsetime = 5.0s`).
   - **Tier 1 (Primary):** Native KWin Scripting D-Bus bridge (`com.naveen.VibeWatch.Receiver` + `org.kde.kwin.Scripting.loadScript` + `/proc/<pid>/comm` and `/proc/<pid>/cmdline` resolution — zero `"unknown"` Wayland entries).
   - **Tier 2:** `kdotool` CLI fallback.
   - **Tier 3:** X11 `xprop` / `_NET_ACTIVE_WINDOW` fallback.
   - **AFK & Lock:** Wayland `ext_idle_notifier_v1` (`>= 180_000 ms`) + `org.freedesktop.ScreenSaver.GetActive` (plus `GetSessionIdleTime` fallback on X11).
2. **`dashboard.py` (Native PyQt6 GUI Viewer)** — Instant-launch analytics viewer reading directly from SQLite, with a custom `QPainter` 24-hour timeline, smart app grouping with expandable `window_title` sub-lists, live-reloading `~/.config/vibewatch/categories.json`, Dark/Light themes (`Ctrl+Shift+D`), dynamic zoom (`Ctrl+=`/`-`/`0`), and frameless mode (`F11`).

## Quick Install (KDE Plasma 6)

```bash
cd Linux/VibeWatch
python3 setup.py
```

Or install directly from either script:

```bash
python3 daemon.py --install
```

This creates `.venv` (`PyQt6`, `dbus-next`, `psutil`), enables `~/.config/systemd/user/vibewatch.service`, and installs `~/.local/share/applications/vibewatch.desktop` (`StartupWMClass=VibeWatch`) for KRunner (`Alt+Space`).
