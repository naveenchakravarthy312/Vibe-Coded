import winfixPy from '../../winfix_py/winfix.py?raw';
import winfixCorePy from '../../winfix_py/winfix_core.py?raw';
import appsJson from '../../winfix_py/apps.json?raw';
import requirementsTxt from '../../winfix_py/requirements.txt?raw';
import buildPortableBat from '../../winfix_py/build_portable.bat?raw';
import readmeMd from '../../winfix_py/README.md?raw';
import type { PythonBundleFile } from '../types/winfix';

export function getPythonBundleFiles(currentAppsJson?: string): PythonBundleFile[] {
  return [
    {
      path: 'winfix.py',
      language: 'python',
      summary: 'PyQt6 main desktop window, matte dark QSS theme, 4 control tabs, and collapsible live log drawer.',
      content: winfixPy,
    },
    {
      path: 'winfix_core.py',
      language: 'python',
      summary: 'System telemetry, QThread workers, ShellExecuteW UAC elevation, Non-Admin fallback, and HKCU registry helpers.',
      content: winfixCorePy,
    },
    {
      path: 'apps.json',
      language: 'json',
      summary: 'Customizable Parent Essentials & Portable / Power Tools app catalog (bundled & external override supported).',
      content: currentAppsJson || appsJson,
    },
    {
      path: 'requirements.txt',
      language: 'text',
      summary: 'Pinned Python dependencies (PyQt6, requests, psutil, pyinstaller).',
      content: requirementsTxt,
    },
    {
      path: 'build_portable.bat',
      language: 'batch',
      summary: 'One-click Windows build script creating single-file dist\\WinFix.exe with bundled apps.json.',
      content: buildPortableBat,
    },
    {
      path: 'README.md',
      language: 'markdown',
      summary: 'Quick-start guide for running from Python source or packaging portable WinFix.exe.',
      content: readmeMd,
    },
  ];
}
