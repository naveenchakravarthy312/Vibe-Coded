#!/usr/bin/env python3
"""
Auto Clicker — Ultra-minimalist, zero-chrome GUI autoclicker for Linux (Wayland & X11).

Design Aesthetic (per DESKTOP-STYLE.md):
- Matte Dark Canvas (#141414) with zero borders, dividers, or bevels
- Soft Off-White Foreground (#e0e0e0)
- Muted Slate Selection (#323842 with #ffffff text)
- Whisper-Quiet Metadata (#585858) in the bottom-right corner
- 24px Viewport Padding around all four edges
- Optional Frameless Mode (F11 / Ctrl+Shift+F) with 24px margin drag & 8px edge resizing
- Secondary Light Palette toggleable via Ctrl+Shift+D
- Fast Exit via Ctrl+Q and context-aware Escape
- Modern Monospace Stack (JetBrains Mono, Fira Code, Cascadia Code, Iosevka, Source Code Pro)
  at 13pt default size, scalable on the fly (Ctrl+= / Ctrl+- / Ctrl+0 / Ctrl+Wheel)
- Crisp 2px-wide blinking cursor (1000ms cycle) and plain-text-only numeric pasting
"""

from __future__ import annotations

import ctypes
import ctypes.util
import glob
import os
import random
import select
import shutil
import subprocess
import threading
import time
import tkinter as tk
import tkinter.font as tkfont
from typing import Literal, Sequence

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
    "Ubuntu Mono",
    "DejaVu Sans Mono",
    "Liberation Mono",
    "monospace",
]

VIEWPORT_MARGIN = 24
RESIZE_EDGE_MARGIN = 8


# ---------------------------------------------------------------------------
# Wayland / Linux Clicking & Global Hotkey Backends
# ---------------------------------------------------------------------------
class ClickerBackend:
    """
    Lightweight, high-speed Linux click backend:
    1. Kernel uinput via python3-evdev (fastest, microsecond precision, native Wayland & X11)
    2. Persistent dotool pipe fallback (Wayland/uinput without per-click process spawn)
    3. X11 ctypes libXtst fallback (for X11 and XWayland windows)
    """

    def __init__(self):
        self.method = "none"
        self.ready = False
        self.uinput_dev = None
        self._dotool_proc = None
        self._x11_display = None
        self._x11_lib = None
        self._xtst_lib = None
        self._lock = threading.Lock()
        self._init_backends()

    def _init_backends(self):
        # 1. Try python-evdev UInput (Works natively on Wayland & X11 up to 1000 CPS)
        try:
            from evdev import UInput, ecodes  # type: ignore[import-not-found,reportMissingImports]

            events: dict[int, Sequence[int]] = {
                ecodes.EV_KEY: [BTN_LEFT, BTN_RIGHT, BTN_MIDDLE],
                ecodes.EV_REL: [REL_X, REL_Y],
            }
            self.uinput_dev = UInput(events, name="AutoClicker-Virtual-Mouse")  # type: ignore[arg-type]
            self.method = "uinput"
            self.ready = True
            return
        except Exception:
            self.uinput_dev = None

        # 2. Check for persistent dotool daemon pipe
        if shutil.which("dotool"):
            try:
                self._dotool_proc = subprocess.Popen(
                    ["dotool"],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                )
                self.method = "dotool"
                self.ready = True
                return
            except Exception:
                self._dotool_proc = None

        # 3. Try X11 libXtst via ctypes (works on X11 and XWayland windows)
        try:
            x11_path = ctypes.util.find_library("X11")
            xtst_path = ctypes.util.find_library("Xtst")
            if x11_path and xtst_path:
                self._x11_lib = ctypes.cdll.LoadLibrary(x11_path)
                self._xtst_lib = ctypes.cdll.LoadLibrary(xtst_path)
                self._x11_lib.XOpenDisplay.restype = ctypes.c_void_p
                self._x11_lib.XOpenDisplay.argtypes = [ctypes.c_char_p]
                self._x11_lib.XCloseDisplay.argtypes = [ctypes.c_void_p]
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
                    self.ready = True
                    return
        except Exception:
            self._x11_display = None

        self.method = "none"
        self.ready = False

    def perform_click(
        self,
        button_name: str = "Left",
        double: bool = False,
        intra_delay: float = 0.015,
        stop_event: threading.Event | None = None,
    ) -> bool:
        """
        Dispatches a single or double mouse click.
        Returns True if the click was sent to an active backend, False otherwise.
        """
        if not self.ready:
            return False

        btn_code = BUTTON_MAP.get(button_name, BTN_LEFT)
        if not self._single_click(button_name, btn_code):
            return False

        if double:
            if intra_delay > 0:
                if stop_event is not None:
                    if stop_event.wait(timeout=intra_delay):
                        return True
                else:
                    time.sleep(intra_delay)
            self._single_click(button_name, btn_code)

        return True

    def _single_click(self, button_name: str, btn_code: int) -> bool:
        # 1. evdev UInput
        if self.uinput_dev is not None:
            try:
                with self._lock:
                    self.uinput_dev.write(EV_KEY, btn_code, 1)
                    self.uinput_dev.write(EV_SYN, SYN_REPORT, 0)
                    self.uinput_dev.write(EV_KEY, btn_code, 0)
                    self.uinput_dev.write(EV_SYN, SYN_REPORT, 0)
                return True
            except Exception:
                return False

        # 2. Persistent dotool pipe
        if self.method == "dotool":
            do_btn = {"Left": "left", "Right": "right", "Middle": "middle"}.get(button_name, "left")
            with self._lock:
                try:
                    if self._dotool_proc is None or self._dotool_proc.poll() is not None:
                        self._dotool_proc = subprocess.Popen(
                            ["dotool"],
                            stdin=subprocess.PIPE,
                            stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL,
                        )
                    if self._dotool_proc.stdin:
                        self._dotool_proc.stdin.write(f"click {do_btn}\n".encode())
                        self._dotool_proc.stdin.flush()
                        return True
                except Exception:
                    self._dotool_proc = None
            return False

        # 3. libXtst
        if self._x11_display and self._xtst_lib and self._x11_lib:
            x_btn = {"Left": 1, "Middle": 2, "Right": 3}.get(button_name, 1)
            try:
                with self._lock:
                    self._xtst_lib.XTestFakeButtonEvent(self._x11_display, x_btn, 1, 0)
                    self._xtst_lib.XTestFakeButtonEvent(self._x11_display, x_btn, 0, 0)
                    self._x11_lib.XFlush(self._x11_display)
                return True
            except Exception:
                return False

        return False

    def close(self):
        with self._lock:
            if self.uinput_dev is not None:
                try:
                    self.uinput_dev.close()
                except Exception:
                    pass
                self.uinput_dev = None

            if self._dotool_proc is not None:
                try:
                    if self._dotool_proc.stdin:
                        self._dotool_proc.stdin.close()
                    self._dotool_proc.terminate()
                except Exception:
                    pass
                self._dotool_proc = None

            if self._x11_display and self._x11_lib:
                try:
                    self._x11_lib.XCloseDisplay(self._x11_display)
                except Exception:
                    pass
                self._x11_display = None


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
            import evdev  # type: ignore[import-not-found,reportMissingImports]

            devices = []
            for path in glob.glob("/dev/input/event*"):
                try:
                    dev = evdev.InputDevice(path)
                    caps = dev.capabilities()
                    if EV_KEY in caps:
                        key_caps = caps[EV_KEY]
                        if any(code in key_caps for code in KEYCODE_MAP.values()):
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
                try:
                    while not self._stop_event.is_set() and fd_to_dev:
                        r, _, _ = select.select(list(fd_to_dev.keys()), [], [], 0.15)
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
                                closed_dev = fd_to_dev.pop(fd, None)
                                if closed_dev:
                                    try:
                                        closed_dev.close()
                                    except Exception:
                                        pass
                finally:
                    for dev in devices:
                        try:
                            dev.close()
                        except Exception:
                            pass
                return
        except Exception:
            pass

        # Attempt 2: X11 XQueryKeymap polling
        x11 = None
        disp = None
        try:
            x11_path = ctypes.util.find_library("X11")
            if x11_path:
                x11 = ctypes.cdll.LoadLibrary(x11_path)
                x11.XOpenDisplay.restype = ctypes.c_void_p
                x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
                x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
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
        except Exception:
            pass
        finally:
            if disp and x11:
                try:
                    x11.XCloseDisplay(disp)
                except Exception:
                    pass

    def stop(self):
        self._stop_event.set()
        if self._thread.is_alive():
            self._thread.join(timeout=0.2)


# ---------------------------------------------------------------------------
# Ultra-Minimalist Matte Dark GUI Application
# ---------------------------------------------------------------------------
class AutoClickerApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("Auto Clicker")
        self.root.geometry("480x360")
        self.root.minsize(360, 280)

        self._load_window_icon()

        # Runtime State
        self.is_running = False
        self._is_closing = False
        self.click_count = 0
        self.is_dark = True
        self.is_frameless = False
        self._stop_clicking_event = threading.Event()
        self._click_thread: threading.Thread | None = None

        # Drag / Resize State (for Frameless Mode & Alt+Drag)
        self._drag_mode: str | None = None
        self._drag_start_x = 0
        self._drag_start_y = 0
        self._win_start_x = 0
        self._win_start_y = 0
        self._win_start_w = 0
        self._win_start_h = 0

        # Typography State (13pt default, scalable via Ctrl+= / Ctrl+- / Ctrl+0 / Ctrl+Wheel)
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

        # Build Zero-Chrome Interface immediately so window renders instantly
        self._init_fonts()
        self._build_ui()
        self._apply_palette()
        self._bind_shortcuts_and_window_events()

        # Initialize backends asynchronously in background thread
        self.backend: ClickerBackend | None = None
        self.hotkey_listener: GlobalHotkeyListener | None = None
        self._init_backends_async()

        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def _schedule_ui(self, callback):
        """Safely schedules a callback on the Tk main loop if the window is still alive."""
        if self._is_closing:
            return
        try:
            self.root.after(0, callback)
        except Exception:
            pass

    def _init_backends_async(self):
        def _worker():
            backend = ClickerBackend()
            listener = GlobalHotkeyListener(
                self.hotkey_name,
                lambda: self._schedule_ui(self.toggle_clicking),
            )
            self.backend = backend
            self.hotkey_listener = listener
            self._schedule_ui(self._refresh_metadata)

        threading.Thread(target=_worker, daemon=True).start()

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
        Strips rich formatting, carriage returns, whitespace, and non-digit characters.
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

    def _read_live_int(self, var: tk.StringVar, default: int, min_val: int, max_val: int, clamp_ui: bool = False) -> int:
        raw = var.get().strip()
        if not raw or not raw.isdigit():
            if clamp_ui:
                var.set(str(default))
            return default
        val = int(raw)
        clamped = max(min_val, min(max_val, val))
        if clamp_ui and clamped != val:
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

        self.key_labels: list[tk.Label] = []
        self.value_labels: list[tuple[tk.Label, bool]] = []
        self.entries: list[tk.Entry] = []

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
            pack_side="left",
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
            pack_side="left",
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
            cursor="hand2",
        )
        self.meta_counter_label.pack(side=tk.RIGHT)
        self.meta_counter_label.bind("<Button-1>", lambda _e: self.toggle_clicking())

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
        pack_side: Literal["left", "right", "top", "bottom"] = "left",
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
        if self.hotkey_listener:
            self.hotkey_listener.set_hotkey(self.hotkey_name)
        self._refresh_metadata()

    def _toggle_surface_theme(self, _event=None):
        self.is_dark = not self.is_dark
        self._apply_palette()
        return "break"

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
    # Palette & Typography Scaling (Ctrl+= / Ctrl+- / Ctrl+0 / Ctrl+Wheel)
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
        new_size = max(8, min(48, self.font_size + delta))
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
        self._drag_mode = None
        self.root.configure(cursor="")
        try:
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
        if alt_held:
            self._start_drag_or_resize(event, "move")
            return

        # Margin dragging and 8px edge resizing apply only when frameless
        if not self.is_frameless:
            return

        edge = self._detect_resize_edge(x_win, y_win, w, h)
        if edge:
            self._start_drag_or_resize(event, edge)
            return

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
        )
        if in_24px_margin or is_bg_widget:
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

        # Edge resizing (frameless mode)
        min_w, min_h = 360, 280
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

    def _on_escape(self, _event=None):
        """
        Context-aware Escape (per DESKTOP-STYLE.md):
        - If an Entry has focus, unfocus to the root canvas.
        - If currently clicking, stop clicking.
        - Otherwise, exit immediately.
        """
        focused = self.root.focus_get()
        if isinstance(focused, tk.Entry):
            self.root.focus_set()
            return "break"
        if self.is_running:
            self.stop_clicking()
            return "break"
        self.on_close()
        return "break"

    def _bind_shortcuts_and_window_events(self):
        # Context-aware Escape & Fast Exit (Ctrl+Q)
        self.root.bind("<Escape>", self._on_escape)
        self.root.bind("<Control-q>", lambda _e: self.on_close())
        self.root.bind("<Control-Q>", lambda _e: self.on_close())

        # Dark / Light surface toggle: Ctrl+Shift+D
        self.root.bind("<Control-Shift-D>", self._toggle_surface_theme)
        self.root.bind("<Control-Shift-d>", self._toggle_surface_theme)

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
        if event.keysym.upper() == self.hotkey_name.upper() and self.hotkey_listener is not None:
            self.hotkey_listener.trigger_debounced()

    # -----------------------------------------------------------------------
    # Status & Whisper-Quiet Bottom-Right Metadata (#585858)
    # -----------------------------------------------------------------------
    def _update_status_display(self):
        self._refresh_metadata()

    def _refresh_metadata(self):
        if self._is_closing:
            return
        if self.backend is not None and not self.backend.ready:
            state_word = "no input access"
        else:
            state_word = "active" if self.is_running else "idle"
        unit = "click" if self.click_count == 1 else "clicks"
        frame_tag = " · frameless" if self.is_frameless else ""
        self.meta_counter_label.configure(
            text=f"{state_word} · {self.click_count} {unit} · {self.hotkey_name}{frame_tag}"
        )

    # -----------------------------------------------------------------------
    # Core Clicking Engine Loop (Reads Live Configuration)
    # -----------------------------------------------------------------------
    def _get_live_click_config(self, clamp_ui: bool = False) -> tuple[float, float, str, bool, int]:
        """Reads current UI settings safely so changes take effect immediately while running."""
        if self.speed_mode == "cps":
            cps = self._read_live_int(self.cps_var, default=10, min_val=1, max_val=1000, clamp_ui=clamp_ui)
            base_interval_sec = 1.0 / float(cps)
        else:
            ms = self._read_live_int(self.ms_var, default=100, min_val=1, max_val=3600000, clamp_ui=clamp_ui)
            base_interval_sec = float(ms) / 1000.0

        jitter_sec = 0.0
        if self.jitter_enabled:
            jitter_ms = self._read_live_int(self.jitter_ms_var, default=5, min_val=0, max_val=5000, clamp_ui=clamp_ui)
            jitter_sec = float(jitter_ms) / 1000.0

        max_clicks = 0
        if self.repeat_mode == "fixed":
            max_clicks = self._read_live_int(
                self.repeat_count_var, default=100, min_val=1, max_val=10000000, clamp_ui=clamp_ui
            )

        button_name = self.button_name
        is_double = self.click_type == "Double"
        return base_interval_sec, jitter_sec, button_name, is_double, max_clicks

    def toggle_clicking(self):
        if self.is_running:
            self.stop_clicking()
        else:
            self.start_clicking()

    def start_clicking(self):
        if self.is_running:
            return
        if self.backend is not None and not self.backend.ready:
            self._refresh_metadata()
            return

        # Sanitize and clamp any out-of-range UI fields on start
        self._get_live_click_config(clamp_ui=True)

        self.click_count = 0
        self.is_running = True
        self._stop_clicking_event.clear()
        self._update_status_display()

        self._click_thread = threading.Thread(
            target=self._click_worker,
            daemon=True,
        )
        self._click_thread.start()

    def stop_clicking(self):
        if not self.is_running:
            return
        self._stop_clicking_event.set()
        self.is_running = False
        self._update_status_display()

    def _click_worker(self):
        next_time = time.perf_counter()
        ui_last_update = 0.0

        while not self._stop_clicking_event.is_set():
            base_interval, jitter_sec, button_name, is_double, max_clicks = self._get_live_click_config(
                clamp_ui=False
            )

            # Scale double-click gap dynamically so high CPS (> 65 CPS) is never bottlenecked
            intra_delay = min(0.015, base_interval * 0.35) if is_double else 0.0

            dispatched = False
            if self.backend:
                dispatched = self.backend.perform_click(
                    button_name=button_name,
                    double=is_double,
                    intra_delay=intra_delay,
                    stop_event=self._stop_clicking_event,
                )

            if dispatched:
                self.click_count += 1
            else:
                self._schedule_ui(self.stop_clicking)
                break

            now = time.perf_counter()
            if now - ui_last_update >= 0.065:
                ui_last_update = now
                self._schedule_ui(self._refresh_metadata)

            if max_clicks > 0 and self.click_count >= max_clicks:
                self._schedule_ui(self.stop_clicking)
                break

            delay = base_interval
            if jitter_sec > 0.0:
                delay += random.uniform(-jitter_sec, jitter_sec)
                delay = max(0.0005, delay)

            next_time += delay
            sleep_duration = next_time - time.perf_counter()
            if sleep_duration > 0:
                if sleep_duration > 0.004:
                    if self._stop_clicking_event.wait(timeout=sleep_duration - 0.002):
                        break
                while time.perf_counter() < next_time:
                    if self._stop_clicking_event.is_set():
                        break
            else:
                next_time = time.perf_counter()

        self._schedule_ui(self._refresh_metadata)

    def on_close(self):
        if self._is_closing:
            return
        self._is_closing = True
        self._stop_clicking_event.set()
        self.is_running = False

        if self._click_thread and self._click_thread.is_alive():
            self._click_thread.join(timeout=0.15)
        if self.hotkey_listener:
            self.hotkey_listener.stop()
        if self.backend:
            self.backend.close()
        try:
            self.root.destroy()
        except Exception:
            pass


def main():
    root = tk.Tk(className="Auto Clicker")
    AutoClickerApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
