import {
  Activity,
  FolderSync,
  HardDrive,
  Loader2,
  Network,
  Printer,
  RefreshCw,
  Shield,
  Smartphone,
  Trash2,
  Wifi,
} from 'lucide-react';
import type { ComponentType } from 'react';
import type { SystemTelemetry } from '../types/winfix';

interface QuickFixAction {
  id: string;
  label: string;
  commandSummary: string;
  description: string;
  requiresAdmin: boolean;
  primary?: boolean;
  icon: ComponentType<{ className?: string }>;
}

interface QuickFixSection {
  title: string;
  subtitle: string;
  actions: QuickFixAction[];
}

const SECTIONS: QuickFixSection[] = [
  {
    title: 'Network & Connectivity',
    subtitle: 'DNS cache, DHCP renewal, latency checks, and Winsock/TCP-IP stack recovery',
    actions: [
      {
        id: 'flush-dns',
        label: 'Flush DNS & Renew IP',
        commandSummary: 'ipconfig /flushdns && ipconfig /renew',
        description: 'Clears stale DNS records and requests a fresh local IPv4 lease.',
        requiresAdmin: false,
        primary: true,
        icon: RefreshCw,
      },
      {
        id: 'test-internet',
        label: 'Test Internet & Ping',
        commandSummary: 'DNS resolve + ping 1.1.1.1 & google.com',
        description: 'Verifies DNS lookup and logs round-trip latency to Cloudflare & Google.',
        requiresAdmin: false,
        icon: Activity,
      },
      {
        id: 'reset-network',
        label: 'Reset Network Stack',
        commandSummary: 'netsh winsock reset && netsh int ip reset',
        description: 'Rebuilds Winsock catalog and IP interfaces; prompts for reboot.',
        requiresAdmin: true,
        icon: Network,
      },
    ],
  },
  {
    title: 'System & Shell Recovery',
    subtitle: 'Unfreeze taskbar, purge stuck print queues, clean temp files, and verify system files',
    actions: [
      {
        id: 'restart-explorer',
        label: 'Restart Explorer / Taskbar',
        commandSummary: 'taskkill /f /im explorer.exe && start explorer.exe',
        description: 'Cleanly restarts Windows Shell without closing open applications.',
        requiresAdmin: false,
        primary: true,
        icon: FolderSync,
      },
      {
        id: 'fix-printer',
        label: 'Fix Stuck Printer',
        commandSummary: 'Stop Spooler · clear PRINTERS\\* · Start Spooler',
        description: 'Purges jammed spool jobs and restarts the Windows Print Spooler.',
        requiresAdmin: true,
        icon: Printer,
      },
      {
        id: 'clean-temp',
        label: 'Clean Temp & Update Cache',
        commandSummary: 'Clear %TEMP% safely (preserves user data)',
        description: 'Wipes temporary files without touching documents or browser profiles.',
        requiresAdmin: false,
        icon: Trash2,
      },
      {
        id: 'repair-sfc',
        label: 'Repair System Files (SFC)',
        commandSummary: 'sfc /scannow (background QThread worker)',
        description: 'Scans and repairs protected Windows system binaries non-blockingly.',
        requiresAdmin: true,
        icon: HardDrive,
      },
    ],
  },
  {
    title: 'Phone Sync & O+ Connect Doctor',
    subtitle: 'Verify wireless transfer services, WebView2 runtime, and Private network firewall rules',
    actions: [
      {
        id: 'check-phone-sync',
        label: 'Check Phone Sync Services',
        commandSummary: 'WPNService · bthserv · Wi-Fi Direct · WebView2',
        description: 'Audits push notifications, Bluetooth, Wi-Fi Direct adapter, and Edge WebView2.',
        requiresAdmin: false,
        primary: true,
        icon: Smartphone,
      },
      {
        id: 'fix-oplus-firewall',
        label: 'Fix O+ Connect Firewall',
        commandSummary: 'Inbound/Outbound Allow on Private networks',
        description: 'Creates Windows Firewall allow rules on Private networks while keeping Public strict.',
        requiresAdmin: true,
        icon: Shield,
      },
    ],
  },
];

interface QuickFixesTabProps {
  telemetry: SystemTelemetry;
  runningActionId: string | null;
  onRunQuickFix: (actionId: string, label: string, requiresAdmin: boolean, commandPreview: string) => void;
}

export function QuickFixesTab({
  telemetry,
  runningActionId,
  onRunQuickFix,
}: QuickFixesTabProps) {
  return (
    <div className="space-y-3">
      {SECTIONS.map((section) => {
        const gridCols =
          section.actions.length === 4
            ? 'grid-cols-1 sm:grid-cols-2 xl:grid-cols-4'
            : section.actions.length === 2
              ? 'grid-cols-1 md:grid-cols-2'
              : 'grid-cols-1 md:grid-cols-3';

        return (
          <section
            key={section.title}
            className="bg-[#1B1B1D] border border-[#2C2C30] rounded-md px-4 py-3.5"
          >
            <div className="flex flex-wrap items-baseline justify-between gap-2 mb-3 pb-2 border-b border-[#26262A]">
              <h2 className="text-[13.5px] font-semibold text-[#E0E0E0]">{section.title}</h2>
              <p className="text-xs text-[#909098]">{section.subtitle}</p>
            </div>

            <div className={`grid ${gridCols} gap-x-4 gap-y-3`}>
              {section.actions.map((action) => {
                const Icon = action.icon;
                const isRunning = runningActionId === action.id;
                const needsUac = action.requiresAdmin && !telemetry.isAdmin;

                return (
                  <div
                    key={action.id}
                    className="flex flex-col justify-between gap-1.5 min-w-0"
                  >
                    <div className="space-y-1.5">
                      <button
                        type="button"
                        disabled={runningActionId !== null}
                        onClick={() =>
                          onRunQuickFix(
                            action.id,
                            action.label,
                            action.requiresAdmin,
                            action.commandSummary,
                          )
                        }
                        className={`w-full inline-flex items-center justify-center gap-2 px-3 py-2 rounded text-[12.5px] transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap ${
                          action.primary
                            ? 'bg-[#88C0D0] text-[#141414] font-bold hover:bg-[#9BCBD9]'
                            : 'bg-[#242428] text-[#E0E0E0] font-medium border border-[#2C2C30] hover:bg-[#2D2D33] hover:border-[#42424A]'
                        }`}
                      >
                        {isRunning ? (
                          <Loader2 className="w-3.5 h-3.5 animate-spin shrink-0" aria-hidden="true" />
                        ) : (
                          <Icon className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                        )}
                        <span className="truncate">{action.label}</span>
                      </button>

                      <p className="text-[11.5px] text-[#A8A8B2] leading-snug">
                        {action.description}
                      </p>
                    </div>

                    <div className="flex items-center justify-between gap-2 pt-1 font-mono text-[10.5px] text-[#8A8A94]">
                      <span className="truncate" title={action.commandSummary}>
                        {action.commandSummary}
                      </span>
                      {needsUac ? (
                        <span className="text-[#E5C07B] shrink-0 font-semibold">UAC</span>
                      ) : action.requiresAdmin ? (
                        <span className="text-[#98C379] shrink-0">Admin</span>
                      ) : (
                        <span className="text-[#88C0D0] shrink-0 flex items-center gap-1">
                          <Wifi className="w-3 h-3 hidden" aria-hidden="true" />
                          User
                        </span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}
