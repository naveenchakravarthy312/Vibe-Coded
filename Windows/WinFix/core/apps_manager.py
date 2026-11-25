"""App catalog loading, GitHub JSON sync, live detection, and Non-Admin smart installs."""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Any, Callable
from urllib.parse import urlparse

import requests

from core.system_info import IS_WINDOWS, get_cpu_arch, is_admin
from core.workers import CREATE_NO_WINDOW, ShellStep, run_streaming_command

if IS_WINDOWS:
    import winreg

LogFn = Callable[[str, str], None]

_HTTP_HEADERS = {"User-Agent": "WinFix/2.0 (Windows Troubleshooting Utility)"}


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
    bundled = get_bundled_apps_json_path()
    if ext.is_file():
        is_override = ext.resolve() != bundled.resolve()
        return ext, is_override
    return bundled, False


def _normalize_github_raw_url(url: str) -> str:
    """Convert a github.com/.../blob/... URL into a raw.githubusercontent.com URL."""
    match = re.match(
        r"^https?://github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.*)$",
        url.strip(),
        re.IGNORECASE,
    )
    if match:
        owner, repo, branch, path = match.groups()
        return f"https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}"
    return url.strip()


def load_apps_catalog() -> tuple[list[dict[str, Any]], str, Path, bool]:
    """Load apps.json and return (apps, github_raw_url, active_path, is_external)."""
    path, is_external = resolve_apps_json_path()
    if not path.is_file():
        return [], "", path, is_external

    with open(path, "r", encoding="utf-8") as fh:
        data = json.load(fh)

    apps = data.get("apps", [])
    raw_url = _normalize_github_raw_url(data.get("github_raw_url", ""))
    return apps, raw_url, path, is_external


def sync_json_from_github(url: str, log: LogFn) -> tuple[bool, str]:
    """Fetch apps.json from a raw GitHub URL and save as external override next to WinFix."""
    url = _normalize_github_raw_url(url)
    if not url:
        return False, "GitHub raw URL is empty."

    log("INFO", f"Fetching app catalog from: {url}")
    try:
        resp = requests.get(url, headers=_HTTP_HEADERS, timeout=15)
        resp.raise_for_status()
        data = resp.json()
    except Exception as exc:
        log("ERR", f"Failed to fetch or parse JSON: {exc}")
        return False, f"Sync failed: {exc}"

    if not isinstance(data, dict) or not isinstance(data.get("apps"), list):
        log("ERR", "Invalid apps.json format: expected an object with an 'apps' list.")
        return False, "Invalid apps.json format."

    data["github_raw_url"] = url
    out_path = get_external_apps_json_path()
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)

    log("OK", f"Saved {len(data['apps'])} apps to {out_path}")
    return True, f"Synced {len(data['apps'])} apps from GitHub."


def get_portable_apps_root() -> Path:
    """Return %USERPROFILE%\\Apps (or ~/Apps on non-Windows)."""
    home = Path(os.path.expandvars("%USERPROFILE%")) if IS_WINDOWS else Path.home()
    return home / "Apps"


def get_installers_download_dir() -> Path:
    """Return %USERPROFILE%\\Downloads\\WinFix-Installers."""
    home = Path(os.path.expandvars("%USERPROFILE%")) if IS_WINDOWS else Path.home()
    return home / "Downloads" / "WinFix-Installers"


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
            timeout=25,
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


def fetch_registry_installed_names() -> set[str]:
    """Collect DisplayName strings from HKLM/HKCU Uninstall registry keys."""
    if not IS_WINDOWS:
        return set()
    names: set[str] = set()
    uninstall_paths = [
        (winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"),
        (winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall"),
        (winreg.HKEY_CURRENT_USER, r"Software\Microsoft\Windows\CurrentVersion\Uninstall"),
    ]
    for hive, base_path in uninstall_paths:
        try:
            with winreg.OpenKey(hive, base_path) as root_key:
                sub_count, _, _ = winreg.QueryInfoKey(root_key)
                for idx in range(sub_count):
                    try:
                        sub_name = winreg.EnumKey(root_key, idx)
                        with winreg.OpenKey(root_key, sub_name) as item_key:
                            disp, _ = winreg.QueryValueEx(item_key, "DisplayName")
                            if disp:
                                names.add(str(disp).strip().lower())
                    except OSError:
                        continue
        except OSError:
            continue
    return names


def _match_winget_id(winget_id: str, winget_ids: set[str]) -> bool:
    """Match winget ID exactly or against `winget list` ellipsis-truncated IDs."""
    wid = winget_id.lower()
    if wid in winget_ids:
        return True
    for candidate in winget_ids:
        if candidate.endswith("…") and len(candidate) >= 12:
            prefix = candidate[:-1]
            if wid.startswith(prefix):
                return True
    return False


def _find_portable_exe(portable_dir: Path, exe_name: str | None) -> bool:
    """Return True if exe_name (or any .exe up to depth 2) exists inside portable_dir."""
    if not portable_dir.is_dir():
        return False
    if exe_name:
        if (portable_dir / exe_name).is_file():
            return True
        for child in portable_dir.iterdir():
            if child.is_dir() and (child / exe_name).is_file():
                return True
    for item in portable_dir.iterdir():
        if item.is_file() and item.suffix.lower() == ".exe":
            return True
        if item.is_dir():
            for sub in item.iterdir():
                if sub.is_file() and sub.suffix.lower() == ".exe":
                    return True
    return False


def is_app_installed(
    app: dict[str, Any],
    winget_ids: set[str] | None = None,
    reg_names: set[str] | None = None,
) -> bool:
    """Check if an app is installed via detect_paths, %USERPROFILE%\\Apps, registry, or winget."""
    for raw_path in app.get("detect_paths", []):
        expanded = os.path.expandvars(raw_path)
        if os.path.exists(expanded):
            return True

    exe_name = app.get("exe_name")
    app_name = app.get("name", "")
    if app_name:
        portable_dir = get_portable_apps_root() / app_name
        if _find_portable_exe(portable_dir, exe_name):
            return True

    if exe_name and IS_WINDOWS:
        which_path = shutil.which(exe_name)
        # Skip 0-byte WindowsApps store alias stubs (e.g. python.exe before installation)
        if which_path and "windowsapps" not in which_path.lower():
            return True

    winget_id = (app.get("winget_id") or "").lower()
    if winget_id and winget_ids and _match_winget_id(winget_id, winget_ids):
        return True

    if reg_names and app_name:
        low_name = app_name.lower()
        for reg_name in reg_names:
            if low_name == reg_name or reg_name.startswith(low_name + " "):
                return True

    return False


def _resolve_github_latest_asset(url: str, prefer_zip: bool, log: LogFn) -> str | None:
    """Resolve a github.com/<owner>/<repo>/releases/latest URL to a Windows asset URL."""
    match = re.match(
        r"^https?://github\.com/([^/]+)/([^/]+)/releases/latest/?$",
        url.strip(),
        re.IGNORECASE,
    )
    if not match:
        return None
    owner, repo = match.groups()
    api_url = f"https://api.github.com/repos/{owner}/{repo}/releases/latest"
    log("INFO", f"Querying latest release assets from {owner}/{repo}...")
    try:
        resp = requests.get(api_url, headers=_HTTP_HEADERS, timeout=15)
        resp.raise_for_status()
        assets = resp.json().get("assets", [])
    except Exception as exc:
        log("WARN", f"GitHub API lookup failed: {exc}")
        return None

    arch = get_cpu_arch().lower()
    win_assets: list[tuple[int, str]] = []
    for asset in assets:
        dl = str(asset.get("browser_download_url", ""))
        name = str(asset.get("name", "")).lower()
        if not dl or any(bad in name for bad in (".sig", ".sha", ".asc", ".blockmap", "linux", "macos", "darwin", ".dmg", ".appimage", ".deb", ".rpm", ".tar.")):
            continue
        if not name.endswith((".zip", ".exe", ".msi", ".msix")):
            continue

        is_arm64 = any(tok in name for tok in ("arm64", "arm-64", "aarch64"))
        is_x64 = any(tok in name for tok in ("x64", "win64", "amd64", "x86_64", "x86-64"))
        is_x86_32 = any(tok in name for tok in ("x86", "win32", "i386", "i686")) and not is_x64

        score = 0
        if prefer_zip and name.endswith(".zip"):
            score += 20
        if "portable" in name:
            score += 10
        if "win" in name or "windows" in name or name.endswith((".exe", ".msi", ".msix")):
            score += 5

        if arch == "arm64":
            if is_arm64:
                score += 25
            elif is_x64:
                score -= 10
        elif arch == "x64":
            if is_x64:
                score += 25
            elif is_arm64:
                score -= 25
            elif is_x86_32:
                score -= 10
        elif arch == "x86":
            if is_x86_32:
                score += 25
            elif is_arm64 or is_x64:
                score -= 25

        win_assets.append((score, dl))

    if not win_assets:
        return None
    win_assets.sort(key=lambda item: item[0], reverse=True)
    return win_assets[0][1]


def _url_ext(url: str) -> str:
    """Extract lowercase file extension from a URL, ignoring query strings."""
    path = urlparse(url).path
    return os.path.splitext(path)[1].lower()


def _download_file(url: str, dest_path: Path, log: LogFn) -> bool:
    """Stream download from URL to dest_path."""
    dest_path.parent.mkdir(parents=True, exist_ok=True)
    log("INFO", f"Downloading {url} ...")
    with requests.get(url, headers=_HTTP_HEADERS, stream=True, timeout=60, allow_redirects=True) as resp:
        resp.raise_for_status()
        with open(dest_path, "wb") as fh:
            for chunk in resp.iter_content(chunk_size=65536):
                if chunk:
                    fh.write(chunk)
    size_mb = dest_path.stat().st_size / (1024 * 1024)
    log("OK", f"Downloaded {dest_path.name} ({size_mb:.1f} MB)")
    return True


def _download_and_install_url(
    app: dict[str, Any],
    url: str,
    log: LogFn,
    auto_launch: bool = True,
    prefer_zip: bool = True,
) -> tuple[bool, str]:
    """Download a .zip (extract to %USERPROFILE%\\Apps) or .exe/.msi installer."""
    name = app.get("name", app.get("id", "App"))

    resolved = _resolve_github_latest_asset(url, prefer_zip=prefer_zip, log=log)
    if resolved:
        url = resolved
        log("INFO", f"Resolved release asset: {url}")

    ext = _url_ext(url)
    if ext not in (".zip", ".exe", ".msi", ".msix", ".msixbundle"):
        return False, f"URL '{url}' is a webpage rather than a direct archive/installer."

    if ext == ".zip":
        target_dir = get_portable_apps_root() / name
        target_dir.mkdir(parents=True, exist_ok=True)
        tmp_fd, tmp_str = tempfile.mkstemp(suffix=".zip")
        os.close(tmp_fd)
        tmp_path = Path(tmp_str)
        try:
            _download_file(url, tmp_path, log)
            log("INFO", f"Extracting archive to {target_dir} ...")
            with zipfile.ZipFile(tmp_path, "r") as zf:
                zf.extractall(target_dir)
            log("OK", f"Installed portable {name} to {target_dir}")
            return True, f"{name} installed to {target_dir}."
        except Exception as exc:
            log("ERR", f"Portable download/extract failed for {name}: {exc}")
            return False, f"Failed to install {name}: {exc}"
        finally:
            if tmp_path.exists():
                try:
                    tmp_path.unlink()
                except OSError:
                    pass

    # Direct installer (.exe / .msi / .msix)
    dl_dir = get_installers_download_dir() / name
    filename = os.path.basename(urlparse(url).path) or f"{name}{ext}"
    dest_installer = dl_dir / filename
    try:
        _download_file(url, dest_installer, log)
        if auto_launch and IS_WINDOWS:
            log("INFO", f"Launching installer: {dest_installer}")
            os.startfile(str(dest_installer))  # type: ignore[attr-defined]
            return True, f"Downloaded and launched installer for {name}."
        return True, f"Installer saved to {dest_installer}."
    except Exception as exc:
        log("ERR", f"Installer download failed for {name}: {exc}")
        return False, f"Failed to download {name}: {exc}"


def _download_via_winget(
    app: dict[str, Any],
    log: LogFn,
    auto_launch: bool = True,
) -> tuple[bool, str]:
    """Use `winget download` to fetch the verified installer from the internet."""
    name = app.get("name", "App")
    winget_id = app.get("winget_id", "")
    if not winget_id:
        return False, f"No winget_id for {name}."

    dl_dir = get_installers_download_dir() / name
    dl_dir.mkdir(parents=True, exist_ok=True)

    cmd = (
        f'winget download --id "{winget_id}" --exact -d "{dl_dir}" '
        f"--accept-package-agreements --accept-source-agreements --skip-dependencies"
    )
    rc = run_streaming_command(ShellStep(cmd, title=f"Download {name} installer via winget"), log)
    if rc != 0:
        return False, f"winget download failed for {name} (exit code {rc})."

    installers = sorted(
        [
            p
            for p in dl_dir.iterdir()
            if p.is_file() and p.suffix.lower() in (".exe", ".msi", ".msix", ".msixbundle", ".zip")
        ],
        key=lambda p: p.stat().st_mtime,
        reverse=True,
    )
    if not installers:
        return False, f"winget download finished, but no installer file found in {dl_dir}."

    installer = installers[0]
    if installer.suffix.lower() == ".zip":
        target_dir = get_portable_apps_root() / name
        target_dir.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(installer, "r") as zf:
            zf.extractall(target_dir)
        log("OK", f"Extracted {installer.name} to {target_dir}")
        return True, f"{name} extracted to {target_dir}."

    log("OK", f"Installer ready: {installer}")
    if auto_launch and IS_WINDOWS:
        try:
            log("INFO", f"Launching downloaded installer: {installer.name}")
            os.startfile(str(installer))  # type: ignore[attr-defined]
            return True, f"Downloaded & launched {installer.name}."
        except Exception as exc:
            log("WARN", f"Could not auto-launch installer ({exc}); saved at {installer}")
            return True, f"Installer saved to {installer}."

    return True, f"Installer saved to {installer}."


def _open_fallback_webpage(app: dict[str, Any], log: LogFn) -> tuple[bool, str]:
    """Open the official download webpage as a last-resort fallback."""
    name = app.get("name", "App")
    url = app.get("portable_url") or app.get("direct_url") or app.get("web_url")
    if not url:
        return False, f"No fallback URL available for {name}."
    log("INFO", f"Opening official download page for {name}: {url}")
    if IS_WINDOWS:
        os.startfile(url)  # type: ignore[attr-defined]
    return True, f"Opened download page for {name}."


def task_download_installer(app: dict[str, Any], log: LogFn, auto_launch: bool = False) -> tuple[bool, str]:
    """Download the installer for an app from the internet without running silent install."""
    name = app.get("name", "App")
    if not IS_WINDOWS:
        return False, "Installer download requires Windows."

    direct_url = app.get("direct_url") or ""
    portable_url = app.get("portable_url") or ""
    winget_id = app.get("winget_id") or ""

    if direct_url:
        ok, msg = _download_and_install_url(app, direct_url, log, auto_launch=auto_launch, prefer_zip=False)
        if ok:
            return True, msg

    if winget_id:
        ok, msg = _download_via_winget(app, log, auto_launch=auto_launch)
        if ok:
            return True, msg

    if portable_url:
        ok, msg = _download_and_install_url(app, portable_url, log, auto_launch=auto_launch, prefer_zip=True)
        if ok:
            return True, msg

    return _open_fallback_webpage(app, log)


def task_install_app(app: dict[str, Any], log: LogFn, auto_launch_installer: bool = True) -> tuple[bool, str]:
    """Install an app, using smart Non-Admin fallbacks (user scope -> portable zip -> download installer -> web)."""
    name = app.get("name", "App")
    method = app.get("method", "winget")
    winget_id = app.get("winget_id") or ""
    direct_url = app.get("direct_url") or ""
    portable_url = app.get("portable_url") or ""
    supports_user_scope = bool(app.get("supports_user_scope", True))
    admin = is_admin()

    if not IS_WINDOWS:
        log("WARN", f"Simulated install for {name} (non-Windows host).")
        return False, "App installation requires Windows."

    # 1. Explicit direct_url method
    if method == "direct_url" and (direct_url or portable_url):
        url = direct_url or portable_url
        ok, msg = _download_and_install_url(app, url, log, auto_launch=auto_launch_installer, prefer_zip=True)
        if ok:
            return True, msg

    # 2. Admin winget silent install (works for both machine and user-scope packages)
    if admin and winget_id:
        cmd = (
            f'winget install --id "{winget_id}" --exact --silent '
            f"--accept-package-agreements --accept-source-agreements"
        )
        rc = run_streaming_command(ShellStep(cmd, title=f"Install {name} via winget"), log)
        if rc == 0:
            return True, f"{name} installed successfully."
        log("WARN", f"winget install exited with {rc}; trying fallback download...")

    # 3. Standard User (Non-Admin): try --scope user ONLY when supported
    if not admin and winget_id and supports_user_scope:
        log("INFO", f"Non-Admin mode: installing {name} with '--scope user'...")
        cmd = (
            f'winget install --id "{winget_id}" --exact --silent '
            f"--accept-package-agreements --accept-source-agreements --scope user"
        )
        rc = run_streaming_command(ShellStep(cmd, title=f"Install {name} (User Scope)"), log)
        if rc == 0:
            return True, f"{name} installed (user scope)."
        log("WARN", f"User-scope winget install failed (rc={rc}); trying portable/installer fallback...")

    # 4. Portable ZIP fallback (GitHub releases or direct .zip)
    if portable_url:
        ok, msg = _download_and_install_url(app, portable_url, log, auto_launch=auto_launch_installer, prefer_zip=True)
        if ok:
            return True, msg

    # 5. Direct installer URL fallback
    if direct_url:
        ok, msg = _download_and_install_url(app, direct_url, log, auto_launch=auto_launch_installer, prefer_zip=False)
        if ok:
            return True, msg

    # 6. Official winget installer download fallback (saves to Downloads\WinFix-Installers & launches)
    if winget_id:
        if not admin and not supports_user_scope:
            log("INFO", f"{name} requires Administrator for silent install — downloading official installer instead...")
        ok, msg = _download_via_winget(app, log, auto_launch=auto_launch_installer)
        if ok:
            return True, msg

    # 7. Last resort: open official webpage
    return _open_fallback_webpage(app, log)


def task_install_multiple_apps(apps: list[dict[str, Any]], log: LogFn) -> tuple[bool, str]:
    """Install a list of checked apps sequentially (avoiding overlapping GUI installers in batch)."""
    if not apps:
        return False, "No apps selected."
    ok_count = 0
    for app in apps:
        ok, _ = task_install_app(app, log, auto_launch_installer=False)
        if ok:
            ok_count += 1

    dl_root = get_installers_download_dir()
    if IS_WINDOWS and dl_root.is_dir() and any(dl_root.iterdir()):
        log("INFO", f"Downloaded installers saved under: {dl_root}")
        try:
            os.startfile(str(dl_root))  # type: ignore[attr-defined]
        except Exception:
            pass

    return ok_count == len(apps), f"Processed {ok_count}/{len(apps)} selected apps."


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
