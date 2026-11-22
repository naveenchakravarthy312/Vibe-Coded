import {
  Check,
  CloudDownload,
  Copy,
  Download,
  FileCode2,
  RotateCcw,
  ShieldAlert,
  X,
} from 'lucide-react';
import { useState } from 'react';
import { DEFAULT_APPS_CATALOG, DEFAULT_GITHUB_APPS_URL, serializeAppsJson } from '../data/defaultApps';
import { getPythonBundleFiles } from '../data/pythonSourceFiles';
import type { AppCatalogItem, PendingElevation } from '../types/winfix';
import { parseAppsJson } from '../utils/winfixLogic';
import { createZipArchive } from '../utils/zipBuilder';

/* -------------------------------------------------------------------------- */
/* Granular UAC Elevation Modal                                               */
/* -------------------------------------------------------------------------- */

export function ElevationModal({
  elevation,
  onCancel,
}: {
  elevation: PendingElevation | null;
  onCancel: () => void;
}) {
  if (!elevation) return null;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="uac-modal-title"
    >
      <div className="w-full max-w-lg bg-[#1B1B1D] border border-[#2C2C30] rounded-md overflow-hidden">
        <div className="px-5 py-3.5 bg-[#202024] border-b border-[#2C2C30] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <ShieldAlert className="w-4 h-4 text-[#E5C07B]" aria-hidden="true" />
            <h3 id="uac-modal-title" className="text-sm font-semibold text-[#E0E0E0]">
              User Account Control — Granular Elevation
            </h3>
          </div>
          <button
            type="button"
            onClick={onCancel}
            aria-label="Close UAC dialog"
            className="text-[#909098] hover:text-[#E0E0E0] p-1 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 space-y-3.5">
          <p className="text-xs text-[#C8C8D0] leading-relaxed">
            <span className="font-semibold text-[#E0E0E0]">{elevation.title}</span> requires
            Administrator privileges. WinFix uses{' '}
            <span className="font-mono text-[#88C0D0]">ShellExecuteW(&quot;runas&quot;)</span> only
            for this action so standard tasks stay UAC-free.
          </p>

          <div className="bg-[#101012] border border-[#26262A] rounded p-3 font-mono text-[11px] text-[#88C0D0] break-all">
            {elevation.commandPreview}
          </div>

          <p className="text-xs text-[#909098]">{elevation.reason}</p>
        </div>

        <div className="px-5 py-3 bg-[#161618] border-t border-[#26262A] flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="px-3.5 py-1.5 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] cursor-pointer"
          >
            Cancel (No)
          </button>
          <button
            type="button"
            onClick={elevation.onApprove}
            className="px-3.5 py-1.5 text-xs font-bold bg-[#88C0D0] text-[#141414] rounded hover:bg-[#9BCBD9] cursor-pointer"
          >
            Approve Elevation (Yes)
          </button>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Reboot Prompt Modal (after Reset Network Stack)                            */
/* -------------------------------------------------------------------------- */

export function RebootPromptModal({
  open,
  onRestartLater,
  onRestartNow,
}: {
  open: boolean;
  onRestartLater: () => void;
  onRestartNow: () => void;
}) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reboot-modal-title"
    >
      <div className="w-full max-w-md bg-[#1B1B1D] border border-[#2C2C30] rounded-md overflow-hidden">
        <div className="px-5 py-3.5 bg-[#202024] border-b border-[#2C2C30] flex items-center justify-between">
          <h3 id="reboot-modal-title" className="text-sm font-semibold text-[#E0E0E0]">
            Restart Required
          </h3>
          <button
            type="button"
            onClick={onRestartLater}
            aria-label="Close reboot prompt"
            className="text-[#909098] hover:text-[#E0E0E0] p-1 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 space-y-2.5 text-xs text-[#C8C8D0] leading-relaxed">
          <p>
            Winsock and TCP/IP stack reset succeeded. Windows must restart to finalize the clean
            network interface configuration.
          </p>
        </div>

        <div className="px-5 py-3 bg-[#161618] border-t border-[#26262A] flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onRestartLater}
            className="px-3.5 py-1.5 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] cursor-pointer"
          >
            Restart Later
          </button>
          <button
            type="button"
            onClick={onRestartNow}
            className="px-3.5 py-1.5 text-xs font-bold bg-[#88C0D0] text-[#141414] rounded hover:bg-[#9BCBD9] cursor-pointer"
          >
            Schedule Reboot (5s)
          </button>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* apps.json Live Editor Modal                                                */
/* -------------------------------------------------------------------------- */

export function AppsJsonEditorModal({
  open,
  apps,
  onClose,
  onSave,
}: {
  open: boolean;
  apps: AppCatalogItem[];
  onClose: () => void;
  onSave: (parsedApps: AppCatalogItem[]) => void;
}) {
  const [draft, setDraft] = useState(() => serializeAppsJson(apps));
  const [error, setError] = useState<string | null>(null);

  if (!open) return null;

  const installedMap = Object.fromEntries(apps.map((a) => [a.id, a.installed]));

  const handleApply = () => {
    const result = parseAppsJson(draft, installedMap);
    if (!result.ok) {
      setError(result.error);
      return;
    }
    setError(null);
    onSave(result.apps);
    onClose();
  };

  const handleReset = () => {
    setDraft(serializeAppsJson(DEFAULT_APPS_CATALOG));
    setError(null);
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="apps-json-title"
    >
      <div className="w-full max-w-3xl bg-[#1B1B1D] border border-[#2C2C30] rounded-md flex flex-col max-h-[86vh]">
        <div className="px-5 py-3.5 bg-[#202024] border-b border-[#2C2C30] flex items-center justify-between">
          <div>
            <h3 id="apps-json-title" className="text-sm font-semibold text-[#E0E0E0]">
              apps.json Catalog Editor
            </h3>
            <p className="text-[11px] font-mono text-[#909098]">
              Bundled inside WinFix.exe · Overridden automatically by external apps.json next to .exe
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close apps.json editor"
            className="text-[#909098] hover:text-[#E0E0E0] p-1 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-4 flex-1 flex flex-col gap-3 min-h-0">
          <label htmlFor="apps-json-textarea" className="sr-only">
            apps.json content
          </label>
          <textarea
            id="apps-json-textarea"
            value={draft}
            onChange={(e) => {
              setDraft(e.target.value);
              if (error) setError(null);
            }}
            spellCheck={false}
            className="w-full h-[380px] bg-[#101012] text-[#E0E0E0] border border-[#2C2C30] rounded p-3 font-mono text-[12px] leading-relaxed resize-none"
          />

          {error && (
            <div
              role="alert"
              className="text-xs font-mono text-[#E06C75] bg-[#241618] border border-[#4A2B2E] rounded px-3 py-2"
            >
              {error}
            </div>
          )}
        </div>

        <div className="px-5 py-3 bg-[#161618] border-t border-[#26262A] flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={handleReset}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] cursor-pointer"
          >
            <RotateCcw className="w-3.5 h-3.5" aria-hidden="true" />
            Reset to Default Catalog
          </button>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-1.5 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleApply}
              className="px-3.5 py-1.5 text-xs font-bold bg-[#88C0D0] text-[#141414] rounded hover:bg-[#9BCBD9] cursor-pointer"
            >
              Validate & Save apps.json
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Sync JSON from GitHub Modal                                                */
/* -------------------------------------------------------------------------- */

export function GithubSyncModal({
  open,
  initialUrl,
  onClose,
  onSyncUrl,
}: {
  open: boolean;
  initialUrl: string;
  onClose: () => void;
  onSyncUrl: (url: string) => Promise<{ ok: boolean; message: string }>;
}) {
  const [url, setUrl] = useState(initialUrl || DEFAULT_GITHUB_APPS_URL);
  const [status, setStatus] = useState<{ ok: boolean; message: string } | null>(null);
  const [loading, setLoading] = useState(false);

  if (!open) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url.trim()) {
      setStatus({ ok: false, message: 'Enter a valid raw GitHub JSON URL.' });
      return;
    }
    setLoading(true);
    setStatus(null);
    const res = await onSyncUrl(url.trim());
    setLoading(false);
    setStatus(res);
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="github-sync-title"
    >
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-xl bg-[#1B1B1D] border border-[#2C2C30] rounded-md overflow-hidden"
      >
        <div className="px-5 py-3.5 bg-[#202024] border-b border-[#2C2C30] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CloudDownload className="w-4 h-4 text-[#88C0D0]" aria-hidden="true" />
            <h3 id="github-sync-title" className="text-sm font-semibold text-[#E0E0E0]">
              Sync apps.json from GitHub
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close GitHub sync dialog"
            className="text-[#909098] hover:text-[#E0E0E0] p-1 cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-5 space-y-3">
          <label htmlFor="github-raw-url" className="block text-xs font-medium text-[#C8C8D0]">
            Raw GitHub JSON URL
          </label>
          <input
            id="github-raw-url"
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://raw.githubusercontent.com/<user>/<repo>/main/apps.json"
            className="w-full bg-[#101012] text-[#E0E0E0] border border-[#2C2C30] rounded px-3 py-2 font-mono text-xs"
          />
          <p className="text-[11px] text-[#909098]">
            Fetches the remote JSON schema, validates all package IDs, and saves an external
            override next to <span className="font-mono">WinFix.exe</span>.
          </p>

          {status && (
            <div
              role="status"
              className={`text-xs font-mono px-3 py-2 rounded border ${
                status.ok
                  ? 'text-[#98C379] bg-[#162219] border-[#2C4632]'
                  : 'text-[#E06C75] bg-[#241618] border-[#4A2B2E]'
              }`}
            >
              {status.message}
            </div>
          )}
        </div>

        <div className="px-5 py-3 bg-[#161618] border-t border-[#26262A] flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="px-3.5 py-1.5 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] cursor-pointer"
          >
            Close
          </button>
          <button
            type="submit"
            disabled={loading}
            className="px-3.5 py-1.5 text-xs font-bold bg-[#88C0D0] text-[#141414] rounded hover:bg-[#9BCBD9] disabled:opacity-50 cursor-pointer"
          >
            {loading ? 'Syncing...' : 'Fetch & Validate'}
          </button>
        </div>
      </form>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Portable Python + PyQt6 Build Kit Modal                                    */
/* -------------------------------------------------------------------------- */

export function PythonKitModal({
  open,
  apps,
  onClose,
}: {
  open: boolean;
  apps: AppCatalogItem[];
  onClose: () => void;
}) {
  const files = getPythonBundleFiles(serializeAppsJson(apps));
  const [selectedPath, setSelectedPath] = useState(files[0].path);
  const [copiedFile, setCopiedFile] = useState(false);

  if (!open) return null;

  const activeFile = files.find((f) => f.path === selectedPath) ?? files[0];

  const handleCopyFile = async () => {
    try {
      await navigator.clipboard.writeText(activeFile.content);
      setCopiedFile(true);
      setTimeout(() => setCopiedFile(false), 1600);
    } catch {
      // Ignore if clipboard blocked
    }
  };

  const handleDownloadZip = () => {
    const zipBytes = createZipArchive(
      files.map((f) => ({ name: `WinFix/${f.path}`, content: f.content })),
    );
    const blob = new Blob([zipBytes.buffer as ArrayBuffer], { type: 'application/zip' });
    const blobUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = blobUrl;
    a.download = 'WinFix-PyQt6-Portable.zip';
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
  };

  return (
    <div
      className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="pyqt-kit-title"
    >
      <div className="w-full max-w-5xl bg-[#1B1B1D] border border-[#2C2C30] rounded-md flex flex-col max-h-[88vh]">
        <div className="px-5 py-3.5 bg-[#202024] border-b border-[#2C2C30] flex items-center justify-between gap-4">
          <div className="min-w-0">
            <h3 id="pyqt-kit-title" className="text-sm font-semibold text-[#E0E0E0] truncate">
              WinFix Portable Python + PyQt6 Source & Build Kit
            </h3>
            <p className="text-[11px] font-mono text-[#909098] truncate">
              Single-file Windows 11 PyQt6 source · current apps.json · requirements.txt · build_portable.bat
            </p>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={handleDownloadZip}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-bold bg-[#88C0D0] text-[#141414] rounded hover:bg-[#9BCBD9] cursor-pointer whitespace-nowrap"
            >
              <Download className="w-3.5 h-3.5" aria-hidden="true" />
              Download WinFix-PyQt6-Portable.zip
            </button>

            <button
              type="button"
              onClick={onClose}
              aria-label="Close PyQt6 build kit"
              className="text-[#909098] hover:text-[#E0E0E0] p-1 cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-[230px_1fr] flex-1 min-h-0">
          {/* File Sidebar */}
          <nav
            aria-label="Python source files"
            className="border-b md:border-b-0 md:border-r border-[#2C2C30] bg-[#161618] p-2.5 flex md:flex-col gap-1 overflow-x-auto"
          >
            {files.map((file) => {
              const active = file.path === activeFile.path;
              return (
                <button
                  key={file.path}
                  type="button"
                  onClick={() => setSelectedPath(file.path)}
                  aria-current={active ? 'page' : undefined}
                  className={`flex items-center gap-2 px-3 py-2 rounded text-left font-mono text-xs cursor-pointer whitespace-nowrap ${
                    active
                      ? 'bg-[#242428] text-[#88C0D0] font-semibold border border-[#88C0D0]/50'
                      : 'text-[#C8C8D0] hover:bg-[#1E1E22] border border-transparent'
                  }`}
                >
                  <FileCode2 className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
                  <span>{file.path}</span>
                </button>
              );
            })}
          </nav>

          {/* Code Viewer */}
          <div className="flex flex-col min-h-0">
            <div className="px-4 py-2.5 bg-[#1B1B1D] border-b border-[#26262A] flex items-center justify-between gap-3">
              <p className="text-xs text-[#A8A8B2] truncate">{activeFile.summary}</p>
              <button
                type="button"
                onClick={handleCopyFile}
                className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] cursor-pointer shrink-0"
              >
                {copiedFile ? (
                  <Check className="w-3.5 h-3.5 text-[#98C379]" aria-hidden="true" />
                ) : (
                  <Copy className="w-3.5 h-3.5 text-[#909098]" aria-hidden="true" />
                )}
                {copiedFile ? 'Copied' : `Copy ${activeFile.path}`}
              </button>
            </div>

            <pre className="flex-1 overflow-auto p-4 bg-[#101012] font-mono text-[11.5px] leading-relaxed text-[#E0E0E0] max-h-[58vh]">
              <code>{activeFile.content}</code>
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
}
