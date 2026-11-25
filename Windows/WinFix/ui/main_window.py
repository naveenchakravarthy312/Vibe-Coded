"""Main application window for WinFix."""

from __future__ import annotations

from typing import Callable

from PyQt6.QtWidgets import (
    QApplication,
    QMainWindow,
    QMessageBox,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from core.system_info import relaunch_as_admin
from core.workers import CallableWorker, ShellStep, StepsWorker
from ui.log_drawer import LogDrawer
from ui.tab_admin import AdminShortcutsTab
from ui.tab_apps import AppsTab
from ui.tab_quick_fixes import QuickFixesTab
from ui.tab_tweaks import TweaksTab
from ui.telemetry_bar import TelemetryBar


class MainWindow(QMainWindow):
    """Primary WinFix control panel window."""

    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("WinFix — Portable Windows Troubleshooting & Power Utility")
        self.resize(1140, 860)
        self.setMinimumSize(940, 680)

        self._active_workers: list[object] = []
        self._build_ui()

    def _build_ui(self) -> None:
        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(10, 10, 10, 10)
        root.setSpacing(10)

        # Persistent System Telemetry Header
        self.telemetry_bar = TelemetryBar()
        self.telemetry_bar.relaunch_admin_requested.connect(self._on_relaunch_admin)
        self.telemetry_bar.refresh_requested.connect(self._on_global_refresh)
        root.addWidget(self.telemetry_bar)

        # 4-Tab Control Panel
        self.tabs = QTabWidget()
        self.tab_quick = QuickFixesTab(self.run_steps, self.run_callable)
        self.tab_apps = AppsTab(self.run_callable)
        self.tab_tweaks = TweaksTab(self.run_callable)
        self.tab_admin = AdminShortcutsTab(self._log)

        self.tabs.addTab(self.tab_quick, "Quick Fixes && Diagnostics")
        self.tabs.addTab(self.tab_apps, "App Installer && Updater")
        self.tabs.addTab(self.tab_tweaks, "Win 10 UI && Debloat")
        self.tabs.addTab(self.tab_admin, "Admin Shortcuts")
        self.tabs.currentChanged.connect(self._on_tab_changed)
        root.addWidget(self.tabs, 1)

        # Collapsible Live Log Drawer
        self.log_drawer = LogDrawer()
        root.addWidget(self.log_drawer)

        self._log("INFO", "WinFix initialized. Ready.")

    def _on_global_refresh(self) -> None:
        self.tab_tweaks.refresh_states()
        self.tab_apps.run_detection()

    def _on_tab_changed(self, index: int) -> None:
        if self.tabs.widget(index) is self.tab_tweaks:
            self.tab_tweaks.refresh_states()

    def _log(self, level: str, message: str) -> None:
        self.log_drawer.append_log(level, message)

    def _on_relaunch_admin(self) -> None:
        if relaunch_as_admin():
            QApplication.quit()
        else:
            QMessageBox.warning(
                self,
                "Elevation Cancelled",
                "Could not relaunch as Administrator (UAC prompt was cancelled or unavailable).",
            )

    def run_steps(
        self,
        task_name: str,
        steps: list[ShellStep],
        on_done: Callable[[bool, str], None] | None = None,
    ) -> None:
        """Execute ShellSteps in a non-blocking QThread worker."""
        self.log_drawer.set_expanded(True)
        self.log_drawer.set_status(f"Running: {task_name}...")

        worker = StepsWorker(task_name, steps, self)
        self._attach_worker(worker, on_done)
        worker.start()

    def run_callable(
        self,
        task_name: str,
        fn: Callable,
        on_done: Callable[[bool, str], None] | None = None,
    ) -> None:
        """Execute a Python callable in a non-blocking QThread worker."""
        self.log_drawer.set_expanded(True)
        self.log_drawer.set_status(f"Running: {task_name}...")

        worker = CallableWorker(task_name, fn, self)
        self._attach_worker(worker, on_done)
        worker.start()

    def _attach_worker(
        self,
        worker: StepsWorker | CallableWorker,
        on_done: Callable[[bool, str], None] | None,
    ) -> None:
        self._active_workers.append(worker)
        worker.log_signal.connect(self.log_drawer.append_log)

        def _finished(ok: bool, summary: str) -> None:
            self.log_drawer.set_status(summary)
            if worker in self._active_workers:
                self._active_workers.remove(worker)
            worker.deleteLater()
            if on_done:
                on_done(ok, summary)

        worker.finished_signal.connect(_finished)

    def closeEvent(self, event) -> None:
        """Ensure background QThreads finish cleanly before window destruction."""
        self.tab_apps.shutdown()
        for worker in list(self._active_workers):
            if hasattr(worker, "isRunning") and worker.isRunning():
                worker.wait(5000)
        super().closeEvent(event)
