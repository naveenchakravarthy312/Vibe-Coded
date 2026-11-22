export type TabId = 'quick-fixes' | 'apps' | 'tweaks' | 'shortcuts';

export type CpuArch = 'ARM64' | 'x64';

export type AppCategory = 'Parent Essentials' | 'Portable / Power Tools';

export type InstallMethod = 'winget' | 'direct_url';

export interface SystemTelemetry {
  profileName: string;
  windowsVersion: string;
  windowsBuild: string;
  arch: CpuArch;
  isAdmin: boolean;
  ipv4: string;
  adapterName: string;
  hostname: string;
  userProfile: string;
}

export interface AppCatalogItem {
  id: string;
  name: string;
  category: AppCategory;
  method: InstallMethod;
  wingetId?: string;
  portableUrl?: string;
  portableFolder?: string;
  description: string;
  installed: boolean;
  arm64Safe: boolean;
  version?: string;
}

export interface TweakState {
  classicContextMenu: boolean;
  leftAlignTaskbar: boolean;
  hideStartRecommendations: boolean;
  textScaling: 100 | 125 | 150;
  consumerBloatRemoved: boolean;
  adsTelemetryDisabled: boolean;
}

export type LogStreamKind = 'cmd' | 'stdout' | 'stderr' | 'info' | 'exit-ok' | 'exit-err';

export interface LogEntry {
  id: string;
  timestamp: string;
  kind: LogStreamKind;
  text: string;
  source?: string;
}

export interface PendingElevation {
  actionId: string;
  title: string;
  commandPreview: string;
  reason: string;
  onApprove: () => void;
}

export interface PythonBundleFile {
  path: string;
  language: 'python' | 'json' | 'batch' | 'text' | 'markdown';
  summary: string;
  content: string;
}
