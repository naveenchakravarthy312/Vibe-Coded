import { Layers, Package, Shield, Wrench } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AdminShortcutsTab } from './components/AdminShortcutsTab';
import { AppInstallerTab } from './components/AppInstallerTab';
import { LiveLogDrawer } from './components/LiveLogDrawer';
import {
  AppsJsonEditorModal,
  ElevationModal,
  GithubSyncModal,
  PythonKitModal,
  RebootPromptModal,
} from './components/Modals';
import { QuickFixesTab } from './components/QuickFixesTab';
import { TelemetryHeader } from './components/TelemetryHeader';
import { TweaksTab } from './components/TweaksTab';
import {
  DEFAULT_APPS_CATALOG,
  DEFAULT_GITHUB_APPS_URL,
  SYSTEM_PROFILES,
} from './data/defaultApps';
import type {
  AppCatalogItem,
  LogEntry,
  LogStreamKind,
  PendingElevation,
  SystemTelemetry,
  TabId,
  TweakState,
} from './types/winfix';
import {
  buildAppInstallCommand,
  buildAppUninstallCommand,
  formatTimestamp,
  getDebloatPlan,
  getQuickFixSimulation,
  parseAppsJson,
  type SimulationStep,
} from './utils/winfixLogic';

const TABS: Array<{ id: TabId; label: string; shortcut: string; icon: typeof Wrench }> = [
  { id: 'quick-fixes', label: 'Quick Fixes & Diagnostics', shortcut: '1', icon: Wrench },
  { id: 'apps', label: 'App Installer & Updater', shortcut: '2', icon: Package },
  { id: 'tweaks', label: 'Windows 10 UI & Safe Debloat', shortcut: '3', icon: Layers },
  { id: 'shortcuts', label: 'Windows Admin Shortcuts', shortcut: '4', icon: Shield },
];

export default function App() {
  const [activeTab, setActiveTab] = useState<TabId>('quick-fixes');
  const [activeProfile, setActiveProfile] = useState<'parent-arm64' | 'workstation-x64'>(
    'parent-arm64',
  );
  const [telemetry, setTelemetry] = useState<SystemTelemetry>(SYSTEM_PROFILES['parent-arm64']);
  const [apps, setApps] = useState<AppCatalogItem[]>(DEFAULT_APPS_CATALOG);
  const [checkedIds, setCheckedIds] = useState<Record<string, boolean>>({
    'oplus-connect': true,
    'vlc-media-player': true,
    'flow-launcher': true,
  });
  const [tweaks, setTweaks] = useState<TweakState>({
    classicContextMenu: false,
    leftAlignTaskbar: true,
    hideStartRecommendations: false,
    textScaling: 100,
    consumerBloatRemoved: false,
    adsTelemetryDisabled: false,
  });

  const [logExpanded, setLogExpanded] = useState(true);
  const [runningActionId, setRunningActionId] = useState<string | null>(null);
  const [runningLabel, setRunningLabel] = useState<string | null>(null);
  const [logs, setLogs] = useState<LogEntry[]>(() => [
    {
      id: 'init-1',
      timestamp: formatTimestamp(),
      kind: 'info',
      text: `WinFix initialized — ${SYSTEM_PROFILES['parent-arm64'].windowsVersion} (${SYSTEM_PROFILES['parent-arm64'].windowsBuild}) | ARM64 | Standard User (Non-Admin)`,
    },
    {
      id: 'init-2',
      timestamp: formatTimestamp(),
      kind: 'info',
      text: 'Launched without UAC prompt. Standard User actions run immediately; Admin tasks trigger granular ShellExecuteW("runas").',
    },
  ]);

  const [pendingElevation, setPendingElevation] = useState<PendingElevation | null>(null);
  const [rebootModalOpen, setRebootModalOpen] = useState(false);
  const [appsEditorOpen, setAppsEditorOpen] = useState(false);
  const [githubSyncOpen, setGithubSyncOpen] = useState(false);
  const [githubUrl, setGithubUrl] = useState(DEFAULT_GITHUB_APPS_URL);
  const [pythonKitOpen, setPythonKitOpen] = useState(false);

  const timersRef = useRef<number[]>([]);
  const logSeqRef = useRef(10);

  const appendLog = useCallback((kind: LogStreamKind, text: string) => {
    logSeqRef.current += 1;
    const entry: LogEntry = {
      id: `log-${logSeqRef.current}`,
      timestamp: formatTimestamp(),
      kind,
      text,
    };
    setLogs((prev) => [...prev.slice(-600), entry]);
  }, []);

  const clearPendingTimers = useCallback(() => {
    timersRef.current.forEach((id) => window.clearTimeout(id));
    timersRef.current = [];
  }, []);

  useEffect(() => {
    return () => clearPendingTimers();
  }, [clearPendingTimers]);

  // Keyboard shortcuts: Ctrl+1..4 tabs, Ctrl+L log drawer, Escape modals
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setPendingElevation(null);
        setRebootModalOpen(false);
        setAppsEditorOpen(false);
        setGithubSyncOpen(false);
        setPythonKitOpen(false);
        return;
      }
      if ((e.ctrlKey || e.metaKey) && !e.shiftKey) {
        if (e.key.toLowerCase() === 'l') {
          e.preventDefault();
          setLogExpanded((prev) => !prev);
        } else if (e.key === '1') {
          e.preventDefault();
          setActiveTab('quick-fixes');
        } else if (e.key === '2') {
          e.preventDefault();
          setActiveTab('apps');
        } else if (e.key === '3') {
          e.preventDefault();
          setActiveTab('tweaks');
        } else if (e.key === '4') {
          e.preventDefault();
          setActiveTab('shortcuts');
        }
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const runStreamedSteps = useCallback(
    (
      actionId: string,
      label: string,
      commandLine: string,
      steps: SimulationStep[],
      onComplete?: () => void,
    ) => {
      setLogExpanded(true);
      setRunningActionId(actionId);
      setRunningLabel(label);
      appendLog('cmd', `$ ${commandLine}`);

      let elapsed = 0;
      steps.forEach((step, idx) => {
        elapsed += step.delayMs;
        const timerId = window.setTimeout(() => {
          appendLog(step.kind, step.text);
          if (idx === steps.length - 1) {
            setRunningActionId(null);
            setRunningLabel(null);
            onComplete?.();
          }
        }, elapsed);
        timersRef.current.push(timerId);
      });
    },
    [appendLog],
  );

  // Switch simulated Windows target profile
  const handleSwitchProfile = (profile: 'parent-arm64' | 'workstation-x64') => {
    setActiveProfile(profile);
    const next = SYSTEM_PROFILES[profile];
    setTelemetry(next);
    appendLog(
      'info',
      `Target switched -> ${next.windowsVersion} (${next.windowsBuild}) | ${next.arch} | ${
        next.isAdmin ? 'Administrator' : 'Standard User (Non-Admin)'
      } | ${next.ipv4}`,
    );
  };

  // Relaunch as Admin button in top header
  const handleRelaunchAdmin = () => {
    setPendingElevation({
      actionId: 'relaunch-admin',
      title: 'Relaunch WinFix as Administrator',
      commandPreview: 'ShellExecuteW(None, "runas", "WinFix.exe", "", None, SW_SHOWNORMAL)',
      reason:
        'Elevates the entire WinFix session so system-wide repairs (SFC, Print Spooler, Winsock, Firewall) execute without additional prompts.',
      onApprove: () => {
        setPendingElevation(null);
        setTelemetry((prev) => ({ ...prev, isAdmin: true }));
        appendLog('cmd', '$ ShellExecuteW("runas", "WinFix.exe")');
        appendLog('exit-ok', '[EXIT 0] Relaunched with Administrator token (UAC approved).');
      },
    });
  };

  // Tab 1: Quick Fixes & Diagnostics
  const handleRunQuickFix = (
    actionId: string,
    label: string,
    requiresAdmin: boolean,
    commandPreview: string,
  ) => {
    const execute = (elevatedViaUac = false) => {
      const sim = getQuickFixSimulation(actionId, telemetry);
      const prefix = elevatedViaUac ? '[UAC runas] ' : '';
      runStreamedSteps(actionId, label, `${prefix}${sim.command}`, sim.steps, () => {
        if (sim.triggersRebootPrompt) {
          setRebootModalOpen(true);
        }
      });
    };

    if (requiresAdmin && !telemetry.isAdmin) {
      setPendingElevation({
        actionId,
        title: label,
        commandPreview,
        reason: 'This action modifies protected Windows system services, firewall rules, or system files.',
        onApprove: () => {
          setPendingElevation(null);
          execute(true);
        },
      });
      return;
    }

    execute(false);
  };

  // Tab 2: App Installer & Updater
  const handleInstallApp = (app: AppCatalogItem) => {
    const plan = buildAppInstallCommand(app, telemetry);
    const steps: SimulationStep[] =
      app.method === 'direct_url'
        ? [
            {
              delayMs: 180,
              kind: 'info',
              text: `Downloading portable archive for ${app.name}...`,
            },
            {
              delayMs: 260,
              kind: 'stdout',
              text: `Verified archive integrity and extracted to ${plan.targetDir}`,
            },
            {
              delayMs: 140,
              kind: 'exit-ok',
              text: `[EXIT 0] ${app.name} installed cleanly into %USERPROFILE%\\Apps\\ (no UAC required).`,
            },
          ]
        : [
            {
              delayMs: 180,
              kind: 'stdout',
              text: `Found ${app.name} [${app.wingetId}] ${app.version ? `v${app.version}` : ''}`,
            },
            {
              delayMs: 240,
              kind: 'stdout',
              text: plan.usesUserScopeFallback
                ? 'Standard User detected -> applying --scope user install target.'
                : 'Installing package with system scope...',
            },
            {
              delayMs: 200,
              kind: 'stdout',
              text: 'Successfully verified installer hash. Starting package install...',
            },
            {
              delayMs: 140,
              kind: 'exit-ok',
              text: `[EXIT 0] Successfully installed ${app.name}.`,
            },
          ];

    runStreamedSteps(`install-${app.id}`, `Install ${app.name}`, plan.command, steps, () => {
      setApps((prev) =>
        prev.map((item) => (item.id === app.id ? { ...item, installed: true } : item)),
      );
    });
  };

  const handleUninstallApp = (app: AppCatalogItem) => {
    const plan = buildAppUninstallCommand(app, telemetry);
    const steps: SimulationStep[] = [
      {
        delayMs: 200,
        kind: 'stdout',
        text: `Removing ${app.name} (${app.method === 'direct_url' && !app.wingetId ? plan.targetDir : app.wingetId})...`,
      },
      {
        delayMs: 180,
        kind: 'exit-ok',
        text: `[EXIT 0] Successfully uninstalled ${app.name}.`,
      },
    ];

    runStreamedSteps(`uninstall-${app.id}`, `Uninstall ${app.name}`, plan.command, steps, () => {
      setApps((prev) =>
        prev.map((item) => (item.id === app.id ? { ...item, installed: false } : item)),
      );
    });
  };

  const handleInstallAllChecked = () => {
    const selected = apps.filter((a) => checkedIds[a.id]);
    if (selected.length === 0) {
      appendLog('info', 'No applications checked. Select one or more apps to run batch install.');
      setLogExpanded(true);
      return;
    }

    const scopeNote = telemetry.isAdmin ? 'machine scope' : '--scope user / %USERPROFILE%\\Apps\\';
    const steps: SimulationStep[] = selected.flatMap((app) => {
      const plan = buildAppInstallCommand(app, telemetry);
      return [
        { delayMs: 140, kind: 'cmd' as const, text: `$ ${plan.command}` },
        {
          delayMs: 200,
          kind: 'stdout' as const,
          text: `Installed ${app.name} (${app.method}${plan.usesUserScopeFallback ? ' · user-scope fallback' : ''}).`,
        },
      ];
    });
    steps.push({
      delayMs: 140,
      kind: 'exit-ok',
      text: `[EXIT 0] Batch install finished (${selected.length} packages via ${scopeNote}).`,
    });

    runStreamedSteps(
      'install-checked',
      `Install ${selected.length} Checked Apps`,
      `winfix --batch-install (${selected.map((s) => s.id).join(', ')})`,
      steps,
      () => {
        const selectedSet = new Set(selected.map((s) => s.id));
        setApps((prev) =>
          prev.map((item) => (selectedSet.has(item.id) ? { ...item, installed: true } : item)),
        );
      },
    );
  };

  const handleUpgradeAll = () => {
    const cmd = telemetry.isAdmin
      ? 'winget upgrade --all --accept-package-agreements --accept-source-agreements'
      : 'winget upgrade --all --scope user --accept-package-agreements --accept-source-agreements';
    runStreamedSteps('upgrade-all', 'Upgrade All Apps', cmd, [
      { delayMs: 200, kind: 'stdout', text: 'Checking installed winget packages for newer versions...' },
      { delayMs: 260, kind: 'stdout', text: 'Google Chrome (131.0.6778) -> Up to date' },
      { delayMs: 220, kind: 'stdout', text: '7-Zip (24.08) -> Up to date' },
      { delayMs: 220, kind: 'stdout', text: 'QuickLook (3.7.3) -> Up to date' },
      { delayMs: 140, kind: 'exit-ok', text: '[EXIT 0] All detected packages are up to date.' },
    ]);
  };

  const handleGithubSync = async (url: string): Promise<{ ok: boolean; message: string }> => {
    setGithubUrl(url);
    appendLog('cmd', `$ GET ${url}`);
    try {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status} ${response.statusText}`);
      }
      const text = await response.text();
      const installedMap = Object.fromEntries(apps.map((a) => [a.id, a.installed]));
      const parsed = parseAppsJson(text, installedMap);
      if (!parsed.ok) {
        appendLog('stderr', `GitHub sync schema error: ${parsed.error}`);
        return { ok: false, message: parsed.error };
      }
      setApps(parsed.apps);
      appendLog('exit-ok', `[EXIT 0] Synced ${parsed.apps.length} apps from ${url}`);
      return {
        ok: true,
        message: `Synced ${parsed.apps.length} apps cleanly and updated local apps.json override.`,
      };
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Network error';
      appendLog(
        'stderr',
        `Remote fetch failed (${msg}). Tip: Verify URL is a public raw.githubusercontent.com JSON endpoint, or edit apps.json directly.`,
      );
      return {
        ok: false,
        message: `Fetch failed (${msg}). Confirm the raw GitHub URL is publicly reachable or edit apps.json locally.`,
      };
    }
  };

  // Tab 3: Windows 10 UI & Safe Debloat
  const handleToggleTweak = (
    key: 'classicContextMenu' | 'leftAlignTaskbar' | 'hideStartRecommendations' | 'adsTelemetryDisabled',
  ) => {
    const nextValue = !tweaks[key];

    if (key === 'classicContextMenu') {
      const cmd = nextValue
        ? 'reg add "HKCU\\Software\\Classes\\CLSID\\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}\\InprocServer32" /f /ve && taskkill /f /im explorer.exe && start explorer.exe'
        : 'reg delete "HKCU\\Software\\Classes\\CLSID\\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}" /f && taskkill /f /im explorer.exe && start explorer.exe';
      runStreamedSteps(
        `tweak-${key}`,
        'Classic Right-Click Menu',
        cmd,
        [
          {
            delayMs: 180,
            kind: 'stdout',
            text: nextValue
              ? 'Registry override written to HKCU\\Software\\Classes\\CLSID\\{86ca1aa0...}\\InprocServer32.'
              : 'Removed Classic Right-Click Menu CLSID override.',
          },
          { delayMs: 220, kind: 'stdout', text: 'Restarted explorer.exe cleanly to apply context menu change.' },
          {
            delayMs: 120,
            kind: 'exit-ok',
            text: `[EXIT 0] Classic Right-Click Menu is now ${nextValue ? 'Enabled' : 'Disabled'}.`,
          },
        ],
        () => setTweaks((prev) => ({ ...prev, classicContextMenu: nextValue })),
      );
      return;
    }

    if (key === 'leftAlignTaskbar') {
      const dword = nextValue ? 0 : 1;
      runStreamedSteps(
        `tweak-${key}`,
        'Left-Align Taskbar',
        `reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced" /v TaskbarAl /t REG_DWORD /d ${dword} /f`,
        [
          { delayMs: 160, kind: 'stdout', text: `Set TaskbarAl = ${dword} (${nextValue ? 'Left-Aligned' : 'Centered'}).` },
          { delayMs: 120, kind: 'exit-ok', text: `[EXIT 0] Left-Align Taskbar is now ${nextValue ? 'Enabled' : 'Disabled'}.` },
        ],
        () => setTweaks((prev) => ({ ...prev, leftAlignTaskbar: nextValue })),
      );
      return;
    }

    if (key === 'hideStartRecommendations') {
      const dword = nextValue ? 0 : 1;
      runStreamedSteps(
        `tweak-${key}`,
        'Hide Start Recommendations',
        `reg add "HKCU\\...\\Explorer\\Advanced" /v Start_IrisRecommendations /t REG_DWORD /d ${dword} /f`,
        [
          { delayMs: 160, kind: 'stdout', text: `Set Start_IrisRecommendations = ${dword} and Start_TrackDocs = ${dword}.` },
          {
            delayMs: 120,
            kind: 'exit-ok',
            text: `[EXIT 0] Hide Start Recommendations is now ${nextValue ? 'Enabled' : 'Disabled'}.`,
          },
        ],
        () => setTweaks((prev) => ({ ...prev, hideStartRecommendations: nextValue })),
      );
      return;
    }

    const dword = nextValue ? 0 : 1;
    runStreamedSteps(
      `tweak-${key}`,
      'Disable Ads & Telemetry',
      `Set-ItemProperty HKCU:\\...\\AdvertisingInfo,ContentDeliveryManager,ShowCopilotButton = ${dword}`,
      [
        { delayMs: 160, kind: 'stdout', text: `AdvertisingInfo\\Enabled = ${dword}` },
        { delayMs: 160, kind: 'stdout', text: `ContentDeliveryManager Start/Settings suggestions = ${dword}` },
        { delayMs: 160, kind: 'stdout', text: `Explorer\\Advanced\\ShowCopilotButton = ${dword}` },
        {
          delayMs: 120,
          kind: 'exit-ok',
          text: `[EXIT 0] Disable Ads & Telemetry is now ${nextValue ? 'Enabled' : 'Disabled'}.`,
        },
      ],
      () => setTweaks((prev) => ({ ...prev, adsTelemetryDisabled: nextValue })),
    );
  };

  const handleSetTextScaling = (scale: 100 | 125 | 150) => {
    runStreamedSteps(
      'tweak-text-scale',
      `Text Scaling ${scale}%`,
      `reg add "HKCU\\Software\\Microsoft\\Accessibility" /v TextScaleFactor /t REG_DWORD /d ${scale} /f`,
      [
        { delayMs: 150, kind: 'stdout', text: `Updated Accessibility TextScaleFactor -> ${scale}%.` },
        { delayMs: 120, kind: 'exit-ok', text: `[EXIT 0] Text scaling set to ${scale}%.` },
      ],
      () => setTweaks((prev) => ({ ...prev, textScaling: scale })),
    );
  };

  const handleRemoveConsumerBloat = () => {
    const plan = getDebloatPlan(telemetry.arch);
    const steps: SimulationStep[] = [
      { delayMs: 160, kind: 'info', text: plan.arm64Notice },
      {
        delayMs: 160,
        kind: 'info',
        text: `Protected allowlist locked: ${plan.protectedComponents.join(', ')}`,
      },
      ...plan.targets.map((target) => ({
        delayMs: 170,
        kind: 'stdout' as const,
        text: `winget uninstall --id ${target.wingetId} -e --silent -> Removed (${target.name})`,
      })),
      {
        delayMs: 140,
        kind: 'exit-ok',
        text: `[EXIT 0] Surgical debloat complete (${plan.targets.length} consumer apps removed, ${telemetry.arch} system runtimes untouched).`,
      },
    ];

    runStreamedSteps(
      'remove-bloat',
      'Remove Consumer Bloat',
      'winfix --surgical-debloat --preserve-webview2 --preserve-bluetooth --preserve-prism',
      steps,
      () => setTweaks((prev) => ({ ...prev, consumerBloatRemoved: true })),
    );
  };

  // Tab 4: Windows Admin Shortcuts
  const handleLaunchShortcut = (label: string, command: string, elevate: boolean) => {
    setLogExpanded(true);
    if (elevate && !telemetry.isAdmin) {
      setPendingElevation({
        actionId: 'admin-ps',
        title: 'Open Admin PowerShell',
        commandPreview: 'ShellExecuteW(None, "runas", "powershell.exe", "-NoExit", None, 1)',
        reason: 'Spawns a standalone elevated PowerShell window while keeping WinFix lightweight.',
        onApprove: () => {
          setPendingElevation(null);
          appendLog('cmd', '$ [UAC runas] powershell.exe -NoExit');
          appendLog('exit-ok', '[EXIT 0] Spawned elevated PowerShell console via ShellExecuteW.');
        },
      });
      return;
    }

    appendLog('cmd', `$ start ${command}`);
    appendLog('exit-ok', `[EXIT 0] Launched ${label} (${command}).`);
  };

  return (
    <div className="min-h-screen h-screen flex flex-col bg-[#141414] text-[#E0E0E0] overflow-hidden">
      {/* Persistent System Telemetry Header */}
      <TelemetryHeader
        telemetry={telemetry}
        activeProfile={activeProfile}
        onSwitchProfile={handleSwitchProfile}
        onRelaunchAdmin={handleRelaunchAdmin}
        onOpenPythonKit={() => setPythonKitOpen(true)}
      />

      {/* Tab Strip */}
      <nav
        aria-label="WinFix Control Panel Sections"
        className="bg-[#18181A] border-b border-[#2C2C30] px-4 lg:px-6 shrink-0"
      >
        <div
          role="tablist"
          className="max-w-[1440px] mx-auto flex items-center gap-1 overflow-x-auto pt-2"
        >
          {TABS.map((tab) => {
            const Icon = tab.icon;
            const selected = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={selected}
                onClick={() => setActiveTab(tab.id)}
                className={`inline-flex items-center gap-2 px-4 py-2 text-[13px] rounded-t border border-b-0 transition-colors cursor-pointer whitespace-nowrap shrink-0 ${
                  selected
                    ? 'bg-[#141414] text-[#88C0D0] border-[#2C2C30] font-semibold -mb-px pb-[9px]'
                    : 'bg-transparent text-[#A0A0A8] border-transparent hover:text-[#E0E0E0] hover:bg-[#1E1E22]'
                }`}
              >
                <Icon className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                <span>{tab.label}</span>
                <span className="font-mono text-[10px] text-[#6A6A74] hidden xl:inline">
                  ^{tab.shortcut}
                </span>
              </button>
            );
          })}
        </div>
      </nav>

      {/* Main Scrollable Workspace */}
      <main className="flex-1 overflow-y-auto px-4 lg:px-6 py-4">
        <div className="max-w-[1440px] mx-auto">
          {activeTab === 'quick-fixes' && (
            <QuickFixesTab
              telemetry={telemetry}
              runningActionId={runningActionId}
              onRunQuickFix={handleRunQuickFix}
            />
          )}

          {activeTab === 'apps' && (
            <AppInstallerTab
              apps={apps}
              checkedIds={checkedIds}
              telemetry={telemetry}
              runningActionId={runningActionId}
              onToggleCheck={(id) =>
                setCheckedIds((prev) => ({ ...prev, [id]: !prev[id] }))
              }
              onInstallApp={handleInstallApp}
              onUninstallApp={handleUninstallApp}
              onInstallAllChecked={handleInstallAllChecked}
              onUpgradeAll={handleUpgradeAll}
              onOpenGithubSync={() => setGithubSyncOpen(true)}
              onOpenAppsJson={() => setAppsEditorOpen(true)}
            />
          )}

          {activeTab === 'tweaks' && (
            <TweaksTab
              tweaks={tweaks}
              telemetry={telemetry}
              runningActionId={runningActionId}
              onToggleTweak={handleToggleTweak}
              onSetTextScaling={handleSetTextScaling}
              onRemoveConsumerBloat={handleRemoveConsumerBloat}
            />
          )}

          {activeTab === 'shortcuts' && (
            <AdminShortcutsTab onLaunchShortcut={handleLaunchShortcut} />
          )}
        </div>
      </main>

      {/* Collapsible Live Log Drawer */}
      <LiveLogDrawer
        logs={logs}
        expanded={logExpanded}
        runningLabel={runningLabel}
        onToggleExpand={() => setLogExpanded((prev) => !prev)}
        onClearLogs={() => setLogs([])}
      />

      {/* Interactive Modals */}
      <ElevationModal
        elevation={pendingElevation}
        onCancel={() => {
          if (pendingElevation) {
            appendLog('stderr', `[UAC CANCELLED] Elevation declined for ${pendingElevation.title}.`);
          }
          setPendingElevation(null);
        }}
      />

      <RebootPromptModal
        open={rebootModalOpen}
        onRestartLater={() => {
          setRebootModalOpen(false);
          appendLog('info', 'Reboot deferred by operator. Remember to restart to finalize Winsock/IP reset.');
        }}
        onRestartNow={() => {
          setRebootModalOpen(false);
          appendLog('cmd', '$ shutdown /r /t 5 /c "WinFix Network Stack Reset"');
          appendLog('exit-ok', '[EXIT 0] Windows restart scheduled in 5 seconds.');
        }}
      />

      <AppsJsonEditorModal
        key={appsEditorOpen ? 'open' : 'closed'}
        open={appsEditorOpen}
        apps={apps}
        onClose={() => setAppsEditorOpen(false)}
        onSave={(parsedApps) => {
          setApps(parsedApps);
          appendLog('exit-ok', `[EXIT 0] Saved apps.json (${parsedApps.length} packages loaded).`);
        }}
      />

      <GithubSyncModal
        open={githubSyncOpen}
        initialUrl={githubUrl}
        onClose={() => setGithubSyncOpen(false)}
        onSyncUrl={handleGithubSync}
      />

      <PythonKitModal
        open={pythonKitOpen}
        apps={apps}
        onClose={() => setPythonKitOpen(false)}
      />
    </div>
  );
}
