import {
  Cog,
  Cpu,
  Database,
  Globe,
  Package,
  Power,
  RefreshCcw,
  TerminalSquare,
} from 'lucide-react';
import type { ComponentType } from 'react';

interface ShortcutItem {
  id: string;
  label: string;
  command: string;
  description: string;
  elevate: boolean;
  icon: ComponentType<{ className?: string }>;
}

const ADMIN_SHORTCUTS: ShortcutItem[] = [
  {
    id: 'devmgmt',
    label: 'Device Manager',
    command: 'devmgmt.msc',
    description: 'Inspect Wi-Fi, Bluetooth, and ARM64/x64 hardware drivers.',
    elevate: false,
    icon: Cpu,
  },
  {
    id: 'services',
    label: 'Services',
    command: 'services.msc',
    description: 'Start, stop, or inspect Spooler, WPNService, and bthserv.',
    elevate: false,
    icon: Cog,
  },
  {
    id: 'ncpa',
    label: 'Network Adapters',
    command: 'ncpa.cpl',
    description: 'Classic adapter connections panel for IPv4 & DNS settings.',
    elevate: false,
    icon: Globe,
  },
  {
    id: 'appwiz',
    label: 'Programs & Features',
    command: 'appwiz.cpl',
    description: 'Classic fast uninstaller list without Settings lag.',
    elevate: false,
    icon: Package,
  },
  {
    id: 'startupapps',
    label: 'Startup Apps',
    command: 'ms-settings:startupapps',
    description: 'Disable slow login background launchers directly.',
    elevate: false,
    icon: Power,
  },
  {
    id: 'windowsupdate',
    label: 'Windows Update',
    command: 'ms-settings:windowsupdate',
    description: 'Check cumulative updates, security patches, and optional drivers.',
    elevate: false,
    icon: RefreshCcw,
  },
  {
    id: 'regedit',
    label: 'Registry Editor',
    command: 'regedit.exe',
    description: 'Inspect HKCU / HKLM keys directly.',
    elevate: false,
    icon: Database,
  },
  {
    id: 'admin-ps',
    label: 'Open Admin PowerShell',
    command: 'powershell.exe (runas)',
    description: 'Spawns an elevated PowerShell console via ShellExecuteW.',
    elevate: true,
    icon: TerminalSquare,
  },
];

interface AdminShortcutsTabProps {
  onLaunchShortcut: (label: string, command: string, elevate: boolean) => void;
}

export function AdminShortcutsTab({ onLaunchShortcut }: AdminShortcutsTabProps) {
  return (
    <section className="bg-[#1B1B1D] border border-[#2C2C30] rounded-md p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2 mb-4 pb-2.5 border-b border-[#26262A]">
        <h2 className="text-[14px] font-semibold text-[#E0E0E0]">Windows Admin Shortcuts</h2>
        <p className="text-xs text-[#909098]">
          Direct one-click launchers — bypasses nested Windows 11 Settings menus
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {ADMIN_SHORTCUTS.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onLaunchShortcut(item.label, item.command, item.elevate)}
              className={`text-left p-3.5 rounded transition-colors cursor-pointer flex flex-col justify-between gap-3 min-h-[108px] ${
                item.elevate
                  ? 'bg-[#88C0D0] text-[#141414] hover:bg-[#9BCBD9]'
                  : 'bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] hover:bg-[#2D2D33] hover:border-[#42424A]'
              }`}
            >
              <div className="flex items-start justify-between gap-2">
                <span className={`text-[13.5px] ${item.elevate ? 'font-bold' : 'font-semibold'}`}>
                  {item.label}
                </span>
                <Icon
                  className={`w-4 h-4 shrink-0 ${
                    item.elevate ? 'text-[#141414]' : 'text-[#88C0D0]'
                  }`}
                  aria-hidden="true"
                />
              </div>

              <div className="space-y-1">
                <p
                  className={`text-xs leading-snug ${
                    item.elevate ? 'text-[#141414]/85 font-medium' : 'text-[#A8A8B2]'
                  }`}
                >
                  {item.description}
                </p>
                <div
                  className={`font-mono text-[11px] truncate ${
                    item.elevate ? 'text-[#141414] font-semibold' : 'text-[#88C0D0]'
                  }`}
                >
                  {item.command}
                </div>
              </div>
            </button>
          );
        })}
      </div>
    </section>
  );
}
