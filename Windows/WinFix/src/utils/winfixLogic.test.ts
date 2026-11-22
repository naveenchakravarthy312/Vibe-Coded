import test from 'node:test';
import assert from 'node:assert/strict';
import { DEFAULT_APPS_CATALOG, serializeAppsJson } from '../data/defaultApps';
import {
  buildAppInstallCommand,
  buildAppUninstallCommand,
  getDebloatPlan,
  parseAppsJson,
} from './winfixLogic';
import { computeCrc32, createZipArchive } from './zipBuilder';

test('Non-Admin winget install automatically appends --scope user', () => {
  const vlc = DEFAULT_APPS_CATALOG.find((a) => a.id === 'vlc-media-player')!;
  const plan = buildAppInstallCommand(vlc, {
    isAdmin: false,
    userProfile: 'C:\\Users\\Mom',
    arch: 'ARM64',
  });

  assert.equal(plan.usesUserScopeFallback, true);
  assert.equal(plan.requiresAdmin, false);
  assert.match(plan.command, /winget install --id VideoLAN\.VLC -e --scope user/);
});

test('Admin winget install omits --scope user', () => {
  const vlc = DEFAULT_APPS_CATALOG.find((a) => a.id === 'vlc-media-player')!;
  const plan = buildAppInstallCommand(vlc, {
    isAdmin: true,
    userProfile: 'C:\\Users\\Admin',
    arch: 'x64',
  });

  assert.equal(plan.usesUserScopeFallback, false);
  assert.doesNotMatch(plan.command, /--scope user/);
});

test('direct_url portable app extracts into %USERPROFILE%\\Apps\\ without UAC', () => {
  const flow = DEFAULT_APPS_CATALOG.find((a) => a.id === 'flow-launcher')!;
  const plan = buildAppInstallCommand(flow, {
    isAdmin: false,
    userProfile: 'C:\\Users\\Mom',
    arch: 'ARM64',
  });

  assert.equal(plan.requiresAdmin, false);
  assert.equal(plan.targetDir, 'C:\\Users\\Mom\\Apps\\FlowLauncher');
  assert.match(plan.command, /Expand-Archive/);
  assert.match(plan.command, /C:\\Users\\Mom\\Apps\\FlowLauncher/);
});

test('Non-Admin winget uninstall appends --scope user', () => {
  const chrome = DEFAULT_APPS_CATALOG.find((a) => a.id === 'google-chrome')!;
  const plan = buildAppUninstallCommand(chrome, {
    isAdmin: false,
    userProfile: 'C:\\Users\\Mom',
  });
  assert.match(plan.command, /winget uninstall --id Google\.Chrome -e --scope user --silent/);
});

test('parseAppsJson validates round-trip catalog and rejects invalid JSON/missing fields', () => {
  const raw = serializeAppsJson(DEFAULT_APPS_CATALOG);
  const okRes = parseAppsJson(raw, { 'google-chrome': true });
  assert.equal(okRes.ok, true);
  if (okRes.ok) {
    assert.equal(okRes.apps.length, DEFAULT_APPS_CATALOG.length);
    assert.equal(okRes.apps.find((a) => a.id === 'google-chrome')?.installed, true);
  }

  const badSyntax = parseAppsJson('{ not valid json');
  assert.equal(badSyntax.ok, false);

  const missingWingetId = parseAppsJson(
    JSON.stringify({ apps: [{ id: 'broken', name: 'Broken App', method: 'winget' }] }),
  );
  assert.equal(missingWingetId.ok, false);
});

test('ARM64 debloat plan strictly excludes Edge WebView2, Bluetooth, and Prism', () => {
  const plan = getDebloatPlan('ARM64');
  const ids = plan.targets.map((t) => t.wingetId.toLowerCase()).join(' ');
  assert.doesNotMatch(ids, /webview2|bluetooth|bthserv|prism/);
  assert.ok(plan.protectedComponents.some((c) => c.includes('EdgeWebView2')));
  assert.ok(plan.protectedComponents.some((c) => c.includes('ARM64 Prism')));
});

test('createZipArchive generates valid PKZIP headers and CRC32 checksum', () => {
  const helloBytes = new TextEncoder().encode('hello');
  assert.equal(computeCrc32(helloBytes), 0x3610a686);

  const zip = createZipArchive([
    { name: 'WinFix/requirements.txt', content: 'PyQt6>=6.7.0\n' },
    { name: 'WinFix/README.md', content: '# WinFix\n' },
  ]);

  const dv = new DataView(zip.buffer);
  // Starts with Local File Header signature PK\x03\x04
  assert.equal(dv.getUint32(0, true), 0x04034b50);
  // Ends with End of Central Directory signature PK\x05\x06 (22 bytes from end)
  assert.equal(dv.getUint32(zip.length - 22, true), 0x06054b50);
  // Total entry count = 2
  assert.equal(dv.getUint16(zip.length - 12, true), 2);
});
