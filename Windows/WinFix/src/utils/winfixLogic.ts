import type { AppCatalogItem, CpuArch, LogStreamKind, SystemTelemetry } from '../types/winfix';

export interface CommandPlan {
  command: string;
  usesUserScopeFallback: boolean;
  targetDir?: string;
  requiresAdmin: boolean;
}

export interface SimulationStep {
  delayMs: number;
  kind: LogStreamKind;
  text: string;
}

export interface DebloatPlan {
  targets: Array<{ name: string; wingetId: string }>;
  protectedComponents: string[];
  arm64Notice: string;
}

export interface ParseAppsResult {
  ok: true;
  apps: AppCatalogItem[];
}

export interface ParseAppsError {
  ok: false;
  error: string;
}

export function formatTimestamp(date: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export function buildAppInstallCommand(
  app: AppCatalogItem,
  env: Pick<SystemTelemetry, 'isAdmin' | 'userProfile' | 'arch'>,
): CommandPlan {
  const folder = app.portableFolder || app.name.replace(/[^a-zA-Z0-9_-]/g, '');
  const userAppsDir = `${env.userProfile}\\Apps\\${folder}`;

  if (app.method === 'direct_url' || (!app.wingetId && app.portableUrl)) {
    const url = app.portableUrl || `https://downloads.winfix.local/${folder}.zip`;
    return {
      command: `powershell -NoProfile -Command "New-Item -Force -ItemType Directory '${userAppsDir}' | Out-Null; Invoke-WebRequest -Uri '${url}' -OutFile '$env:TEMP\\${folder}.zip'; Expand-Archive -Force '$env:TEMP\\${folder}.zip' '${userAppsDir}'"`,
      usesUserScopeFallback: !env.isAdmin,
      targetDir: userAppsDir,
      requiresAdmin: false,
    };
  }

  const wingetId = app.wingetId || app.id;
  if (!env.isAdmin) {
    return {
      command: `winget install --id ${wingetId} -e --scope user --accept-package-agreements --accept-source-agreements --silent`,
      usesUserScopeFallback: true,
      targetDir: `${env.userProfile}\\AppData\\Local\\Programs`,
      requiresAdmin: false,
    };
  }

  return {
    command: `winget install --id ${wingetId} -e --accept-package-agreements --accept-source-agreements --silent`,
    usesUserScopeFallback: false,
    requiresAdmin: false,
  };
}

export function buildAppUninstallCommand(
  app: AppCatalogItem,
  env: Pick<SystemTelemetry, 'isAdmin' | 'userProfile'>,
): CommandPlan {
  const folder = app.portableFolder || app.name.replace(/[^a-zA-Z0-9_-]/g, '');
  const userAppsDir = `${env.userProfile}\\Apps\\${folder}`;

  if (app.method === 'direct_url' && !app.wingetId) {
    return {
      command: `powershell -NoProfile -Command "Remove-Item -Recurse -Force -ErrorAction SilentlyContinue '${userAppsDir}'"`,
      usesUserScopeFallback: !env.isAdmin,
      targetDir: userAppsDir,
      requiresAdmin: false,
    };
  }

  const wingetId = app.wingetId || app.id;
  const scopeFlag = !env.isAdmin ? ' --scope user' : '';
  return {
    command: `winget uninstall --id ${wingetId} -e${scopeFlag} --silent`,
    usesUserScopeFallback: !env.isAdmin,
    requiresAdmin: false,
  };
}

export function parseAppsJson(
  rawText: string,
  existingInstalledMap: Record<string, boolean> = {},
): ParseAppsResult | ParseAppsError {
  let parsed: unknown;
  try {
    parsed = JSON.parse(rawText);
  } catch (err) {
    return {
      ok: false,
      error: `Invalid JSON syntax: ${err instanceof Error ? err.message : 'Unable to parse file'}`,
    };
  }

  const rawList = Array.isArray(parsed)
    ? parsed
    : typeof parsed === 'object' && parsed !== null && Array.isArray((parsed as { apps?: unknown }).apps)
      ? (parsed as { apps: unknown[] }).apps
      : null;

  if (!rawList) {
    return {
      ok: false,
      error: 'Expected a JSON array or an object containing an "apps" array.',
    };
  }

  if (rawList.length === 0) {
    return {
      ok: false,
      error: 'The "apps" catalog cannot be empty.',
    };
  }

  const apps: AppCatalogItem[] = [];
  const seenIds = new Set<string>();

  for (let i = 0; i < rawList.length; i++) {
    const entry = rawList[i] as Record<string, unknown>;
    if (!entry || typeof entry !== 'object') {
      return { ok: false, error: `Entry #${i + 1} must be a JSON object.` };
    }

    const name = typeof entry.name === 'string' ? entry.name.trim() : '';
    const id =
      typeof entry.id === 'string' && entry.id.trim()
        ? entry.id.trim()
        : name.toLowerCase().replace(/[^a-z0-9]+/g, '-');

    if (!name) {
      return { ok: false, error: `Entry #${i + 1} is missing a valid "name" string.` };
    }
    if (seenIds.has(id)) {
      return { ok: false, error: `Duplicate app id "${id}" found in apps.json.` };
    }
    seenIds.add(id);

    const category =
      entry.category === 'Parent Essentials' || entry.category === 'Portable / Power Tools'
        ? entry.category
        : 'Portable / Power Tools';

    const method = entry.method === 'direct_url' ? 'direct_url' : 'winget';
    const wingetId =
      typeof entry.winget_id === 'string'
        ? entry.winget_id.trim()
        : typeof entry.wingetId === 'string'
          ? entry.wingetId.trim()
          : undefined;
    const portableUrl =
      typeof entry.portable_url === 'string'
        ? entry.portable_url.trim()
        : typeof entry.portableUrl === 'string'
          ? entry.portableUrl.trim()
          : undefined;
    const portableFolder =
      typeof entry.portable_folder === 'string'
        ? entry.portable_folder.trim()
        : typeof entry.portableFolder === 'string'
          ? entry.portableFolder.trim()
          : undefined;

    if (method === 'winget' && !wingetId) {
      return {
        ok: false,
        error: `App "${name}" uses method "winget" but is missing "winget_id".`,
      };
    }
    if (method === 'direct_url' && !portableUrl) {
      return {
        ok: false,
        error: `App "${name}" uses method "direct_url" but is missing "portable_url".`,
      };
    }

    apps.push({
      id,
      name,
      category,
      method,
      wingetId,
      portableUrl,
      portableFolder,
      description:
        typeof entry.description === 'string' && entry.description.trim()
          ? entry.description.trim()
          : 'Configured via apps.json',
      installed: existingInstalledMap[id] ?? Boolean(entry.installed),
      arm64Safe: entry.arm64_safe !== false && entry.arm64Safe !== false,
      version: typeof entry.version === 'string' ? entry.version.trim() : undefined,
    });
  }

  return { ok: true, apps };
}

export function getDebloatPlan(arch: CpuArch): DebloatPlan {
  const targets = [
    { name: 'TikTok Preload', wingetId: 'BytedancePte.Ltd.TikTok_6yccndn6064se' },
    { name: 'Microsoft Solitaire Collection', wingetId: 'Microsoft.MicrosoftSolitaireCollection_8wekyb3d8bbwe' },
    { name: 'Clipchamp Video Editor', wingetId: 'Clipchamp.Clipchamp_yxz26nhyzhsrt' },
    { name: 'Microsoft News (Bing News)', wingetId: 'Microsoft.BingNews_8wekyb3d8bbwe' },
    { name: 'MSN Weather (Bing Weather)', wingetId: 'Microsoft.BingWeather_8wekyb3d8bbwe' },
    { name: 'Xbox Gaming Overlay Clutter', wingetId: 'Microsoft.GamingApp_8wekyb3d8bbwe' },
  ];

  const protectedComponents = [
    'Microsoft.EdgeWebView2Runtime',
    'Bluetooth Support Service (bthserv)',
    'Windows Push Notifications (WPNService)',
    ...(arch === 'ARM64' ? ['ARM64 Prism Emulation & Qualcomm Runtime Dependencies'] : ['x64 System Runtime & VCLibs']),
  ];

  return {
    targets,
    protectedComponents,
    arm64Notice:
      arch === 'ARM64'
        ? 'ARM64 Safety Lock active: Preserving Edge WebView2, Bluetooth stack, and Prism x64 emulation dependencies.'
        : 'Safety Lock active: Preserving Edge WebView2, Bluetooth stack, and core Windows Shell dependencies.',
  };
}

export function getQuickFixSimulation(actionId: string, env: SystemTelemetry): {
  command: string;
  steps: SimulationStep[];
  triggersRebootPrompt?: boolean;
} {
  switch (actionId) {
    case 'flush-dns':
      return {
        command: 'ipconfig /flushdns && ipconfig /renew',
        steps: [
          { delayMs: 160, kind: 'stdout', text: 'Windows IP Configuration' },
          { delayMs: 220, kind: 'stdout', text: 'Successfully flushed the DNS Resolver Cache.' },
          { delayMs: 280, kind: 'stdout', text: `Renewing DHCP lease on ${env.adapterName}...` },
          { delayMs: 260, kind: 'stdout', text: `IPv4 Address. . . . . . . . . . . : ${env.ipv4}` },
          { delayMs: 120, kind: 'exit-ok', text: '[EXIT 0] DNS cache flushed and IPv4 lease renewed.' },
        ],
      };

    case 'test-internet':
      return {
        command: 'Resolve-DnsName google.com; ping -n 2 1.1.1.1; ping -n 2 google.com',
        steps: [
          { delayMs: 180, kind: 'stdout', text: 'DNS Resolve google.com -> 142.250.190.78 (11 ms)' },
          { delayMs: 260, kind: 'stdout', text: 'Pinging 1.1.1.1 with 32 bytes of data:' },
          { delayMs: 200, kind: 'stdout', text: 'Reply from 1.1.1.1: bytes=32 time=9ms TTL=58' },
          { delayMs: 200, kind: 'stdout', text: 'Reply from 1.1.1.1: bytes=32 time=10ms TTL=58 (Avg: 9.5ms, 0% loss)' },
          { delayMs: 220, kind: 'stdout', text: 'Pinging google.com [142.250.190.78] with 32 bytes of data:' },
          { delayMs: 200, kind: 'stdout', text: 'Reply from 142.250.190.78: bytes=32 time=14ms TTL=117 (Avg: 14ms, 0% loss)' },
          { delayMs: 120, kind: 'exit-ok', text: '[EXIT 0] Connectivity healthy — DNS OK, 1.1.1.1 (9.5ms), google.com (14ms).' },
        ],
      };

    case 'reset-network':
      return {
        command: 'netsh winsock reset && netsh int ip reset',
        triggersRebootPrompt: true,
        steps: [
          { delayMs: 220, kind: 'stdout', text: 'Successfully reset the Winsock Catalog.' },
          { delayMs: 260, kind: 'stdout', text: 'Resetting Compartment Forwarding, OK!' },
          { delayMs: 220, kind: 'stdout', text: 'Resetting Interface, OK!' },
          { delayMs: 200, kind: 'stdout', text: 'Resetting Unicast Address, OK!' },
          { delayMs: 140, kind: 'exit-ok', text: '[EXIT 0] Network stack reset complete. A restart is required to finalize.' },
        ],
      };

    case 'restart-explorer':
      return {
        command: 'taskkill /f /im explorer.exe && start explorer.exe',
        steps: [
          { delayMs: 180, kind: 'stdout', text: 'SUCCESS: The process "explorer.exe" with PID 7412 has been terminated.' },
          { delayMs: 320, kind: 'stdout', text: 'Relaunching Windows Shell (explorer.exe)...' },
          { delayMs: 220, kind: 'stdout', text: 'Taskbar and desktop shell responsive (PID 11204).' },
          { delayMs: 120, kind: 'exit-ok', text: '[EXIT 0] Explorer / Taskbar restarted cleanly.' },
        ],
      };

    case 'fix-printer':
      return {
        command: 'net stop Spooler && del /Q /F %SystemRoot%\\System32\\spool\\PRINTERS\\* && net start Spooler',
        steps: [
          { delayMs: 200, kind: 'stdout', text: 'The Print Spooler service is stopping...' },
          { delayMs: 220, kind: 'stdout', text: 'The Print Spooler service was stopped successfully.' },
          { delayMs: 220, kind: 'stdout', text: 'Cleared 2 stuck print queue files from C:\\Windows\\System32\\spool\\PRINTERS\\' },
          { delayMs: 260, kind: 'stdout', text: 'The Print Spooler service was started successfully.' },
          { delayMs: 120, kind: 'exit-ok', text: '[EXIT 0] Print spooler queue purged and restarted.' },
        ],
      };

    case 'clean-temp':
      return {
        command: 'powershell -NoProfile -Command "Get-ChildItem $env:TEMP -Force | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue"',
        steps: [
          { delayMs: 180, kind: 'stdout', text: `Scanning ${env.userProfile}\\AppData\\Local\\Temp (user docs & browser profiles untouched)...` },
          { delayMs: 280, kind: 'stdout', text: 'Removed 418 stale temporary files (342.6 MB reclaimed).' },
          { delayMs: 200, kind: 'stdout', text: 'Skipped 6 in-use session lock files safely.' },
          { delayMs: 120, kind: 'exit-ok', text: '[EXIT 0] Temporary cache cleaned with zero user data affected.' },
        ],
      };

    case 'repair-sfc':
      return {
        command: 'sfc /scannow',
        steps: [
          { delayMs: 220, kind: 'stdout', text: 'Beginning system scan. This process will take some time.' },
          { delayMs: 320, kind: 'stdout', text: 'Beginning verification phase of system scan... Verification 34% complete.' },
          { delayMs: 340, kind: 'stdout', text: 'Verification 78% complete.' },
          { delayMs: 300, kind: 'stdout', text: 'Verification 100% complete.' },
          { delayMs: 180, kind: 'stdout', text: 'Windows Resource Protection did not find any integrity violations.' },
          { delayMs: 120, kind: 'exit-ok', text: '[EXIT 0] System File Checker completed cleanly.' },
        ],
      };

    case 'check-phone-sync':
      return {
        command: 'powershell -NoProfile -Command "Get-Service WpnService,bthserv; Get-NetAdapter *Wi-Fi Direct*; Get-ItemProperty HKLM:\\...\\EdgeWebView"',
        steps: [
          { delayMs: 180, kind: 'stdout', text: '[OK] WpnService (Windows Push Notifications) .... Running (Automatic)' },
          { delayMs: 180, kind: 'stdout', text: '[OK] bthserv (Bluetooth Support Service) ........ Running (Manual/Triggered)' },
          { delayMs: 200, kind: 'stdout', text: '[OK] Wi-Fi Direct Virtual Adapter #2 ............ Up (Link Ready)' },
          { delayMs: 220, kind: 'stdout', text: `[OK] Edge WebView2 Runtime (${env.arch}) ............. v131.0.2903.86 Installed` },
          { delayMs: 120, kind: 'exit-ok', text: '[EXIT 0] All 4 Phone Sync / O+ Connect prerequisites verified.' },
        ],
      };

    case 'fix-oplus-firewall':
      return {
        command: 'New-NetFirewallRule -DisplayName "O+ Connect (Private In/Out)" -Direction Inbound/Outbound -Profile Private -Action Allow',
        steps: [
          { delayMs: 200, kind: 'stdout', text: 'Adding Inbound rule: "O+ Connect (Private In)" [Profile=Private, Action=Allow]' },
          { delayMs: 220, kind: 'stdout', text: 'Adding Outbound rule: "O+ Connect (Private Out)" [Profile=Private, Action=Allow]' },
          { delayMs: 180, kind: 'stdout', text: 'Verified Public profile remains restricted for security.' },
          { delayMs: 120, kind: 'exit-ok', text: '[EXIT 0] Windows Firewall Private network rules applied for O+ Connect.' },
        ],
      };

    default:
      return {
        command: `winfix --run ${actionId}`,
        steps: [{ delayMs: 120, kind: 'exit-ok', text: `[EXIT 0] Action ${actionId} completed.` }],
      };
  }
}
