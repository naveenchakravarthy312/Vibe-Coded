#!/usr/bin/env python3
"""
VibeWatch Setup & Linux System Integration Installer.
- Provisions local `.venv` with lean dependencies (PyQt6, dbus-next, psutil).
- Writes and enables `~/.config/systemd/user/vibewatch.service` for `daemon.py`.
- Writes `~/.local/share/applications/vibewatch.desktop` (`StartupWMClass=VibeWatch`)
  for clean KDE Plasma menu and KRunner (Alt+Space) launching.
"""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Optional


APP_DIR = Path(__file__).resolve().parent
SYSTEMD_USER_DIR = Path.home() / ".config" / "systemd" / "user"
APPLICATIONS_DIR = Path.home() / ".local" / "share" / "applications"
SERVICE_FILE = SYSTEMD_USER_DIR / "vibewatch.service"
DESKTOP_FILE = APPLICATIONS_DIR / "vibewatch.desktop"


def render_systemd_unit(app_dir: Path = APP_DIR, python_exec: Optional[Path] = None) -> str:
    py = python_exec or (app_dir / ".venv" / "bin" / "python")
    daemon = app_dir / "daemon.py"
    return (
        "[Unit]\n"
        "Description=VibeWatch Native Linux Activity & Screen-Time Daemon (KDE Plasma 6 Wayland)\n"
        "PartOf=graphical-session.target\n"
        "After=graphical-session.target plasma-kwin_wayland.service\n"
        "\n"
        "[Service]\n"
        "Type=simple\n"
        f"WorkingDirectory={app_dir}\n"
        f"ExecStart={py} {daemon}\n"
        "Restart=on-failure\n"
        "RestartSec=3\n"
        "Environment=PYTHONUNBUFFERED=1\n"
        "Nice=10\n"
        "MemoryMax=128M\n"
        "\n"
        "[Install]\n"
        "WantedBy=graphical-session.target\n"
    )


def render_desktop_entry(app_dir: Path = APP_DIR, python_exec: Optional[Path] = None) -> str:
    py = python_exec or (app_dir / ".venv" / "bin" / "python")
    dashboard = app_dir / "dashboard.py"
    return (
        "[Desktop Entry]\n"
        "Type=Application\n"
        "Version=1.0\n"
        "Name=VibeWatch\n"
        "GenericName=Activity & Screen-Time Tracker\n"
        "Comment=Zero-chrome native activity and screen-time viewer for KDE Plasma 6\n"
        f"Exec={py} {dashboard}\n"
        f"Path={app_dir}\n"
        "Icon=utilities-system-monitor\n"
        "Terminal=false\n"
        "Categories=Utility;Monitor;Qt;\n"
        "Keywords=activity;screen time;tracker;productivity;kde;wayland;\n"
        "StartupWMClass=VibeWatch\n"
        "StartupNotify=false\n"
    )


def ensure_virtualenv(app_dir: Path = APP_DIR, dry_run: bool = False) -> Path:
    venv_dir = app_dir / ".venv"
    venv_python = venv_dir / "bin" / "python"
    req_file = app_dir / "requirements.txt"

    if not venv_python.exists():
        print(f"[setup] Creating virtual environment at {venv_dir}...")
        if not dry_run:
            subprocess.run([sys.executable, "-m", "venv", str(venv_dir)], check=True)

    if req_file.exists():
        print(f"[setup] Installing dependencies from {req_file.name}...")
        if not dry_run:
            subprocess.run(
                [str(venv_python), "-m", "pip", "install", "--upgrade", "pip"],
                check=False,
            )
            subprocess.run(
                [str(venv_python), "-m", "pip", "install", "-r", str(req_file)],
                check=True,
            )
    return venv_python


def install_all(app_dir: Path = APP_DIR, dry_run: bool = False, skip_venv: bool = False) -> None:
    venv_python = (app_dir / ".venv" / "bin" / "python")
    if not skip_venv:
        venv_python = ensure_virtualenv(app_dir=app_dir, dry_run=dry_run)

    unit_text = render_systemd_unit(app_dir=app_dir, python_exec=venv_python)
    desktop_text = render_desktop_entry(app_dir=app_dir, python_exec=venv_python)

    if dry_run:
        print(f"\n--- {SERVICE_FILE} ---\n{unit_text}")
        print(f"--- {DESKTOP_FILE} ---\n{desktop_text}")
        return

    SYSTEMD_USER_DIR.mkdir(parents=True, exist_ok=True)
    SERVICE_FILE.write_text(unit_text, encoding="utf-8")
    print(f"[setup] Wrote {SERVICE_FILE}")

    APPLICATIONS_DIR.mkdir(parents=True, exist_ok=True)
    DESKTOP_FILE.write_text(desktop_text, encoding="utf-8")
    print(f"[setup] Wrote {DESKTOP_FILE}")

    # Enable and restart systemd user service so updated code takes effect immediately
    if shutil.which("systemctl"):
        subprocess.run(["systemctl", "--user", "daemon-reload"], check=False)
        res = subprocess.run(
            ["systemctl", "--user", "enable", "--now", "vibewatch.service"],
            check=False,
        )
        if res.returncode == 0:
            subprocess.run(["systemctl", "--user", "restart", "vibewatch.service"], check=False)
            print("[setup] Enabled and started systemd --user service: vibewatch.service")
        else:
            print(
                "[setup] Note: systemctl --user returned non-zero (headless session?). "
                "Run `systemctl --user enable --now vibewatch.service` inside Plasma."
            )

    if shutil.which("update-desktop-database"):
        subprocess.run(["update-desktop-database", str(APPLICATIONS_DIR)], check=False)
    if shutil.which("kbuildsycoca6"):
        subprocess.run(["kbuildsycoca6", "--noincremental"], check=False)

    print("[setup] VibeWatch installation complete. Launch via KRunner (Alt+Space) -> 'VibeWatch'.")


def uninstall_all() -> None:
    if shutil.which("systemctl"):
        subprocess.run(["systemctl", "--user", "disable", "--now", "vibewatch.service"], check=False)
    if SERVICE_FILE.exists():
        SERVICE_FILE.unlink()
        print(f"[setup] Removed {SERVICE_FILE}")
    if DESKTOP_FILE.exists():
        DESKTOP_FILE.unlink()
        print(f"[setup] Removed {DESKTOP_FILE}")
    if shutil.which("systemctl"):
        subprocess.run(["systemctl", "--user", "daemon-reload"], check=False)
    print("[setup] VibeWatch service and launcher uninstalled (SQLite data preserved).")


def main() -> None:
    parser = argparse.ArgumentParser(description="Install VibeWatch systemd service and KDE desktop entry.")
    parser.add_argument("--dry-run", action="store_true", help="Print unit and desktop files without writing.")
    parser.add_argument("--skip-venv", action="store_true", help="Skip creating/updating .venv.")
    parser.add_argument("--uninstall", action="store_true", help="Disable service and remove desktop entry.")
    args = parser.parse_args()

    if args.uninstall:
        uninstall_all()
    else:
        install_all(dry_run=args.dry_run, skip_venv=args.skip_venv)


if __name__ == "__main__":
    main()
