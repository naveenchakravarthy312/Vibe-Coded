import { Check, Loader2, ShieldCheck, SlidersHorizontal, Trash2 } from 'lucide-react';
import type { SystemTelemetry, TweakState } from '../types/winfix';
import { getDebloatPlan } from '../utils/winfixLogic';

interface TweaksTabProps {
  tweaks: TweakState;
  telemetry: SystemTelemetry;
  runningActionId: string | null;
  onToggleTweak: (key: 'classicContextMenu' | 'leftAlignTaskbar' | 'hideStartRecommendations' | 'adsTelemetryDisabled') => void;
  onSetTextScaling: (scale: 100 | 125 | 150) => void;
  onRemoveConsumerBloat: () => void;
}

export function TweaksTab({
  tweaks,
  telemetry,
  runningActionId,
  onToggleTweak,
  onSetTextScaling,
  onRemoveConsumerBloat,
}: TweaksTabProps) {
  const debloatPlan = getDebloatPlan(telemetry.arch);

  const familiarityRows: Array<{
    key: 'classicContextMenu' | 'leftAlignTaskbar' | 'hideStartRecommendations';
    label: string;
    regKey: string;
    description: string;
    enabled: boolean;
  }> = [
    {
      key: 'classicContextMenu',
      label: 'Classic Right-Click Menu',
      regKey: 'HKCU\\Software\\Classes\\CLSID\\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}\\InprocServer32',
      description: 'Restores the full Windows 10 right-click menu immediately and restarts Explorer.',
      enabled: tweaks.classicContextMenu,
    },
    {
      key: 'leftAlignTaskbar',
      label: 'Left-Align Taskbar',
      regKey: 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\\TaskbarAl (0 / 1)',
      description: 'Pins Start and taskbar icons to the classic bottom-left corner without reboot.',
      enabled: tweaks.leftAlignTaskbar,
    },
    {
      key: 'hideStartRecommendations',
      label: 'Hide Start Recommendations',
      regKey: 'HKCU\\...\\Explorer\\Advanced\\Start_IrisRecommendations = 0',
      description: 'Removes recommended tips, web promos, and clutter from the Windows 11 Start menu.',
      enabled: tweaks.hideStartRecommendations,
    },
  ];

  return (
    <div className="space-y-3">
      {/* Section 1: Windows 10 Familiarity Tweaks */}
      <section className="bg-[#1B1B1D] border border-[#2C2C30] rounded-md px-4 py-3.5">
        <div className="flex flex-wrap items-baseline justify-between gap-2 mb-2.5 pb-2 border-b border-[#26262A]">
          <div className="flex items-center gap-2">
            <SlidersHorizontal className="w-4 h-4 text-[#88C0D0]" aria-hidden="true" />
            <h2 className="text-[13.5px] font-semibold text-[#E0E0E0]">
              Windows 10 Familiarity Tweaks
            </h2>
          </div>
          <span className="font-mono text-[11px] text-[#909098]">
            HKCU registry keys · Standard User safe (no UAC)
          </span>
        </div>

        <div className="divide-y divide-[#26262A]">
          {familiarityRows.map((row) => {
            const busy = runningActionId === `tweak-${row.key}`;
            return (
              <div
                key={row.key}
                className="py-2.5 first:pt-0 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5"
              >
                <div className="space-y-0.5 min-w-0">
                  <div className="flex items-center gap-3">
                    <span className="text-[13px] font-semibold text-[#E0E0E0]">{row.label}</span>
                    <span
                      data-testid={`tweak-state-${row.key}`}
                      className={`font-mono text-[11px] font-semibold ${
                        row.enabled ? 'text-[#98C379]' : 'text-[#909098]'
                      }`}
                    >
                      {row.enabled ? 'Enabled' : 'Disabled'}
                    </span>
                  </div>
                  <p className="text-xs text-[#A8A8B2]">{row.description}</p>
                  <p className="font-mono text-[10.5px] text-[#7A7A84] truncate">{row.regKey}</p>
                </div>

                <button
                  type="button"
                  disabled={runningActionId !== null}
                  onClick={() => onToggleTweak(row.key)}
                  aria-label={`Toggle ${row.label}`}
                  className={`px-3.5 py-1.5 text-xs rounded transition-colors cursor-pointer disabled:opacity-50 whitespace-nowrap shrink-0 inline-flex items-center gap-1.5 ${
                    row.enabled
                      ? 'bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] hover:bg-[#2D2D33]'
                      : 'bg-[#88C0D0] text-[#141414] font-bold hover:bg-[#9BCBD9]'
                  }`}
                >
                  {busy && <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />}
                  {row.enabled ? 'Disable' : 'Enable'}
                </button>
              </div>
            );
          })}

          {/* Text Scaling Quick Buttons */}
          <div className="pt-2.5 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
            <div className="space-y-0.5">
              <div className="flex items-center gap-3">
                <span className="text-[13px] font-semibold text-[#E0E0E0]">Text Scaling</span>
                <span
                  data-testid="tweak-state-textScaling"
                  className="font-mono text-[11px] font-semibold text-[#88C0D0] tabular-nums"
                >
                  Current: {tweaks.textScaling}%
                </span>
              </div>
              <p className="text-xs text-[#A8A8B2]">
                Adjusts Windows Accessibility TextScaleFactor immediately without changing display resolution.
              </p>
            </div>

            <div className="flex items-center gap-1.5 shrink-0" role="group" aria-label="Text Scaling Presets">
              {([100, 125, 150] as const).map((pct) => {
                const active = tweaks.textScaling === pct;
                return (
                  <button
                    key={pct}
                    type="button"
                    disabled={runningActionId !== null}
                    onClick={() => onSetTextScaling(pct)}
                    aria-pressed={active}
                    className={`px-3.5 py-1.5 text-xs font-mono tabular-nums rounded transition-colors cursor-pointer whitespace-nowrap ${
                      active
                        ? 'bg-[#88C0D0] text-[#141414] font-bold'
                        : 'bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] hover:bg-[#2D2D33]'
                    }`}
                  >
                    {pct}%
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      </section>

      {/* Section 2: Surgical Bloat & Telemetry Cleanup (ARM64-Safe) */}
      <section className="bg-[#1B1B1D] border border-[#2C2C30] rounded-md px-4 py-3.5 space-y-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2 pb-2 border-b border-[#26262A]">
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-[#98C379]" aria-hidden="true" />
            <h2 className="text-[13.5px] font-semibold text-[#E0E0E0]">
              Surgical Bloat & Telemetry Cleanup (ARM64-Safe)
            </h2>
          </div>
          <span className="font-mono text-[11px] text-[#98C379]">
            {telemetry.arch} Protected Allowlist Enforced
          </span>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 lg:divide-x lg:divide-[#26262A]">
          {/* Remove Consumer Bloat */}
          <div className="flex flex-col justify-between gap-3">
            <div className="space-y-1.5">
              <div className="flex items-center justify-between gap-2">
                <h3 className="text-[13px] font-semibold text-[#E0E0E0]">
                  Remove Consumer Bloat
                </h3>
                <span
                  data-testid="tweak-state-consumerBloat"
                  className={`font-mono text-[11px] font-semibold ${
                    tweaks.consumerBloatRemoved ? 'text-[#98C379]' : 'text-[#909098]'
                  }`}
                >
                  {tweaks.consumerBloatRemoved ? 'Cleaned' : 'Pending Cleanup'}
                </span>
              </div>
              <p className="text-xs text-[#A8A8B2] leading-snug">
                Runs targeted <span className="font-mono text-[#E0E0E0]">winget uninstall</span> on
                TikTok, Solitaire, Clipchamp, Microsoft News, Bing Weather, and Xbox Gaming clutter.
              </p>
              <p className="text-[11px] font-mono text-[#909098]">
                <span className="text-[#98C379]">Preserved:</span>{' '}
                {debloatPlan.protectedComponents.join(' · ')}
              </p>
            </div>

            <div>
              <button
                type="button"
                disabled={runningActionId !== null}
                onClick={onRemoveConsumerBloat}
                className="inline-flex items-center gap-2 px-3.5 py-1.5 text-xs font-bold bg-[#88C0D0] text-[#141414] rounded hover:bg-[#9BCBD9] transition-colors cursor-pointer disabled:opacity-50 whitespace-nowrap"
              >
                {runningActionId === 'remove-bloat' ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
                ) : tweaks.consumerBloatRemoved ? (
                  <Check className="w-3.5 h-3.5" aria-hidden="true" />
                ) : (
                  <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                )}
                {tweaks.consumerBloatRemoved ? 'Re-Run Bloat Cleanup' : 'Remove Consumer Bloat'}
              </button>
            </div>
          </div>

          {/* Disable Ads & Telemetry */}
          <div className="lg:pl-6 flex flex-col justify-between gap-3">
            <div className="space-y-1.5">
              <div className="flex items-center justify-between gap-2">
                <h3 className="text-[13px] font-semibold text-[#E0E0E0]">
                  Disable Ads & Telemetry
                </h3>
                <span
                  data-testid="tweak-state-adsTelemetryDisabled"
                  className={`font-mono text-[11px] font-semibold ${
                    tweaks.adsTelemetryDisabled ? 'text-[#98C379]' : 'text-[#909098]'
                  }`}
                >
                  {tweaks.adsTelemetryDisabled ? 'Enabled' : 'Disabled'}
                </span>
              </div>
              <p className="text-xs text-[#A8A8B2] leading-snug">
                Turns off Windows Advertising ID, Start & Settings suggestions, and Copilot taskbar
                button via per-user <span className="font-mono text-[#E0E0E0]">HKCU</span> registry
                keys.
              </p>
              <p className="font-mono text-[10.5px] text-[#7A7A84]">
                HKCU\...\AdvertisingInfo · ContentDeliveryManager · ShowCopilotButton
              </p>
            </div>

            <div>
              <button
                type="button"
                disabled={runningActionId !== null}
                onClick={() => onToggleTweak('adsTelemetryDisabled')}
                className={`inline-flex items-center gap-2 px-3.5 py-1.5 text-xs rounded transition-colors cursor-pointer disabled:opacity-50 whitespace-nowrap ${
                  tweaks.adsTelemetryDisabled
                    ? 'bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] hover:bg-[#2D2D33]'
                    : 'bg-[#88C0D0] text-[#141414] font-bold hover:bg-[#9BCBD9]'
                }`}
              >
                {runningActionId === 'tweak-adsTelemetryDisabled' && (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
                )}
                {tweaks.adsTelemetryDisabled
                  ? 'Restore Default Suggestions'
                  : 'Disable Ads & Telemetry'}
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
