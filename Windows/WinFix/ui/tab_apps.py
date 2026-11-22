"""Tab 2: App Installer & Updater (apps.json)."""

from __future__ import annotations

import os
import subprocess
from pathlib import Path
from typing import Any

from PyQt6.QtCore import QThread, pyqtSignal
from PyQt6.QtWidgets import (
    QCheckBox,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)

from core import apps_manager
from core.system_info import IS_WINDOWS, is_admin
from ui.theme import PRIMARY_ACCENT, STATUS_AMBER, STATUS_GREEN, STATUS_RED, TEXT_MUTED


class _DetectionWorker(QThread):
    """Background worker to detect installed apps without freezing the UI."""

    detected_signal = pyqtSignal(dict)

    def __init__(self, apps: list[dict[str, Any]], parent=None):
        super().__init__(parent)
        self.apps = apps

    def run(self) -> None:
        winget_ids = apps_manager.fetch_winget_installed_ids()
        results: dict[str, bool] = {}
        for app in self.apps:
            app_id = app.get("id", app.get("name", ""))
            results[app_id] = apps_manager.is_app_installed(app, winget_ids)
        self.detected_signal.emit(results)


class AppsTab(QWidget):
    """App Installer & Updater tab backed by apps.json with Non-Admin smart fallback."""

    def __init__(self, run_callable_cb, parent=None):
        super().__init__(parent)
        self._run_callable = run_callable_cb
        self._apps: list[dict[str, Any]] = []
        self._active_json_path: Path | None = None
        self._checkboxes: dict[str, QCheckBox] = {}
        self._status_badges: dict[str, QLabel] = {}
        self._detect_worker: _DetectionWorker | None = None
        self._build_ui()
        self.reload_catalog()

    def _build_ui(self) -> None:
        root = QVBoxLayout(self)
        root.setContentsMargins(8, 8, 8, 8)
        root.setSpacing(10)

        # Top Action Bar
        top_card = QFrame()
        top_card.setObjectName("CardPanel")
        top_vbox = QVBoxLayout(top_card)
        top_vbox.setContentsMargins(12, 10, 12, 10)
        top_vbox.setSpacing(8)

        btn_row = QHBoxLayout()
        btn_row.setSpacing(8)

        self.btn_install_checked = QPushButton("Install All Checked")
        self.btn_install_checked.setObjectName("PrimaryButton")
        self.btn_install_checked.clicked.connect(self._on_install_checked)
        btn_row.addWidget(self.btn_install_checked)

        self.btn_upgrade_all = QPushButton("Upgrade All (winget upgrade --all)")
        self.btn_upgrade_all.clicked.connect(self._on_upgrade_all)
        btn_row.addWidget(self.btn_upgrade_all)

        self.btn_open_json = QPushButton("Open apps.json")
        self.btn_open_json.clicked.connect(self._on_open_json)
        btn_row.addWidget(self.btn_open_json)

        self.btn_rescan = QPushButton("Rescan Installed")
        self.btn_rescan.clicked.connect(self.run_detection)
        btn_row.addWidget(self.btn_rescan)

        btn_row.addStretch(1)
        top_vbox.addLayout(btn_row)

        # GitHub Raw URL Sync Row
        sync_row = QHBoxLayout()
        sync_row.setSpacing(8)
        lbl_url = QLabel("GitHub Raw URL:")
        lbl_url.setObjectName("MutedLabel")
        sync_row.addWidget(lbl_url)

        self.txt_github_url = QLineEdit()
        self.txt_github_url.setPlaceholderText("https://raw.githubusercontent.com/<user>/<repo>/main/apps.json")
        sync_row.addWidget(self.txt_github_url, 1)

        self.btn_sync_github = QPushButton("Sync JSON from GitHub")
        self.btn_sync_github.clicked.connect(self._on_sync_github)
        sync_row.addWidget(self.btn_sync_github)
        top_vbox.addLayout(sync_row)

        # Source & Non-Admin banner
        self.lbl_catalog_info = QLabel("")
        self.lbl_catalog_info.setObjectName("MutedLabel")
        top_vbox.addWidget(self.lbl_catalog_info)

        root.addWidget(top_card)

        # Scrollable App Categories
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.cards_container = QWidget()
        self.cards_layout = QVBoxLayout(self.cards_container)
        self.cards_layout.setContentsMargins(0, 0, 0, 0)
        self.cards_layout.setSpacing(12)
        self.scroll.setWidget(self.cards_container)
        root.addWidget(self.scroll, 1)

    def reload_catalog(self) -> None:
        """Load apps.json (external override or bundled) and rebuild app cards."""
        apps, raw_url, active_path, is_external = apps_manager.load_apps_catalog()
        self._apps = apps
        self._active_json_path = active_path
        if raw_url and not self.txt_github_url.text().strip():
            self.txt_github_url.setText(raw_url)

        source_tag = "External Override" if is_external else "Bundled Default"
        mode_tag = (
            "Admin Mode (system-wide winget)"
            if is_admin()
            else r"Non-Admin Smart Fallback (--scope user / %USERPROFILE%\Apps)"
        )
        self.lbl_catalog_info.setText(
            f"Catalog: {source_tag} ({active_path})  |  {mode_tag}"
        )

        self._rebuild_cards()
        self.run_detection()

    def _clear_layout(self, layout) -> None:
        while layout.count():
            item = layout.takeAt(0)
            w = item.widget()
            if w is not None:
                w.deleteLater()
            elif item.layout() is not None:
                self._clear_layout(item.layout())

    def _rebuild_cards(self) -> None:
        self._clear_layout(self.cards_layout)
        self._checkboxes.clear()
        self._status_badges.clear()

        categories = ["Parent Essentials", "Portable / Power Tools"]
        grouped: dict[str, list[dict[str, Any]]] = {cat: [] for cat in categories}
        for app in self._apps:
            cat = app.get("category", "Portable / Power Tools")
            grouped.setdefault(cat, []).append(app)

        for cat, items in grouped.items():
            if not items:
                continue
            section = QFrame()
            section.setObjectName("CardPanel")
            sec_layout = QVBoxLayout(section)
            sec_layout.setContentsMargins(14, 12, 14, 14)
            sec_layout.setSpacing(10)

            lbl_cat = QLabel(cat)
            lbl_cat.setObjectName("SectionTitle")
            sec_layout.addWidget(lbl_cat)

            grid = QGridLayout()
            grid.setSpacing(10)
            cols = 2
            for idx, app in enumerate(items):
                card = self._make_app_card(app)
                grid.addWidget(card, idx // cols, idx % cols)

            sec_layout.addLayout(grid)
            self.cards_layout.addWidget(section)

        self.cards_layout.addStretch(1)

    def _make_app_card(self, app: dict[str, Any]) -> QFrame:
        app_id = app.get("id", app.get("name", ""))
        name = app.get("name", app_id)
        category = app.get("category", "")
        method = app.get("method", "winget")
        desc = app.get("description", "")

        card = QFrame()
        card.setStyleSheet(
            "QFrame { background-color: #141414; border: 1px solid #2C2C30; border-radius: 6px; }"
        )
        vbox = QVBoxLayout(card)
        vbox.setContentsMargins(12, 10, 12, 10)
        vbox.setSpacing(6)

        # Header row: Checkbox + Name + Status Badge
        top = QHBoxLayout()
        top.setSpacing(8)
        chk = QCheckBox(name)
        chk.setStyleSheet("font-weight: 700; font-size: 11pt;")
        self._checkboxes[app_id] = chk
        top.addWidget(chk, 1)

        badge = QLabel("Checking...")
        badge.setStyleSheet(
            f"font-size: 9.5pt; font-weight: 700; padding: 2px 8px; border-radius: 4px; "
            f"color: #141414; background-color: {STATUS_AMBER};"
        )
        self._status_badges[app_id] = badge
        top.addWidget(badge)
        vbox.addLayout(top)

        # Meta tags: category + method
        meta = QLabel(f"{category}  •  Method: {method}")
        meta.setStyleSheet(f"font-size: 9.5pt; color: {PRIMARY_ACCENT}; border: none;")
        vbox.addWidget(meta)

        if desc:
            lbl_desc = QLabel(desc)
            lbl_desc.setWordWrap(True)
            lbl_desc.setStyleSheet(f"font-size: 10pt; color: {TEXT_MUTED}; border: none;")
            vbox.addWidget(lbl_desc)

        # Buttons row
        btns = QHBoxLayout()
        btns.setSpacing(8)
        btn_install = QPushButton("Install")
        btn_install.setObjectName("PrimaryButton")
        btn_install.clicked.connect(lambda _=False, a=app: self._on_install_one(a))
        btns.addWidget(btn_install)

        btn_uninstall = QPushButton("Uninstall")
        btn_uninstall.setObjectName("DangerButton")
        btn_uninstall.clicked.connect(lambda _=False, a=app: self._on_uninstall_one(a))
        btns.addWidget(btn_uninstall)
        btns.addStretch(1)
        vbox.addLayout(btns)

        return card

    def run_detection(self) -> None:
        """Launch async detection for all apps in the catalog."""
        if self._detect_worker and self._detect_worker.isRunning():
            return
        self._detect_worker = _DetectionWorker(self._apps, self)
        self._detect_worker.detected_signal.connect(self._apply_detection_results)
        self._detect_worker.start()

    def shutdown(self) -> None:
        """Wait for any running detection worker before closing."""
        if self._detect_worker and self._detect_worker.isRunning():
            self._detect_worker.wait(5000)

    def _apply_detection_results(self, results: dict[str, bool]) -> None:
        for app_id, installed in results.items():
            badge = self._status_badges.get(app_id)
            if not badge:
                continue
            if installed:
                badge.setText("Installed")
                badge.setStyleSheet(
                    f"font-size: 9.5pt; font-weight: 700; padding: 2px 8px; border-radius: 4px; "
                    f"color: #141414; background-color: {STATUS_GREEN};"
                )
            else:
                badge.setText("Not Installed")
                badge.setStyleSheet(
                    f"font-size: 9.5pt; font-weight: 700; padding: 2px 8px; border-radius: 4px; "
                    f"color: #E0E0E0; background-color: #2C2C30;"
                )

    # --- Actions ---

    def _on_install_one(self, app: dict[str, Any]) -> None:
        name = app.get("name", "App")
        self._run_callable(
            f"Install {name}",
            lambda log, a=app: apps_manager.task_install_app(a, log),
            on_done=lambda _ok, _msg: self.run_detection(),
        )

    def _on_uninstall_one(self, app: dict[str, Any]) -> None:
        name = app.get("name", "App")
        self._run_callable(
            f"Uninstall {name}",
            lambda log, a=app: apps_manager.task_uninstall_app(a, log),
            on_done=lambda _ok, _msg: self.run_detection(),
        )

    def _on_install_checked(self) -> None:
        selected = [
            app
            for app in self._apps
            if self._checkboxes.get(app.get("id", app.get("name", "")))
            and self._checkboxes[app.get("id", app.get("name", ""))].isChecked()
        ]
        self._run_callable(
            f"Install {len(selected)} Checked Apps",
            lambda log: apps_manager.task_install_multiple_apps(selected, log),
            on_done=lambda _ok, _msg: self.run_detection(),
        )

    def _on_upgrade_all(self) -> None:
        self._run_callable(
            "Upgrade All Apps",
            apps_manager.task_upgrade_all_winget,
            on_done=lambda _ok, _msg: self.run_detection(),
        )

    def _on_sync_github(self) -> None:
        url = self.txt_github_url.text().strip()
        self._run_callable(
            "Sync apps.json from GitHub",
            lambda log: apps_manager.sync_json_from_github(url, log),
            on_done=lambda ok, _msg: self.reload_catalog() if ok else None,
        )

    def _on_open_json(self) -> None:
        if not self._active_json_path:
            return
        path_str = str(self._active_json_path)
        if IS_WINDOWS:
            os.startfile(path_str)  # type: ignore[attr-defined]
        else:
            subprocess.Popen(["xdg-open", path_str])
