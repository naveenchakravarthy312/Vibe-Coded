"""WinFix entry point."""

from __future__ import annotations

import sys

from PyQt6.QtWidgets import QApplication

from ui.main_window import MainWindow
from ui.theme import DARK_STYLESHEET


def main() -> int:
    """Launch the WinFix PyQt6 desktop utility."""
    app = QApplication(sys.argv)
    app.setApplicationName("WinFix")
    app.setStyle("Fusion")
    app.setStyleSheet(DARK_STYLESHEET)

    window = MainWindow()
    window.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
