#!/usr/bin/env python3
"""
VibeWatch Headless Background Daemon (`daemon.py`)
Tailored for KDE Plasma 6 (Wayland) with a 3-tier Linux active-window collector,
org.freedesktop.ScreenSaver AFK/lock detection, and heartbeat-merged SQLite WAL writes.

Usage:
  python daemon.py              # Run headless 2.0s watcher loop
  python daemon.py --once       # Perform a single poll and print JSON
  python daemon.py --install    # Provision .venv, systemd --user service, and .desktop launcher
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import re
import shutil
import signal
import socket
import struct
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, List, Optional, Tuple

from vibewatch_core import (
    AFK_THRESHOLD_MS,
    DEFAULT_DB_PATH,
    HEARTBEAT_PULSETIME_SECONDS,
    POLL_INTERVAL_SECONDS,
    ActivityDatabase,
    CategoryEngine,
    ensure_venv,
    resolve_pid_binary,
    write_daemon_status,
)

# Self-heal into local .venv before importing optional third-party packages
ensure_venv(Path(__file__))

try:
    from dbus_next import Message, MessageType  # type: ignore
    from dbus_next.aio import MessageBus  # type: ignore
    from dbus_next.service import ServiceInterface, method  # type: ignore
    HAS_DBUS_NEXT = True
except ImportError:
    Message = None  # type: ignore
    MessageType = None  # type: ignore
    MessageBus = None  # type: ignore
    ServiceInterface = object  # type: ignore
    HAS_DBUS_NEXT = False

    def method():  # type: ignore
        def _decorator(fn):
            return fn
        return _decorator


DBUS_SERVICE_NAME = "com.naveen.VibeWatch.Receiver"
DBUS_OBJECT_PATH = "/Receiver"
DBUS_INTERFACE_NAME = "com.naveen.VibeWatch.Receiver"


def build_kwin_bridge_script(service_name: str) -> str:
    """Persistent KDE Plasma 6 KWin JS bridge pushing window/caption updates via callDBus."""
    svc = json.dumps(service_name)
    return f"""\
(function () {{
    var hookedWindows = {{}};
    function pushActiveWindow(win) {{
        var w = win || workspace.activeWindow;
        if (!w) {{
            callDBus(
                {svc},
                "/Receiver",
                "com.naveen.VibeWatch.Receiver",
                "ReportActiveWindow",
                "",
                "",
                0
            );
            return;
        }}
        var caption = w.caption ? String(w.caption) : "";
        var resClass = w.resourceClass ? String(w.resourceClass) : "";
        var pid = typeof w.pid === "number" ? w.pid : 0;
        callDBus(
            {svc},
            "/Receiver",
            "com.naveen.VibeWatch.Receiver",
            "ReportActiveWindow",
            caption,
            resClass,
            pid
        );
    }}

    function hookCaption(win) {{
        if (!win || !win.captionChanged) {{
            return;
        }}
        var key = String(win.internalId || "");
        if (!key) {{
            return;
        }}
        if (hookedWindows[key]) {{
            return;
        }}
        hookedWindows[key] = true;
        try {{
            win.captionChanged.connect(function () {{
                if (workspace.activeWindow === win) {{
                    pushActiveWindow(win);
                }}
            }});
            if (win.closed) {{
                win.closed.connect(function () {{
                    delete hookedWindows[key];
                }});
            }}
        }} catch (e) {{}}
    }}

    if (workspace.windowActivated) {{
        workspace.windowActivated.connect(function (win) {{
            pushActiveWindow(win);
            hookCaption(win);
        }});
    }}

    if (workspace.activeWindow) {{
        hookCaption(workspace.activeWindow);
    }}
    pushActiveWindow(workspace.activeWindow);
}})();
"""


def build_kwin_oneshot_script(service_name: str) -> str:
    """One-shot KWin query script pushing the current active window once."""
    svc = json.dumps(service_name)
    return f"""\
(function () {{
    var w = workspace.activeWindow;
    var caption = (w && w.caption) ? String(w.caption) : "";
    var resClass = (w && w.resourceClass) ? String(w.resourceClass) : "";
    var pid = (w && typeof w.pid === "number") ? w.pid : 0;
    callDBus(
        {svc},
        "/Receiver",
        "com.naveen.VibeWatch.Receiver",
        "ReportActiveWindow",
        caption,
        resClass,
        pid
    );
}})();
"""


@dataclass
class ActiveWindowSample:
    app_binary: str
    wm_class: str
    window_title: str
    pid: int
    backend: str


def _wl_str(text: str) -> bytes:
    raw = text.encode("utf-8") + b"\x00"
    pad = (-len(raw)) % 4
    return struct.pack("<I", len(raw)) + raw + (b"\x00" * pad)


def _wl_msg(obj_id: int, opcode: int, payload: bytes = b"") -> bytes:
    size = 8 + len(payload)
    return struct.pack("<IHH", obj_id, opcode, size) + payload


class WaylandIdleMonitor:
    """
    Native Wayland `ext_idle_notifier_v1` client for KDE Plasma 6 (kwin_wayland).
    KWin does not implement `org.freedesktop.ScreenSaver.GetSessionIdleTime` on
    Wayland, so we subscribe directly to `ext_idle_notifier_v1` on `$WAYLAND_DISPLAY`.
    """

    def __init__(self, timeout_ms: int = AFK_THRESHOLD_MS) -> None:
        self.timeout_ms = max(1000, int(timeout_ms))
        self.available: bool = False
        self.is_idle: bool = False
        self._idle_since: Optional[float] = None
        self._reader: Optional[asyncio.StreamReader] = None
        self._writer: Optional[asyncio.StreamWriter] = None
        self._task: Optional[asyncio.Task[None]] = None
        self._buf: bytearray = bytearray()

    def current_idle_ms(self) -> int:
        if not self.is_idle or self._idle_since is None:
            return 0
        elapsed_ms = int(max(0.0, time.monotonic() - self._idle_since) * 1000.0)
        return self.timeout_ms + elapsed_ms

    async def start(self) -> bool:
        if os.environ.get("XDG_SESSION_TYPE", "").lower() != "wayland":
            return False
        disp = os.environ.get("WAYLAND_DISPLAY")
        xdg = os.environ.get("XDG_RUNTIME_DIR")
        if not disp or not xdg:
            return False
        sock_path = disp if os.path.isabs(disp) else os.path.join(xdg, disp)
        if not os.path.exists(sock_path):
            return False

        try:
            self._reader, self._writer = await asyncio.open_unix_connection(sock_path)
            # wl_display(1).get_registry(new_id=2) + wl_display(1).sync(new_id=3)
            self._writer.write(
                _wl_msg(1, 1, struct.pack("<I", 2)) + _wl_msg(1, 0, struct.pack("<I", 3))
            )
            await self._writer.drain()

            seat_name: Optional[int] = None
            idle_name: Optional[int] = None
            sync_done = False
            deadline = time.monotonic() + 1.5
            while not sync_done and time.monotonic() < deadline:
                packets = await asyncio.wait_for(self._read_packets(), timeout=1.0)
                for oid, op, body in packets:
                    if oid == 2 and op == 0 and len(body) >= 12:
                        name, slen = struct.unpack_from("<II", body, 0)
                        if slen > 0 and 8 + slen <= len(body):
                            iface = body[8 : 8 + slen - 1].decode("utf-8", errors="ignore")
                            if iface == "wl_seat" and seat_name is None:
                                seat_name = name
                            elif iface == "ext_idle_notifier_v1" and idle_name is None:
                                idle_name = name
                    elif oid == 3 and op == 0:
                        sync_done = True

            if seat_name is None or idle_name is None:
                await self.stop()
                return False

            # Bind wl_seat -> 4, ext_idle_notifier_v1 -> 5, get_idle_notification(6, timeout, seat=4)
            m_seat = _wl_msg(2, 0, struct.pack("<I", seat_name) + _wl_str("wl_seat") + struct.pack("<II", 1, 4))
            m_idle = _wl_msg(
                2, 0, struct.pack("<I", idle_name) + _wl_str("ext_idle_notifier_v1") + struct.pack("<II", 1, 5)
            )
            m_notif = _wl_msg(5, 1, struct.pack("<III", 6, self.timeout_ms, 4))
            self._writer.write(m_seat + m_idle + m_notif)
            await self._writer.drain()

            self.available = True
            self._task = asyncio.create_task(self._listen_loop())
            return True
        except Exception:
            await self.stop()
            return False

    async def _read_packets(self) -> List[Tuple[int, int, bytes]]:
        assert self._reader is not None
        chunk = await self._reader.read(4096)
        if not chunk:
            raise EOFError("Wayland socket closed")
        self._buf.extend(chunk)
        packets: List[Tuple[int, int, bytes]] = []
        while len(self._buf) >= 8:
            oid, op, sz = struct.unpack_from("<IHH", self._buf, 0)
            if sz < 8 or len(self._buf) < sz:
                break
            body = bytes(self._buf[8:sz])
            del self._buf[:sz]
            packets.append((oid, op, body))
        return packets

    async def _listen_loop(self) -> None:
        try:
            while self._reader is not None:
                packets = await self._read_packets()
                for oid, op, _body in packets:
                    if oid == 6:
                        if op == 0:  # ext_idle_notification_v1.idled
                            self.is_idle = True
                            if self._idle_since is None:
                                self._idle_since = time.monotonic()
                        elif op == 1:  # ext_idle_notification_v1.resumed
                            self.is_idle = False
                            self._idle_since = None
        except asyncio.CancelledError:
            raise
        except Exception:
            self.available = False

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            try:
                await self._task
            except (asyncio.CancelledError, Exception):
                pass
            self._task = None
        if self._writer is not None:
            try:
                self._writer.close()
                await self._writer.wait_closed()
            except Exception:
                pass
            self._writer = None
        self._reader = None
        self.available = False


class KWinReceiverInterface(ServiceInterface):
    """Session D-Bus receiver registered as `com.naveen.VibeWatch.Receiver`."""

    def __init__(self) -> None:
        if HAS_DBUS_NEXT:
            super().__init__(DBUS_INTERFACE_NAME)
        self.caption: str = ""
        self.resource_class: str = ""
        self.pid: int = 0
        self.last_received_ts: float = 0.0

    @method()
    def ReportActiveWindow(self, caption: "s", resource_class: "s", pid: "i"):  # type: ignore[valid-type]
        self.caption = str(caption or "")
        self.resource_class = str(resource_class or "")
        self.pid = int(pid or 0)
        self.last_received_ts = time.monotonic()


class KWinDBusCollector:
    """
    Tier 1 — Native KDE Plasma 6 Wayland Collector using KWin Scripting D-Bus Bridge.
    Registers a D-Bus receiver on the session bus and loads a KWin script via
    explicit 2-arg `org.kde.kwin.Scripting.loadScript(ss)` + `start()`.
    """

    def __init__(self, once: bool = False) -> None:
        self.once = once
        self.service_name = f"{DBUS_SERVICE_NAME}.Probe{os.getpid()}" if once else DBUS_SERVICE_NAME
        self._bridge_plugin = "vibewatch_bridge"
        self._probe_plugin = f"vibewatch_probe_{os.getpid()}" if once else "vibewatch_probe"
        self.bus: Optional[Any] = None
        self.receiver: Optional[KWinReceiverInterface] = None
        self._persistent_script_path: Optional[Path] = None
        self._oneshot_script_path: Optional[Path] = None
        self._loaded_plugins: set[str] = set()
        self._available: bool = False

    async def _call_scripting(self, member: str, signature: str = "", body: Optional[List[Any]] = None) -> Any:
        if self.bus is None or Message is None:
            return None
        reply = await self.bus.call(
            Message(
                destination="org.kde.KWin",
                path="/Scripting",
                interface="org.kde.kwin.Scripting",
                member=member,
                signature=signature,
                body=body or [],
            )
        )
        if MessageType is not None and reply.message_type == MessageType.ERROR:
            raise RuntimeError(f"KWin Scripting {member} failed: {reply.body}")
        return reply.body[0] if reply.body else None

    async def start(self) -> bool:
        if not HAS_DBUS_NEXT:
            return False
        try:
            self.bus = await MessageBus().connect()
            self.receiver = KWinReceiverInterface()
            self.bus.export(DBUS_OBJECT_PATH, self.receiver)
            await self.bus.request_name(self.service_name)

            # Verify KWin Scripting service is reachable
            await self.bus.introspect("org.kde.KWin", "/Scripting")

            script_dir = Path(tempfile.gettempdir()) / f"vibewatch-{os.getuid()}"
            script_dir.mkdir(parents=True, exist_ok=True)
            if self.once:
                self._oneshot_script_path = script_dir / f"kwin_oneshot_{os.getpid()}.js"
                self._oneshot_script_path.write_text(
                    build_kwin_oneshot_script(self.service_name), encoding="utf-8"
                )
                loaded = await self._load_and_start(self._oneshot_script_path, self._probe_plugin)
            else:
                self._persistent_script_path = script_dir / "kwin_bridge.js"
                self._oneshot_script_path = script_dir / "kwin_oneshot.js"
                self._persistent_script_path.write_text(
                    build_kwin_bridge_script(self.service_name), encoding="utf-8"
                )
                self._oneshot_script_path.write_text(
                    build_kwin_oneshot_script(self.service_name), encoding="utf-8"
                )
                loaded = await self._load_and_start(self._persistent_script_path, self._bridge_plugin)

            if loaded:
                # Allow KWin a brief moment to execute the initial pushActiveWindow call
                for _ in range(10):
                    if self.receiver.last_received_ts > 0.0:
                        break
                    await asyncio.sleep(0.03)
            self._available = loaded
            return loaded
        except Exception as exc:
            print(f"[vibewatch] KWin D-Bus collector unavailable: {exc}", file=sys.stderr)
            self._available = False
            return False

    async def _load_and_start(self, script_path: Path, plugin_name: str) -> bool:
        if self.bus is None:
            return False
        try:
            try:
                await self._call_scripting("unloadScript", "s", [plugin_name])
            except Exception:
                pass
            script_id = await self._call_scripting("loadScript", "ss", [str(script_path), plugin_name])
            if isinstance(script_id, int) and script_id >= 0:
                await self._call_scripting("start")
                self._loaded_plugins.add(plugin_name)
                return True
        except Exception as exc:
            print(f"[vibewatch] Failed to load KWin script '{plugin_name}': {exc}", file=sys.stderr)
        return False

    async def poll(self) -> Optional[ActiveWindowSample]:
        if not self._available or self.receiver is None:
            return None

        # If no push received in the last 10s, ensure bridge is still loaded or run one-shot probe
        if not self.once and (time.monotonic() - self.receiver.last_received_ts > 10.0):
            bridge_loaded = False
            try:
                bridge_loaded = bool(await self._call_scripting("isScriptLoaded", "s", [self._bridge_plugin]))
            except Exception:
                bridge_loaded = False

            if not bridge_loaded and self._persistent_script_path is not None:
                await self._load_and_start(self._persistent_script_path, self._bridge_plugin)
                await asyncio.sleep(0.06)
            elif self._oneshot_script_path is not None:
                await self._load_and_start(self._oneshot_script_path, self._probe_plugin)
                await asyncio.sleep(0.06)

        if self.receiver.last_received_ts <= 0.0:
            return None

        app_bin = resolve_pid_binary(
            pid=self.receiver.pid,
            resource_class=self.receiver.resource_class,
            caption=self.receiver.caption,
        )
        return ActiveWindowSample(
            app_binary=app_bin,
            wm_class=self.receiver.resource_class or app_bin,
            window_title=self.receiver.caption,
            pid=self.receiver.pid,
            backend="kwin-dbus",
        )

    async def query_screen_locked(self) -> Optional[bool]:
        """Query `org.freedesktop.ScreenSaver.GetActive` independently of GetSessionIdleTime."""
        if self.bus is None:
            return None
        try:
            intro = await self.bus.introspect(
                "org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver"
            )
            proxy = self.bus.get_proxy_object(
                "org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver", intro
            )
            ss = proxy.get_interface("org.freedesktop.ScreenSaver")
            return bool(await ss.call_get_active())
        except Exception:
            return None

    async def query_session_idle_ms(self) -> Optional[int]:
        """Query `GetSessionIdleTime` when supported (X11/non-KDE; raises NotSupported on KWin Wayland)."""
        if self.bus is None:
            return None
        try:
            intro = await self.bus.introspect(
                "org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver"
            )
            proxy = self.bus.get_proxy_object(
                "org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver", intro
            )
            ss = proxy.get_interface("org.freedesktop.ScreenSaver")
            return int(await ss.call_get_session_idle_time())
        except Exception:
            return None

    async def stop(self) -> None:
        if self.bus is not None:
            for name in list(self._loaded_plugins):
                try:
                    await self._call_scripting("unloadScript", "s", [name])
                except Exception:
                    pass
            self._loaded_plugins.clear()
            if self.once and self._oneshot_script_path is not None:
                try:
                    self._oneshot_script_path.unlink(missing_ok=True)
                except OSError:
                    pass
            try:
                self.bus.disconnect()
            except Exception:
                pass


def collect_via_kdotool() -> Optional[ActiveWindowSample]:
    """Tier 2 — `kdotool` CLI fallback when D-Bus receiver is unavailable."""
    if not shutil.which("kdotool"):
        return None
    try:
        win_id = subprocess.check_output(
            ["kdotool", "getactivewindow"], text=True, stderr=subprocess.DEVNULL, timeout=1.5
        ).strip()
        if not win_id:
            return None
        title = subprocess.check_output(
            ["kdotool", "getwindowname", win_id], text=True, stderr=subprocess.DEVNULL, timeout=1.5
        ).strip()
        wm_class = subprocess.check_output(
            ["kdotool", "getwindowclassname", win_id], text=True, stderr=subprocess.DEVNULL, timeout=1.5
        ).strip()
        pid_raw = subprocess.check_output(
            ["kdotool", "getwindowpid", win_id], text=True, stderr=subprocess.DEVNULL, timeout=1.5
        ).strip()
        pid = int(pid_raw) if pid_raw.isdigit() else 0
        app_bin = resolve_pid_binary(pid=pid, resource_class=wm_class, caption=title)
        return ActiveWindowSample(
            app_binary=app_bin,
            wm_class=wm_class or app_bin,
            window_title=title,
            pid=pid,
            backend="kdotool",
        )
    except Exception:
        return None


def collect_via_xprop() -> Optional[ActiveWindowSample]:
    """Tier 3 — X11 fallback using `xprop` / `_NET_ACTIVE_WINDOW`."""
    if os.environ.get("XDG_SESSION_TYPE", "").lower() != "x11":
        return None
    if not shutil.which("xprop"):
        return None
    try:
        root_out = subprocess.check_output(
            ["xprop", "-root", "_NET_ACTIVE_WINDOW"], text=True, stderr=subprocess.DEVNULL, timeout=1.5
        )
        m = re.search(r"window id #\s*(0x[0-9a-fA-F]+)", root_out)
        if not m:
            return None
        wid = m.group(1)
        win_out = subprocess.check_output(
            ["xprop", "-id", wid, "WM_CLASS", "_NET_WM_NAME", "WM_NAME", "_NET_WM_PID"],
            text=True,
            stderr=subprocess.DEVNULL,
            timeout=1.5,
        )
        class_match = re.search(r'WM_CLASS\(STRING\)\s*=\s*"[^"]*",\s*"([^"]+)"', win_out)
        title_match = re.search(r'(?:_NET_WM_NAME|WM_NAME)\([^)]+\)\s*=\s*"([^"]*)"', win_out)
        pid_match = re.search(r"_NET_WM_PID\(CARDINAL\)\s*=\s*(\d+)", win_out)

        wm_class = class_match.group(1) if class_match else ""
        title = title_match.group(1) if title_match else ""
        pid = int(pid_match.group(1)) if pid_match else 0
        app_bin = resolve_pid_binary(pid=pid, resource_class=wm_class, caption=title)
        return ActiveWindowSample(
            app_binary=app_bin,
            wm_class=wm_class or app_bin,
            window_title=title,
            pid=pid,
            backend="x11-xprop",
        )
    except Exception:
        return None


def query_afk_cli_fallback() -> Tuple[bool, int]:
    """CLI fallback for `org.freedesktop.ScreenSaver`, checking GetActive independently of GetSessionIdleTime."""
    qdbus = shutil.which("qdbus6") or shutil.which("qdbus")
    if not qdbus:
        return (False, 0)

    locked = False
    idle_ms = 0
    try:
        active_raw = subprocess.check_output(
            [qdbus, "org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver", "GetActive"],
            text=True,
            stderr=subprocess.DEVNULL,
            timeout=1.5,
        ).strip().lower()
        locked = active_raw == "true"
    except Exception:
        pass

    try:
        idle_raw = subprocess.check_output(
            [qdbus, "org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver", "GetSessionIdleTime"],
            text=True,
            stderr=subprocess.DEVNULL,
            timeout=1.5,
        ).strip()
        if idle_raw.isdigit():
            idle_ms = int(idle_raw)
    except Exception:
        pass

    return (locked or idle_ms >= AFK_THRESHOLD_MS, idle_ms)


class VibeWatchDaemon:
    """Orchestrates multi-tier Wayland/X11 polling, AFK detection, and SQLite heartbeats."""

    def __init__(self, db_path: Path = DEFAULT_DB_PATH, once: bool = False) -> None:
        self.db = ActivityDatabase(db_path=db_path)
        self.categories = CategoryEngine()
        self.once = once
        self.kwin = KWinDBusCollector(once=once)
        self.wayland_idle = WaylandIdleMonitor(timeout_ms=AFK_THRESHOLD_MS)
        self._running = True
        self._screensaver_idle_supported: Optional[bool] = None

    async def poll_once(self) -> Tuple[Optional[ActiveWindowSample], bool, int]:
        sample = await self.kwin.poll()
        if sample is None:
            sample = collect_via_kdotool()
        if sample is None:
            sample = collect_via_xprop()

        locked = await self.kwin.query_screen_locked()
        if locked is None:
            cli_afk, cli_idle = query_afk_cli_fallback()
            locked = cli_afk
            idle_ms = self.wayland_idle.current_idle_ms() if self.wayland_idle.available else cli_idle
        else:
            if self.wayland_idle.available:
                idle_ms = self.wayland_idle.current_idle_ms()
            elif self._screensaver_idle_supported is not False:
                ss_idle = await self.kwin.query_session_idle_ms()
                if ss_idle is None:
                    self._screensaver_idle_supported = False
                    idle_ms = 0
                else:
                    self._screensaver_idle_supported = True
                    idle_ms = ss_idle
            else:
                idle_ms = 0

        is_afk = bool(locked or self.wayland_idle.is_idle or idle_ms >= AFK_THRESHOLD_MS)
        return (sample, is_afk, idle_ms)

    async def run(self, once: Optional[bool] = None) -> None:
        is_once = self.once if once is None else once
        await self.kwin.start()
        if not is_once:
            await self.wayland_idle.start()

        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            try:
                loop.add_signal_handler(sig, self.request_stop)
            except NotImplementedError:
                pass

        try:
            while self._running:
                sample, is_afk, idle_ms = await self.poll_once()
                now = time.time()

                if is_afk:
                    app_bin = "afk"
                    wm_class = "org.freedesktop.ScreenSaver"
                    title = "Screen Locked / Away"
                    backend = sample.backend if sample else "screensaver"
                elif sample is not None:
                    app_bin = sample.app_binary
                    wm_class = sample.wm_class
                    title = sample.window_title
                    backend = sample.backend
                else:
                    app_bin = "desktop"
                    wm_class = "plasmashell"
                    title = "KDE Plasma Desktop"
                    backend = "fallback"

                action, row_id = self.db.record_heartbeat(
                    now=now,
                    app_binary=app_bin,
                    wm_class=wm_class,
                    window_title=title,
                    is_afk=is_afk,
                    pulsetime=HEARTBEAT_PULSETIME_SECONDS,
                )

                if not is_once:
                    write_daemon_status(
                        state="running",
                        active_app="AFK" if is_afk else app_bin,
                        backend=backend,
                        is_afk=is_afk,
                    )

                if is_once:
                    print(
                        json.dumps(
                            {
                                "action": action,
                                "row_id": row_id,
                                "app_binary": app_bin,
                                "wm_class": wm_class,
                                "window_title": title,
                                "is_afk": is_afk,
                                "idle_ms": idle_ms,
                                "backend": backend,
                            },
                            indent=2,
                        )
                    )
                    break

                await asyncio.sleep(POLL_INTERVAL_SECONDS)
        finally:
            if not is_once:
                write_daemon_status(state="stopped", active_app="none", backend="none", is_afk=False)
            await self.wayland_idle.stop()
            await self.kwin.stop()

    def request_stop(self) -> None:
        self._running = False


def main() -> None:
    parser = argparse.ArgumentParser(
        description="VibeWatch headless activity & screen-time daemon for KDE Plasma 6 (Wayland)."
    )
    parser.add_argument("--once", action="store_true", help="Poll once, write heartbeat, and exit.")
    parser.add_argument("--install", action="store_true", help="Install systemd --user service & .desktop file.")
    parser.add_argument("--db", type=Path, default=DEFAULT_DB_PATH, help="Custom SQLite database path.")
    args = parser.parse_args()

    if args.install:
        import setup as vw_setup
        vw_setup.install_all()
        return

    daemon = VibeWatchDaemon(db_path=args.db, once=args.once)
    asyncio.run(daemon.run(once=args.once))


if __name__ == "__main__":
    main()
