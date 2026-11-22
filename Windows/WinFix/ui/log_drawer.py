"""Collapsible Live Log Drawer widget."""

from __future__ import annotations

from datetime import datetime

from PyQt6.QtGui import QTextCursor
from PyQt6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QPlainTextEdit,
    QPushButton,
    QVBoxLayout,
)

from ui.theme import PRIMARY_ACCENT, STATUS_AMBER, STATUS_GREEN, STATUS_RED, TEXT_MUTED, TEXT_PRIMARY

_LEVEL_COLORS = {
    "INFO": PRIMARY_ACCENT,
    "CMD": "#C678DD",
    "OUT": TEXT_PRIMARY,
    "OK": STATUS_GREEN,
    "WARN": STATUS_AMBER,
    "ERR": STATUS_RED,
}


class LogDrawer(QFrame):
    """Toggleable bottom console pane streaming real-time command output."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setObjectName("LogDrawer")
        self._expanded = True
        self._build_ui()

    def _build_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(10, 6, 10, 8)
        root.setSpacing(6)

        header = QHBoxLayout()
        header.setSpacing(8)

        self.btn_toggle = QPushButton("▼ Live Console")
        self.btn_toggle.setStyleSheet("padding: 4px 10px; font-weight: 600;")
        self.btn_toggle.clicked.connect(self.toggle_expanded)
        header.addWidget(self.btn_toggle)

        self.lbl_status = QLabel("Ready")
        self.lbl_status.setObjectName("MutedLabel")
        header.addWidget(self.lbl_status, 1)

        self.btn_clear = QPushButton("Clear")
        self.btn_clear.setStyleSheet("padding: 4px 10px;")
        self.btn_clear.clicked.connect(self.clear)
        header.addWidget(self.btn_clear)

        root.addLayout(header)

        self.console = QPlainTextEdit()
        self.console.setObjectName("LogConsole")
        self.console.setReadOnly(True)
        self.console.setMinimumHeight(150)
        self.console.setMaximumHeight(220)
        root.addWidget(self.console)

    def toggle_expanded(self) -> None:
        """Expand or collapse the live console drawer."""
        self._expanded = not self._expanded
        self.console.setVisible(self._expanded)
        self.btn_toggle.setText("▼ Live Console" if self._expanded else "▲ Live Console")

    def set_expanded(self, expanded: bool) -> None:
        """Explicitly set expanded state."""
        if self._expanded != expanded:
            self.toggle_expanded()

    def set_status(self, text: str) -> None:
        """Update the compact status label in the drawer header."""
        self.lbl_status.setText(text)

    def append_log(self, level: str, message: str) -> None:
        """Append a timestamped, color-coded line to the console."""
        ts = datetime.now().strftime("%H:%M:%S")
        color = _LEVEL_COLORS.get(level.upper(), TEXT_PRIMARY)
        escaped = (
            message.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        )
        html = (
            f'<span style="color:{TEXT_MUTED};">[{ts}]</span> '
            f'<span style="color:{color}; font-weight:600;">[{level}]</span> '
            f'<span style="color:{color};">{escaped}</span>'
        )
        self.console.appendHtml(html)
        cursor = self.console.textCursor()
        cursor.movePosition(QTextCursor.MoveOperation.End)
        self.console.setTextCursor(cursor)

    def clear(self) -> None:
        """Clear all console output."""
        self.console.clear()
