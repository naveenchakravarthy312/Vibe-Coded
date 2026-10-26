#!/usr/bin/env python3
"""
Ephemeral Scratchpad — Ultra-minimalist, zero-persistence scratchpad for Linux.

Features:
- Strictly in-memory: zero disk writes, zero temp files, zero state persistence.
- Distraction-free UI: full-bleed dark canvas (#141414), 24px internal padding,
  hidden scrollbars, modern monospace typography, and a subtle bottom-right word count.
- Instant lifecycle: Escape or Ctrl+Q immediately wipes memory and exits.
- Dynamic typography: Ctrl+= / Ctrl++ to zoom in, Ctrl+- to zoom out, Ctrl+0 to reset.
- Window management: Native decorations by default with F11 frameless toggle,
  plus edge-drag moving/resizing and Alt+Drag window movement.
"""

import gc
import os
from pathlib import Path
import sys

try:
    from PyQt6.QtCore import Qt, QEvent, QRectF
except ModuleNotFoundError:
    # If launched directly via ./ephemeral_pad.py without activating .venv,
    # automatically re-exec using the local .venv interpreter if available.
    # Note: Do not call .resolve() on _venv_python because venv's python3 is a
    # symlink to the system python binary and relies on argv[0] staying inside .venv/bin.
    _venv_dir = Path(__file__).resolve().parent / ".venv"
    _venv_python = _venv_dir / "bin" / "python3"
    if _venv_python.exists() and Path(sys.prefix).resolve() != _venv_dir.resolve():
        os.execv(str(_venv_python), [str(_venv_python), *sys.argv])
    raise

from PyQt6.QtGui import (
    QColor,
    QFont,
    QFontDatabase,
    QIcon,
    QKeySequence,
    QPainter,
    QPainterPath,
    QPixmap,
    QShortcut,
    QTextOption,
)
from PyQt6.QtWidgets import (
    QApplication,
    QFrame,
    QLabel,
    QMainWindow,
    QPlainTextEdit,
    QVBoxLayout,
    QWidget,
)


APP_DIR = Path(__file__).resolve().parent
ICON_CANDIDATES = ("icon.png", "icon.svg", "icon.ico")


PREFERRED_FONTS = [
    "JetBrains Mono",
    "Fira Code",
    "Cascadia Code",
    "Iosevka",
    "Source Code Pro",
    "Ubuntu Mono",
    "DejaVu Sans Mono",
    "Monospace",
]

DEFAULT_FONT_SIZE = 13
MIN_FONT_SIZE = 8
MAX_FONT_SIZE = 48
PADDING_PX = 24
RESIZE_MARGIN_PX = 8


def _render_placeholder_pixmap() -> QPixmap:
    """Render a 256x256 YAMIS-style (Yet Another Monochrome Icon Set) glyph in memory."""
    pixmap = QPixmap(256, 256)
    pixmap.fill(Qt.GlobalColor.transparent)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)

    # Scale from YAMIS 16.933x16.933 viewBox to 256x256
    scale = 256.0 / 16.933
    painter.scale(scale, scale)

    path = QPainterPath()
    path.setFillRule(Qt.FillRule.OddEvenFill)

    # Outer rounded page with top-right corner cutout step
    x1, y1 = 3.0153871, 1.4551795
    x2, y2 = 13.917613, 15.47782
    r = 0.73462
    xcut, ycut = 11.192056, 4.1807359

    path.moveTo(x1 + r, y1)
    path.lineTo(xcut, y1)
    path.lineTo(xcut, ycut)
    path.lineTo(x2, ycut)
    path.lineTo(x2, y2 - r)
    path.arcTo(QRectF(x2 - 2 * r, y2 - 2 * r, 2 * r, 2 * r), 0, -90)
    path.lineTo(x1 + r, y2)
    path.arcTo(QRectF(x1, y2 - 2 * r, 2 * r, 2 * r), -90, -90)
    path.lineTo(x1, y1 + r)
    path.arcTo(QRectF(x1, y1, 2 * r, 2 * r), 180, -90)
    path.closeSubpath()

    # Left binder cutout strip
    path.addRect(QRectF(3.7941994, y1, 0.3896932, 15.082388 - y1))

    # Top-right folded corner wedge with rounded outer corner
    wedge = QPainterPath()
    wedge.moveTo(xcut, y1)
    wedge.lineTo(x2, ycut)
    wedge.lineTo(x2, y1 + r)
    wedge.arcTo(QRectF(x2 - 2 * r, y1, 2 * r, 2 * r), 0, 90)
    wedge.closeSubpath()
    path.addPath(wedge)

    # Segmented horizontal negative-space text lines (YAMIS kwrite glyph)
    h = 0.3896931
    rows = [
        (3.0122304, [(4.9621311, 1.946744), (7.2985683, 1.5570509), (9.2453124, 1.7521846)]),
        (3.7910428, [(4.9621311, 2.7255564), (8.0773808, 1.1679316), (9.634432, 1.363065)]),
        (4.5698552, [(4.9621311, 3.1152497), (8.4665, 1.946744), (10.802363, 1.557625)]),
        (5.3486676, [(4.9621311, 2.3364372), (7.6876875, 1.1679317), (9.2453124, 1.5570506), (11.192056, 1.167932)]),
        (6.12748,   [(4.9621311, 1.5691032), (6.9088751, 2.7255569)]),
        (7.6845309, [(4.9621311, 3.8934881), (9.2453124, 1.9467436), (11.581176, 0.778812)]),
        (8.4633433, [(4.9621311, 1.5691032), (6.9088751, 3.1152499), (10.413244, 1.568529)]),
        (9.2421558, [(4.9621311, 2.7255564)]),
        (10.799206, [(4.9621311, 3.8934881), (9.2453124, 1.5570506), (11.192056, 1.167932)]),
        (11.578019, [(4.9621311, 2.7255564), (8.0773808, 1.5570512), (10.024125, 2.335863)]),
        (12.356831, [(4.9621311, 1.5576248), (6.9088751, 3.1152499)]),
        (13.135644, [(4.9621311, 2.7255564), (8.0773808, 2.7249822)]),
    ]
    for y, segments in rows:
        for x, w in segments:
            path.addRect(QRectF(x, y, w, h))

    painter.setPen(Qt.PenStyle.NoPen)
    painter.setBrush(QColor("#d8dee9"))
    painter.drawPath(path)
    painter.end()
    return pixmap


def load_app_icon() -> QIcon:
    """Load custom icon from Notepad/icon.{png,svg,ico}, or render a fallback in memory."""
    for filename in ICON_CANDIDATES:
        icon_path = APP_DIR / filename
        if icon_path.is_file():
            icon = QIcon(str(icon_path))
            if not icon.isNull():
                return icon

    return QIcon(_render_placeholder_pixmap())


def select_monospace_font(size: int = DEFAULT_FONT_SIZE) -> QFont:
    """Pick the best available modern monospace font on the system."""
    available = set(QFontDatabase.families())
    for family in PREFERRED_FONTS:
        if family in available:
            font = QFont(family, size)
            break
    else:
        font = QFontDatabase.systemFont(QFontDatabase.SystemFont.FixedFont)
        font.setPointSize(size)

    font.setStyleHint(QFont.StyleHint.Monospace)
    font.setFixedPitch(True)
    return font


class EphemeralEditor(QPlainTextEdit):
    """Full-canvas distraction-free plain text editor with 24px viewport margins."""

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)

        # Remove frame and scrollbars completely
        self.setFrameShape(QFrame.Shape.NoFrame)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)

        # Word wrap at widget boundary while keeping clean formatting
        self.setLineWrapMode(QPlainTextEdit.LineWrapMode.WidgetWidth)
        self.setWordWrapMode(QTextOption.WrapMode.WrapAtWordBoundaryOrAnywhere)

        # 24px internal padding around the text canvas
        self.setViewportMargins(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        self.document().setDocumentMargin(0)

        # Crisp 2px cursor and 4-space tab stop
        self.setCursorWidth(2)
        self.setTabStopDistance(32.0)

    def insertFromMimeData(self, source) -> None:
        """Always paste strictly as plain text to keep the scratchpad clean."""
        if source.hasText():
            self.insertPlainText(source.text())
        else:
            super().insertFromMimeData(source)

    def wheelEvent(self, event) -> None:
        """Support Ctrl + Mouse Wheel for dynamic font scaling."""
        if event.modifiers() & Qt.KeyboardModifier.ControlModifier:
            delta = event.angleDelta().y()
            window = self.window()
            if hasattr(window, "adjust_font_size"):
                if delta > 0:
                    window.adjust_font_size(1)
                elif delta < 0:
                    window.adjust_font_size(-1)
            event.accept()
            return
        super().wheelEvent(event)


class EphemeralPadWindow(QMainWindow):
    """Main window hosting the full-bleed editor and subtle word count indicator."""

    def __init__(self) -> None:
        super().__init__()
        self.setWindowTitle("Scratchpad")
        self.setWindowIcon(load_app_icon())
        self.resize(760, 520)
        self.setMinimumSize(320, 200)

        self._font_size = DEFAULT_FONT_SIZE
        self._frameless = False

        # Central container and layout with zero margins
        central = QWidget(self)
        layout = QVBoxLayout(central)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        # Editor
        self.editor = EphemeralEditor(central)
        self._font = select_monospace_font(self._font_size)
        self.editor.setFont(self._font)
        layout.addWidget(self.editor)
        self.setCentralWidget(central)

        # Subtle floating word count in bottom-right corner
        self.word_count_label = QLabel("0 words", self.editor)
        self.word_count_label.setAttribute(
            Qt.WidgetAttribute.WA_TransparentForMouseEvents, True
        )
        self.word_count_label.setAlignment(
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        )
        self._update_word_count_font()

        # Apply dark matte styling
        self._apply_styles()

        # Signals & shortcuts
        self.editor.textChanged.connect(self._update_word_count)
        self._setup_shortcuts()

        # Install event filters for Alt+drag and frameless edge drag/resize
        self.installEventFilter(self)
        self.editor.installEventFilter(self)
        self.editor.viewport().installEventFilter(self)

        # Focus editor immediately on launch
        self.editor.setFocus(Qt.FocusReason.OtherFocusReason)

    def _apply_styles(self) -> None:
        self.setStyleSheet(
            """
            QMainWindow, QWidget {
                background-color: #141414;
                color: #e0e0e0;
                border: none;
            }
            QPlainTextEdit {
                background-color: #141414;
                color: #e0e0e0;
                selection-background-color: #323842;
                selection-color: #ffffff;
                border: none;
            }
            QLabel {
                background: transparent;
                color: #585858;
                padding: 2px 4px;
            }
            """
        )

    def _update_word_count_font(self) -> None:
        wc_font = QFont(self._font)
        wc_font.setPointSize(max(9, min(11, self._font_size - 2)))
        self.word_count_label.setFont(wc_font)

    def _bind_shortcuts(self, sequences: list[QKeySequence], callback) -> None:
        """Bind unique key sequences to callback without creating ambiguous duplicates."""
        seen: set[str] = set()
        for seq in sequences:
            key_str = seq.toString()
            if key_str and key_str not in seen:
                seen.add(key_str)
                shortcut = QShortcut(seq, self)
                shortcut.setContext(Qt.ShortcutContext.ApplicationShortcut)
                shortcut.activated.connect(callback)

    def _setup_shortcuts(self) -> None:
        # Instant quit shortcuts (Escape & Ctrl+Q)
        self._bind_shortcuts(
            [QKeySequence(Qt.Key.Key_Escape), QKeySequence("Ctrl+Q")],
            self.instant_quit,
        )

        # Zoom in shortcuts (Ctrl+= and Ctrl++)
        self._bind_shortcuts(
            [
                QKeySequence("Ctrl+="),
                QKeySequence("Ctrl++"),
                QKeySequence(QKeySequence.StandardKey.ZoomIn),
            ],
            lambda: self.adjust_font_size(1),
        )

        # Zoom out shortcuts (Ctrl+-)
        self._bind_shortcuts(
            [
                QKeySequence("Ctrl+-"),
                QKeySequence(QKeySequence.StandardKey.ZoomOut),
            ],
            lambda: self.adjust_font_size(-1),
        )

        # Reset font size (Ctrl+0)
        self._bind_shortcuts([QKeySequence("Ctrl+0")], self.reset_font_size)

        # Toggle frameless window mode (F11 or Ctrl+Shift+F)
        self._bind_shortcuts(
            [QKeySequence(Qt.Key.Key_F11), QKeySequence("Ctrl+Shift+F")],
            self.toggle_frameless,
        )

    def adjust_font_size(self, delta: int) -> None:
        new_size = max(MIN_FONT_SIZE, min(MAX_FONT_SIZE, self._font_size + delta))
        if new_size != self._font_size:
            self._font_size = new_size
            self._font.setPointSize(self._font_size)
            self.editor.setFont(self._font)
            self._update_word_count_font()
            self._position_word_count()

    def reset_font_size(self) -> None:
        self.adjust_font_size(DEFAULT_FONT_SIZE - self._font_size)

    def toggle_frameless(self) -> None:
        """Toggle between standard window decorations and frameless mode."""
        self._frameless = not self._frameless
        geom = self.geometry()
        if self._frameless:
            self.setWindowFlags(
                Qt.WindowType.Window | Qt.WindowType.FramelessWindowHint
            )
        else:
            self.setWindowFlags(Qt.WindowType.Window)
        self.setGeometry(geom)
        self.show()
        self.editor.setFocus()

    def _update_word_count(self) -> None:
        text = self.editor.toPlainText()
        count = len(text.split()) if text else 0
        self.word_count_label.setText(f"{count} word" if count == 1 else f"{count} words")
        self._position_word_count()

    def _position_word_count(self) -> None:
        self.word_count_label.adjustSize()
        margin_right = 16
        margin_bottom = 10
        x = max(0, self.editor.width() - self.word_count_label.width() - margin_right)
        y = max(0, self.editor.height() - self.word_count_label.height() - margin_bottom)
        self.word_count_label.move(x, y)

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        self._position_word_count()

    def showEvent(self, event) -> None:
        super().showEvent(event)
        self._position_word_count()
        self.editor.setFocus()

    def _detect_resize_edges(self, pos) -> Qt.Edge:
        """Return window edges near the given window-local position."""
        edges = Qt.Edge(0)
        if pos.x() <= RESIZE_MARGIN_PX:
            edges |= Qt.Edge.LeftEdge
        elif pos.x() >= self.width() - RESIZE_MARGIN_PX:
            edges |= Qt.Edge.RightEdge

        if pos.y() <= RESIZE_MARGIN_PX:
            edges |= Qt.Edge.TopEdge
        elif pos.y() >= self.height() - RESIZE_MARGIN_PX:
            edges |= Qt.Edge.BottomEdge

        return edges

    def eventFilter(self, watched, event) -> bool:
        """Support Alt+LeftDrag to move, and border dragging to move/resize when frameless."""
        if event.type() == QEvent.Type.MouseButtonPress:
            if event.button() == Qt.MouseButton.LeftButton:
                win_handle = self.windowHandle()
                if win_handle is not None:
                    # Alt + Left Click anywhere moves the window
                    if event.modifiers() & Qt.KeyboardModifier.AltModifier:
                        win_handle.startSystemMove()
                        return True

                    # In frameless mode, dragging the outer edge resizes, and dragging
                    # within the 24px padding margin moves the window.
                    if self._frameless:
                        win_pos = self.mapFromGlobal(event.globalPosition().toPoint())
                        edges = self._detect_resize_edges(win_pos)
                        if edges:
                            win_handle.startSystemResize(edges)
                            return True
                        if (
                            win_pos.x() < PADDING_PX
                            or win_pos.x() > self.width() - PADDING_PX
                            or win_pos.y() < PADDING_PX
                            or win_pos.y() > self.height() - PADDING_PX
                        ):
                            win_handle.startSystemMove()
                            return True
        return super().eventFilter(watched, event)

    def _wipe_memory(self) -> None:
        """Explicitly clear text buffers and undo/redo history from memory."""
        doc = self.editor.document()
        doc.clearUndoRedoStacks()
        doc.clear()
        self.editor.clear()
        gc.collect()

    def instant_quit(self) -> None:
        """Wipe buffers and exit immediately without confirmation."""
        self._wipe_memory()
        self.close()
        QApplication.quit()

    def closeEvent(self, event) -> None:
        """Never prompt to save; wipe all in-memory content on window close."""
        self._wipe_memory()
        event.accept()


def main() -> int:
    # Prevent Qt from storing or restoring any session state on disk
    QApplication.setDesktopSettingsAware(True)
    app = QApplication(sys.argv)
    app.setApplicationName("ephemeral_pad")
    app.setWindowIcon(load_app_icon())
    app.setCursorFlashTime(1000)

    def commit_data_noop(manager) -> None:
        manager.cancel()

    app.commitDataRequest.connect(commit_data_noop)
    app.saveStateRequest.connect(lambda manager: None)

    window = EphemeralPadWindow()
    window.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
