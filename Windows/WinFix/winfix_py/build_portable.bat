@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo  WinFix Portable Single-File Builder (PyInstaller)
echo ========================================================

if not exist ".venv\Scripts\python.exe" (
    echo [1/3] Creating isolated virtual environment...
    python -m venv .venv
    if errorlevel 1 (
        echo ERROR: Python 3.11+ was not found on PATH.
        exit /b 1
    )
)

echo [2/3] Installing PyQt6, requests, psutil, and PyInstaller...
.venv\Scripts\python.exe -m pip install --upgrade pip >nul
.venv\Scripts\python.exe -m pip install -r requirements.txt
if errorlevel 1 (
    echo ERROR: Failed to install dependencies.
    exit /b 1
)

echo [3/3] Building portable single-file dist\WinFix.exe...
.venv\Scripts\pyinstaller.exe ^
    --noconfirm ^
    --clean ^
    --onefile ^
    --windowed ^
    --name WinFix ^
    --add-data "apps.json;." ^
    winfix.py

if errorlevel 1 (
    echo ERROR: PyInstaller build failed.
    exit /b 1
)

echo.
echo SUCCESS: Portable executable ready at dist\WinFix.exe
echo TIP: Place a custom apps.json next to WinFix.exe to override the bundled catalog.
endlocal
