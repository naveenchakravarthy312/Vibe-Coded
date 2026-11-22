"""Persistent System Telemetry Header widget."""

from __future__ import annotations

from PyQt6.QtCore import pyqtSignal
from PyQt6.QtWidgets import QFrame, QHBoxLayout, QLabel, QPushButton

from core.system_info import SystemTelemetry, get_telemetry_snapshot
from ui.theme import STATUS_AMBER, STATUS_GREEN


class TelemetryBar(QFrame):
    """Compact top status bar showing OS, Arch, Privilege Level, IPv4/Adapter, and Admin relaunch."""

    relaunch_admin_requested = pyqtSignal()
    refresh_requested = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("TelemetryBar")
        self._telemetry: SystemTelemetry | None = None
        self._build_ui()
        self.refresh()

    def _build_ui(self) -> None:
        layout = QHBoxLayout(self)
        layout.setContentsMargins(12, 8, 12, 8)
        layout.setSpacing(12)

        self.lbl_title = QLabel("WinFix")
        self.lbl_title.setStyleSheet("font-size: 13pt; font-weight: 800; color: #88C0D0; background: transparent;")
        layout.addWidget(self.lbl_title)

        self.lbl_os = QLabel("OS: ...")
        self.lbl_os.setObjectName("TelemetryMono")
        layout.addWidget(self.lbl_os)

        self.lbl_arch = QLabel("ARCH: ...")
        self.lbl_arch.setObjectName("TelemetryMono")
        layout.addWidget(self.lbl_arch)

        self.lbl_priv = QLabel("...")
        self.lbl_priv.setStyleSheet("font-size: 10pt; font-weight: 700; padding: 3px 8px; border-radius: 4px;")
        layout.addWidget(self.lbl_priv)

        self.lbl_net = QLabel("IP: ...")
        self.lbl_net.setObjectName("TelemetryMono")
        layout.addWidget(self.lbl_net, 1)

        self.btn_refresh = QPushButton("Refresh")
        self.btn_refresh.setToolTip("Refresh system telemetry & network status")
        self.btn_refresh.clicked.connect(self._on_refresh)
        layout.addWidget(self.btn_refresh)

        self.btn_admin = QPushButton("Relaunch as Admin")
        self.btn_admin.setObjectName("PrimaryButton")
        self.btn_admin.setToolTip("Restart WinFix with Administrator privileges (UAC)")
        self.btn_admin.clicked.connect(self.relaunch_admin_requested.emit)
        layout.addWidget(self.btn_admin)

    def _on_refresh(self) -> None:
        self.refresh()
        self.refresh_requested.emit()

    def refresh(self) -> SystemTelemetry:
        """Query fresh system telemetry and update header labels."""
        snap = get_telemetry_snapshot()
        self._telemetry = snap

        self.lbl_os.setText(f"{snap.os_version}")
        self.lbl_arch.setText(f"[{snap.arch}]")
        self.lbl_net.setText(f"IPv4: {snap.ipv4} ({snap.adapter})")

        if snap.is_admin:
            self.lbl_priv.setText("Administrator")
            self.lbl_priv.setStyleSheet(
                f"font-size: 10pt; font-weight: 700; padding: 3px 9px; border-radius: 4px; "
                f"color: #141414; background-color: {STATUS_GREEN};"
            )
            self.btn_admin.setVisible(False)
        else:
            self.lbl_priv.setText("Standard User (Non-Admin)")
            self.lbl_priv.setStyleSheet(
                f"font-size: 10pt; font-weight: 700; padding: 3px 9px; border-radius: 4px; "
                f"color: #141414; background-color: {STATUS_AMBER};"
            )
            self.btn_admin.setVisible(True)

        return snap

    @property
    def telemetry(self) -> SystemTelemetry | None:
        return self._telemetry
