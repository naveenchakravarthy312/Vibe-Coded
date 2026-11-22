"""Backend action implementations for Quick Fixes, UI Tweaks, Debloat, and Admin Shortcuts."""

from __future__ import annotations

import os
import shutil
import socket
import subprocess
from typing import Callable

from core.system_info import IS_WINDOWS, is_admin, shell_execute_elevated
from core.workers import CREATE_NO_WINDOW, ShellStep, run_streaming_command

if IS_WINDOWS:
    import winreg


LogFn = Callable[[str, str], None]

# ---------------------------------------------------------------------------
# Tab 1: Quick Fixes & Diagnostics
# ---------------------------------------------------------------------------


def steps_flush_dns_renew() -> list[ShellStep]:
    """Return steps to flush DNS cache and renew IP lease."""
    return [
        ShellStep("ipconfig /flushdns", title="Flush DNS Resolver Cache"),
        ShellStep("ipconfig /renew", title="Renew DHCP IP Lease"),
    ]


def task_test_internet_ping(log: LogFn) -> tuple[bool, str]:
    """Test DNS resolution and ping 1.1.1.1 and google.com."""
    log("INFO", "Testing DNS resolution for google.com...")
    dns_ok = False
    try:
        infos = socket.getaddrinfo("google.com", 443, socket.AF_INET)
        ips = sorted({info[4][0] for info in infos})
        log("OK", f"DNS resolved google.com -> {', '.join(ips)}")
        dns_ok = True
    except Exception as exc:
        log("ERR", f"DNS resolution failed: {exc}")

    rc1 = run_streaming_command(
        ShellStep("ping -n 4 1.1.1.1", title="Ping Cloudflare DNS (1.1.1.1)"),
        log,
    )
    rc2 = run_streaming_command(
        ShellStep("ping -n 4 google.com", title="Ping google.com"),
        log,
    )

    if dns_ok and rc1 == 0 and rc2 == 0:
        return True, "Internet connectivity and DNS look healthy."
    return False, "Connectivity or DNS issues detected. Check log details."


def steps_reset_network_stack() -> list[ShellStep]:
    """Return elevated steps to reset Winsock and TCP/IP stack."""
    return [
        ShellStep("netsh winsock reset", title="Reset Winsock Catalog", requires_admin=True),
        ShellStep("netsh int ip reset", title="Reset TCP/IP Stack", requires_admin=True),
    ]


def steps_restart_explorer() -> list[ShellStep]:
    """Kill and cleanly restart Windows Explorer / Taskbar."""
    return [
        ShellStep(
            "Stop-Process -Name explorer -Force -ErrorAction SilentlyContinue; "
            "Start-Sleep -Milliseconds 600; "
            "Start-Process explorer.exe",
            title="Restart Explorer & Taskbar",
            is_powershell=True,
        )
    ]


def steps_fix_stuck_printer() -> list[ShellStep]:
    """Stop Print Spooler, clear queued spool files, and restart Spooler."""
    ps_cmd = (
        "Stop-Service -Name Spooler -Force -ErrorAction Stop; "
        "$spool = Join-Path $env:SystemRoot 'System32\\spool\\PRINTERS\\*'; "
        "Remove-Item -Path $spool -Force -ErrorAction SilentlyContinue; "
        "Start-Service -Name Spooler; "
        "Get-Service -Name Spooler | Format-Table Name, Status -AutoSize"
    )
    return [
        ShellStep(
            ps_cmd,
            title="Clear Stuck Print Spooler Queue",
            requires_admin=True,
            is_powershell=True,
        )
    ]


def task_clean_temp_cache(log: LogFn) -> tuple[bool, str]:
    """Safely wipe %TEMP% without touching user documents or browser data."""
    temp_dir = os.environ.get("TEMP") or os.environ.get("TMP")
    if not temp_dir or not os.path.isdir(temp_dir):
        return False, "Could not locate %TEMP% directory."

    log("INFO", f"Cleaning temporary files in: {temp_dir}")
    removed = 0
    skipped = 0

    for entry in os.scandir(temp_dir):
        try:
            if entry.is_dir(follow_symlinks=False):
                shutil.rmtree(entry.path, ignore_errors=False)
            else:
                os.unlink(entry.path)
            removed += 1
        except Exception:
            skipped += 1

    log("OK", f"%TEMP% cleanup complete: {removed} items removed, {skipped} in-use items skipped.")

    if IS_WINDOWS and is_admin():
        wu_dl = os.path.join(os.environ.get("SystemRoot", r"C:\Windows"), "SoftwareDistribution", "Download")
        if os.path.isdir(wu_dl):
            log("INFO", f"Cleaning Windows Update download cache: {wu_dl}")
            wu_removed = 0
            for entry in os.scandir(wu_dl):
                try:
                    if entry.is_dir(follow_symlinks=False):
                        shutil.rmtree(entry.path, ignore_errors=True)
                    else:
                        os.unlink(entry.path)
                    wu_removed += 1
                except Exception:
                    pass
            log("OK", f"Windows Update cache items cleaned: {wu_removed}")
    else:
        log("INFO", "Skipping Windows Update system cache (requires Administrator).")

    return True, f"Temp cleanup finished ({removed} removed, {skipped} in use)."


def steps_repair_sfc() -> list[ShellStep]:
    """Run sfc /scannow with elevation."""
    return [
        ShellStep("sfc /scannow", title="System File Checker (sfc /scannow)", requires_admin=True)
    ]


def task_check_phone_sync_services(log: LogFn) -> tuple[bool, str]:
    """Verify WPNService, Bluetooth services, Wi-Fi Direct adapter, and Edge WebView2 runtime."""
    if not IS_WINDOWS:
        log("WARN", "Phone Sync Doctor is only supported on Windows.")
        return False, "Unsupported OS."

    ps_script = r"""
$services = @('WPNService', 'bthserv', 'BTAGService')
Write-Output '=== Services ==='
foreach ($svc in $services) {
    $s = Get-Service -Name $svc -ErrorAction SilentlyContinue
    if ($s) {
        Write-Output ("{0,-15} Status={1,-10} StartType={2}" -f $s.Name, $s.Status, $s.StartType)
    } else {
        Write-Output ("{0,-15} NOT FOUND" -f $svc)
    }
}
Write-Output ''
Write-Output '=== Wi-Fi Direct / Wireless Adapters ==='
$adapters = Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object { $_.InterfaceDescription -match 'Wi-Fi Direct|Wireless|Wi-Fi|WLAN|Bluetooth' }
if ($adapters) {
    $adapters | ForEach-Object { Write-Output ("{0} | {1} | Status={2}" -f $_.Name, $_.InterfaceDescription, $_.Status) }
} else {
    Write-Output 'No Wi-Fi Direct or Wireless adapters detected.'
}
"""
    rc = run_streaming_command(
        ShellStep(ps_script, title="Check Windows Services & Wi-Fi Direct", is_powershell=True),
        log,
    )

    # Check Edge WebView2 Runtime via registry
    webview_found = False
    webview_ver = ""
    guid = "{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}"
    candidates = [
        (winreg.HKEY_LOCAL_MACHINE, rf"SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\{guid}"),
        (winreg.HKEY_LOCAL_MACHINE, rf"SOFTWARE\Microsoft\EdgeUpdate\Clients\{guid}"),
        (winreg.HKEY_CURRENT_USER, rf"Software\Microsoft\EdgeUpdate\Clients\{guid}"),
    ]
    for hive, subkey in candidates:
        try:
            with winreg.OpenKey(hive, subkey) as k:
                ver, _ = winreg.QueryValueEx(k, "pv")
                if ver:
                    webview_found = True
                    webview_ver = str(ver)
                    break
        except OSError:
            continue

    if webview_found:
        log("OK", f"Edge WebView2 Runtime detected (version {webview_ver}).")
    else:
        log("ERR", "Edge WebView2 Runtime NOT detected! O+ Connect and Phone Link require WebView2.")

    if rc == 0 and webview_found:
        return True, "Phone Sync & WebView2 checks completed."
    return False, "One or more Phone Sync prerequisites need attention."


def steps_fix_oplus_firewall() -> list[ShellStep]:
    """Add inbound/outbound Windows Firewall rules on Private networks for O+ Connect."""
    ps_cmd = (
        "$candidates = @("
        "\"$env:ProgramFiles\\O+ Connect\\O+ Connect.exe\", "
        "\"$env:ProgramFiles\\OPPO\\O+ Connect\\O+ Connect.exe\", "
        "\"$env:LocalAppData\\Programs\\O+ Connect\\O+ Connect.exe\""
        "); "
        "$exe = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1; "
        "if ($exe) { "
        "  Write-Output \"Found O+ Connect: $exe\"; "
        "  netsh advfirewall firewall delete rule name=\"WinFix - O+ Connect In\" | Out-Null; "
        "  netsh advfirewall firewall delete rule name=\"WinFix - O+ Connect Out\" | Out-Null; "
        "  netsh advfirewall firewall add rule name=\"WinFix - O+ Connect In\" dir=in action=allow program=\"$exe\" enable=yes profile=private; "
        "  netsh advfirewall firewall add rule name=\"WinFix - O+ Connect Out\" dir=out action=allow program=\"$exe\" enable=yes profile=private; "
        "} else { "
        "  Write-Output 'O+ Connect executable not found in standard paths; adding Private mDNS/discovery rules instead.'; "
        "  netsh advfirewall firewall delete rule name=\"WinFix - O+ Connect Discovery In\" | Out-Null; "
        "  netsh advfirewall firewall add rule name=\"WinFix - O+ Connect Discovery In\" dir=in action=allow protocol=UDP localport=5353 profile=private enable=yes; "
        "}"
    )
    return [
        ShellStep(
            ps_cmd,
            title="Configure Windows Firewall (Private Profile) for O+ Connect",
            requires_admin=True,
            is_powershell=True,
        )
    ]


# ---------------------------------------------------------------------------
# Tab 3: Windows 10 UI & Safe Debloat
# ---------------------------------------------------------------------------

_CLASSIC_MENU_KEY = r"Software\Classes\CLSID\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}\InprocServer32"
_EXPLORER_ADV_KEY = r"Software\Microsoft\Windows\CurrentVersion\Explorer\Advanced"
_ACCESSIBILITY_KEY = r"Software\Microsoft\Accessibility"
_ADVERTISING_KEY = r"Software\Microsoft\Windows\CurrentVersion\AdvertisingInfo"
_CONTENT_DELIVERY_KEY = r"Software\Microsoft\Windows\CurrentVersion\ContentDeliveryManager"


def _reg_read_dword(subkey: str, name: str, default: int | None = None) -> int | None:
    if not IS_WINDOWS:
        return default
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, subkey, 0, winreg.KEY_READ) as k:
            val, _ = winreg.QueryValueEx(k, name)
            return int(val)
    except OSError:
        return default


def _reg_write_dword(subkey: str, name: str, value: int) -> None:
    if not IS_WINDOWS:
        return
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, subkey) as k:
        winreg.SetValueEx(k, name, 0, winreg.REG_DWORD, int(value))


def _reg_key_exists(subkey: str) -> bool:
    if not IS_WINDOWS:
        return False
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, subkey, 0, winreg.KEY_READ):
            return True
    except OSError:
        return False


def get_tweak_states() -> dict[str, object]:
    """Read current HKCU tweak states for Tab 3."""
    classic_menu = _reg_key_exists(_CLASSIC_MENU_KEY)
    taskbar_al = _reg_read_dword(_EXPLORER_ADV_KEY, "TaskbarAl", default=1)
    iris_rec = _reg_read_dword(_EXPLORER_ADV_KEY, "Start_IrisRecommendations", default=1)
    track_docs = _reg_read_dword(_EXPLORER_ADV_KEY, "Start_TrackDocs", default=1)
    text_scale = _reg_read_dword(_ACCESSIBILITY_KEY, "TextScaleFactor", default=100)
    ad_info = _reg_read_dword(_ADVERTISING_KEY, "Enabled", default=1)
    copilot_btn = _reg_read_dword(_EXPLORER_ADV_KEY, "ShowCopilotButton", default=1)

    return {
        "classic_menu": classic_menu,
        "left_taskbar": taskbar_al == 0,
        "hide_start_recs": (iris_rec == 0 and track_docs == 0),
        "text_scale": text_scale if text_scale in (100, 125, 150) else (text_scale or 100),
        "ads_disabled": (ad_info == 0 and copilot_btn == 0),
    }


def task_toggle_classic_menu(enable: bool, log: LogFn) -> tuple[bool, str]:
    """Enable or disable Windows 10 Classic Right-Click Context Menu and restart Explorer."""
    if not IS_WINDOWS:
        return False, "Registry tweaks require Windows."

    if enable:
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, _CLASSIC_MENU_KEY) as k:
            winreg.SetValueEx(k, "", 0, winreg.REG_SZ, "")
        log("OK", "Added Classic Context Menu CLSID registry key.")
    else:
        for path in (
            _CLASSIC_MENU_KEY,
            r"Software\Classes\CLSID\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}",
        ):
            try:
                winreg.DeleteKey(winreg.HKEY_CURRENT_USER, path)
            except OSError:
                pass
        log("OK", "Removed Classic Context Menu CLSID registry key (restored Win 11 menu).")

    for step in steps_restart_explorer():
        run_streaming_command(step, log)

    return True, f"Classic Right-Click Menu {'Enabled' if enable else 'Disabled'}."


def task_toggle_left_taskbar(enable: bool, log: LogFn) -> tuple[bool, str]:
    """Set Taskbar alignment to Left (0) or Center (1)."""
    if not IS_WINDOWS:
        return False, "Registry tweaks require Windows."
    val = 0 if enable else 1
    _reg_write_dword(_EXPLORER_ADV_KEY, "TaskbarAl", val)
    log("OK", f"Set HKCU\\...\\Explorer\\Advanced\\TaskbarAl = {val}")
    return True, f"Left-Align Taskbar {'Enabled' if enable else 'Disabled'}."


def task_toggle_hide_start_recommendations(enable: bool, log: LogFn) -> tuple[bool, str]:
    """Hide or show Start Menu recommendations & recent items clutter."""
    if not IS_WINDOWS:
        return False, "Registry tweaks require Windows."
    val = 0 if enable else 1
    _reg_write_dword(_EXPLORER_ADV_KEY, "Start_IrisRecommendations", val)
    _reg_write_dword(_EXPLORER_ADV_KEY, "Start_TrackDocs", val)
    _reg_write_dword(_EXPLORER_ADV_KEY, "Start_AccountNotifications", val)
    log("OK", f"Updated Start recommendation keys to {val}.")
    return True, f"Hide Start Recommendations {'Enabled' if enable else 'Disabled'}."


def task_set_text_scaling(percent: int, log: LogFn) -> tuple[bool, str]:
    """Set Windows Accessibility TextScaleFactor to 100, 125, or 150%."""
    if not IS_WINDOWS:
        return False, "Registry tweaks require Windows."
    _reg_write_dword(_ACCESSIBILITY_KEY, "TextScaleFactor", percent)
    log("OK", f"Set TextScaleFactor = {percent}% in HKCU\\Software\\Microsoft\\Accessibility.")
    # Broadcast WM_SETTINGCHANGE so UWP/shell picks up the change
    run_streaming_command(
        ShellStep(
            "RUNDLL32.EXE user32.dll,UpdatePerUserSystemParameters",
            title="Refresh User System Parameters",
        ),
        log,
    )
    return True, f"Text scaling set to {percent}%."


# Strictly curated consumer bloat list; never touches Edge WebView2, Bluetooth, or ARM64 Prism dependencies.
CONSUMER_BLOAT_IDS = [
    ("TikTok", "BytedancePte.Ltd.TikTok"),
    ("Clipchamp", "Clipchamp.Clipchamp"),
    ("Microsoft News", "Microsoft.BingNews"),
    ("Bing Weather", "Microsoft.BingWeather"),
    ("Microsoft Solitaire Collection", "Microsoft.MicrosoftSolitaireCollection"),
    ("Xbox App", "Microsoft.GamingApp"),
    ("Xbox Game Bar", "Microsoft.XboxGamingOverlay"),
    ("Movies & TV", "Microsoft.ZuneVideo"),
    ("Microsoft Tips", "Microsoft.Getstarted"),
]


def task_remove_consumer_bloat(log: LogFn) -> tuple[bool, str]:
    """Run targeted winget uninstall for consumer bloat while preserving WebView2, Bluetooth, and Prism."""
    if not IS_WINDOWS:
        return False, "Requires Windows."

    log("INFO", "Preserving Edge WebView2, Bluetooth stack, and ARM64 Prism dependencies.")
    removed_count = 0
    for label, pkg_id in CONSUMER_BLOAT_IDS:
        cmd = (
            f'winget uninstall --id "{pkg_id}" --exact --silent '
            f"--accept-source-agreements"
        )
        rc = run_streaming_command(ShellStep(cmd, title=f"Uninstall {label} ({pkg_id})"), log)
        if rc == 0:
            removed_count += 1

    return True, f"Consumer bloat cleanup finished ({removed_count} packages removed/verified)."


def task_toggle_ads_telemetry(disable: bool, log: LogFn) -> tuple[bool, str]:
    """Disable or restore Windows Advertising ID, Start/Settings suggestions, and Copilot taskbar button."""
    if not IS_WINDOWS:
        return False, "Registry tweaks require Windows."

    val = 0 if disable else 1
    _reg_write_dword(_ADVERTISING_KEY, "Enabled", val)
    _reg_write_dword(_EXPLORER_ADV_KEY, "ShowCopilotButton", val)

    suggestion_keys = [
        "SubscribedContent-338388Enabled",
        "SubscribedContent-338389Enabled",
        "SubscribedContent-338393Enabled",
        "SubscribedContent-353694Enabled",
        "SubscribedContent-353696Enabled",
        "SubscribedContent-353698Enabled",
        "SystemPaneSuggestionsEnabled",
        "SoftLandingEnabled",
    ]
    for name in suggestion_keys:
        _reg_write_dword(_CONTENT_DELIVERY_KEY, name, val)

    log("OK", f"Set Advertising ID, ContentDeliveryManager suggestions, and Copilot button = {val}.")
    return True, f"Ads & Telemetry {'Disabled' if disable else 'Restored'}."


# ---------------------------------------------------------------------------
# Tab 4: Windows Admin Shortcuts
# ---------------------------------------------------------------------------

ADMIN_SHORTCUTS: list[dict[str, str]] = [
    {"id": "devmgmt", "label": "Device Manager", "cmd": "devmgmt.msc", "desc": "Hardware & driver status"},
    {"id": "services", "label": "Services", "cmd": "services.msc", "desc": "Windows background services"},
    {"id": "ncpa", "label": "Network Adapters", "cmd": "ncpa.cpl", "desc": "Wi-Fi & Ethernet interfaces"},
    {"id": "appwiz", "label": "Programs & Features", "cmd": "appwiz.cpl", "desc": "Classic uninstall panel"},
    {"id": "startup", "label": "Startup Apps", "cmd": "ms-settings:startupapps", "desc": "Manage login items"},
    {"id": "winupdate", "label": "Windows Update", "cmd": "ms-settings:windowsupdate", "desc": "Check OS & driver updates"},
    {"id": "regedit", "label": "Registry Editor", "cmd": "regedit.exe", "desc": "Open Windows Registry"},
    {"id": "admin_ps", "label": "Open Admin PowerShell", "cmd": "powershell.exe", "desc": "Elevated PowerShell console"},
]


def launch_admin_shortcut(shortcut_id: str, log: LogFn) -> None:
    """Launch a Windows admin tool or ms-settings URI."""
    entry = next((s for s in ADMIN_SHORTCUTS if s["id"] == shortcut_id), None)
    if not entry:
        log("ERR", f"Unknown shortcut: {shortcut_id}")
        return

    cmd = entry["cmd"]
    log("CMD", f"Launching {entry['label']} ({cmd})")

    if not IS_WINDOWS:
        log("WARN", f"Cannot launch '{cmd}' on non-Windows platform.")
        return

    try:
        if shortcut_id == "admin_ps":
            ok = shell_execute_elevated("powershell.exe", "-NoExit")
            log("OK" if ok else "ERR", "Elevated PowerShell launched." if ok else "UAC prompt cancelled.")
        elif cmd.startswith("ms-settings:"):
            os.startfile(cmd)  # type: ignore[attr-defined]
            log("OK", f"Opened {cmd}")
        else:
            subprocess.Popen(
                ["cmd.exe", "/c", "start", "", cmd],
                creationflags=CREATE_NO_WINDOW,
            )
            log("OK", f"Launched {cmd}")
    except Exception as exc:
        log("ERR", f"Failed to launch {cmd}: {exc}")
