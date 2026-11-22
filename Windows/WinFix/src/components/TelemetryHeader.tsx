import { Code2, ShieldAlert, ShieldCheck, Terminal, Wifi } from 'lucide-react';
import type { SystemTelemetry } from '../types/winfix';

interface TelemetryHeaderProps {
  telemetry: SystemTelemetry;
  activeProfile: 'parent-arm64' | 'workstation-x64';
  onSwitchProfile: (profile: 'parent-arm64' | 'workstation-x64') => void;
  onRelaunchAdmin: () => void;
  onOpenPythonKit: () => void;
}

export function TelemetryHeader({
  telemetry,
  activeProfile,
  onSwitchProfile,
  onRelaunchAdmin,
  onOpenPythonKit,
}: TelemetryHeaderProps) {
  return (
    <header className="bg-[#1B1B1D] border-b border-[#2C2C30] px-4 lg:px-6 py-2 shrink-0">
      <div className="max-w-[1440px] mx-auto flex flex-wrap lg:flex-nowrap items-center justify-between gap-x-4 gap-y-2">
        {/* Brand + System Telemetry Strip */}
        <div className="flex items-center gap-x-3 min-w-0 overflow-hidden">
          <div className="flex items-center gap-2 shrink-0">
            <Terminal className="w-4 h-4 text-[#88C0D0]" aria-hidden="true" />
            <span className="text-[15px] font-bold tracking-tight text-[#E0E0E0] whitespace-nowrap">
              WinFix
            </span>
          </div>

          <span className="text-[#3C3C44] select-none hidden sm:inline shrink-0" aria-hidden="true">
            |
          </span>

          {/* Persistent Telemetry Metrics */}
          <div
            className="flex flex-wrap lg:flex-nowrap items-center gap-x-2.5 gap-y-1 font-mono text-[11.5px] tabular-nums min-w-0"
            aria-label="System Telemetry"
          >
            <span className="text-[#E0E0E0] whitespace-nowrap shrink-0" data-testid="telemetry-os">
              {telemetry.windowsVersion}{' '}
              <span className="text-[#909098]">({telemetry.windowsBuild})</span>
            </span>

            <span className="text-[#3C3C44] shrink-0" aria-hidden="true">
              ·
            </span>

            <span
              className="text-[#88C0D0] font-semibold whitespace-nowrap shrink-0"
              data-testid="telemetry-arch"
            >
              {telemetry.arch}
            </span>

            <span className="text-[#3C3C44] shrink-0" aria-hidden="true">
              ·
            </span>

            <span
              className={`inline-flex items-center gap-1 font-semibold whitespace-nowrap shrink-0 ${
                telemetry.isAdmin ? 'text-[#98C379]' : 'text-[#E5C07B]'
              }`}
              data-testid="telemetry-privilege"
            >
              {telemetry.isAdmin ? (
                <ShieldCheck className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
              ) : (
                <ShieldAlert className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
              )}
              {telemetry.isAdmin ? 'Administrator' : 'Standard User (Non-Admin)'}
            </span>

            <span className="text-[#3C3C44] hidden xl:inline shrink-0" aria-hidden="true">
              ·
            </span>

            <span
              className="hidden xl:inline-flex items-center gap-1.5 text-[#A8A8B2] whitespace-nowrap min-w-0"
              data-testid="telemetry-network"
              title={`${telemetry.ipv4} / ${telemetry.adapterName}`}
            >
              <Wifi className="w-3.5 h-3.5 text-[#909098] shrink-0" aria-hidden="true" />
              <span className="shrink-0">{telemetry.ipv4}</span>
              <span className="text-[#585860] shrink-0" aria-hidden="true">
                /
              </span>
              <span className="truncate max-w-[165px]">{telemetry.adapterName}</span>
            </span>
          </div>
        </div>

        {/* Right Controls: Target Environment Switcher + Elevation + PyQt6 Kit */}
        <div className="flex items-center gap-2 shrink-0">
          <label className="sr-only" htmlFor="target-profile-select">
            Simulated Windows Target Profile
          </label>
          <select
            id="target-profile-select"
            value={activeProfile}
            onChange={(e) =>
              onSwitchProfile(e.target.value as 'parent-arm64' | 'workstation-x64')
            }
            className="bg-[#242428] text-[#E0E0E0] text-xs font-medium border border-[#2C2C30] rounded px-2.5 py-1.5 hover:border-[#42424A] transition-colors cursor-pointer"
            title="Switch simulated Windows target machine to test ARM64 & Non-Admin fallback"
          >
            <option value="parent-arm64">ARM64 · Non-Admin</option>
            <option value="workstation-x64">x64 · Admin</option>
          </select>

          {!telemetry.isAdmin && (
            <button
              type="button"
              onClick={onRelaunchAdmin}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold bg-[#88C0D0] text-[#141414] rounded hover:bg-[#9BCBD9] transition-colors whitespace-nowrap shrink-0 cursor-pointer"
            >
              <ShieldCheck className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
              Relaunch as Admin
            </button>
          )}

          <button
            type="button"
            onClick={onOpenPythonKit}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] hover:border-[#42424A] transition-colors whitespace-nowrap shrink-0 cursor-pointer"
          >
            <Code2 className="w-3.5 h-3.5 text-[#88C0D0] shrink-0" aria-hidden="true" />
            PyQt6 Build Kit
          </button>
        </div>
      </div>
    </header>
  );
}
