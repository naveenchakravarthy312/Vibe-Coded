"""Tab 1: Quick Fixes & Diagnostics."""

from __future__ import annotations

from PyQt6.QtWidgets import (
    QFrame,
    QGridLayout,
    QLabel,
    QMessageBox,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)

from core import actions


class QuickFixesTab(QWidget):
    """Button-driven control panel for Network, System Recovery, and Phone Sync diagnostics."""

    def __init__(self, run_steps_cb, run_callable_cb, parent=None):
        super().__init__(parent)
        self._run_steps = run_steps_cb
        self._run_callable = run_callable_cb
        self._build_ui()

    def _build_ui(self) -> None:
        outer = QVBoxLayout(self)
        outer.setContentsMargins(8, 8, 8, 8)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        container = QWidget()
        layout = QVBoxLayout(container)
        layout.setSpacing(12)

        # 1. Network & Connectivity
        net_card = self._make_card(
            "Network & Connectivity",
            "Immediate DNS, DHCP, latency, and Winsock/TCP-IP recovery tools.",
            [
                ("Flush DNS & Renew IP", "ipconfig /flushdns + /renew", True, self._on_flush_dns),
                ("Test Internet & Ping", "DNS + ping 1.1.1.1 & google.com", False, self._on_test_ping),
                ("Reset Network Stack", "netsh winsock & int ip reset (Admin)", False, self._on_reset_network),
            ],
        )
        layout.addWidget(net_card)

        # 2. System & Shell Recovery
        sys_card = self._make_card(
            "System & Shell Recovery",
            "Recover frozen taskbar, stuck print queues, temp clutter, and corrupted system files.",
            [
                ("Restart Explorer / Taskbar", "Kills and restarts explorer.exe", True, self._on_restart_explorer),
                ("Fix Stuck Printer", "Clears Spooler queue (Admin)", False, self._on_fix_printer),
                ("Clean Temp & Update Cache", "Wipes %TEMP% safely", False, self._on_clean_temp),
                ("Repair System Files (SFC)", "Runs sfc /scannow (Admin)", False, self._on_sfc),
            ],
        )
        layout.addWidget(sys_card)

        # 3. Phone Sync & O+ Connect Doctor
        phone_card = self._make_card(
            "Phone Sync & O+ Connect Doctor",
            "Verify Windows Push Notifications, Bluetooth, Wi-Fi Direct, WebView2, and Private firewall rules.",
            [
                ("Check Phone Sync Services", "WPNService, Bluetooth, Wi-Fi Direct & WebView2", True, self._on_check_phone),
                ("Fix O+ Connect Firewall", "Allow O+ Connect on Private networks (Admin)", False, self._on_fix_firewall),
            ],
        )
        layout.addWidget(phone_card)

        layout.addStretch(1)
        scroll.setWidget(container)
        outer.addWidget(scroll)

    def _make_card(
        self,
        title: str,
        subtitle: str,
        buttons: list[tuple[str, str, bool, object]],
    ) -> QFrame:
        card = QFrame()
        card.setObjectName("CardPanel")
        vbox = QVBoxLayout(card)
        vbox.setContentsMargins(14, 12, 14, 14)
        vbox.setSpacing(8)

        lbl_title = QLabel(title)
        lbl_title.setObjectName("SectionTitle")
        vbox.addWidget(lbl_title)

        lbl_sub = QLabel(subtitle)
        lbl_sub.setObjectName("MutedLabel")
        lbl_sub.setWordWrap(True)
        vbox.addWidget(lbl_sub)

        grid = QGridLayout()
        grid.setSpacing(10)
        cols = 2
        for idx, (btn_text, hint, is_primary, slot) in enumerate(buttons):
            cell = QWidget()
            cell_layout = QVBoxLayout(cell)
            cell_layout.setContentsMargins(0, 0, 0, 0)
            cell_layout.setSpacing(3)

            btn = QPushButton(btn_text)
            if is_primary:
                btn.setObjectName("PrimaryButton")
            btn.clicked.connect(slot)
            cell_layout.addWidget(btn)

            lbl_hint = QLabel(hint)
            lbl_hint.setObjectName("MutedLabel")
            cell_layout.addWidget(lbl_hint)

            grid.addWidget(cell, idx // cols, idx % cols)

        vbox.addLayout(grid)
        return card

    # --- Action handlers ---

    def _on_flush_dns(self) -> None:
        self._run_steps("Flush DNS & Renew IP", actions.steps_flush_dns_renew())

    def _on_test_ping(self) -> None:
        self._run_callable("Test Internet & Ping", actions.task_test_internet_ping)

    def _on_reset_network(self) -> None:
        def _after(ok: bool, _msg: str) -> None:
            if ok:
                QMessageBox.information(
                    self,
                    "Reboot Recommended",
                    "Network stack reset completed.\nPlease reboot Windows for Winsock and TCP/IP changes to take full effect.",
                )

        self._run_steps("Reset Network Stack", actions.steps_reset_network_stack(), on_done=_after)

    def _on_restart_explorer(self) -> None:
        self._run_steps("Restart Explorer / Taskbar", actions.steps_restart_explorer())

    def _on_fix_printer(self) -> None:
        self._run_steps("Fix Stuck Printer", actions.steps_fix_stuck_printer())

    def _on_clean_temp(self) -> None:
        self._run_callable("Clean Temp & Update Cache", actions.task_clean_temp_cache)

    def _on_sfc(self) -> None:
        self._run_steps("Repair System Files (SFC)", actions.steps_repair_sfc())

    def _on_check_phone(self) -> None:
        self._run_callable("Check Phone Sync Services", actions.task_check_phone_sync_services)

    def _on_fix_firewall(self) -> None:
        self._run_steps("Fix O+ Connect Firewall", actions.steps_fix_oplus_firewall())
