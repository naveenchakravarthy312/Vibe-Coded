"""
WinFix — Portable Windows 11 Utility (Python + PyQt6)
Matte dark, dense button-driven control panel for troubleshooting parent laptops
and general Windows 11 PCs (including Standard User / Non-Admin and ARM64 devices).
"""

from __future__ import annotations

import os
import subprocess
import sys
from datetime import datetime
from typing import Any, Dict, List, Optional

from PyQt6.QtCore import Qt
from PyQt6.QtGui import QAction, QColor, QFont, QKeySequence, QTextCharFormat, QTextCursor
from PyQt6.QtWidgets import (
    QApplication,
    QCheckBox,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QPushButton,
    QScrollArea,
    QSizePolicy,
    QSplitter,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from winfix_core import (
    DEFAULT_GITHUB_RAW_URL,
    SAFE_DEBLOAT_PACKAGES,
    CommandWorker,
    FunctionWorker,
    PortableDownloadWorker,
    SystemTelemetry,
    build_winget_install_cmd,
    build_winget_uninstall_cmd,
    collect_telemetry,
    detect_installed_apps,
    load_apps_catalog,
    read_tweak_states,
    relaunch_as_admin,
    resolve_apps_json_path,
    set_ads_telemetry_disabled,
    set_classic_context_menu,
    set_hide_start_recommendations,
    set_left_align_taskbar,
    set_text_scaling,
    shell_execute_elevated,
    sync_apps_json_from_github,
)

WINFIX_QSS = """
QMainWindow, QWidget {
    background-color: #141414;
    color: #E0E0E0;
    font-family: "Segoe UI", "Roboto", sans-serif;
    font-size: 11pt;
}
QFrame#TelemetryBar {
    background-color: #1B1B1D;
    border-bottom: 1px solid #2C2C30;
}
QFrame#SectionCard, QFrame#AppCard, QFrame#LogContainer {
    background-color: #1B1B1D;
    border: 1px solid #2C2C30;
    border-radius: 6px;
}
QLabel#SectionTitle {
    color: #E0E0E0;
    font-size: 11.5pt;
    font-weight: 600;
    background: transparent;
}
QLabel#MutedMeta {
    color: #909098;
    font-size: 9.5pt;
    background: transparent;
}
QLabel#TelemetryMono {
    font-family: "JetBrains Mono", "Consolas", monospace;
    font-size: 10pt;
    color: #E0E0E0;
    background: transparent;
}
QTabWidget::pane {
    border: none;
    background-color: #141414;
}
QTabBar::tab {
    background-color: #1B1B1D;
    color: #A0A0A8;
    border: 1px solid #2C2C30;
    border-bottom: none;
    padding: 8px 16px;
    margin-right: 4px;
    border-top-left-radius: 5px;
    border-top-right-radius: 5px;
    font-weight: 500;
}
QTabBar::tab:selected {
    background-color: #242428;
    color: #88C0D0;
    border-color: #88C0D0;
    font-weight: 600;
}
QTabBar::tab:hover:!selected {
    color: #E0E0E0;
    background-color: #202024;
}
QPushButton {
    background-color: #242428;
    color: #E0E0E0;
    border: 1px solid #2C2C30;
    border-radius: 5px;
    padding: 8px 14px;
    font-size: 10.5pt;
    font-weight: 500;
}
QPushButton:hover {
    background-color: #2D2D33;
    border-color: #42424A;
}
QPushButton:pressed {
    background-color: #1E1E22;
}
QPushButton[primary="true"] {
    background-color: #88C0D0;
    color: #141414;
    border: 1px solid #88C0D0;
    font-weight: 700;
}
QPushButton[primary="true"]:hover {
    background-color: #9BCBD9;
}
QPushButton[danger="true"] {
    background-color: #242428;
    color: #E06C75;
    border: 1px solid #4A2B2E;
}
QPushButton[danger="true"]:hover {
    background-color: #2F1F21;
}
QPushButton[activeScale="true"] {
    background-color: #88C0D0;
    color: #141414;
    font-weight: 700;
    border-color: #88C0D0;
}
QPlainTextEdit#LogConsole {
    background-color: #101012;
    color: #E0E0E0;
    border: none;
    font-family: "JetBrains Mono", "Consolas", monospace;
    font-size: 10pt;
    selection-background-color: #323842;
}
QScrollArea {
    border: none;
    background-color: #141414;
}
QCheckBox {
    spacing: 8px;
    background: transparent;
}
"""


class WinFixWindow(QMainWindow):
    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("WinFix — Portable Windows 11 Utility")
        self.resize(1220, 820)
        self.setMinimumSize(960, 680)

        self.telemetry: SystemTelemetry = collect_telemetry()
        self.apps_catalog: List[Dict[str, Any]] = []
        self.installed_map: Dict[str, bool] = {}
        self.app_checkboxes: Dict[str, QCheckBox] = {}
        self.tweak_states: Dict[str, Any] = read_tweak_states()
        self.active_workers: List[Any] = []
        self.log_expanded = True

        self._load_catalog_safe()
        self._build_ui()
        self._bind_shortcuts()
        self._log_line(
            "info",
            f"WinFix initialized on {self.telemetry.windows_version} ({self.telemetry.windows_build}) "
            f"| {self.telemetry.arch} | {'Administrator' if self.telemetry.is_admin else 'Standard User (Non-Admin)'}",
        )
        self._refresh_installed_apps_async()

    def _load_catalog_safe(self) -> None:
        try:
            self.apps_catalog, _ = load_apps_catalog()
        except Exception:
            self.apps_catalog = []

    def _bind_shortcuts(self) -> None:
        quit_act = QAction(self)
        quit_act.setShortcut(QKeySequence("Ctrl+Q"))
        quit_act.triggered.connect(self.close)
        self.addAction(quit_act)

        log_act = QAction(self)
        log_act.setShortcut(QKeySequence("Ctrl+L"))
        log_act.triggered.connect(self._toggle_log_drawer)
        self.addAction(log_act)

        for idx in range(4):
            tab_act = QAction(self)
            tab_act.setShortcut(QKeySequence(f"Ctrl+{idx + 1}"))
            tab_act.triggered.connect(lambda _=False, i=idx: self.tabs.setCurrentIndex(i))
            self.addAction(tab_act)

    def _build_ui(self) -> None:
        root = QWidget()
        root_layout = QVBoxLayout(root)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)

        root_layout.addWidget(self._build_telemetry_header())

        self.splitter = QSplitter(Qt.Orientation.Vertical)
        self.splitter.setHandleWidth(4)

        # Main workspace container
        main_panel = QWidget()
        main_layout = QVBoxLayout(main_panel)
        main_layout.setContentsMargins(16, 12, 16, 8)
        main_layout.setSpacing(10)

        self.tabs = QTabWidget()
        self.tabs.addTab(self._build_tab_quick_fixes(), "1. Quick Fixes & Diagnostics")
        self.tabs.addTab(self._build_tab_apps(), "2. App Installer & Updater")
        self.tabs.addTab(self._build_tab_tweaks(), "3. Windows 10 UI & Safe Debloat")
        self.tabs.addTab(self._build_tab_shortcuts(), "4. Windows Admin Shortcuts")
        main_layout.addWidget(self.tabs)

        self.splitter.addWidget(main_panel)
        self.splitter.addWidget(self._build_log_drawer())
        self.splitter.setStretchFactor(0, 4)
        self.splitter.setStretchFactor(1, 1)

        root_layout.addWidget(self.splitter, 1)
        self.setCentralWidget(root)

    # ------------------------------------------------------------------
    # Persistent Telemetry Header
    # ------------------------------------------------------------------

    def _build_telemetry_header(self) -> QFrame:
        bar = QFrame()
        bar.setObjectName("TelemetryBar")
        layout = QHBoxLayout(bar)
        layout.setContentsMargins(16, 10, 16, 10)
        layout.setSpacing(14)

        brand = QLabel("WinFix")
        brand.setStyleSheet("font-size: 13pt; font-weight: 700; color: #E0E0E0; background: transparent;")
        layout.addWidget(brand)

        sep = QLabel("·")
        sep.setObjectName("MutedMeta")
        layout.addWidget(sep)

        os_lbl = QLabel(f"{self.telemetry.windows_version} ({self.telemetry.windows_build})")
        os_lbl.setObjectName("TelemetryMono")
        layout.addWidget(os_lbl)

        arch_lbl = QLabel(self.telemetry.arch)
        arch_lbl.setObjectName("TelemetryMono")
        arch_lbl.setStyleSheet("font-family: 'JetBrains Mono', Consolas, monospace; font-size: 10pt; color: #88C0D0; font-weight: 600; background: transparent;")
        layout.addWidget(arch_lbl)

        priv_lbl = QLabel(
            "Administrator" if self.telemetry.is_admin else "Standard User (Non-Admin)"
        )
        priv_color = "#98C379" if self.telemetry.is_admin else "#E5C07B"
        priv_lbl.setStyleSheet(
            f"font-family: 'JetBrains Mono', Consolas, monospace; font-size: 10pt; "
            f"font-weight: 600; color: {priv_color}; background: transparent;"
        )
        layout.addWidget(priv_lbl)

        net_lbl = QLabel(f"{self.telemetry.ipv4} · {self.telemetry.adapter_name}")
        net_lbl.setObjectName("TelemetryMono")
        net_lbl.setStyleSheet("font-family: 'JetBrains Mono', Consolas, monospace; font-size: 10pt; color: #A0A0A8; background: transparent;")
        layout.addWidget(net_lbl)

        layout.addStretch(1)

        if not self.telemetry.is_admin:
            elev_btn = QPushButton("Relaunch as Admin")
            elev_btn.setProperty("primary", True)
            elev_btn.clicked.connect(self._handle_relaunch_admin)
            layout.addWidget(elev_btn)

        return bar

    # ------------------------------------------------------------------
    # Tab 1: Quick Fixes & Diagnostics
    # ------------------------------------------------------------------

    def _build_tab_quick_fixes(self) -> QWidget:
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        container = QWidget()
        layout = QVBoxLayout(container)
        layout.setContentsMargins(0, 12, 0, 12)
        layout.setSpacing(14)

        layout.addWidget(
            self._make_action_section(
                "Network & Connectivity",
                [
                    (
                        "Flush DNS & Renew IP",
                        "Clears DNS resolver cache and renews DHCP lease.",
                        False,
                        lambda: self._run_commands(
                            "Flush DNS & Renew IP",
                            [
                                ("ipconfig /flushdns", ["ipconfig", "/flushdns"], False),
                                ("ipconfig /renew", ["ipconfig", "/renew"], False),
                            ],
                        ),
                    ),
                    (
                        "Test Internet & Ping",
                        "Checks DNS resolution and pings 1.1.1.1 & google.com.",
                        False,
                        lambda: self._run_commands(
                            "Test Internet & Ping",
                            [
                                (
                                    "Resolve-DnsName google.com",
                                    ["powershell", "-NoProfile", "-Command", "Resolve-DnsName google.com | Select-Object -First 2"],
                                    False,
                                ),
                                ("ping -n 2 1.1.1.1", ["ping", "-n", "2", "1.1.1.1"], False),
                                ("ping -n 2 google.com", ["ping", "-n", "2", "google.com"], False),
                            ],
                        ),
                    ),
                    (
                        "Reset Network Stack",
                        "Runs netsh winsock & IP reset (Admin · reboot prompt).",
                        True,
                        self._handle_reset_network_stack,
                    ),
                ],
            )
        )

        layout.addWidget(
            self._make_action_section(
                "System & Shell Recovery",
                [
                    (
                        "Restart Explorer / Taskbar",
                        "Cleanly restarts explorer.exe to unfreeze taskbar & Start.",
                        False,
                        lambda: self._run_commands(
                            "Restart Explorer",
                            [("taskkill /f /im explorer.exe && start explorer.exe", "taskkill /f /im explorer.exe && start explorer.exe", True)],
                        ),
                    ),
                    (
                        "Fix Stuck Printer",
                        "Stops Spooler, clears PRINTERS queue, restarts Spooler (Admin).",
                        True,
                        lambda: self._run_admin_or_elevate(
                            "Fix Stuck Printer",
                            "cmd.exe",
                            r'/c net stop Spooler & del /Q /F "%SystemRoot%\System32\spool\PRINTERS\*" & net start Spooler',
                        ),
                    ),
                    (
                        "Clean Temp & Update Cache",
                        "Safely wipes %TEMP% without touching user docs or browsers.",
                        False,
                        lambda: self._run_commands(
                            "Clean Temp Cache",
                            [
                                (
                                    "Clear %TEMP%",
                                    [
                                        "powershell",
                                        "-NoProfile",
                                        "-Command",
                                        "Get-ChildItem $env:TEMP -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue; Write-Output 'Temp cleanup finished.'",
                                    ],
                                    False,
                                )
                            ],
                        ),
                    ),
                    (
                        "Repair System Files (SFC)",
                        "Runs sfc /scannow in background worker (Admin).",
                        True,
                        lambda: self._run_admin_or_elevate("Repair System Files (SFC)", "cmd.exe", "/c sfc /scannow"),
                    ),
                ],
            )
        )

        layout.addWidget(
            self._make_action_section(
                "Phone Sync & O+ Connect Doctor",
                [
                    (
                        "Check Phone Sync Services",
                        "Verifies WPNService, Bluetooth, Wi-Fi Direct, and WebView2.",
                        False,
                        lambda: self._run_commands(
                            "Check Phone Sync Services",
                            [
                                (
                                    "Verify WPNService, bthserv, Wi-Fi Direct, WebView2",
                                    [
                                        "powershell",
                                        "-NoProfile",
                                        "-Command",
                                        "Get-Service WpnService,bthserv -ErrorAction SilentlyContinue | Format-Table Name,Status,StartType; "
                                        "Get-NetAdapter | Where-Object {$_.InterfaceDescription -match 'Wi-Fi Direct'} | Format-Table Name,Status; "
                                        "$wv = Get-ItemProperty 'HKLM:\\SOFTWARE\\WOW6432Node\\Microsoft\\EdgeUpdate\\Clients\\{F3017226-FE2A-4295-8BDF-00C3A9A7E4C5}' -ErrorAction SilentlyContinue; "
                                        "if ($wv) { Write-Output ('Edge WebView2 Runtime: ' + $wv.pv) } else { Write-Output 'Edge WebView2 Runtime: Not found in HKLM' }",
                                    ],
                                    False,
                                )
                            ],
                        ),
                    ),
                    (
                        "Fix O+ Connect Firewall",
                        "Allows O+ Connect inbound/outbound on Private networks (Admin).",
                        True,
                        lambda: self._run_admin_or_elevate(
                            "Fix O+ Connect Firewall",
                            "powershell.exe",
                            '-NoProfile -Command "New-NetFirewallRule -DisplayName \'O+ Connect (Private In)\' -Direction Inbound -Profile Private -Action Allow -ErrorAction SilentlyContinue; '
                            'New-NetFirewallRule -DisplayName \'O+ Connect (Private Out)\' -Direction Outbound -Profile Private -Action Allow -ErrorAction SilentlyContinue"',
                        ),
                    ),
                ],
            )
        )

        layout.addStretch(1)
        scroll.setWidget(container)
        return scroll

    def _make_action_section(self, title: str, actions: List[tuple]) -> QFrame:
        card = QFrame()
        card.setObjectName("SectionCard")
        outer = QVBoxLayout(card)
        outer.setContentsMargins(16, 14, 16, 16)
        outer.setSpacing(12)

        hdr = QLabel(title)
        hdr.setObjectName("SectionTitle")
        outer.addWidget(hdr)

        grid = QGridLayout()
        grid.setSpacing(10)
        cols = 3 if len(actions) != 4 else 2
        for i, (btn_label, desc, needs_admin, handler) in enumerate(actions):
            cell = QWidget()
            cell.setStyleSheet("background: transparent;")
            cell_layout = QVBoxLayout(cell)
            cell_layout.setContentsMargins(0, 0, 0, 0)
            cell_layout.setSpacing(4)

            btn = QPushButton(btn_label)
            btn.setProperty("primary", i == 0)
            btn.clicked.connect(handler)
            cell_layout.addWidget(btn)

            sub = QLabel(desc + (" · Requires UAC" if needs_admin and not self.telemetry.is_admin else ""))
            sub.setObjectName("MutedMeta")
            sub.setWordWrap(True)
            cell_layout.addWidget(sub)

            grid.addWidget(cell, i // cols, i % cols)

        outer.addLayout(grid)
        return card

    # ------------------------------------------------------------------
    # Tab 2: App Installer & Updater (apps.json)
    # ------------------------------------------------------------------

    def _build_tab_apps(self) -> QWidget:
        wrapper = QWidget()
        layout = QVBoxLayout(wrapper)
        layout.setContentsMargins(0, 12, 0, 8)
        layout.setSpacing(10)

        top_bar = QHBoxLayout()
        top_bar.setSpacing(8)

        btn_install_checked = QPushButton("Install All Checked")
        btn_install_checked.setProperty("primary", True)
        btn_install_checked.clicked.connect(self._install_all_checked)
        top_bar.addWidget(btn_install_checked)

        btn_upgrade_all = QPushButton("Upgrade All (winget upgrade --all)")
        btn_upgrade_all.clicked.connect(self._upgrade_all_apps)
        top_bar.addWidget(btn_upgrade_all)

        btn_sync = QPushButton("Sync JSON from GitHub")
        btn_sync.clicked.connect(self._sync_json_github)
        top_bar.addWidget(btn_sync)

        btn_open_json = QPushButton("Open apps.json")
        btn_open_json.clicked.connect(self._open_apps_json)
        top_bar.addWidget(btn_open_json)

        top_bar.addStretch(1)

        if not self.telemetry.is_admin:
            fallback_lbl = QLabel("Non-Admin Fallback: --scope user & %USERPROFILE%\\Apps\\")
            fallback_lbl.setStyleSheet("color: #E5C07B; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 9.5pt;")
            top_bar.addWidget(fallback_lbl)

        layout.addLayout(top_bar)

        self.apps_scroll = QScrollArea()
        self.apps_scroll.setWidgetResizable(True)
        self.apps_container = QWidget()
        self.apps_layout = QVBoxLayout(self.apps_container)
        self.apps_layout.setContentsMargins(0, 4, 0, 12)
        self.apps_layout.setSpacing(14)
        self.apps_scroll.setWidget(self.apps_container)
        layout.addWidget(self.apps_scroll, 1)

        self._rebuild_app_cards()
        return wrapper

    def _rebuild_app_cards(self) -> None:
        while self.apps_layout.count():
            item = self.apps_layout.takeAt(0)
            widget = item.widget()
            if widget:
                widget.deleteLater()
        self.app_checkboxes.clear()

        for category in ("Parent Essentials", "Portable / Power Tools"):
            group = [a for a in self.apps_catalog if a.get("category") == category]
            if not group:
                continue
            hdr = QLabel(category)
            hdr.setObjectName("SectionTitle")
            self.apps_layout.addWidget(hdr)

            grid = QGridLayout()
            grid.setSpacing(10)
            for idx, app in enumerate(group):
                card = QFrame()
                card.setObjectName("AppCard")
                c_layout = QVBoxLayout(card)
                c_layout.setContentsMargins(14, 12, 14, 12)
                c_layout.setSpacing(8)

                row1 = QHBoxLayout()
                cb = QCheckBox(str(app.get("name", "App")))
                cb.setStyleSheet("font-weight: 600; font-size: 11pt;")
                self.app_checkboxes[str(app.get("id", ""))] = cb
                row1.addWidget(cb)
                row1.addStretch(1)

                is_inst = self.installed_map.get(str(app.get("id", "")), False)
                status = QLabel("Installed" if is_inst else "Not Installed")
                status.setStyleSheet(
                    f"color: {'#98C379' if is_inst else '#909098'}; "
                    "font-family: 'JetBrains Mono', Consolas, monospace; font-size: 9.5pt; background: transparent;"
                )
                row1.addWidget(status)
                c_layout.addLayout(row1)

                meta = QLabel(
                    f"{app.get('category')} · {app.get('method')} · {app.get('description', '')}"
                )
                meta.setObjectName("MutedMeta")
                meta.setWordWrap(True)
                c_layout.addWidget(meta)

                btn_row = QHBoxLayout()
                btn_row.setSpacing(8)
                inst_btn = QPushButton("Install")
                inst_btn.setProperty("primary", not is_inst)
                inst_btn.clicked.connect(lambda _=False, a=app: self._install_single_app(a))
                btn_row.addWidget(inst_btn)

                uninst_btn = QPushButton("Uninstall")
                uninst_btn.setProperty("danger", True)
                uninst_btn.clicked.connect(lambda _=False, a=app: self._uninstall_single_app(a))
                btn_row.addWidget(uninst_btn)
                btn_row.addStretch(1)
                c_layout.addLayout(btn_row)

                grid.addWidget(card, idx // 2, idx % 2)

            grid_host = QWidget()
            grid_host.setLayout(grid)
            self.apps_layout.addWidget(grid_host)

        self.apps_layout.addStretch(1)

    # ------------------------------------------------------------------
    # Tab 3: Windows 10 UI & Safe Debloat
    # ------------------------------------------------------------------

    def _build_tab_tweaks(self) -> QWidget:
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        container = QWidget()
        layout = QVBoxLayout(container)
        layout.setContentsMargins(0, 12, 0, 12)
        layout.setSpacing(14)

        # Windows 10 Familiarity Tweaks
        ui_card = QFrame()
        ui_card.setObjectName("SectionCard")
        ui_layout = QVBoxLayout(ui_card)
        ui_layout.setContentsMargins(16, 14, 16, 16)
        ui_layout.setSpacing(12)
        ui_title = QLabel("Windows 10 Familiarity Tweaks")
        ui_title.setObjectName("SectionTitle")
        ui_layout.addWidget(ui_title)

        self.tweak_buttons: Dict[str, QPushButton] = {}
        self.tweak_labels: Dict[str, QLabel] = {}

        tweak_rows = [
            ("classic_context_menu", "Classic Right-Click Menu", "Restores full Windows 10 context menu via HKCU CLSID + restarts Explorer.", self._toggle_classic_menu),
            ("left_align_taskbar", "Left-Align Taskbar", "Sets TaskbarAl to 0 (Left) or 1 (Center) without reboot.", self._toggle_left_taskbar),
            ("hide_start_recommendations", "Hide Start Recommendations", "Removes recommended tips and clutter from Windows 11 Start menu.", self._toggle_start_recs),
        ]
        for key, label, desc, handler in tweak_rows:
            row = QHBoxLayout()
            info_col = QVBoxLayout()
            t_lbl = QLabel(label)
            t_lbl.setStyleSheet("font-weight: 600; background: transparent;")
            d_lbl = QLabel(desc)
            d_lbl.setObjectName("MutedMeta")
            info_col.addWidget(t_lbl)
            info_col.addWidget(d_lbl)
            row.addLayout(info_col, 1)

            state_lbl = QLabel()
            self.tweak_labels[key] = state_lbl
            row.addWidget(state_lbl)

            btn = QPushButton("Toggle")
            btn.clicked.connect(handler)
            self.tweak_buttons[key] = btn
            row.addWidget(btn)
            ui_layout.addLayout(row)

        # Text Scaling Row
        scale_row = QHBoxLayout()
        scale_info = QVBoxLayout()
        s_lbl = QLabel("Text Scaling")
        s_lbl.setStyleSheet("font-weight: 600; background: transparent;")
        s_desc = QLabel("Quickly adjust Windows Accessibility TextScaleFactor for readability.")
        s_desc.setObjectName("MutedMeta")
        scale_info.addWidget(s_lbl)
        scale_info.addWidget(s_desc)
        scale_row.addLayout(scale_info, 1)

        self.scale_buttons: Dict[int, QPushButton] = {}
        for pct in (100, 125, 150):
            s_btn = QPushButton(f"{pct}%")
            s_btn.clicked.connect(lambda _=False, p=pct: self._apply_text_scale(p))
            self.scale_buttons[pct] = s_btn
            scale_row.addWidget(s_btn)
        ui_layout.addLayout(scale_row)
        layout.addWidget(ui_card)

        # Surgical Bloat & Telemetry Cleanup (ARM64-Safe)
        bloat_card = QFrame()
        bloat_card.setObjectName("SectionCard")
        bloat_layout = QVBoxLayout(bloat_card)
        bloat_layout.setContentsMargins(16, 14, 16, 16)
        bloat_layout.setSpacing(12)

        bloat_title = QLabel("Surgical Bloat & Telemetry Cleanup (ARM64-Safe)")
        bloat_title.setObjectName("SectionTitle")
        bloat_layout.addWidget(bloat_title)

        safe_note = QLabel(
            "Strictly preserves Edge WebView2 Runtime, Bluetooth support services, WPNService, and ARM64 Prism dependencies."
        )
        safe_note.setObjectName("MutedMeta")
        bloat_layout.addWidget(safe_note)

        bloat_row = QHBoxLayout()
        btn_bloat = QPushButton("Remove Consumer Bloat")
        btn_bloat.setProperty("primary", True)
        btn_bloat.clicked.connect(self._remove_consumer_bloat)
        bloat_row.addWidget(btn_bloat)

        self.tweak_labels["ads_telemetry_disabled"] = QLabel()
        bloat_row.addSpacing(12)
        btn_ads = QPushButton("Disable Ads & Telemetry")
        btn_ads.clicked.connect(self._toggle_ads_telemetry)
        self.tweak_buttons["ads_telemetry_disabled"] = btn_ads
        bloat_row.addWidget(btn_ads)
        bloat_row.addWidget(self.tweak_labels["ads_telemetry_disabled"])
        bloat_row.addStretch(1)
        bloat_layout.addLayout(bloat_row)

        layout.addWidget(bloat_card)
        layout.addStretch(1)
        self._sync_tweak_ui()
        scroll.setWidget(container)
        return scroll

    def _sync_tweak_ui(self) -> None:
        for key, lbl in self.tweak_labels.items():
            enabled = bool(self.tweak_states.get(key, False))
            lbl.setText("Enabled" if enabled else "Disabled")
            lbl.setStyleSheet(
                f"color: {'#98C379' if enabled else '#909098'}; "
                "font-family: 'JetBrains Mono', Consolas, monospace; font-size: 9.5pt; padding: 0 8px; background: transparent;"
            )
        current_scale = int(self.tweak_states.get("text_scaling", 100))
        for pct, btn in self.scale_buttons.items():
            btn.setProperty("activeScale", pct == current_scale)
            btn.style().unpolish(btn)
            btn.style().polish(btn)

    # ------------------------------------------------------------------
    # Tab 4: Windows Admin Shortcuts
    # ------------------------------------------------------------------

    def _build_tab_shortcuts(self) -> QWidget:
        container = QWidget()
        layout = QVBoxLayout(container)
        layout.setContentsMargins(0, 12, 0, 12)

        card = QFrame()
        card.setObjectName("SectionCard")
        c_layout = QVBoxLayout(card)
        c_layout.setContentsMargins(16, 14, 16, 16)
        c_layout.setSpacing(12)

        hdr = QLabel("Windows Admin Shortcuts")
        hdr.setObjectName("SectionTitle")
        c_layout.addWidget(hdr)

        shortcuts = [
            ("Device Manager", "devmgmt.msc", False),
            ("Services", "services.msc", False),
            ("Network Adapters", "ncpa.cpl", False),
            ("Programs & Features", "appwiz.cpl", False),
            ("Startup Apps", "ms-settings:startupapps", False),
            ("Windows Update", "ms-settings:windowsupdate", False),
            ("Registry Editor", "regedit.exe", False),
            ("Open Admin PowerShell", "powershell.exe", True),
        ]

        grid = QGridLayout()
        grid.setSpacing(10)
        for i, (label, target, elevate) in enumerate(shortcuts):
            btn = QPushButton(f"{label}\n{target}")
            btn.setMinimumHeight(60)
            if elevate:
                btn.setProperty("primary", True)
            btn.clicked.connect(
                lambda _=False, l=label, t=target, e=elevate: self._launch_shortcut(l, t, e)
            )
            grid.addWidget(btn, i // 4, i % 4)

        c_layout.addLayout(grid)
        layout.addWidget(card)
        layout.addStretch(1)
        return container

    # ------------------------------------------------------------------
    # Collapsible Live Log Drawer
    # ------------------------------------------------------------------

    def _build_log_drawer(self) -> QFrame:
        drawer = QFrame()
        drawer.setObjectName("LogContainer")
        layout = QVBoxLayout(drawer)
        layout.setContentsMargins(12, 8, 12, 8)
        layout.setSpacing(6)

        bar = QHBoxLayout()
        self.log_toggle_btn = QPushButton("Hide Live Log (Ctrl+L)")
        self.log_toggle_btn.clicked.connect(self._toggle_log_drawer)
        bar.addWidget(self.log_toggle_btn)

        self.log_status_lbl = QLabel("Idle · Ready")
        self.log_status_lbl.setObjectName("MutedMeta")
        bar.addWidget(self.log_status_lbl)
        bar.addStretch(1)

        copy_btn = QPushButton("Copy Log")
        copy_btn.clicked.connect(lambda: QApplication.clipboard().setText(self.log_view.toPlainText()))
        bar.addWidget(copy_btn)

        clear_btn = QPushButton("Clear")
        clear_btn.clicked.connect(self.log_view.clear)
        bar.addWidget(clear_btn)
        layout.addLayout(bar)

        self.log_view = QPlainTextEdit()
        self.log_view.setObjectName("LogConsole")
        self.log_view.setReadOnly(True)
        self.log_view.setMaximumBlockCount(4000)
        self.log_view.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        layout.addWidget(self.log_view, 1)

        return drawer

    def _toggle_log_drawer(self) -> None:
        self.log_expanded = not self.log_expanded
        self.log_view.setVisible(self.log_expanded)
        self.log_toggle_btn.setText(
            "Hide Live Log (Ctrl+L)" if self.log_expanded else "Show Live Log (Ctrl+L)"
        )

    def _log_line(self, kind: str, text: str) -> None:
        ts = datetime.now().strftime("%H:%M:%S")
        color_map = {
            "cmd": "#88C0D0",
            "stdout": "#E0E0E0",
            "stderr": "#E06C75",
            "info": "#909098",
            "exit-ok": "#98C379",
            "exit-err": "#E06C75",
        }
        fmt = QTextCharFormat()
        fmt.setForeground(QColor(color_map.get(kind, "#E0E0E0")))
        cursor = self.log_view.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.End)
        cursor.insertText(f"[{ts}] {text}\n", fmt)
        self.log_view.setTextCursor(cursor)
        self.log_view.ensureCursorVisible()

    # ------------------------------------------------------------------
    # Action Handlers & Worker Execution
    # ------------------------------------------------------------------

    def _run_commands(self, label: str, commands: List[tuple], on_done: Optional[Any] = None) -> None:
        self.log_status_lbl.setText(f"Running: {label}...")
        worker = CommandWorker(label, commands, self)
        worker.line_emitted.connect(self._log_line)

        def _finished(code: int, done_label: str) -> None:
            kind = "exit-ok" if code == 0 else "exit-err"
            self._log_line(kind, f"[EXIT {code}] {done_label}")
            self.log_status_lbl.setText(f"Last: {done_label} (exit {code})")
            if worker in self.active_workers:
                self.active_workers.remove(worker)
            if on_done:
                on_done(code)

        worker.finished_with_code.connect(_finished)
        self.active_workers.append(worker)
        worker.start()

    def _run_admin_or_elevate(self, label: str, exe: str, params: str, on_done: Optional[Any] = None) -> None:
        if self.telemetry.is_admin:
            self._run_commands(label, [(f"{exe} {params}", f"{exe} {params}", True)], on_done=on_done)
        else:
            self._log_line("cmd", f"$ [UAC runas] {exe} {params}")
            ok = shell_execute_elevated(exe, params)
            if ok:
                self._log_line("exit-ok", f"[UAC APPROVED] Spawned elevated process for {label}.")
                if on_done:
                    on_done(0)
            else:
                self._log_line("stderr", f"[UAC CANCELLED] Elevation declined or failed for {label}.")

    def _handle_relaunch_admin(self) -> None:
        if relaunch_as_admin():
            self.close()
        else:
            self._log_line("stderr", "UAC relaunch cancelled or unavailable on this OS.")

    def _handle_reset_network_stack(self) -> None:
        def _after_reset(code: int) -> None:
            if code == 0:
                reply = QMessageBox.question(
                    self,
                    "Reboot Required",
                    "Winsock and TCP/IP stack reset completed. Restart Windows now?",
                    QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
                )
                if reply == QMessageBox.StandardButton.Yes:
                    self._run_commands("Restart Windows", [("shutdown /r /t 5", ["shutdown", "/r", "/t", "5"], False)])

        self._run_admin_or_elevate(
            "Reset Network Stack",
            "cmd.exe",
            "/c netsh winsock reset && netsh int ip reset",
            on_done=_after_reset,
        )

    def _refresh_installed_apps_async(self) -> None:
        worker = FunctionWorker(lambda: detect_installed_apps(self.apps_catalog), self)
        worker.result_ready.connect(self._on_apps_detected)
        self.active_workers.append(worker)
        worker.start()

    def _on_apps_detected(self, mapping: Dict[str, bool]) -> None:
        self.installed_map = mapping
        self._rebuild_app_cards()

    def _install_single_app(self, app: Dict[str, Any]) -> None:
        method = app.get("method", "winget")
        name = str(app.get("name", "App"))
        if method == "direct_url" or (not app.get("winget_id") and app.get("portable_url")):
            url = str(app.get("portable_url", ""))
            folder = str(app.get("portable_folder") or name.replace(" ", ""))
            worker = PortableDownloadWorker(name, url, folder, self)
            worker.line_emitted.connect(self._log_line)
            worker.finished_with_code.connect(
                lambda code, lbl: (
                    self._log_line("exit-ok" if code == 0 else "exit-err", f"[EXIT {code}] {lbl}"),
                    self._refresh_installed_apps_async(),
                )
            )
            self.active_workers.append(worker)
            worker.start()
        else:
            cmd = build_winget_install_cmd(str(app["winget_id"]), self.telemetry.is_admin)
            self._run_commands(
                f"Install {name}",
                [(" ".join(cmd), cmd, False)],
                on_done=lambda _c: self._refresh_installed_apps_async(),
            )

    def _uninstall_single_app(self, app: Dict[str, Any]) -> None:
        name = str(app.get("name", "App"))
        if app.get("winget_id"):
            cmd = build_winget_uninstall_cmd(str(app["winget_id"]), self.telemetry.is_admin)
            self._run_commands(
                f"Uninstall {name}",
                [(" ".join(cmd), cmd, False)],
                on_done=lambda _c: self._refresh_installed_apps_async(),
            )
        else:
            folder = str(app.get("portable_folder") or name.replace(" ", ""))
            ps = f"Remove-Item -Recurse -Force -ErrorAction SilentlyContinue \"$env:USERPROFILE\\Apps\\{folder}\""
            self._run_commands(
                f"Uninstall {name}",
                [(ps, ["powershell", "-NoProfile", "-Command", ps], False)],
                on_done=lambda _c: self._refresh_installed_apps_async(),
            )

    def _install_all_checked(self) -> None:
        selected = [
            app for app in self.apps_catalog
            if self.app_checkboxes.get(str(app.get("id", "")))
            and self.app_checkboxes[str(app.get("id", ""))].isChecked()
        ]
        if not selected:
            self._log_line("info", "No apps checked for batch install.")
            return
        for app in selected:
            self._install_single_app(app)

    def _upgrade_all_apps(self) -> None:
        cmd = ["winget", "upgrade", "--all", "--accept-package-agreements", "--accept-source-agreements"]
        if not self.telemetry.is_admin:
            cmd.extend(["--scope", "user"])
        self._run_commands("Upgrade All Apps", [(" ".join(cmd), cmd, False)])

    def _sync_json_github(self) -> None:
        url, ok = QInputDialog.getText(
            self, "Sync apps.json from GitHub", "Raw GitHub URL:", text=DEFAULT_GITHUB_RAW_URL
        )
        if not ok or not url.strip():
            return
        self._log_line("cmd", f"$ GET {url.strip()}")
        worker = FunctionWorker(lambda: sync_apps_json_from_github(url.strip()), self)
        worker.result_ready.connect(
            lambda res: (
                setattr(self, "apps_catalog", res[0]),
                self._log_line("exit-ok", f"[EXIT 0] Synced {len(res[0])} apps to {res[1]}"),
                self._refresh_installed_apps_async(),
            )
        )
        worker.error_occurred.connect(lambda err: self._log_line("stderr", f"GitHub sync failed: {err}"))
        self.active_workers.append(worker)
        worker.start()

    def _open_apps_json(self) -> None:
        path = resolve_apps_json_path()
        self._log_line("info", f"Opening {path}")
        if sys.platform == "win32":
            os.startfile(str(path))  # type: ignore[attr-defined]
        else:
            subprocess.Popen(["xdg-open", str(path)])

    def _toggle_classic_menu(self) -> None:
        nxt = not bool(self.tweak_states.get("classic_context_menu"))
        msg = set_classic_context_menu(nxt)
        self.tweak_states["classic_context_menu"] = nxt
        self._log_line("stdout", msg)
        self._sync_tweak_ui()
        self._run_commands("Restart Explorer", [("taskkill /f /im explorer.exe && start explorer.exe", "taskkill /f /im explorer.exe && start explorer.exe", True)])

    def _toggle_left_taskbar(self) -> None:
        nxt = not bool(self.tweak_states.get("left_align_taskbar"))
        msg = set_left_align_taskbar(nxt)
        self.tweak_states["left_align_taskbar"] = nxt
        self._log_line("exit-ok", f"[EXIT 0] {msg}")
        self._sync_tweak_ui()

    def _toggle_start_recs(self) -> None:
        nxt = not bool(self.tweak_states.get("hide_start_recommendations"))
        msg = set_hide_start_recommendations(nxt)
        self.tweak_states["hide_start_recommendations"] = nxt
        self._log_line("exit-ok", f"[EXIT 0] {msg}")
        self._sync_tweak_ui()

    def _apply_text_scale(self, pct: int) -> None:
        msg = set_text_scaling(pct)
        self.tweak_states["text_scaling"] = pct
        self._log_line("exit-ok", f"[EXIT 0] {msg}")
        self._sync_tweak_ui()

    def _remove_consumer_bloat(self) -> None:
        self._log_line(
            "info",
            f"ARM64-Safe Debloat: Preserving Edge WebView2, Bluetooth, WPNService & {self.telemetry.arch} core dependencies.",
        )
        cmds = [
            (
                f"winget uninstall --id {pkg_id} -e --silent",
                ["winget", "uninstall", "--id", pkg_id, "-e", "--silent"],
                False,
            )
            for _name, pkg_id in SAFE_DEBLOAT_PACKAGES
        ]
        self._run_commands("Remove Consumer Bloat", cmds)

    def _toggle_ads_telemetry(self) -> None:
        nxt = not bool(self.tweak_states.get("ads_telemetry_disabled"))
        msg = set_ads_telemetry_disabled(nxt)
        self.tweak_states["ads_telemetry_disabled"] = nxt
        self._log_line("exit-ok", f"[EXIT 0] {msg}")
        self._sync_tweak_ui()

    def _launch_shortcut(self, label: str, target: str, elevate: bool) -> None:
        if elevate:
            self._run_admin_or_elevate(label, "powershell.exe", "-NoExit")
            return
        self._log_line("cmd", f"$ start {target}")
        try:
            if sys.platform == "win32":
                os.startfile(target)  # type: ignore[attr-defined]
            self._log_line("exit-ok", f"[EXIT 0] Launched {label} ({target}).")
        except Exception as exc:
            self._log_line("stderr", f"Failed to launch {target}: {exc}")


def main() -> int:
    app = QApplication(sys.argv)
    app.setStyleSheet(WINFIX_QSS)
    app.setFont(QFont("Segoe UI", 11))
    window = WinFixWindow()
    window.show()
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
