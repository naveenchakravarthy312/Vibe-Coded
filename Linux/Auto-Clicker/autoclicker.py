#!/usr/bin/env python3
"""
Auto Clicker — Ultra-minimalist, zero-chrome GUI autoclicker for Linux (Wayland & X11).

Design Aesthetic:
- Matte Dark Canvas (#141414) with zero borders, dividers, or bevels
- Soft Off-White Foreground (#e0e0e0)
- Muted Slate Selection (#323842 with #ffffff text)
- Whisper-Quiet Metadata (#585858) in the bottom-right corner
- 24px Viewport Padding around all four edges
- Optional Frameless Mode (F11 / Ctrl+Shift+F) with 24px margin drag & edge resizing
- Modern Monospace Stack (JetBrains Mono, Fira Code, Cascadia Code, Iosevka, Source Code Pro)
  at 13pt default size, scalable on the fly (Ctrl+= / Ctrl+- / Ctrl+Wheel)
- Crisp 2px-wide blinking cursor (1000ms cycle) and plain-text-only numeric pasting
"""

import ctypes
import ctypes.util

import os
import random
import shutil
import subprocess
import threading
import time
import tkinter as tk
import tkinter.font as tkfont

# ---------------------------------------------------------------------------
# Paths & Constants
# ---------------------------------------------------------------------------
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ICON_PATH = os.path.join(BASE_DIR, "icon.png")

# Ensure the placeholder icon file exists in the project folder
if not os.path.exists(ICON_PATH):
    try:
        with open(ICON_PATH, "wb"):
            pass
    except OSError:
        pass

# Linux evdev constants
EV_SYN = 0x00
EV_KEY = 0x01
EV_REL = 0x02
SYN_REPORT = 0x00
REL_X = 0x00
REL_Y = 0x01

BTN_LEFT = 0x110
BTN_RIGHT = 0x111
BTN_MIDDLE = 0x112

BUTTON_MAP = {
    "Left": BTN_LEFT,
    "Right": BTN_RIGHT,
    "Middle": BTN_MIDDLE,
}

# Standard Linux input event keycodes for function keys F1-F10
KEYCODE_MAP = {
    "F1": 59,
    "F2": 60,
    "F3": 61,
    "F4": 62,
    "F5": 63,
    "F6": 64,
    "F7": 65,
    "F8": 66,
    "F9": 67,
    "F10": 68,
}

HOTKEY_CYCLE = ["F6", "F8", "F7", "F9", "F10"]
BUTTON_CYCLE = ["Left", "Right", "Middle"]
TYPE_CYCLE = ["Single", "Double"]

# ---------------------------------------------------------------------------
# Color Palette & Surface Constants
# ---------------------------------------------------------------------------
PALETTE_DARK = {
    "canvas": "#141414",       # Matte Dark Canvas (100% flat charcoal, zero bevels/borders)
    "fg": "#e0e0e0",           # Soft Off-White Foreground
    "sel_bg": "#323842",       # Muted Slate Selection background
    "sel_fg": "#ffffff",       # Muted Slate Selection text
    "meta": "#585858",         # Whisper-Quiet Metadata (bottom-right counter & subtle labels)
    "active": "#e0e0e0",       # Crisp high-legibility active state
}

PALETTE_LIGHT = {
    "canvas": "#f2f2f0",
    "fg": "#1e1e1e",
    "sel_bg": "#323842",
    "sel_fg": "#ffffff",
    "meta": "#888888",
    "active": "#141414",
}

MONOSPACE_CANDIDATES = [
    "JetBrains Mono",
    "Fira Code",
    "Cascadia Code",
    "Iosevka",
    "Source Code Pro",
    "DejaVu Sans Mono",
    "Liberation Mono",
    "Ubuntu Mono",
    "monospace",
]

VIEWPORT_MARGIN = 24
RESIZE_EDGE_MARGIN = 8


# ---------------------------------------------------------------------------
# Wayland / Linux Clicking & Global Hotkey Backends
# ---------------------------------------------------------------------------
class ClickerBackend:
    """
    Multi-layered Linux click backend prioritizing Wayland-native methods:
    1. Kernel uinput via python3-evdev (fastest, microsecond precision, native Wayland & X11)
    2. XDG Desktop Portal RemoteDesktop (org.freedesktop.portal.RemoteDesktop via GI/Gio)
    3. CLI fallbacks (ydotool / dotool / xdotool)
    4. X11 ctypes libXtst fallback (for XWayland apps if nothing else is permitted)
    """

    def __init__(self):
        self.method = "init"
        self.uinput_dev = None
        self.portal_proxy = None
        self.portal_session = None
        self._x11_display = None
        self._x11_lib = None
        self._xtst_lib = None
        self._lock = threading.Lock()
        self._init_backends()

    def _init_backends(self):
        # 1. Try python-evdev UInput (Works natively on Wayland & X11)
        try:
            from evdev import UInput, ecodes

            cap = {
                ecodes.EV_KEY: [BTN_LEFT, BTN_RIGHT, BTN_MIDDLE],
                ecodes.EV_REL: [REL_X, REL_Y],
            }
            self.uinput_dev = UInput(cap, name="AutoClicker-Virtual-Mouse")
            self.method = "uinput"
            return
        except Exception:
            self.uinput_dev = None

        # 2. Check for CLI Wayland/X11 tools
        if shutil.which("ydotool"):
            self.method = "ydotool"
            return
        if shutil.which("dotool"):
            self.method = "dotool"
            return
        if shutil.which("xdotool"):
            self.method = "xdotool"
            return

        # 3. Try X11 libXtst via ctypes (works on X11 and XWayland windows)
        try:
            x11_path = ctypes.util.find_library("X11")
            xtst_path = ctypes.util.find_library("Xtst")
            if x11_path and xtst_path:
                self._x11_lib = ctypes.cdll.LoadLibrary(x11_path)
                self._xtst_lib = ctypes.cdll.LoadLibrary(xtst_path)
                self._x11_lib.XOpenDisplay.restype = ctypes.c_void_p
                self._x11_lib.XOpenDisplay.argtypes = [ctypes.c_char_p]
                self._x11_display = self._x11_lib.XOpenDisplay(None)
                if self._x11_display:
                    self._xtst_lib.XTestFakeButtonEvent.argtypes = [
                        ctypes.c_void_p,
                        ctypes.c_uint,
                        ctypes.c_int,
                        ctypes.c_ulong,
                    ]
                    self._x11_lib.XFlush.argtypes = [ctypes.c_void_p]
                    self.method = "xtest"
                    return
        except Exception:
            self._x11_display = None

        self.method = "portal"

    def setup_wayland_portal_session(self, on_ready_callback=None):
        """
        Initializes an XDG Desktop Portal RemoteDesktop session on KDE Wayland
        if uinput wasn't directly accessible without root/input group.
        """
        if self.uinput_dev is not None:
            return

        def _worker():
            try:
                import gi

                gi.require_version("Gio", "2.0")
                from gi.repository import Gio, GLib

                bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
                portal = Gio.DBusProxy.new_sync(
                    bus,
                    Gio.DBusProxyFlags.NONE,
                    None,
                    "org.freedesktop.portal.Desktop",
                    "/org/freedesktop/portal/desktop",
                    "org.freedesktop.portal.RemoteDesktop",
                    None,
                )
                if not portal:
                    return

                session_token = f"autoclicker_sess_{random.randint(1000, 9999)}"
                sender = bus.get_unique_name().lstrip(":").replace(".", "_")

                def call_portal(method_name, params):
                    req_tok = f"req_{random.randint(10000, 99999)}"
                    req_path = f"/org/freedesktop/portal/desktop/request/{sender}/{req_tok}"
                    event = threading.Event()
                    result_holder = {}

                    def on_response(conn, sender_name, obj_path, iface, sig, res_params):
                        resp_code, results = res_params.unpack()
                        result_holder["code"] = resp_code
                        result_holder["results"] = results
                        event.set()

                    sub_id = bus.signal_subscribe(
                        "org.freedesktop.portal.Desktop",
                        "org.freedesktop.portal.Request",
                        "Response",
                        req_path,
                        None,
                        Gio.DBusSignalFlags.NO_MATCH_RULE,
                        on_response,
                    )
                    try:
                        unpacked = list(params)
                        unpacked[-1]["handle_token"] = GLib.Variant("s", req_tok)
                        portal.call_sync(
                            method_name,
                            unpacked[0](*unpacked[1:]),
                            Gio.DBusCallFlags.NONE,
                            -1,
                            None,
                        )
                        start_wait = time.monotonic()
                        ctx = GLib.MainContext.default()
                        while not event.is_set() and (time.monotonic() - start_wait) < 30.0:
                            while ctx.pending():
                                ctx.iteration(False)
                            time.sleep(0.02)
                    finally:
                        bus.signal_unsubscribe(sub_id)
                    return result_holder

                res = call_portal(
                    "CreateSession",
                    [
                        lambda opts: GLib.Variant("(a{sv})", (opts,)),
                        {"session_handle_token": GLib.Variant("s", session_token)},
                    ],
                )
                if res.get("code") != 0:
                    return
                sess_handle = res["results"].get("session_handle")
                if not sess_handle:
                    return

                res = call_portal(
                    "SelectDevices",
                    [
                        lambda sh, opts: GLib.Variant("(oa{sv})", (sh, opts)),
                        sess_handle,
                        {"types": GLib.Variant("u", 2)},
                    ],
                )
                if res.get("code") != 0:
                    return

                res = call_portal(
                    "Start",
                    [
                        lambda sh, pw, opts: GLib.Variant("(osa{sv})", (sh, pw, opts)),
                        sess_handle,
                        "",
                        {},
                    ],
                )
                if res.get("code") == 0:
                    self.portal_proxy = portal
                    self.portal_session = sess_handle
                    self.method = "portal"
                    if on_ready_callback:
                        on_ready_callback(self.method)
            except Exception:
                pass

        threading.Thread(target=_worker, daemon=True).start()

    def perform_click(self, button_name: str = "Left", double: bool = False):
        """Performs a single or double mouse click using the active backend."""
        count = 2 if double else 1
        btn_code = BUTTON_MAP.get(button_name, BTN_LEFT)

        for i in range(count):
            self._single_click(button_name, btn_code)
            if double and i == 0:
                time.sleep(0.015)

    def _single_click(self, button_name: str, btn_code: int):
        # 1. evdev UInput
        if self.uinput_dev is not None:
            with self._lock:
                self.uinput_dev.write(EV_KEY, btn_code, 1)
                self.uinput_dev.write(EV_SYN, SYN_REPORT, 0)
                self.uinput_dev.write(EV_KEY, btn_code, 0)
                self.uinput_dev.write(EV_SYN, SYN_REPORT, 0)
            return

        # 2. XDG Portal RemoteDesktop
        if self.portal_proxy is not None and self.portal_session is not None:
            try:
                from gi.repository import Gio, GLib

                with self._lock:
                    self.portal_proxy.call_sync(
                        "NotifyPointerButton",
                        GLib.Variant("(oa{sv}iu)", (self.portal_session, {}, btn_code, 1)),
                        Gio.DBusCallFlags.NONE,
                        -1,
                        None,
                    )
                    self.portal_proxy.call_sync(
                        "NotifyPointerButton",
                        GLib.Variant("(oa{sv}iu)", (self.portal_session, {}, btn_code, 0)),
                        Gio.DBusCallFlags.NONE,
                        -1,
                        None,
                    )
                return
            except Exception:
                pass

        # 3. CLI tools
        if self.method == "ydotool":
            ydo_btn = {"Left": "0xC0", "Right": "0xC1", "Middle": "0xC2"}.get(button_name, "0xC0")
            subprocess.run(["ydotool", "click", ydo_btn], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return

        if self.method == "dotool":
            do_btn = {"Left": "left", "Right": "right", "Middle": "middle"}.get(button_name, "left")
            subprocess.run(
                ["dotool"],
                input=f"click {do_btn}\n".encode(),
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            return

        if self.method == "xdotool":
            xdo_btn = {"Left": "1", "Right": "3", "Middle": "2"}.get(button_name, "1")
            subprocess.run(["xdotool", "click", xdo_btn], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return

        # 4. libXtst
        if self._x11_display:
            x_btn = {"Left": 1, "Middle": 2, "Right": 3}.get(button_name, 1)
            with self._lock:
                self._xtst_lib.XTestFakeButtonEvent(self._x11_display, x_btn, 1, 0)
                self._xtst_lib.XTestFakeButtonEvent(self._x11_display, x_btn, 0, 0)
                self._x11_lib.XFlush(self._x11_display)

    def close(self):
        if self.uinput_dev is not None:
            try:
                self.uinput_dev.close()
            except Exception:
                pass


class GlobalHotkeyListener:
    """
    Listens for global hotkeys on Linux (Wayland & X11):
    1. Kernel evdev input devices (/dev/input/event*) — works natively across all Wayland compositors
       even when minimized or unfocused.
    2. X11/XWayland XQueryKeymap polling fallback.
    """

    def __init__(self, hotkey_name: str, toggle_callback):
        self.hotkey_name = hotkey_name.upper()
        self.toggle_callback = toggle_callback
        self._stop_event = threading.Event()
        self._last_trigger = 0.0
        self.active_mode = "local"
        self._thread = threading.Thread(target=self._listen_loop, daemon=True)
        self._thread.start()

    def set_hotkey(self, hotkey_name: str):
        self.hotkey_name = hotkey_name.upper()

    def trigger_debounced(self):
        now = time.monotonic()
        if now - self._last_trigger > 0.25:
            self._last_trigger = now
            self.toggle_callback()

    def _listen_loop(self):
        # Attempt 1: Linux evdev /dev/input/event* monitoring (works everywhere on Wayland)
        try:
            import evdev
            import select

            devices = []
            for path in glob.glob("/dev/input/event*"):
                try:
                    dev = evdev.InputDevice(path)
                    caps = dev.capabilities()
                    if EV_KEY in caps:
                        key_caps = caps[EV_KEY]
                        if KEYCODE_MAP["F6"] in key_caps or KEYCODE_MAP["F8"] in key_caps:
                            devices.append(dev)
                        else:
                            dev.close()
                    else:
                        dev.close()
                except Exception:
                    pass

            if devices:
                self.active_mode = "evdev"
                fd_to_dev = {dev.fd: dev for dev in devices}
                while not self._stop_event.is_set():
                    r, _, _ = select.select(list(fd_to_dev.keys()), [], [], 0.2)
                    for fd in r:
                        dev = fd_to_dev.get(fd)
                        if not dev:
                            continue
                        try:
                            for event in dev.read():
                                if event.type == EV_KEY and event.value == 1:
                                    target_code = KEYCODE_MAP.get(self.hotkey_name, 64)
                                    if event.code == target_code:
                                        self.trigger_debounced()
                        except OSError:
                            fd_to_dev.pop(fd, None)
                for dev in devices:
                    try:
                        dev.close()
                    except Exception:
                        pass
                return
        except Exception:
            pass

        # Attempt 2: X11 XQueryKeymap polling
        try:
            x11_path = ctypes.util.find_library("X11")
            if x11_path:
                x11 = ctypes.cdll.LoadLibrary(x11_path)
                x11.XOpenDisplay.restype = ctypes.c_void_p
                x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
                x11.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
                x11.XKeysymToKeycode.restype = ctypes.c_ubyte
                x11.XQueryKeymap.argtypes = [ctypes.c_void_p, ctypes.c_char * 32]

                disp = x11.XOpenDisplay(None)
                if disp:
                    self.active_mode = "xkeymap"
                    xk_map = {f"F{i}": 0xFFBE + (i - 1) for i in range(1, 11)}
                    keymap = (ctypes.c_char * 32)()
                    was_pressed = False
                    while not self._stop_event.is_set():
                        ks = xk_map.get(self.hotkey_name, 0xFFC3)
                        kc = x11.XKeysymToKeycode(disp, ks)
                        if kc:
                            x11.XQueryKeymap(disp, keymap)
                            byte_idx = kc >> 3
                            bit_mask = 1 << (kc & 7)
                            is_pressed = bool(ord(keymap[byte_idx]) & bit_mask)
                            if is_pressed and not was_pressed:
                                self.trigger_debounced()
                            was_pressed = is_pressed
                        time.sleep(0.025)
                    return
        except Exception:
            pass

    def stop(self):
        self._stop_event.set()


# ---------------------------------------------------------------------------
# Ultra-Minimalist Matte Dark GUI Application
# ---------------------------------------------------------------------------
class AutoClickerApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("Auto Clicker")
        self.root.geometry("500x400")
        self.root.minsize(380, 310)

        self._load_window_icon()

        # Runtime State
        self.is_running = False
        self.click_count = 0
        self.is_dark = True
        self.is_frameless = False
        self._stop_clicking_event = threading.Event()
        self._click_thread = None

        # Drag / Resize State (for Frameless Mode & Margin Dragging)
        self._drag_mode = None  # "move" or resize edge string ("n", "s", "e", "w", "ne", "nw", "se", "sw")
        self._drag_start_x = 0
        self._drag_start_y = 0
        self._win_start_x = 0
        self._win_start_y = 0
        self._win_start_w = 0
        self._win_start_h = 0

        # Typography State (13pt default, scalable via Ctrl+= / Ctrl+- / Ctrl+Wheel)
        self.font_family = self._select_monospace_font()
        self.font_size = 13

        # Configuration Variables
        self.speed_mode = "cps"        # "cps" or "ms"
        self.cps_var = tk.StringVar(value="10")
        self.ms_var = tk.StringVar(value="100")
        self.jitter_enabled = False
        self.jitter_ms_var = tk.StringVar(value="5")
        self.button_name = "Left"      # "Left", "Right", "Middle"
        self.click_type = "Single"     # "Single", "Double"
        self.repeat_mode = "infinite"  # "infinite" or "fixed"
        self.repeat_count_var = tk.StringVar(value="100")
        self.hotkey_name = "F6"

        # Backends
        self.backend = ClickerBackend()
        self.hotkey_listener = GlobalHotkeyListener(
            self.hotkey_name,
            lambda: self.root.after(0, self.toggle_clicking),
        )

        # Build Zero-Chrome Interface
        self._init_fonts()
        self._build_ui()
        self._apply_palette()
        self._bind_shortcuts_and_window_events()

        if self.backend.uinput_dev is None and os.environ.get("XDG_SESSION_TYPE") == "wayland":
            self.backend.setup_wayland_portal_session(
                on_ready_callback=lambda _m: self.root.after(0, self._refresh_metadata)
            )

        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def _load_window_icon(self):
        if os.path.exists(ICON_PATH) and os.path.getsize(ICON_PATH) > 0:
            try:
                icon_img = tk.PhotoImage(file=ICON_PATH)
                self.root.iconphoto(True, icon_img)
                self._icon_ref = icon_img
            except Exception:
                pass

    def _select_monospace_font(self) -> str:
        try:
            available = {f.lower(): f for f in tkfont.families(self.root)}
            for candidate in MONOSPACE_CANDIDATES:
                if candidate.lower() in available:
                    return available[candidate.lower()]
        except Exception:
            pass
        return "monospace"

    def _init_fonts(self):
        self.main_font = tkfont.Font(family=self.font_family, size=self.font_size, weight="normal")
        self.bold_font = tkfont.Font(family=self.font_family, size=self.font_size, weight="bold")
        self.meta_font = tkfont.Font(family=self.font_family, size=max(8, self.font_size - 2), weight="normal")

    # -----------------------------------------------------------------------
    # Input Validation & Plain-Text-Only Pasting (insertFromMimeData behavior)
    # -----------------------------------------------------------------------
    def _validate_digits_only(self, proposed_value: str) -> bool:
        """Prevents negative numbers, spaces, or non-digits while allowing empty string during typing."""
        if proposed_value == "":
            return True
        return proposed_value.isdigit()

    def _on_plain_text_paste(self, event):
        """
        Plain-text-only clipboard pasting (insertFromMimeData equivalent):
        Strips rich formatting, whitespace, and non-digit characters so clipboard
        contents never pollute or break numeric fields.
        """
        widget = event.widget
        try:
            raw_clip = self.root.clipboard_get()
        except tk.TclError:
            return "break"

        clean_digits = "".join(ch for ch in raw_clip if ch.isdigit())
        if not clean_digits:
            return "break"

        try:
            if widget.selection_present():
                widget.delete(tk.SEL_FIRST, tk.SEL_LAST)
        except tk.TclError:
            pass

        widget.insert(tk.INSERT, clean_digits)
        return "break"

    def _get_safe_int(self, var: tk.StringVar, default: int, min_val: int, max_val: int) -> int:
        raw = var.get().strip()
        if not raw or not raw.isdigit():
            var.set(str(default))
            return default
        val = int(raw)
        clamped = max(min_val, min(max_val, val))
        if clamped != val:
            var.set(str(clamped))
        return clamped

    # -----------------------------------------------------------------------
    # UI Construction (Zero Chrome + 24px Viewport Padding + Crisp Caret)
    # -----------------------------------------------------------------------
    def _build_ui(self):
        vcmd = (self.root.register(self._validate_digits_only), "%P")

        # Outer Window Canvas — 24px Viewport Margin around all four edges
        self.viewport = tk.Frame(
            self.root,
            bd=0,
            highlightthickness=0,
            padx=VIEWPORT_MARGIN,
            pady=VIEWPORT_MARGIN,
        )
        self.viewport.pack(fill=tk.BOTH, expand=True)

        # All interactive rows live inside the flat content surface
        self.rows_container = tk.Frame(self.viewport, bd=0, highlightthickness=0)
        self.rows_container.pack(fill=tk.BOTH, expand=True, anchor="nw")

        self.key_labels = []
        self.value_labels = []
        self.entries = []

        # Row 1: Speed Mode (CPS vs Interval ms)
        r_mode = self._make_row()
        self.lbl_mode_key = self._make_key(r_mode, "timing")
        self.lbl_mode_val = self._make_interactive_val(
            r_mode,
            text="cps (1-1000)",
            command=self._cycle_speed_mode,
        )

        # Row 2: Speed Value Input (CPS or ms depending on mode)
        r_speed = self._make_row()
        self.lbl_speed_key = self._make_key(r_speed, "rate (cps)")
        self.ent_speed = self._make_entry(r_speed, self.cps_var, vcmd)

        # Row 3: Randomization / Jitter Toggle & Value
        r_jitter = self._make_row()
        self.lbl_jitter_key = self._make_key(r_jitter, "jitter (±ms)")
        jitter_right = tk.Frame(r_jitter, bd=0, highlightthickness=0)
        jitter_right.pack(side=tk.LEFT)
        self._jitter_frame = jitter_right

        self.lbl_jitter_toggle = self._make_interactive_val(
            jitter_right,
            text="off",
            command=self._toggle_jitter,
            pack_side=tk.LEFT,
        )
        self.lbl_jitter_sep = tk.Label(jitter_right, text="  ", bd=0, highlightthickness=0, font=self.main_font)
        self.lbl_jitter_sep.pack(side=tk.LEFT)
        self.ent_jitter = self._make_entry(jitter_right, self.jitter_ms_var, vcmd, width=6)

        # Row 4: Mouse Button (Left / Right / Middle)
        r_btn = self._make_row()
        self.lbl_btn_key = self._make_key(r_btn, "button")
        self.lbl_btn_val = self._make_interactive_val(
            r_btn,
            text="left",
            command=self._cycle_button,
        )

        # Row 5: Click Type (Single / Double)
        r_type = self._make_row()
        self.lbl_type_key = self._make_key(r_type, "click")
        self.lbl_type_val = self._make_interactive_val(
            r_type,
            text="single",
            command=self._cycle_click_type,
        )

        # Row 6: Repeat Mode & Fixed Count Input
        r_rep = self._make_row()
        self.lbl_rep_key = self._make_key(r_rep, "repeat")
        rep_right = tk.Frame(r_rep, bd=0, highlightthickness=0)
        rep_right.pack(side=tk.LEFT)
        self._rep_frame = rep_right

        self.lbl_rep_toggle = self._make_interactive_val(
            rep_right,
            text="infinite",
            command=self._toggle_repeat_mode,
            pack_side=tk.LEFT,
        )
        self.lbl_rep_sep = tk.Label(rep_right, text="  ", bd=0, highlightthickness=0, font=self.main_font)
        self.lbl_rep_sep.pack(side=tk.LEFT)
        self.ent_rep = self._make_entry(rep_right, self.repeat_count_var, vcmd, width=7)

        # Row 7: Global Toggle Hotkey (F6 / F8 / ...)
        r_hk = self._make_row()
        self.lbl_hk_key = self._make_key(r_hk, "hotkey")
        self.lbl_hk_val = self._make_interactive_val(
            r_hk,
            text="F6",
            command=self._cycle_hotkey,
        )

        # Row 8: Surface Theme Option (Matte Dark #141414 default / Soft Light)
        r_surface = self._make_row()
        self.lbl_surface_key = self._make_key(r_surface, "surface")
        self.lbl_surface_val = self._make_interactive_val(
            r_surface,
            text="matte dark",
            command=self._toggle_surface_theme,
        )

        # Bottom-Right Whisper-Quiet Metadata (#585858)
        self.bottom_bar = tk.Frame(self.viewport, bd=0, highlightthickness=0)
        self.bottom_bar.pack(side=tk.BOTTOM, fill=tk.X, pady=(12, 0))

        self.meta_counter_label = tk.Label(
            self.bottom_bar,
            text="idle · 0 clicks · F6",
            font=self.meta_font,
            bd=0,
            highlightthickness=0,
            anchor="e",
        )
        self.meta_counter_label.pack(side=tk.RIGHT)

        self._sync_conditional_inputs()

    def _make_row(self) -> tk.Frame:
        row = tk.Frame(self.rows_container, bd=0, highlightthickness=0, pady=3)
        row.pack(fill=tk.X, anchor="w")
        return row

    def _make_key(self, parent: tk.Widget, text: str) -> tk.Label:
        lbl = tk.Label(
            parent,
            text=f"{text:<15}",
            font=self.main_font,
            bd=0,
            highlightthickness=0,
            anchor="w",
        )
        lbl.pack(side=tk.LEFT)
        self.key_labels.append(lbl)
        return lbl

    def _make_interactive_val(
        self,
        parent: tk.Widget,
        text: str,
        command,
        bold: bool = False,
        pack_side=tk.LEFT,
    ) -> tk.Label:
        lbl = tk.Label(
            parent,
            text=text,
            font=self.bold_font if bold else self.main_font,
            bd=0,
            highlightthickness=0,
            cursor="hand2",
            anchor="w",
        )
        lbl.pack(side=pack_side)
        lbl.bind("<Button-1>", lambda _e: command())
        self.value_labels.append((lbl, bold))
        return lbl

    def _make_entry(
        self,
        parent: tk.Widget,
        textvariable: tk.StringVar,
        vcmd,
        width: int = 10,
    ) -> tk.Entry:
        """
        Creates a zero-chrome Entry with:
        - Crisp 2px-wide blinking cursor (insertwidth=2, 1000ms cycle: 500ms on / 500ms off)
        - Muted Slate Selection (#323842 with #ffffff text)
        - Zero border / bevels (relief=FLAT, bd=0, highlightthickness=0)
        - Plain-text-only paste handler
        """
        ent = tk.Entry(
            parent,
            textvariable=textvariable,
            validate="key",
            validatecommand=vcmd,
            font=self.main_font,
            width=width,
            relief=tk.FLAT,
            bd=0,
            highlightthickness=0,
            insertwidth=2,
            insertofftime=500,
            insertontime=500,
            exportselection=False,
        )
        ent.pack(side=tk.LEFT)
        ent.bind("<<Paste>>", self._on_plain_text_paste)
        ent.bind("<Control-v>", self._on_plain_text_paste)
        ent.bind("<Control-V>", self._on_plain_text_paste)
        ent.bind("<Return>", lambda _e: self.root.focus_set())
        ent.bind("<Escape>", lambda _e: self.root.focus_set())
        self.entries.append(ent)
        return ent

    # -----------------------------------------------------------------------
    # Interactive Control Callbacks
    # -----------------------------------------------------------------------
    def _cycle_speed_mode(self):
        if self.speed_mode == "cps":
            self.speed_mode = "ms"
            self.lbl_mode_val.configure(text="interval (ms)")
            self.lbl_speed_key.configure(text=f"{'interval (ms)':<15}")
            self.ent_speed.configure(textvariable=self.ms_var)
        else:
            self.speed_mode = "cps"
            self.lbl_mode_val.configure(text="cps (1-1000)")
            self.lbl_speed_key.configure(text=f"{'rate (cps)':<15}")
            self.ent_speed.configure(textvariable=self.cps_var)
        self.ent_speed.focus_set()
        self.ent_speed.icursor(tk.END)

    def _toggle_jitter(self):
        self.jitter_enabled = not self.jitter_enabled
        self.lbl_jitter_toggle.configure(text="on" if self.jitter_enabled else "off")
        self._sync_conditional_inputs()
        if self.jitter_enabled:
            self.ent_jitter.focus_set()
            self.ent_jitter.icursor(tk.END)

    def _cycle_button(self):
        idx = (BUTTON_CYCLE.index(self.button_name) + 1) % len(BUTTON_CYCLE)
        self.button_name = BUTTON_CYCLE[idx]
        self.lbl_btn_val.configure(text=self.button_name.lower())

    def _cycle_click_type(self):
        idx = (TYPE_CYCLE.index(self.click_type) + 1) % len(TYPE_CYCLE)
        self.click_type = TYPE_CYCLE[idx]
        self.lbl_type_val.configure(text=self.click_type.lower())

    def _toggle_repeat_mode(self):
        if self.repeat_mode == "infinite":
            self.repeat_mode = "fixed"
            self.lbl_rep_toggle.configure(text="fixed")
        else:
            self.repeat_mode = "infinite"
            self.lbl_rep_toggle.configure(text="infinite")
        self._sync_conditional_inputs()
        if self.repeat_mode == "fixed":
            self.ent_rep.focus_set()
            self.ent_rep.icursor(tk.END)

    def _cycle_hotkey(self):
        idx = (HOTKEY_CYCLE.index(self.hotkey_name) + 1) % len(HOTKEY_CYCLE)
        self.hotkey_name = HOTKEY_CYCLE[idx]
        self.lbl_hk_val.configure(text=self.hotkey_name)
        self.hotkey_listener.set_hotkey(self.hotkey_name)
        self._refresh_metadata()

    def _toggle_surface_theme(self):
        self.is_dark = not self.is_dark
        self.lbl_surface_val.configure(text="matte dark" if self.is_dark else "soft light")
        self._apply_palette()

    def _sync_conditional_inputs(self):
        if self.jitter_enabled:
            self.lbl_jitter_sep.pack(side=tk.LEFT)
            self.ent_jitter.pack(side=tk.LEFT)
        else:
            self.lbl_jitter_sep.pack_forget()
            self.ent_jitter.pack_forget()

        if self.repeat_mode == "fixed":
            self.lbl_rep_sep.pack(side=tk.LEFT)
            self.ent_rep.pack(side=tk.LEFT)
        else:
            self.lbl_rep_sep.pack_forget()
            self.ent_rep.pack_forget()

    # -----------------------------------------------------------------------
    # Palette & Typography Scaling (Ctrl+= / Ctrl+- / Ctrl+Wheel)
    # -----------------------------------------------------------------------
    def _apply_palette(self):
        p = PALETTE_DARK if self.is_dark else PALETTE_LIGHT

        # 100% Matte Canvas (#141414 in dark mode) with zero borders/dividers
        for frame in (
            self.root,
            self.viewport,
            self.rows_container,
            self._jitter_frame,
            self._rep_frame,
            self.bottom_bar,
        ):
            frame.configure(bg=p["canvas"])

        for row in self.rows_container.winfo_children():
            if isinstance(row, tk.Frame):
                row.configure(bg=p["canvas"])

        self.lbl_jitter_sep.configure(bg=p["canvas"], fg=p["meta"])
        self.lbl_rep_sep.configure(bg=p["canvas"], fg=p["meta"])

        # Whisper-quiet keys (#585858) vs Soft Off-White values (#e0e0e0)
        for k_lbl in self.key_labels:
            k_lbl.configure(bg=p["canvas"], fg=p["meta"])

        for v_lbl, _is_bold in self.value_labels:
            v_lbl.configure(bg=p["canvas"], fg=p["fg"])

        # Entries: #141414 canvas, #e0e0e0 text, #323842 selection with #ffffff text, 2px caret
        for ent in self.entries:
            ent.configure(
                bg=p["canvas"],
                fg=p["fg"],
                insertbackground=p["fg"],
                selectbackground=p["sel_bg"],
                selectforeground=p["sel_fg"],
                disabledbackground=p["canvas"],
                disabledforeground=p["meta"],
            )

        # Whisper-Quiet Metadata (#585858) in bottom-right
        self.meta_counter_label.configure(bg=p["canvas"], fg=p["meta"])
        self._update_status_display()

    def _scale_typography(self, delta: int):
        new_size = max(9, min(26, self.font_size + delta))
        if new_size == self.font_size:
            return
        self.font_size = new_size
        self.main_font.configure(size=self.font_size)
        self.bold_font.configure(size=self.font_size)
        self.meta_font.configure(size=max(8, self.font_size - 2))

    # -----------------------------------------------------------------------
    # Optional Frameless Mode (F11 / Ctrl+Shift+F) + Drag & Edge Resize
    # -----------------------------------------------------------------------
    def toggle_frameless(self, _event=None):
        self.is_frameless = not self.is_frameless
        try:
            # Attempt transient/override_redirect or WM decorations hint
            self.root.overrideredirect(self.is_frameless)
            if not self.is_frameless:
                self.root.deiconify()
            else:
                self.root.focus_force()
        except Exception:
            try:
                self.root.attributes("-type", "splash" if self.is_frameless else "normal")
            except Exception:
                pass
        self._refresh_metadata()
        return "break"

    def _detect_resize_edge(self, x_win: int, y_win: int, w: int, h: int) -> str:
        """Detects if cursor is within RESIZE_EDGE_MARGIN of window borders."""
        on_left = x_win < RESIZE_EDGE_MARGIN
        on_right = x_win > (w - RESIZE_EDGE_MARGIN)
        on_top = y_win < RESIZE_EDGE_MARGIN
        on_bottom = y_win > (h - RESIZE_EDGE_MARGIN)

        edge = ""
        if on_top:
            edge += "n"
        elif on_bottom:
            edge += "s"
        if on_left:
            edge += "w"
        elif on_right:
            edge += "e"
        return edge

    def _on_window_motion(self, event):
        if not self.is_frameless or self._drag_mode is not None:
            return
        w = self.root.winfo_width()
        h = self.root.winfo_height()
        x_win = event.x_root - self.root.winfo_rootx()
        y_win = event.y_root - self.root.winfo_rooty()
        edge = self._detect_resize_edge(x_win, y_win, w, h)

        cursor_map = {
            "n": "top_side",
            "s": "bottom_side",
            "w": "left_side",
            "e": "right_side",
            "nw": "top_left_corner",
            "ne": "top_right_corner",
            "sw": "bottom_left_corner",
            "se": "bottom_right_corner",
        }
        self.root.configure(cursor=cursor_map.get(edge, ""))

    def _on_mouse_press(self, event):
        w = self.root.winfo_width()
        h = self.root.winfo_height()
        x_win = event.x_root - self.root.winfo_rootx()
        y_win = event.y_root - self.root.winfo_rooty()

        alt_held = bool(event.state & 0x0008)  # Mod1 / Alt
        in_24px_margin = (
            x_win <= VIEWPORT_MARGIN
            or x_win >= (w - VIEWPORT_MARGIN)
            or y_win <= VIEWPORT_MARGIN
            or y_win >= (h - VIEWPORT_MARGIN)
        )
        is_bg_widget = event.widget in (
            self.root,
            self.viewport,
            self.rows_container,
            self.bottom_bar,
            self.meta_counter_label,
        )

        # 1. Check edge resize when in frameless mode
        if self.is_frameless:
            edge = self._detect_resize_edge(x_win, y_win, w, h)
            if edge:
                self._start_drag_or_resize(event, edge)
                return

        # 2. Move window if Alt+Drag OR dragging the 24px margin / empty canvas
        if alt_held or in_24px_margin or (self.is_frameless and is_bg_widget):
            self._start_drag_or_resize(event, "move")

    def _start_drag_or_resize(self, event, mode: str):
        self._drag_mode = mode
        self._drag_start_x = event.x_root
        self._drag_start_y = event.y_root
        self._win_start_x = self.root.winfo_x()
        self._win_start_y = self.root.winfo_y()
        self._win_start_w = self.root.winfo_width()
        self._win_start_h = self.root.winfo_height()

    def _on_mouse_drag(self, event):
        if not self._drag_mode:
            return
        dx = event.x_root - self._drag_start_x
        dy = event.y_root - self._drag_start_y

        if self._drag_mode == "move":
            nx = self._win_start_x + dx
            ny = self._win_start_y + dy
            self.root.geometry(f"+{nx}+{ny}")
            return

        # Edge resizing
        min_w, min_h = 380, 310
        nx = self._win_start_x
        ny = self._win_start_y
        nw = self._win_start_w
        nh = self._win_start_h

        if "e" in self._drag_mode:
            nw = max(min_w, self._win_start_w + dx)
        if "s" in self._drag_mode:
            nh = max(min_h, self._win_start_h + dy)
        if "w" in self._drag_mode:
            proposed_w = self._win_start_w - dx
            if proposed_w >= min_w:
                nw = proposed_w
                nx = self._win_start_x + dx
        if "n" in self._drag_mode:
            proposed_h = self._win_start_h - dy
            if proposed_h >= min_h:
                nh = proposed_h
                ny = self._win_start_y + dy

        self.root.geometry(f"{nw}x{nh}+{nx}+{ny}")

    def _on_mouse_release(self, _event):
        self._drag_mode = None

    def _bind_shortcuts_and_window_events(self):
        # Frameless mode shortcuts: F11 and Ctrl+Shift+F
        self.root.bind("<F11>", self.toggle_frameless)
        self.root.bind("<Control-Shift-F>", self.toggle_frameless)
        self.root.bind("<Control-Shift-f>", self.toggle_frameless)

        # Dynamic font scaling: Ctrl+= / Ctrl++ / Ctrl+- / Ctrl+0 / Ctrl+Wheel
        self.root.bind("<Control-equal>", lambda _e: self._scale_typography(+1))
        self.root.bind("<Control-plus>", lambda _e: self._scale_typography(+1))
        self.root.bind("<Control-KP_Add>", lambda _e: self._scale_typography(+1))
        self.root.bind("<Control-minus>", lambda _e: self._scale_typography(-1))
        self.root.bind("<Control-KP_Subtract>", lambda _e: self._scale_typography(-1))
        self.root.bind("<Control-0>", lambda _e: self._scale_typography(13 - self.font_size))
        self.root.bind("<Control-Button-4>", lambda _e: self._scale_typography(+1))
        self.root.bind("<Control-Button-5>", lambda _e: self._scale_typography(-1))
        self.root.bind(
            "<Control-MouseWheel>",
            lambda e: self._scale_typography(+1 if e.delta > 0 else -1),
        )

        # Window dragging & edge resizing
        self.root.bind("<Motion>", self._on_window_motion)
        self.root.bind("<ButtonPress-1>", self._on_mouse_press)
        self.root.bind("<B1-Motion>", self._on_mouse_drag)
        self.root.bind("<ButtonRelease-1>", self._on_mouse_release)

        # Local function key bindings (F1-F10)
        for fk in KEYCODE_MAP:
            self.root.bind(f"<{fk}>", self._on_local_hotkey)

    def _on_local_hotkey(self, event):
        if event.keysym.upper() == self.hotkey_name.upper():
            self.hotkey_listener.trigger_debounced()

    # -----------------------------------------------------------------------
    # Status & Whisper-Quiet Bottom-Right Metadata (#585858)
    # -----------------------------------------------------------------------
    def _update_status_display(self):
        self._refresh_metadata()

    def _refresh_metadata(self):
        state_word = "active" if self.is_running else "idle"
        unit = "click" if self.click_count == 1 else "clicks"
        frame_tag = " · frameless" if self.is_frameless else ""
        self.meta_counter_label.configure(
            text=f"{state_word} · {self.click_count} {unit} · {self.hotkey_name}{frame_tag}"
        )

    # -----------------------------------------------------------------------
    # Core Clicking Engine Loop
    # -----------------------------------------------------------------------
    def toggle_clicking(self):
        if self.is_running:
            self.stop_clicking()
        else:
            self.start_clicking()

    def start_clicking(self):
        if self.is_running:
            return

        if self.speed_mode == "cps":
            cps = self._get_safe_int(self.cps_var, default=10, min_val=1, max_val=1000)
            base_interval_sec = 1.0 / float(cps)
        else:
            ms = self._get_safe_int(self.ms_var, default=100, min_val=1, max_val=3600000)
            base_interval_sec = float(ms) / 1000.0

        jitter_sec = 0.0
        if self.jitter_enabled:
            jitter_ms = self._get_safe_int(self.jitter_ms_var, default=5, min_val=0, max_val=5000)
            jitter_sec = float(jitter_ms) / 1000.0

        max_clicks = 0
        if self.repeat_mode == "fixed":
            max_clicks = self._get_safe_int(self.repeat_count_var, default=100, min_val=1, max_val=10000000)

        button_name = self.button_name
        is_double = self.click_type == "Double"

        self.click_count = 0
        self.is_running = True
        self._stop_clicking_event.clear()
        self._update_status_display()

        self._click_thread = threading.Thread(
            target=self._click_worker,
            args=(base_interval_sec, jitter_sec, button_name, is_double, max_clicks),
            daemon=True,
        )
        self._click_thread.start()

    def stop_clicking(self):
        if not self.is_running:
            return
        self._stop_clicking_event.set()
        self.is_running = False
        self._update_status_display()

    def _click_worker(
        self,
        base_interval: float,
        jitter_sec: float,
        button_name: str,
        is_double: bool,
        max_clicks: int,
    ):
        next_time = time.perf_counter()
        ui_last_update = 0.0

        while not self._stop_clicking_event.is_set():
            self.backend.perform_click(button_name=button_name, double=is_double)
            self.click_count += 1

            now = time.perf_counter()
            if now - ui_last_update >= 0.065:
                ui_last_update = now
                self.root.after(0, self._refresh_metadata)

            if max_clicks > 0 and self.click_count >= max_clicks:
                self.root.after(0, self.stop_clicking)
                break

            delay = base_interval
            if jitter_sec > 0.0:
                delay += random.uniform(-jitter_sec, jitter_sec)
                delay = max(0.0005, delay)

            next_time += delay
            sleep_duration = next_time - time.perf_counter()
            if sleep_duration > 0:
                if self._stop_clicking_event.wait(timeout=sleep_duration):
                    break
            else:
                next_time = time.perf_counter()

        self.root.after(0, self._refresh_metadata)

    def on_close(self):
        self.stop_clicking()
        self.hotkey_listener.stop()
        self.backend.close()
        self.root.destroy()


def main():
    root = tk.Tk()
    AutoClickerApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
