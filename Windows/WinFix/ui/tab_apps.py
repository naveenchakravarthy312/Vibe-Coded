"""Tab 2: App Installer & Updater (apps.json)."""

from __future__ import annotations

import os
import subprocess
from pathlib import Path
from typing import Any

from PyQt6.QtCore import QThread, pyqtSignal
from PyQt6.QtWidgets import (
    QCheckBox,
    QComboBox,
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
from ui.theme import PRIMARY_ACCENT, STATUS_AMBER, STATUS_GREEN, TEXT_MUTED

_CATEGORY_ORDER = [
    "Browser",
    "Connectivity",
    "Office",
    "Utilities",
    "Media & Creativity",
    "Power Tools",
    "Security & Remote Support",
    "Diagnostics & Admin Tools",
    "Dev & Runtimes",
]


class _DetectionWorker(QThread):
    """Background worker to detect installed apps without freezing the UI."""

    detected_signal = pyqtSignal(dict)

    def __init__(self, apps: list[dict[str, Any]], parent=None):
        super().__init__(parent)
        self.apps = apps

    def run(self) -> None:
        winget_ids = apps_manager.fetch_winget_installed_ids()
        reg_names = apps_manager.fetch_registry_installed_names()
        results: dict[str, bool] = {}
        for app in self.apps:
            app_id = app.get("id", app.get("name", ""))
            results[app_id] = apps_manager.is_app_installed(app, winget_ids, reg_names)
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
        self._last_results: dict[str, bool] = {}
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

        # Filter & Search Row
        filter_row = QHBoxLayout()
        filter_row.setSpacing(8)

        lbl_cat = QLabel("Category:")
        lbl_cat.setObjectName("MutedLabel")
        filter_row.addWidget(lbl_cat)

        self.cmb_category = QComboBox()
        self.cmb_category.addItem("All Categories")
        self.cmb_category.currentIndexChanged.connect(self._rebuild_cards)
        filter_row.addWidget(self.cmb_category)

        self.txt_search = QLineEdit()
        self.txt_search.setPlaceholderText("Search apps by name, category, or winget ID...")
        self.txt_search.textChanged.connect(self._rebuild_cards)
        filter_row.addWidget(self.txt_search, 1)

        self.btn_select_visible = QPushButton("Select Visible")
        self.btn_select_visible.setObjectName("SmallButton")
        self.btn_select_visible.clicked.connect(lambda: self._set_visible_checked(True))
        filter_row.addWidget(self.btn_select_visible)

        self.btn_clear_checked = QPushButton("Clear")
        self.btn_clear_checked.setObjectName("SmallButton")
        self.btn_clear_checked.clicked.connect(lambda: self._set_visible_checked(False))
        filter_row.addWidget(self.btn_clear_checked)

        top_vbox.addLayout(filter_row)

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
        self.lbl_catalog_info.setWordWrap(True)
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

    def _ordered_categories(self) -> list[str]:
        seen = {app.get("category", "Utilities") for app in self._apps}
        ordered = [c for c in _CATEGORY_ORDER if c in seen]
        for app in self._apps:
            cat = app.get("category", "Utilities")
            if cat not in ordered:
                ordered.append(cat)
        return ordered

    def reload_catalog(self) -> None:
        """Load apps.json (external override or bundled) and rebuild app cards."""
        apps, raw_url, active_path, is_external = apps_manager.load_apps_catalog()
        self._apps = apps
        self._active_json_path = active_path
        if raw_url:
            self.txt_github_url.setText(raw_url)

        current_cat = self.cmb_category.currentText()
        self.cmb_category.blockSignals(True)
        self.cmb_category.clear()
        self.cmb_category.addItem("All Categories")
        for cat in self._ordered_categories():
            self.cmb_category.addItem(cat)
        idx = self.cmb_category.findText(current_cat)
        self.cmb_category.setCurrentIndex(idx if idx >= 0 else 0)
        self.cmb_category.blockSignals(False)

        source_tag = "External Override" if is_external else "Bundled Default"
        mode_tag = (
            "Admin Mode (system-wide winget)"
            if is_admin()
            else r"Non-Admin Smart Fallback (--scope user / %USERPROFILE%\Apps / Downloads\WinFix-Installers)"
        )
        self.lbl_catalog_info.setText(
            f"Catalog: {len(self._apps)} apps • {source_tag} ({active_path.name})  |  {mode_tag}"
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

    def _filtered_apps(self) -> list[dict[str, Any]]:
        selected_cat = self.cmb_category.currentText()
        query = self.txt_search.text().strip().lower()
        out = []
        for app in self._apps:
            cat = app.get("category", "Utilities")
            if selected_cat and selected_cat != "All Categories" and cat != selected_cat:
                continue
            if query:
                hay = " ".join(
                    [
                        str(app.get("name", "")),
                        str(app.get("category", "")),
                        str(app.get("winget_id", "")),
                        str(app.get("description", "")),
                    ]
                ).lower()
                if query not in hay:
                    continue
            out.append(app)
        return out

    def _rebuild_cards(self) -> None:
        prev_checked = {aid: chk.isChecked() for aid, chk in self._checkboxes.items()}
        self._clear_layout(self.cards_layout)
        self._checkboxes.clear()
        self._status_badges.clear()

        filtered = self._filtered_apps()
        grouped: dict[str, list[dict[str, Any]]] = {cat: [] for cat in self._ordered_categories()}
        for app in filtered:
            cat = app.get("category", "Utilities")
            grouped.setdefault(cat, []).append(app)

        any_rendered = False
        for cat, items in grouped.items():
            if not items:
                continue
            any_rendered = True
            section = QFrame()
            section.setObjectName("CardPanel")
            sec_layout = QVBoxLayout(section)
            sec_layout.setContentsMargins(14, 12, 14, 14)
            sec_layout.setSpacing(10)

            lbl_cat = QLabel(f"{cat} ({len(items)})")
            lbl_cat.setObjectName("SectionTitle")
            sec_layout.addWidget(lbl_cat)

            grid = QGridLayout()
            grid.setSpacing(10)
            cols = 2
            for col in range(cols):
                grid.setColumnStretch(col, 1)
            for idx, app in enumerate(items):
                app_id = app.get("id", app.get("name", ""))
                card = self._make_app_card(app, checked=prev_checked.get(app_id, False))
                grid.addWidget(card, idx // cols, idx % cols)

            sec_layout.addLayout(grid)
            self.cards_layout.addWidget(section)

        if not any_rendered:
            empty_lbl = QLabel("No apps match your current filter.")
            empty_lbl.setObjectName("MutedLabel")
            empty_lbl.setStyleSheet("padding: 24px;")
            self.cards_layout.addWidget(empty_lbl)

        self.cards_layout.addStretch(1)
        if self._last_results:
            self._apply_detection_results(self._last_results)

    def _make_app_card(self, app: dict[str, Any], checked: bool = False) -> QFrame:
        app_id = app.get("id", app.get("name", ""))
        name = app.get("name", app_id)
        category = app.get("category", "")
        method = app.get("method", "winget")
        desc = app.get("description", "")
        supports_user = bool(app.get("supports_user_scope", True))
        has_portable = bool(app.get("portable_url"))
        web_url = app.get("web_url") or ""

        card = QFrame()
        card.setObjectName("SubCard")
        vbox = QVBoxLayout(card)
        vbox.setContentsMargins(12, 10, 12, 10)
        vbox.setSpacing(6)

        # Header row: Checkbox + Name + Status Badge
        top = QHBoxLayout()
        top.setSpacing(8)
        chk = QCheckBox(name.replace("&", "&&"))
        chk.setChecked(checked)
        chk.setStyleSheet("font-weight: 700; font-size: 11pt;")
        self._checkboxes[app_id] = chk
        top.addWidget(chk, 1)

        badge = QLabel("Checking...")
        badge.setStyleSheet(
            f"font-size: 9pt; font-weight: 700; padding: 2px 8px; border-radius: 4px; border: none; "
            f"color: #141414; background-color: {STATUS_AMBER};"
        )
        self._status_badges[app_id] = badge
        top.addWidget(badge)
        vbox.addLayout(top)

        # Meta tags: category + method + scope hint
        if supports_user:
            scope_note = "User-Scope OK"
        elif has_portable:
            scope_note = "Portable / Installer"
        else:
            scope_note = "Admin / Installer DL"
        meta = QLabel(f"{category}  •  {method}  •  {scope_note}")
        meta.setStyleSheet(f"font-size: 9.5pt; color: {PRIMARY_ACCENT}; border: none; background: transparent;")
        vbox.addWidget(meta)

        if desc:
            lbl_desc = QLabel(desc)
            lbl_desc.setObjectName("MutedLabel")
            lbl_desc.setWordWrap(True)
            vbox.addWidget(lbl_desc)

        vbox.addStretch(1)

        # Buttons row
        btns = QHBoxLayout()
        btns.setSpacing(6)
        btn_install = QPushButton("Install")
        btn_install.setObjectName("SmallPrimary")
        btn_install.clicked.connect(lambda _=False, a=app: self._on_install_one(a))
        btns.addWidget(btn_install)

        btn_dl = QPushButton("Get Installer")
        btn_dl.setObjectName("SmallButton")
        btn_dl.setToolTip("Download installer from the internet to Downloads\\WinFix-Installers")
        btn_dl.clicked.connect(lambda _=False, a=app: self._on_download_one(a))
        btns.addWidget(btn_dl)

        btn_uninstall = QPushButton("Uninstall")
        btn_uninstall.setObjectName("SmallDanger")
        btn_uninstall.clicked.connect(lambda _=False, a=app: self._on_uninstall_one(a))
        btns.addWidget(btn_uninstall)

        if web_url:
            btn_web = QPushButton("Web")
            btn_web.setObjectName("SmallButton")
            btn_web.setToolTip(f"Open official site: {web_url}")
            btn_web.clicked.connect(lambda _=False, u=web_url: self._open_url(u))
            btns.addWidget(btn_web)

        btns.addStretch(1)
        vbox.addLayout(btns)

        return card

    def _set_visible_checked(self, state: bool) -> None:
        for chk in self._checkboxes.values():
            chk.setChecked(state)

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
        self._last_results = results
        for app_id, installed in results.items():
            badge = self._status_badges.get(app_id)
            if not badge:
                continue
            if installed:
                badge.setText("Installed")
                badge.setStyleSheet(
                    f"font-size: 9pt; font-weight: 700; padding: 2px 8px; border-radius: 4px; border: none; "
                    f"color: #141414; background-color: {STATUS_GREEN};"
                )
            else:
                badge.setText("Not Installed")
                badge.setStyleSheet(
                    "font-size: 9pt; font-weight: 700; padding: 2px 8px; border-radius: 4px; border: none; "
                    "color: #E0E0E0; background-color: #2C2C30;"
                )

    # --- Actions ---

    def _on_install_one(self, app: dict[str, Any]) -> None:
        name = app.get("name", "App")
        self._run_callable(
            f"Install {name}",
            lambda log, a=app: apps_manager.task_install_app(a, log),
            on_done=lambda _ok, _msg: self.run_detection(),
        )

    def _on_download_one(self, app: dict[str, Any]) -> None:
        name = app.get("name", "App")
        self._run_callable(
            f"Download Installer: {name}",
            lambda log, a=app: apps_manager.task_download_installer(a, log, auto_launch=True),
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

    def _open_url(self, url: str) -> None:
        if IS_WINDOWS:
            os.startfile(url)  # type: ignore[attr-defined]
        else:
            subprocess.Popen(["xdg-open", url])
