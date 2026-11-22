"""System telemetry, architecture detection, and UAC elevation helpers."""

from __future__ import annotations

import ctypes
import os
import platform
import socket
import subprocess
import sys
from dataclasses import dataclass

import psutil

IS_WINDOWS = sys.platform == "win32"

if IS_WINDOWS:
    import winreg


@dataclass
class SystemTelemetry:
    """Snapshot of local system telemetry for the persistent header."""

    os_version: str
    arch: str
    is_admin: bool
    ipv4: str
    adapter: str


def is_admin() -> bool:
    """Return True if the current process has Administrator privileges."""
    if not IS_WINDOWS:
        return False
    try:
        return bool(ctypes.windll.shell32.IsUserAnAdmin())
    except Exception:
        return False


def get_cpu_arch() -> str:
    """Return normalized CPU architecture: 'ARM64', 'x64', or 'x86'."""
    arch_candidates = [
        os.environ.get("PROCESSOR_ARCHITEW6432", ""),
        os.environ.get("PROCESSOR_ARCHITECTURE", ""),
        platform.machine(),
    ]
    for raw in arch_candidates:
        val = raw.strip().upper()
        if not val:
            continue
        if "ARM64" in val or "AARCH64" in val:
            return "ARM64"
        if val in ("AMD64", "X86_64", "X64"):
            return "x64"
        if val in ("X86", "I386", "I686"):
            return "x86"
    return platform.machine() or "Unknown"


def get_windows_version() -> str:
    """Return a concise Windows version string such as 'Win 11 24H2 (26100)'."""
    if not IS_WINDOWS:
        return f"{platform.system()} {platform.release()}"

    try:
        key_path = r"SOFTWARE\Microsoft\Windows NT\CurrentVersion"
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, key_path) as key:
            def _read(name: str, default: str = "") -> str:
                try:
                    val, _ = winreg.QueryValueEx(key, name)
                    return str(val)
                except OSError:
                    return default

            display_ver = _read("DisplayVersion") or _read("ReleaseId")
            build_str = _read("CurrentBuild") or _read("CurrentBuildNumber")
            product_name = _read("ProductName", "Windows")

            try:
                build_num = int(build_str)
            except ValueError:
                build_num = 0

            if build_num >= 22000:
                prefix = "Win 11"
            elif "Windows 10" in product_name or build_num >= 10240:
                prefix = "Win 10"
            else:
                prefix = product_name

            parts = [prefix]
            if display_ver:
                parts.append(display_ver)
            if build_str:
                parts.append(f"(Build {build_str})")
            return " ".join(parts)
    except Exception:
        return f"Windows {platform.release()}"


def get_network_info() -> tuple[str, str]:
    """Return (ipv4_address, active_adapter_name) for the primary active interface."""
    try:
        addrs = psutil.net_if_addrs()
        stats = psutil.net_if_stats()
    except Exception:
        return ("Unavailable", "Unknown")

    candidates: list[tuple[int, str, str]] = []
    skip_keywords = ("loopback", "vethernet", "virtual", "vmware", "vbox", "wsl", "hyper-v", "tailscale")

    for iface, addr_list in addrs.items():
        st = stats.get(iface)
        if not st or not st.isup:
            continue
        low = iface.lower()
        for addr in addr_list:
            if addr.family == socket.AF_INET and not addr.address.startswith("127.") and not addr.address.startswith("169.254."):
                priority = 2 if any(k in low for k in skip_keywords) else 0
                if "wi-fi" in low or "wifi" in low or "wlan" in low or "ethernet" in low:
                    priority = min(priority, 0)
                candidates.append((priority, iface, addr.address))

    if candidates:
        candidates.sort(key=lambda item: item[0])
        _, iface, ip = candidates[0]
        return (ip, iface)

    return ("Disconnected", "No Active Adapter")


def get_telemetry_snapshot() -> SystemTelemetry:
    """Collect a fresh SystemTelemetry snapshot."""
    ipv4, adapter = get_network_info()
    return SystemTelemetry(
        os_version=get_windows_version(),
        arch=get_cpu_arch(),
        is_admin=is_admin(),
        ipv4=ipv4,
        adapter=adapter,
    )


def relaunch_as_admin() -> bool:
    """Trigger UAC elevation to relaunch WinFix as Administrator."""
    if not IS_WINDOWS:
        return False

    if getattr(sys, "frozen", False):
        executable = sys.executable
        params = subprocess.list2cmdline(sys.argv[1:])
    else:
        executable = sys.executable
        params = subprocess.list2cmdline(sys.argv)

    rc = ctypes.windll.shell32.ShellExecuteW(None, "runas", executable, params, None, 1)
    return int(rc) > 32


def shell_execute_elevated(executable: str, params: str = "", show: int = 1) -> bool:
    """Launch an external executable or script with UAC 'runas' elevation."""
    if not IS_WINDOWS:
        return False
    rc = ctypes.windll.shell32.ShellExecuteW(None, "runas", executable, params, None, show)
    return int(rc) > 32
