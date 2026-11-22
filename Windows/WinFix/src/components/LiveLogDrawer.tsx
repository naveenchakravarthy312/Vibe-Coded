import { Check, ChevronDown, ChevronUp, Copy, Terminal, Trash2 } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { LogEntry } from '../types/winfix';

interface LiveLogDrawerProps {
  logs: LogEntry[];
  expanded: boolean;
  runningLabel: string | null;
  onToggleExpand: () => void;
  onClearLogs: () => void;
}

export function LiveLogDrawer({
  logs,
  expanded,
  runningLabel,
  onToggleExpand,
  onClearLogs,
}: LiveLogDrawerProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (expanded && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [logs, expanded]);

  const handleCopy = async () => {
    const plain = logs.map((l) => `[${l.timestamp}] ${l.text}`).join('\n');
    try {
      await navigator.clipboard.writeText(plain);
      setCopied(true);
      setTimeout(() => setCopied(false), 1600);
    } catch {
      // Fallback ignored if clipboard blocked in headless context
    }
  };

  const getLineColor = (kind: LogEntry['kind']) => {
    switch (kind) {
      case 'cmd':
        return 'text-[#88C0D0] font-medium';
      case 'stderr':
      case 'exit-err':
        return 'text-[#E06C75]';
      case 'exit-ok':
        return 'text-[#98C379] font-medium';
      case 'info':
        return 'text-[#909098]';
      default:
        return 'text-[#E0E0E0]';
    }
  };

  return (
    <aside
      aria-label="Collapsible Live Log Drawer"
      className="bg-[#1B1B1D] border-t border-[#2C2C30] shrink-0"
    >
      {/* Drawer Header Bar */}
      <div className="max-w-[1440px] mx-auto px-4 lg:px-6 py-2 flex items-center justify-between gap-3">
        <div className="flex items-center gap-3 min-w-0">
          <button
            type="button"
            onClick={onToggleExpand}
            aria-expanded={expanded}
            className="inline-flex items-center gap-2 px-2.5 py-1 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] transition-colors cursor-pointer whitespace-nowrap"
          >
            <Terminal className="w-3.5 h-3.5 text-[#88C0D0]" aria-hidden="true" />
            <span>{expanded ? 'Hide Live Log' : 'Show Live Log'}</span>
            {expanded ? (
              <ChevronDown className="w-3.5 h-3.5 text-[#909098]" aria-hidden="true" />
            ) : (
              <ChevronUp className="w-3.5 h-3.5 text-[#909098]" aria-hidden="true" />
            )}
          </button>

          <span className="font-mono text-[11px] text-[#909098] truncate tabular-nums">
            {runningLabel ? (
              <span className="text-[#88C0D0]">Executing: {runningLabel}...</span>
            ) : (
              <span>
                Idle · {logs.length} {logs.length === 1 ? 'line' : 'lines'} · Ctrl+L
              </span>
            )}
          </span>
        </div>

        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={handleCopy}
            className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] transition-colors cursor-pointer whitespace-nowrap"
          >
            {copied ? (
              <Check className="w-3.5 h-3.5 text-[#98C379]" aria-hidden="true" />
            ) : (
              <Copy className="w-3.5 h-3.5 text-[#909098]" aria-hidden="true" />
            )}
            {copied ? 'Copied' : 'Copy Log'}
          </button>

          <button
            type="button"
            onClick={onClearLogs}
            className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-medium bg-[#242428] text-[#E0E0E0] border border-[#2C2C30] rounded hover:bg-[#2D2D33] transition-colors cursor-pointer whitespace-nowrap"
          >
            <Trash2 className="w-3.5 h-3.5 text-[#909098]" aria-hidden="true" />
            Clear
          </button>
        </div>
      </div>

      {/* Expandable Console Stream */}
      {expanded && (
        <div
          ref={scrollRef}
          data-testid="live-log-console"
          className="bg-[#101012] border-t border-[#26262A] h-[152px] overflow-y-auto px-4 lg:px-6 py-2 font-mono text-[11.5px] leading-relaxed tabular-nums"
        >
          <div className="max-w-[1440px] mx-auto space-y-0.5">
            {logs.length === 0 ? (
              <div className="text-[#7A7A84]">
                Console cleared. Trigger any diagnostic, installer, or registry action to stream real-time output.
              </div>
            ) : (
              logs.map((entry) => (
                <div key={entry.id} className="flex items-start gap-2.5 break-words">
                  <span className="text-[#62626C] shrink-0 select-none">[{entry.timestamp}]</span>
                  <span className={getLineColor(entry.kind)}>{entry.text}</span>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </aside>
  );
}
