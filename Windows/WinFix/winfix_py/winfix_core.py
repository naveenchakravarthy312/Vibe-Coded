"""
WinFix Core Engine
System telemetry, non-blocking QThread workers, granular UAC elevation,
Non-Admin smart fallback installer, and ARM64-safe Windows registry helpers.
"""

from __future__ import annotations

import ctypes
import json
import os
import platform
import shutil
import socket
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Sequence, Tuple

import psutil
import requests
from PyQt6.QtCore import QThread, pyqtSignal

IS_WINDOWS = sys.platform == "win32"

if IS_WINDOWS:
    import winreg  # type: ignore
else:
    winreg = None  # type: ignore

DEFAULT_GITHUB_RAW_URL = (
    "https://raw.githubusercontent.com/winfix-portable/catalog/main/apps.json"
)

# Strictly curated consumer bloat list — never touches Edge WebView2, Bluetooth,
# Windows Push Notifications (WPNService), or ARM64 Prism emulation packages.
SAFE_DEBLOAT_PACKAGES: List[Tuple[str, str]] = [
    ("TikTok Preload", "BytedancePte.Ltd.TikTok_6yccndn6064se"),
    ("Microsoft Solitaire Collection", "Microsoft.MicrosoftSolitaireCollection_8wekyb3d8bbwe"),
    ("Clipchamp Video Editor", "Clipchamp.Clipchamp_yxz26nhyzhsrt"),
    ("Microsoft News", "Microsoft.BingNews_8wekyb3d8bbwe"),
    ("Bing Weather", "Microsoft.BingWeather_8wekyb3d8bbwe"),
    ("Xbox Gaming App", "Microsoft.GamingApp_8wekyb3d8bbwe"),
]

PROTECTED_COMPONENTS: Tuple[str, ...] = (
    "Microsoft.EdgeWebView2Runtime",
    "bthserv",
    "WpnService",
    "ARM64.Prism",
)


@dataclass
class SystemTelemetry:
    windows_version: str
    windows_build: str
    arch: str
    is_admin: bool
    ipv4: str
    adapter_name: str
    user_profile: str


def is_user_admin() -> bool:
    if not IS_WINDOWS:
        return os.geteuid() == 0 if hasattr(os, "geteuid") else False
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False


def detect_cpu_arch() -> str:
    machine = (
        os.environ.get("PROCESSOR_ARCHITECTURE")
        or platform.machine()
        or "AMD64"
    ).upper()
    if "ARM64" in machine or "AARCH64" in machine:
        return "ARM64"
    return "x64"


def detect_windows_version() -> Tuple[str, str]:
    if not IS_WINDOWS or winreg is None:
        return f"{platform.system()} {platform.release()}", platform.version()

    display_ver = ""
    current_build = ""
    ubr = ""
    product_name = "Win 11"
    try:
        with winreg.OpenKey(
            winreg.HKEY_LOCAL_MACHINE,
            r"SOFTWARE\Microsoft\Windows NT\CurrentVersion",
        ) as key:
            display_ver = str(_reg_get(key, "DisplayVersion", ""))
            current_build = str(_reg_get(key, "CurrentBuildNumber", ""))
            ubr_val = _reg_get(key, "UBR", "")
            ubr = f".{ubr_val}" if ubr_val != "" else ""
            raw_name = str(_reg_get(key, "ProductName", "Windows"))
            if current_build.isdigit() and int(current_build) >= 22000:
                product_name = "Win 11"
            elif "10" in raw_name:
                product_name = "Win 10"
            else:
                product_name = raw_name
    except Exception:
        pass

    label = f"{product_name} {display_ver}".strip()
    build = f"{current_build}{ubr}".strip() or platform.version()
    return label, build


def detect_active_network() -> Tuple[str, str]:
    ipv4 = "127.0.0.1"
    adapter = "No Active Adapter"
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(0.5)
        try:
            sock.connect(("1.1.1.1", 80))
            ipv4 = sock.getsockname()[0]
        finally:
            sock.close()
    except Exception:
        pass

    try:
        stats = psutil.net_if_stats()
        addrs = psutil.net_if_addrs()
        for iface, addr_list in addrs.items():
            if not stats.get(iface) or not stats[iface].isup:
                continue
            for addr in addr_list:
                if addr.family == socket.AF_INET and addr.address == ipv4:
                    return ipv4, iface
        for iface, addr_list in addrs.items():
            if not stats.get(iface) or not stats[iface].isup:
                continue
            for addr in addr_list:
                if addr.family == socket.AF_INET and not addr.address.startswith("127."):
                    return addr.address, iface
    except Exception:
        pass

    return ipv4, adapter


def collect_telemetry() -> SystemTelemetry:
    win_ver, win_build = detect_windows_version()
    ipv4, adapter = detect_active_network()
    return SystemTelemetry(
        windows_version=win_ver,
        windows_build=win_build,
        arch=detect_cpu_arch(),
        is_admin=is_user_admin(),
        ipv4=ipv4,
        adapter_name=adapter,
        user_profile=os.environ.get("USERPROFILE") or str(Path.home()),
    )


def get_base_resource_dir() -> Path:
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        return Path(getattr(sys, "_MEIPASS"))
    return Path(__file__).resolve().parent


def get_external_apps_json_path() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent / "apps.json"
    return Path(__file__).resolve().parent / "apps.json"


def resolve_apps_json_path() -> Path:
    external = get_external_apps_json_path()
    if external.exists():
        return external
    return get_base_resource_dir() / "apps.json"


def load_apps_catalog() -> Tuple[List[Dict[str, Any]], Path]:
    path = resolve_apps_json_path()
    raw = json.loads(path.read_text(encoding="utf-8"))
    apps = raw.get("apps", raw) if isinstance(raw, dict) else raw
    if not isinstance(apps, list):
        raise ValueError("apps.json must contain a list or {'apps': [...]}")
    return apps, path


def sync_apps_json_from_github(raw_url: str, timeout: int = 12) -> Tuple[List[Dict[str, Any]], Path]:
    resp = requests.get(raw_url, timeout=timeout)
    resp.raise_for_status()
    payload = resp.json()
    apps = payload.get("apps", payload) if isinstance(payload, dict) else payload
    if not isinstance(apps, list) or len(apps) == 0:
        raise ValueError("Remote JSON does not contain a non-empty 'apps' list.")
    target = get_external_apps_json_path()
    target.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    return apps, target


def relaunch_as_admin() -> bool:
    if not IS_WINDOWS:
        return False
    exe = sys.executable
    if getattr(sys, "frozen", False):
        params = subprocess.list2cmdline(sys.argv[1:])
    else:
        params = subprocess.list2cmdline([os.path.abspath(sys.argv[0]), *sys.argv[1:]])
    rc = ctypes.windll.shell32.ShellExecuteW(None, "runas", exe, params, None, 1)
    return rc > 32


def shell_execute_elevated(executable: str, parameters: str = "") -> bool:
    if not IS_WINDOWS:
        return False
    rc = ctypes.windll.shell32.ShellExecuteW(
        None, "runas", executable, parameters, None, 1
    )
    return rc > 32


def build_winget_install_cmd(winget_id: str, is_admin: bool) -> List[str]:
    cmd = [
        "winget",
        "install",
        "--id",
        winget_id,
        "-e",
        "--accept-package-agreements",
        "--accept-source-agreements",
        "--silent",
    ]
    if not is_admin:
        cmd.extend(["--scope", "user"])
    return cmd


def build_winget_uninstall_cmd(winget_id: str, is_admin: bool) -> List[str]:
    cmd = ["winget", "uninstall", "--id", winget_id, "-e", "--silent"]
    if not is_admin:
        cmd.extend(["--scope", "user"])
    return cmd


def detect_installed_apps(apps: Sequence[Dict[str, Any]]) -> Dict[str, bool]:
    """Detects installed status via portable %USERPROFILE%\\Apps folder and winget list."""
    result: Dict[str, bool] = {}
    user_apps_root = Path(os.environ.get("USERPROFILE") or str(Path.home())) / "Apps"

    winget_output = ""
    if IS_WINDOWS and shutil.which("winget"):
        try:
            creation = getattr(subprocess, "CREATE_NO_WINDOW", 0)
            proc = subprocess.run(
                ["winget", "list", "--accept-source-agreements"],
                capture_output=True,
                text=True,
                timeout=15,
                creationflags=creation,
            )
            winget_output = (proc.stdout or "").lower()
        except Exception:
            winget_output = ""

    for app in apps:
        app_id = str(app.get("id", ""))
        folder = str(app.get("portable_folder") or app.get("name", "")).replace(" ", "")
        winget_id = str(app.get("winget_id") or "").lower()
        portable_dir = user_apps_root / folder
        found = False
        if portable_dir.exists() and any(portable_dir.iterdir()):
            found = True
        elif winget_id and winget_id in winget_output:
            found = True
        result[app_id] = found
    return result


# ---------------------------------------------------------------------------
# Registry Helpers (HKCU — Standard User Safe, No UAC Required)
# ---------------------------------------------------------------------------

def _reg_get(key: Any, name: str, default: Any = None) -> Any:
    if winreg is None:
        return default
    try:
        value, _ = winreg.QueryValueEx(key, name)
        return value
    except OSError:
        return default


def read_tweak_states() -> Dict[str, Any]:
    states: Dict[str, Any] = {
        "classic_context_menu": False,
        "left_align_taskbar": False,
        "hide_start_recommendations": False,
        "text_scaling": 100,
        "ads_telemetry_disabled": False,
    }
    if not IS_WINDOWS or winreg is None:
        return states

    try:
        with winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            r"Software\Classes\CLSID\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}\InprocServer32",
        ):
            states["classic_context_menu"] = True
    except OSError:
        states["classic_context_menu"] = False

    try:
        with winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            r"Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced",
        ) as key:
            states["left_align_taskbar"] = _reg_get(key, "TaskbarAl", 1) == 0
            states["hide_start_recommendations"] = (
                _reg_get(key, "Start_IrisRecommendations", 1) == 0
            )
    except OSError:
        pass

    try:
        with winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            r"Software\Microsoft\Accessibility",
        ) as key:
            scale = int(_reg_get(key, "TextScaleFactor", 100))
            states["text_scaling"] = scale if scale in (100, 125, 150) else 100
    except OSError:
        pass

    try:
        with winreg.OpenKey(
            winreg.HKEY_CURRENT_USER,
            r"Software\Microsoft\Windows\CurrentVersion\AdvertisingInfo",
        ) as key:
            states["ads_telemetry_disabled"] = _reg_get(key, "Enabled", 1) == 0
    except OSError:
        pass

    return states


def set_classic_context_menu(enable: bool) -> str:
    clsid_path = r"Software\Classes\CLSID\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}\InprocServer32"
    if not IS_WINDOWS or winreg is None:
        return f"[dry-run] Classic Context Menu -> {'Enabled' if enable else 'Disabled'}"
    if enable:
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, clsid_path) as key:
            winreg.SetValueEx(key, "", 0, winreg.REG_SZ, "")
        return "Created HKCU CLSID InprocServer32 override for Windows 10 Classic Context Menu."
    else:
        for sub in (
            clsid_path,
            r"Software\Classes\CLSID\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}",
        ):
            try:
                winreg.DeleteKey(winreg.HKEY_CURRENT_USER, sub)
            except OSError:
                pass
        return "Removed Classic Context Menu CLSID override (Windows 11 modern menu restored)."


def set_left_align_taskbar(enable: bool) -> str:
    val = 0 if enable else 1
    if not IS_WINDOWS or winreg is None:
        return f"[dry-run] TaskbarAl = {val}"
    with winreg.CreateKey(
        winreg.HKEY_CURRENT_USER,
        r"Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced",
    ) as key:
        winreg.SetValueEx(key, "TaskbarAl", 0, winreg.REG_DWORD, val)
    return f"Set HKCU\\...\\Explorer\\Advanced\\TaskbarAl = {val} ({'Left' if enable else 'Center'})."


def set_hide_start_recommendations(enable: bool) -> str:
    val = 0 if enable else 1
    if not IS_WINDOWS or winreg is None:
        return f"[dry-run] Start_IrisRecommendations = {val}"
    with winreg.CreateKey(
        winreg.HKEY_CURRENT_USER,
        r"Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced",
    ) as key:
        winreg.SetValueEx(key, "Start_IrisRecommendations", 0, winreg.REG_DWORD, val)
        winreg.SetValueEx(key, "Start_TrackDocs", 0, winreg.REG_DWORD, val)
    return f"Updated Start recommendation & recent tracking keys to {val}."


def set_text_scaling(percent: int) -> str:
    clamped = 100 if percent not in (100, 125, 150) else percent
    if not IS_WINDOWS or winreg is None:
        return f"[dry-run] TextScaleFactor = {clamped}"
    with winreg.CreateKey(
        winreg.HKEY_CURRENT_USER,
        r"Software\Microsoft\Accessibility",
    ) as key:
        winreg.SetValueEx(key, "TextScaleFactor", 0, winreg.REG_DWORD, clamped)
    return f"Set HKCU\\Software\\Microsoft\\Accessibility\\TextScaleFactor = {clamped}%."


def set_ads_telemetry_disabled(disable: bool) -> str:
    flag = 0 if disable else 1
    if not IS_WINDOWS or winreg is None:
        return f"[dry-run] Ads & Telemetry -> {'Disabled' if disable else 'Default'}"

    with winreg.CreateKey(
        winreg.HKEY_CURRENT_USER,
        r"Software\Microsoft\Windows\CurrentVersion\AdvertisingInfo",
    ) as key:
        winreg.SetValueEx(key, "Enabled", 0, winreg.REG_DWORD, flag)

    cdm_keys = [
        "SubscribedContent-338388Enabled",
        "SubscribedContent-338389Enabled",
        "SubscribedContent-338393Enabled",
        "SubscribedContent-353694Enabled",
        "SubscribedContent-353696Enabled",
        "SystemPaneSuggestionsEnabled",
    ]
    with winreg.CreateKey(
        winreg.HKEY_CURRENT_USER,
        r"Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager",
    ) as key:
        for name in cdm_keys:
            winreg.SetValueEx(key, name, 0, winreg.REG_DWORD, flag)

    with winreg.CreateKey(
        winreg.HKEY_CURRENT_USER,
        r"Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced",
    ) as key:
        winreg.SetValueEx(key, "ShowCopilotButton", 0, winreg.REG_DWORD, flag)

    return (
        "Disabled Advertising ID, Start/Settings suggestions, and Copilot taskbar button (HKCU)."
        if disable
        else "Restored default Windows Advertising ID, suggestions, and Copilot taskbar button."
    )


# ---------------------------------------------------------------------------
# Non-Blocking QThread Workers
# ---------------------------------------------------------------------------

class CommandWorker(QThread):
    """Runs one or more shell commands asynchronously and streams stdout/stderr."""

    line_emitted = pyqtSignal(str, str)  # (kind: cmd|stdout|stderr|info, text)
    finished_with_code = pyqtSignal(int, str)  # (exit_code, label)

    def __init__(
        self,
        label: str,
        commands: Sequence[Tuple[str, Sequence[str] | str, bool]],
        parent: Optional[Any] = None,
    ) -> None:
        super().__init__(parent)
        self.label = label
        # Each item: (display_cmd, args_or_str, use_shell)
        self.commands = list(commands)

    def run(self) -> None:
        creation = getattr(subprocess, "CREATE_NO_WINDOW", 0) if IS_WINDOWS else 0
        final_rc = 0
        for display_cmd, cmd_spec, use_shell in self.commands:
            self.line_emitted.emit("cmd", f"$ {display_cmd}")
            try:
                proc = subprocess.Popen(
                    cmd_spec,
                    shell=use_shell,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    creationflags=creation,
                )
                assert proc.stdout is not None
                for raw_line in proc.stdout:
                    cleaned = raw_line.rstrip("\r\n")
                    if cleaned:
                        kind = "stderr" if "error" in cleaned.lower() else "stdout"
                        self.line_emitted.emit(kind, cleaned)
                rc = proc.wait()
                if rc != 0:
                    final_rc = rc
                    self.line_emitted.emit("stderr", f"Command exited with code {rc}")
            except FileNotFoundError as exc:
                final_rc = 127
                self.line_emitted.emit("stderr", f"Executable not found: {exc}")
            except Exception as exc:
                final_rc = 1
                self.line_emitted.emit("stderr", f"Execution error: {exc}")
        self.finished_with_code.emit(final_rc, self.label)


class PortableDownloadWorker(QThread):
    """Downloads a portable ZIP/EXE into %USERPROFILE%\\Apps\\ without UAC."""

    line_emitted = pyqtSignal(str, str)
    finished_with_code = pyqtSignal(int, str)

    def __init__(
        self,
        app_name: str,
        url: str,
        folder_name: str,
        parent: Optional[Any] = None,
    ) -> None:
        super().__init__(parent)
        self.app_name = app_name
        self.url = url
        self.folder_name = folder_name

    def run(self) -> None:
        user_root = Path(os.environ.get("USERPROFILE") or str(Path.home()))
        target_dir = user_root / "Apps" / self.folder_name
        self.line_emitted.emit("cmd", f"$ portable-install {self.app_name} -> {target_dir}")
        try:
            target_dir.mkdir(parents=True, exist_ok=True)
            self.line_emitted.emit("info", f"Downloading {self.url} ...")
            with requests.get(self.url, stream=True, timeout=30) as resp:
                resp.raise_for_status()
                suffix = ".zip" if self.url.lower().endswith(".zip") else ".exe"
                with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
                    tmp_path = Path(tmp.name)
                    downloaded = 0
                    for chunk in resp.iter_content(chunk_size=65536):
                        if chunk:
                            tmp.write(chunk)
                            downloaded += len(chunk)
            self.line_emitted.emit(
                "stdout", f"Downloaded {downloaded / (1024 * 1024):.2f} MB to temp cache."
            )
            if tmp_path.suffix == ".zip":
                with zipfile.ZipFile(tmp_path, "r") as zf:
                    # Zip Slip guard
                    resolved_target = target_dir.resolve()
                    for member in zf.infolist():
                        dest = (target_dir / member.filename).resolve()
                        if not str(dest).startswith(str(resolved_target)):
                            raise ValueError(f"Blocked unsafe archive path: {member.filename}")
                    zf.extractall(target_dir)
                tmp_path.unlink(missing_ok=True)
                self.line_emitted.emit("stdout", f"Extracted portable bundle to {target_dir}")
            else:
                dest_exe = target_dir / f"{self.folder_name}.exe"
                shutil.move(str(tmp_path), str(dest_exe))
                self.line_emitted.emit("stdout", f"Saved portable installer to {dest_exe}")
            self.finished_with_code.emit(0, f"Install {self.app_name}")
        except Exception as exc:
            self.line_emitted.emit("stderr", f"Portable download failed: {exc}")
            self.finished_with_code.emit(1, f"Install {self.app_name}")


class FunctionWorker(QThread):
    """Runs a Python callable (e.g., registry update, app scan) off the UI thread."""

    result_ready = pyqtSignal(object)
    error_occurred = pyqtSignal(str)

    def __init__(self, fn: Callable[[], Any], parent: Optional[Any] = None) -> None:
        super().__init__(parent)
        self._fn = fn

    def run(self) -> None:
        try:
            res = self._fn()
            self.result_ready.emit(res)
        except Exception as exc:
            self.error_occurred.emit(str(exc))
