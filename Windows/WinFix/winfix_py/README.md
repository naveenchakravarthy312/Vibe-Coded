# WinFix — Portable Windows 11 Utility (Python + PyQt6)

A single-file portable troubleshooting control panel for Windows 11 (x64 & ARM64), supporting both Standard User (Non-Admin) accounts and granular UAC elevation.

## Quick Run (From Source)
```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python winfix.py
```

## Build Portable `WinFix.exe`
Double-click or run:
```bat
build_portable.bat
```
Output binary: `dist\WinFix.exe` (with `apps.json` bundled inside; drop an external `apps.json` next to `WinFix.exe` to override at runtime).

## Key Guarantees
- **Launches without UAC** as a Standard User by default.
- **Non-Admin Smart Fallback**: Automatically adds `--scope user` to `winget` installs or extracts portable `.zip` releases into `%USERPROFILE%\Apps\`.
- **ARM64-Safe Debloat**: Strictly preserves Edge WebView2 Runtime, Bluetooth support services, WPNService, and ARM64 Prism dependencies.
