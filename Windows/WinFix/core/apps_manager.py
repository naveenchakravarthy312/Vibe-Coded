"""App catalog loading, GitHub JSON sync, live detection, and Non-Admin smart installs."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Any, Callable

import requests

from core.system_info import IS_WINDOWS, is_admin
from core.workers import CREATE_NO_WINDOW, ShellStep, run_streaming_command

LogFn = Callable[[str, str], None]


def get_app_base_dir() -> Path:
    """Return the directory where the executable or main script lives."""
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent.parent


def get_bundled_apps_json_path() -> Path:
    """Return the path to the bundled apps.json (inside PyInstaller _MEIPASS or repo root)."""
    if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
        return Path(sys._MEIPASS) / "apps.json"  # type: ignore[attr-defined]
    return Path(__file__).resolve().parent.parent / "apps.json"


def get_external_apps_json_path() -> Path:
    """Return the external override path next to the executable/script."""
    return get_app_base_dir() / "apps.json"


def resolve_apps_json_path() -> tuple[Path, bool]:
    """Return (active_path, is_external_override)."""
    ext = get_external_apps_json_path()
    if ext.is_file():
        return ext, True
    return get_bundled_apps_json_path(), False


def load_apps_catalog() -> tuple[list[dict[str, Any]], str, Path, bool]:
    """Load apps.json and return (apps, github_raw_url, active_path, is_external)."""
    path, is_external = resolve_apps_json_path()
    if not path.is_file():
        return [], "", path, is_external

    with open(path, "r", encoding="utf-8") as fh:
        data = json.load(fh)

    apps = data.get("apps", [])
    raw_url = data.get("github_raw_url", "")
    return apps, raw_url, path, is_external


def sync_json_from_github(url: str, log: LogFn) -> tuple[bool, str]:
    """Fetch apps.json from a raw GitHub URL and save as external override next to WinFix."""
    url = url.strip()
    if not url:
        return False, "GitHub raw URL is empty."

    log("INFO", f"Fetching app catalog from: {url}")
    try:
        resp = requests.get(url, timeout=15)
        resp.raise_for_status()
        data = resp.json()
    except Exception as exc:
        log("ERR", f"Failed to fetch or parse JSON: {exc}")
        return False, f"Sync failed: {exc}"

    if not isinstance(data, dict) or not isinstance(data.get("apps"), list):
        log("ERR", "Invalid apps.json format: expected an object with an 'apps' list.")
        return False, "Invalid apps.json format."

    data.setdefault("github_raw_url", url)
    out_path = get_external_apps_json_path()
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)

    log("OK", f"Saved {len(data['apps'])} apps to {out_path}")
    return True, f"Synced {len(data['apps'])} apps from GitHub."


def get_portable_apps_root() -> Path:
    """Return %USERPROFILE%\\Apps (or ~/Apps on non-Windows)."""
    home = Path(os.path.expandvars("%USERPROFILE%")) if IS_WINDOWS else Path.home()
    return home / "Apps"


def fetch_winget_installed_ids() -> set[str]:
    """Query `winget list` to collect installed package IDs (lowercase)."""
    if not IS_WINDOWS:
        return set()
    try:
        proc = subprocess.run(
            ["winget", "list", "--accept-source-agreements"],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=20,
            creationflags=CREATE_NO_WINDOW,
        )
        ids: set[str] = set()
        for line in proc.stdout.splitlines():
            for token in line.split():
                if "." in token and not token.startswith(".") and not token.endswith("."):
                    ids.add(token.lower())
        return ids
    except Exception:
        return set()


def is_app_installed(app: dict[str, Any], winget_ids: set[str] | None = None) -> bool:
    """Check if an app is installed via detect_paths, %USERPROFILE%\\Apps, or winget IDs."""
    for raw_path in app.get("detect_paths", []):
        expanded = os.path.expandvars(raw_path)
        if os.path.exists(expanded):
            return True

    exe_name = app.get("exe_name")
    app_name = app.get("name", "")
    if app_name:
        portable_dir = get_portable_apps_root() / app_name
        if exe_name and (portable_dir / exe_name).is_file():
            return True
        if portable_dir.is_dir() and any(portable_dir.iterdir()):
            return True

    winget_id = (app.get("winget_id") or "").lower()
    if winget_id and winget_ids and winget_id in winget_ids:
        return True

    return False


def _download_and_install_portable(app: dict[str, Any], url: str, log: LogFn) -> tuple[bool, str]:
    """Download a portable .zip (or .exe) into %USERPROFILE%\\Apps\\<AppName> without UAC."""
    name = app.get("name", app.get("id", "App"))
    target_dir = get_portable_apps_root() / name
    target_dir.mkdir(parents=True, exist_ok=True)

    log("INFO", f"Downloading portable package for {name} from {url}")
    if not url.lower().endswith((".zip", ".exe", ".msi")):
        log("WARN", f"URL '{url}' is not a direct .zip/.exe archive. Opening in browser...")
        if IS_WINDOWS:
            os.startfile(url)  # type: ignore[attr-defined]
        return True, f"Opened download page for {name}."

    tmp_fd, tmp_path = tempfile.mkstemp(suffix=os.path.splitext(url)[1] or ".zip")
    os.close(tmp_fd)
    try:
        with requests.get(url, stream=True, timeout=60) as resp:
            resp.raise_for_status()
            with open(tmp_path, "wb") as fh:
                for chunk in resp.iter_content(chunk_size=65536):
                    if chunk:
                        fh.write(chunk)

        if tmp_path.lower().endswith(".zip"):
            log("INFO", f"Extracting archive to {target_dir} ...")
            with zipfile.ZipFile(tmp_path, "r") as zf:
                zf.extractall(target_dir)
            log("OK", f"Installed portable {name} to {target_dir}")
            return True, f"{name} installed to {target_dir}."
        else:
            dest_exe = target_dir / os.path.basename(url.split("?")[0])
            shutil.move(tmp_path, dest_exe)
            log("OK", f"Downloaded {name} to {dest_exe}")
            return True, f"{name} downloaded to {dest_exe}."
    except Exception as exc:
        log("ERR", f"Portable download/extract failed for {name}: {exc}")
        return False, f"Failed to install {name}: {exc}"
    finally:
        if os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except OSError:
                pass


def task_install_app(app: dict[str, Any], log: LogFn) -> tuple[bool, str]:
    """Install a single app with Non-Admin Smart Fallback (--scope user or portable zip)."""
    name = app.get("name", "App")
    method = app.get("method", "winget")
    winget_id = app.get("winget_id", "")
    direct_url = app.get("direct_url", "")
    portable_url = app.get("portable_url", "") or direct_url
    admin = is_admin()

    if not IS_WINDOWS:
        log("WARN", f"Simulated install for {name} (non-Windows host).")
        return False, "App installation requires Windows."

    if method == "direct_url":
        if not portable_url:
            return False, f"No direct_url configured for {name}."
        return _download_and_install_portable(app, portable_url, log)

    # winget method
    if not winget_id:
        return False, f"No winget_id configured for {name}."

    scope_flag = "" if admin else " --scope user"
    if not admin:
        log("INFO", f"Non-Admin mode: appending '--scope user' for {name}.")

    cmd = (
        f'winget install --id "{winget_id}" --exact --silent '
        f"--accept-package-agreements --accept-source-agreements{scope_flag}"
    )
    rc = run_streaming_command(ShellStep(cmd, title=f"Install {name} via winget"), log)
    if rc == 0:
        return True, f"{name} installed successfully."

    if not admin and portable_url and portable_url.lower().endswith(".zip"):
        log("WARN", f"winget user-scope install failed (rc={rc}); falling back to portable ZIP...")
        return _download_and_install_portable(app, portable_url, log)

    return False, f"winget install failed for {name} (exit code {rc})."


def task_install_multiple_apps(apps: list[dict[str, Any]], log: LogFn) -> tuple[bool, str]:
    """Install a list of checked apps sequentially."""
    if not apps:
        return False, "No apps selected."
    ok_count = 0
    for app in apps:
        ok, _ = task_install_app(app, log)
        if ok:
            ok_count += 1
    return ok_count == len(apps), f"Installed {ok_count}/{len(apps)} selected apps."


def task_uninstall_app(app: dict[str, Any], log: LogFn) -> tuple[bool, str]:
    """Uninstall an app via portable folder removal and/or winget uninstall."""
    name = app.get("name", "App")
    winget_id = app.get("winget_id", "")
    removed_portable = False

    portable_dir = get_portable_apps_root() / name
    if portable_dir.is_dir():
        try:
            shutil.rmtree(portable_dir)
            log("OK", f"Removed portable directory: {portable_dir}")
            removed_portable = True
        except Exception as exc:
            log("ERR", f"Could not remove {portable_dir}: {exc}")

    if IS_WINDOWS and winget_id:
        cmd = f'winget uninstall --id "{winget_id}" --exact --silent --accept-source-agreements'
        rc = run_streaming_command(ShellStep(cmd, title=f"Uninstall {name} via winget"), log)
        if rc == 0 or removed_portable:
            return True, f"{name} uninstalled."
        return False, f"Uninstall failed for {name} (exit code {rc})."

    if removed_portable:
        return True, f"{name} portable folder removed."
    return False, f"No uninstall method succeeded for {name}."


def task_upgrade_all_winget(log: LogFn) -> tuple[bool, str]:
    """Run `winget upgrade --all`."""
    if not IS_WINDOWS:
        return False, "winget requires Windows."
    cmd = "winget upgrade --all --silent --accept-package-agreements --accept-source-agreements"
    rc = run_streaming_command(ShellStep(cmd, title="Upgrade All Apps (winget upgrade --all)"), log)
    if rc == 0:
        return True, "All winget packages upgraded."
    return False, f"winget upgrade --all finished with exit code {rc}."
