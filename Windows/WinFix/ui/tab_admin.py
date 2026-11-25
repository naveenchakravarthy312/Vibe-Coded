"""Tab 4: Windows Admin Shortcuts."""

from __future__ import annotations

from PyQt6.QtWidgets import (
    QFrame,
    QGridLayout,
    QLabel,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)

from core import actions
from ui.theme import TEXT_MUTED


class AdminShortcutsTab(QWidget):
    """Grid of direct one-click launch buttons for Windows administrative consoles."""

    def __init__(self, log_cb, parent=None):
        super().__init__(parent)
        self._log = log_cb
        self._build_ui()

    def _build_ui(self) -> None:
        outer = QVBoxLayout(self)
        outer.setContentsMargins(8, 8, 8, 8)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        container = QWidget()
        root = QVBoxLayout(container)
        root.setContentsMargins(0, 0, 0, 0)

        card = QFrame()
        card.setObjectName("CardPanel")
        vbox = QVBoxLayout(card)
        vbox.setContentsMargins(12, 10, 12, 12)
        vbox.setSpacing(6)

        lbl_title = QLabel("Windows Admin Shortcuts")
        lbl_title.setObjectName("SectionTitle")
        vbox.addWidget(lbl_title)

        lbl_sub = QLabel("Direct one-click launchers for built-in Windows troubleshooting tools.")
        lbl_sub.setObjectName("MutedLabel")
        vbox.addWidget(lbl_sub)

        grid = QGridLayout()
        grid.setSpacing(8)
        cols = 3
        for col in range(cols):
            grid.setColumnStretch(col, 1)
        for idx, entry in enumerate(actions.ADMIN_SHORTCUTS):
            cell = QFrame()
            cell.setObjectName("SubCard")
            c_layout = QVBoxLayout(cell)
            c_layout.setContentsMargins(10, 8, 10, 8)
            c_layout.setSpacing(4)

            btn = QPushButton(entry["label"].replace("&", "&&"))
            if entry["id"] == "admin_ps":
                btn.setObjectName("PrimaryButton")
            btn.clicked.connect(lambda _=False, sid=entry["id"]: actions.launch_admin_shortcut(sid, self._log))
            c_layout.addWidget(btn)

            lbl_cmd = QLabel(f"{entry['cmd']}  —  {entry['desc']}")
            lbl_cmd.setObjectName("MutedLabel")
            lbl_cmd.setStyleSheet("font-size: 9.5pt;")
            lbl_cmd.setWordWrap(True)
            c_layout.addWidget(lbl_cmd)

            grid.addWidget(cell, idx // cols, idx % cols)

        vbox.addLayout(grid)
        vbox.addStretch(1)
        root.addWidget(card)
        scroll.setWidget(container)
        outer.addWidget(scroll)
