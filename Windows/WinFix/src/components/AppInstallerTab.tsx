import {
  ArrowUpCircle,
  CloudDownload,
  Download,
  FileJson,
  Loader2,
  ShieldAlert,
  Trash2,
} from 'lucide-react';
import type { AppCatalogItem, AppCategory, SystemTelemetry } from '../types/winfix';
import { buildAppInstallCommand } from '../utils/winfixLogic';

interface AppInstallerTabProps {
  apps: AppCatalogItem[];
  checkedIds: Record<string, boolean>;
  telemetry: SystemTelemetry;
  runningActionId: string | null;
  onToggleCheck: (id: string) => void;
  onInstallApp: (app: AppCatalogItem) => void;
  onUninstallApp: (app: AppCatalogItem) => void;
  onInstallAllChecked: () => void;
  onUpgradeAll: () => void;
  onOpenGithubSync: () => void;
  onOpenAppsJson: () => void;
}

const CATEGORIES: AppCategory[] = ['Parent Essentials', 'Portable / Power Tools'];

export function AppInstallerTab({
  apps,
  checkedIds,
  telemetry,
  runningActionId,
  onToggleCheck,
  onInstallApp,
  onUninstallApp,
  onInstallAllChecked,
  onUpgradeAll,
  onOpenGithubSync,
  onOpenAppsJson,
}: AppInstallerTabProps) {
  const checkedCount = apps.filter((a) => checkedIds[a.id]).length;

  return (
    <div className="space-y-4">
      {/* Top Action Toolbar */}
      <div className="bg-[#1B1B1D] border border-[#2C2C30] rounded-md px-4 py-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            disabled={runningActionId !== null}
            onClick={onInstallAllChecked}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-bold bg-[#88C0D0] text-[#141414] rounded hover:bg-[#9BCBD9] transition-colors disabled:opacity-50 whitespace-nowrap cursor-pointer"
          >
            <Download className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
            Install All Checked{checkedCount > 0 ? ` (${checkedCount})` : ''}
          </button>

          <button
            type="button"
            disabled={runningActionId !== null}
            onClick={onUpgradeAll}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] hover:border-[#42424A] transition-colors disabled:opacity-50 whitespace-nowrap cursor-pointer"
          >
            <ArrowUpCircle className="w-3.5 h-3.5 text-[#88C0D0] shrink-0" aria-hidden="true" />
            Upgrade All (winget upgrade --all)
          </button>

          <button
            type="button"
            disabled={runningActionId !== null}
            onClick={onOpenGithubSync}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] hover:border-[#42424A] transition-colors disabled:opacity-50 whitespace-nowrap cursor-pointer"
          >
            <CloudDownload className="w-3.5 h-3.5 text-[#88C0D0] shrink-0" aria-hidden="true" />
            Sync JSON from GitHub
          </button>

          <button
            type="button"
            onClick={onOpenAppsJson}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] hover:border-[#42424A] transition-colors whitespace-nowrap cursor-pointer"
          >
            <FileJson className="w-3.5 h-3.5 text-[#88C0D0] shrink-0" aria-hidden="true" />
            Open apps.json
          </button>
        </div>

        <div className="font-mono text-[11px] text-[#909098] tabular-nums">
          {apps.filter((a) => a.installed).length}/{apps.length} installed · bundled + external override
        </div>
      </div>

      {/* Non-Admin Smart Fallback Status Notice */}
      {!telemetry.isAdmin && (
        <div
          className="bg-[#1B1B1D] border border-[#3E3524] rounded-md px-4 py-2.5 flex flex-wrap items-center justify-between gap-2 text-xs"
          data-testid="non-admin-fallback-banner"
        >
          <div className="flex items-center gap-2 text-[#E5C07B] font-medium">
            <ShieldAlert className="w-4 h-4 shrink-0" aria-hidden="true" />
            <span>Non-Admin Smart Fallback Active</span>
          </div>
          <p className="font-mono text-[11px] text-[#C8C8D0]">
            Automatically appending <span className="text-[#88C0D0]">--scope user</span> to winget
            and extracting portable ZIPs to{' '}
            <span className="text-[#88C0D0]">{telemetry.userProfile}\Apps\</span> (no UAC prompt).
          </p>
        </div>
      )}

      {/* App Category Sections */}
      {CATEGORIES.map((category) => {
        const items = apps.filter((app) => app.category === category);
        return (
          <section key={category} className="space-y-2.5">
            <div className="flex items-baseline justify-between gap-2 pb-1.5 border-b border-[#26262A]">
              <h2 className="text-[13.5px] font-semibold text-[#E0E0E0]">{category}</h2>
              <span className="font-mono text-[11px] text-[#909098] tabular-nums">
                {items.length} packages
              </span>
            </div>

            {items.length === 0 ? (
              <p className="text-xs text-[#909098] py-4">
                No apps configured under {category}. Edit apps.json to add entries.
              </p>
            ) : (
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
                {items.map((app) => {
                  const isBusy =
                    runningActionId === `install-${app.id}` ||
                    runningActionId === `uninstall-${app.id}`;
                  const plan = buildAppInstallCommand(app, telemetry);

                  return (
                    <div
                      key={app.id}
                      data-testid={`app-card-${app.id}`}
                      className="flex flex-col justify-between gap-2.5 p-3.5 rounded-md bg-[#1B1B1D] border border-[#2C2C30]"
                    >
                      <div className="space-y-1.5">
                        <div className="flex items-start justify-between gap-3">
                          <label className="inline-flex items-center gap-2.5 cursor-pointer min-w-0">
                            <input
                              type="checkbox"
                              checked={Boolean(checkedIds[app.id])}
                              onChange={() => onToggleCheck(app.id)}
                              className="w-4 h-4 rounded border-[#3C3C44] bg-[#242428] accent-[#88C0D0] cursor-pointer shrink-0"
                            />
                            <span className="text-[13.5px] font-semibold text-[#E0E0E0] truncate">
                              {app.name}
                            </span>
                          </label>

                          <span
                            data-testid={`app-status-${app.id}`}
                            className={`font-mono text-[11px] font-semibold whitespace-nowrap shrink-0 ${
                              app.installed ? 'text-[#98C379]' : 'text-[#909098]'
                            }`}
                          >
                            {app.installed ? 'Installed' : 'Not Installed'}
                          </span>
                        </div>

                        {/* Clean Unboxed Metadata */}
                        <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[11px] font-mono text-[#909098] pl-6">
                          <span>{app.category}</span>
                          <span aria-hidden="true">·</span>
                          <span className="text-[#88C0D0]">{app.method}</span>
                          {app.version && (
                            <>
                              <span aria-hidden="true">·</span>
                              <span className="tabular-nums">v{app.version}</span>
                            </>
                          )}
                          {!telemetry.isAdmin && plan.usesUserScopeFallback && (
                            <>
                              <span aria-hidden="true">·</span>
                              <span className="text-[#E5C07B]">
                                {app.method === 'direct_url' ? 'portable %USERPROFILE%\\Apps' : '--scope user'}
                              </span>
                            </>
                          )}
                        </div>

                        <p className="text-xs text-[#A8A8B2] pl-6 leading-relaxed">
                          {app.description}
                        </p>
                      </div>

                      <div className="flex items-center justify-between gap-2 pl-6 pt-1.5 border-t border-[#222226]">
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            disabled={runningActionId !== null}
                            onClick={() => onInstallApp(app)}
                            aria-label={`Install ${app.name}`}
                            className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs rounded transition-colors cursor-pointer disabled:opacity-50 whitespace-nowrap ${
                              !app.installed
                                ? 'bg-[#88C0D0] text-[#141414] font-bold hover:bg-[#9BCBD9]'
                                : 'bg-[#242428] text-[#E0E0E0] font-medium border border-[#2C2C30] hover:bg-[#2D2D33]'
                            }`}
                          >
                            {runningActionId === `install-${app.id}` ? (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
                            ) : (
                              <Download className="w-3.5 h-3.5" aria-hidden="true" />
                            )}
                            {app.installed ? 'Reinstall' : 'Install'}
                          </button>

                          <button
                            type="button"
                            disabled={runningActionId !== null || !app.installed}
                            onClick={() => onUninstallApp(app)}
                            aria-label={`Uninstall ${app.name}`}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-[#242428] text-[#E06C75] border border-[#3E282B] rounded hover:bg-[#2D1E20] transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed whitespace-nowrap"
                          >
                            {runningActionId === `uninstall-${app.id}` ? (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
                            ) : (
                              <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
                            )}
                            Uninstall
                          </button>
                        </div>

                        {isBusy && (
                          <span className="font-mono text-[11px] text-[#88C0D0]">
                            Running in worker...
                          </span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}
