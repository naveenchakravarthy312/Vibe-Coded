@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   Building Portable WinFix.exe
echo ============================================

where python >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python is not installed or not in PATH.
    pause
    exit /b 1
)

echo [1/3] Installing dependencies...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
if errorlevel 1 (
    echo [ERROR] Failed to install dependencies.
    pause
    exit /b 1
)

echo [2/3] Building single-file WinFix.exe with PyInstaller...
python -m PyInstaller --noconfirm --clean --onefile --windowed --name WinFix --add-data "apps.json;." main.py
if errorlevel 1 (
    echo [ERROR] PyInstaller build failed.
    pause
    exit /b 1
)

echo [3/3] Done! Portable executable is located at:
echo       %~dp0dist\WinFix.exe
echo.
pause
