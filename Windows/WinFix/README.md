# WinFix

Portable Windows 10/11 troubleshooting, app deployment, and UI recovery utility built with Python and PyQt6. Designed for power users supporting both a parent's laptop and any general Windows PC — including Standard User (Non-Admin) accounts and ARM64 devices.

## Features
- **Persistent System Telemetry Header**: Windows Version & Build, CPU Architecture (`ARM64` / `x64`), Privilege Badge (`Administrator` / `Standard User (Non-Admin)`), Local IPv4 & Active Adapter, and one-click `Relaunch as Admin`.
- **Collapsible Live Log Drawer**: Streams real-time stdout/stderr and exit codes from PowerShell, `winget`, `netsh`, and registry operations.
- **Tab 1 — Quick Fixes & Diagnostics**: DNS flush/renew, internet & ping test, Winsock/TCP-IP reset, Explorer restart, Print Spooler queue reset, safe `%TEMP%` cleanup, `sfc /scannow`, and Phone Sync / O+ Connect Doctor (services, Wi-Fi Direct, WebView2, Private firewall rules).
- **Tab 2 — App Installer & Updater (`apps.json`)**: Loads bundled `apps.json` (with external override if `apps.json` sits next to `WinFix.exe`), supports `Sync JSON from GitHub`, live installation detection, and **Non-Admin Smart Fallback** (`--scope user` or portable `.zip` extraction to `%USERPROFILE%\Apps\`).
- **Tab 3 — Win 10 UI & Debloat**: Classic right-click context menu, left-aligned taskbar, Start recommendations cleanup, `100% / 125% / 150%` text scaling, ARM64-safe consumer bloat removal, and Ads/Telemetry/Copilot toggle.
- **Tab 4 — Admin Shortcuts**: Direct launchers for Device Manager, Services, Network Adapters, Programs & Features, Startup Apps, Windows Update, Registry Editor, and Elevated PowerShell.

## Run from Source
```bat
pip install -r requirements.txt
python main.py
```

## Build Portable `WinFix.exe`
Run `build_portable.bat` on Windows:
```bat
build_portable.bat
```
Output will be generated at `dist\WinFix.exe` with `apps.json` bundled inside.
