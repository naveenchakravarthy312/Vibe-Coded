#!/usr/bin/env python3
"""
Scratchpad — Ultra-minimalist, zero-chrome text & markdown scratchpad for Linux.

Design Aesthetic (matching the original Linux Scratchpad & Auto-Clicker):
- 100% Full-Bleed Matte Dark Canvas (#141414) with zero toolbars, buttons, borders, or dividers
- Soft Off-White Foreground (#e0e0e0) and Muted Slate Selection (#323842 with #ffffff text)
- Whisper-Quiet Metadata (#585858) in the bottom-right corner using middle-dot (·) separators
- 24px Viewport Padding around all four edges for maximum distraction-free writing space
- Crisp 2px-wide blinking cursor (1000ms cycle) and plain-text-only paste
- 100% Keyboard-Driven: every Android Scratchpad feature and .txt/.md file operation is
  bound to a direct keyboard shortcut with zero permanent UI chrome.
"""

from __future__ import annotations

import datetime
import gc
import os
from pathlib import Path
import sqlite3
import sys
from dataclasses import dataclass

try:
    from PyQt6.QtCore import (
        QEvent,
        QMimeData,
        QPoint,
        QRectF,
        Qt,
        QTimer,
        pyqtSignal,
    )
except ModuleNotFoundError:
    # Automatically re-exec using the local .venv interpreter if launched directly
    _venv_dir = Path(__file__).resolve().parent / ".venv"
    _venv_python = _venv_dir / "bin" / "python3"
    if _venv_python.exists() and Path(sys.prefix).resolve() != _venv_dir.resolve():
        os.execv(str(_venv_python), [str(_venv_python), *sys.argv])
    raise

from PyQt6.QtGui import (
    QColor,
    QFont,
    QFontDatabase,
    QFontMetricsF,
    QIcon,
    QKeySequence,
    QPainter,
    QPainterPath,
    QPixmap,
    QShortcut,
    QTextCharFormat,
    QTextCursor,
    QTextOption,
)
from PyQt6.QtWidgets import (
    QApplication,
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QMainWindow,
    QPlainTextEdit,
    QScrollArea,
    QStackedWidget,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)


# ---------------------------------------------------------------------------
# Color Palettes (Exact match with original Scratchpad & Auto-Clicker)
# ---------------------------------------------------------------------------
PALETTE_DARK = {
    "canvas": "#141414",       # Matte Dark Canvas (100% flat charcoal, zero bevels/borders)
    "fg": "#e0e0e0",           # Soft Off-White Foreground
    "sel_bg": "#323842",       # Muted Slate Selection background
    "sel_fg": "#ffffff",       # Muted Slate Selection text
    "meta": "#585858",         # Whisper-Quiet Metadata (bottom-right counter & subtle labels)
    "active": "#e0e0e0",       # Crisp high-legibility active state
    "match_bg": "#242932",     # Subtle inactive find match highlight
}

PALETTE_LIGHT = {
    "canvas": "#f2f2f0",
    "fg": "#1e1e1e",
    "sel_bg": "#323842",
    "sel_fg": "#ffffff",
    "meta": "#888888",
    "active": "#141414",
    "match_bg": "#dcdcdc",
}


# ---------------------------------------------------------------------------
# Typography & Layout Constants
# ---------------------------------------------------------------------------
APP_DIR = Path(__file__).resolve().parent
DATA_DIR = Path.home() / ".local" / "share" / "ephemeral-scratchpad"
DB_PATH = DATA_DIR / "scratchpad.db"
ICON_CANDIDATES = ("icon.png", "icon.svg", "icon.ico")

PREFERRED_MONO_FONTS = [
    "JetBrains Mono",
    "Fira Code",
    "Cascadia Code",
    "Iosevka",
    "Source Code Pro",
    "Ubuntu Mono",
    "DejaVu Sans Mono",
    "Monospace",
]

PREFERRED_SANS_FONTS = [
    "Roboto",
    "Inter",
    "Cantarell",
    "Ubuntu",
    "Noto Sans",
    "DejaVu Sans",
    "Sans Serif",
]

DEFAULT_FONT_SIZE = 13
MIN_FONT_SIZE = 8
MAX_FONT_SIZE = 48
PADDING_PX = 24
RESIZE_MARGIN_PX = 8


# ---------------------------------------------------------------------------
# Data Model & SQLite Repository (Saved Notes Library & Preferences)
# ---------------------------------------------------------------------------
@dataclass
class SavedNote:
    id: int
    title: str
    content: str
    created_at: int  # epoch ms
    updated_at: int  # epoch ms
    is_pinned: bool
    word_count: int
    char_count: int


class ScratchpadRepository:
    """SQLite persistence for Saved Notes Library and typography preferences."""

    def __init__(self, db_path: Path | None = None) -> None:
        self.db_path = db_path if db_path is not None else DB_PATH
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.db_path))
        conn.row_factory = sqlite3.Row
        return conn

    def _init_db(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    is_pinned INTEGER NOT NULL DEFAULT 0,
                    word_count INTEGER NOT NULL DEFAULT 0,
                    char_count INTEGER NOT NULL DEFAULT 0
                )
                """
            )
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS preferences (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """
            )
            conn.commit()

    def get_pref(self, key: str, default: str = "") -> str:
        with self._connect() as conn:
            row = conn.execute(
                "SELECT value FROM preferences WHERE key = ?", (key,)
            ).fetchone()
            return str(row["value"]) if row else default

    def set_pref(self, key: str, value: str) -> None:
        with self._connect() as conn:
            conn.execute(
                "INSERT OR REPLACE INTO preferences (key, value) VALUES (?, ?)",
                (key, str(value)),
            )
            conn.commit()

    def list_notes(self, search_query: str = "") -> list[SavedNote]:
        query = search_query.strip()
        with self._connect() as conn:
            if query:
                like = f"%{query}%"
                rows = conn.execute(
                    """
                    SELECT * FROM notes
                    WHERE title LIKE ? OR content LIKE ?
                    ORDER BY is_pinned DESC, updated_at DESC
                    """,
                    (like, like),
                ).fetchall()
            else:
                rows = conn.execute(
                    "SELECT * FROM notes ORDER BY is_pinned DESC, updated_at DESC"
                ).fetchall()
        return [
            SavedNote(
                id=int(r["id"]),
                title=str(r["title"]),
                content=str(r["content"]),
                created_at=int(r["created_at"]),
                updated_at=int(r["updated_at"]),
                is_pinned=bool(r["is_pinned"]),
                word_count=int(r["word_count"]),
                char_count=int(r["char_count"]),
            )
            for r in rows
        ]

    def get_note_by_id(self, note_id: int) -> SavedNote | None:
        with self._connect() as conn:
            r = conn.execute(
                "SELECT * FROM notes WHERE id = ?", (note_id,)
            ).fetchone()
            if not r:
                return None
            return SavedNote(
                id=int(r["id"]),
                title=str(r["title"]),
                content=str(r["content"]),
                created_at=int(r["created_at"]),
                updated_at=int(r["updated_at"]),
                is_pinned=bool(r["is_pinned"]),
                word_count=int(r["word_count"]),
                char_count=int(r["char_count"]),
            )

    def upsert_note(
        self,
        title: str,
        content: str,
        note_id: int | None = None,
        created_at: int | None = None,
        is_pinned: bool = False,
    ) -> int:
        now_ms = int(datetime.datetime.now().timestamp() * 1000)
        words = len(content.split()) if content.strip() else 0
        chars = len(content)
        existing = self.get_note_by_id(note_id) if note_id is not None else None

        with self._connect() as conn:
            if existing is not None:
                conn.execute(
                    """
                    UPDATE notes
                    SET title = ?, content = ?, updated_at = ?, is_pinned = ?,
                        word_count = ?, char_count = ?
                    WHERE id = ?
                    """,
                    (
                        title,
                        content,
                        now_ms,
                        int(existing.is_pinned),
                        words,
                        chars,
                        existing.id,
                    ),
                )
                conn.commit()
                return existing.id
            else:
                created = created_at if created_at is not None else now_ms
                cur = conn.execute(
                    """
                    INSERT INTO notes (title, content, created_at, updated_at, is_pinned, word_count, char_count)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (title, content, created, now_ms, int(is_pinned), words, chars),
                )
                conn.commit()
                return int(cur.lastrowid)

    def restore_note(self, note: SavedNote) -> int:
        with self._connect() as conn:
            cur = conn.execute(
                """
                INSERT OR REPLACE INTO notes (id, title, content, created_at, updated_at, is_pinned, word_count, char_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    note.id,
                    note.title,
                    note.content,
                    note.created_at,
                    note.updated_at,
                    int(note.is_pinned),
                    note.word_count,
                    note.char_count,
                ),
            )
            conn.commit()
            return int(cur.lastrowid or note.id)

    def toggle_pin(self, note: SavedNote) -> None:
        with self._connect() as conn:
            conn.execute(
                "UPDATE notes SET is_pinned = ? WHERE id = ?",
                (0 if note.is_pinned else 1, note.id),
            )
            conn.commit()

    def delete_note(self, note_id: int) -> None:
        with self._connect() as conn:
            conn.execute("DELETE FROM notes WHERE id = ?", (note_id,))
            conn.commit()


# ---------------------------------------------------------------------------
# App Icon & Font Helpers
# ---------------------------------------------------------------------------
def _render_placeholder_pixmap() -> QPixmap:
    """Render a 256x256 YAMIS-style (Yet Another Monochrome Icon Set) glyph in memory."""
    pixmap = QPixmap(256, 256)
    pixmap.fill(Qt.GlobalColor.transparent)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)

    scale = 256.0 / 16.933
    painter.scale(scale, scale)

    path = QPainterPath()
    path.setFillRule(Qt.FillRule.OddEvenFill)

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

    path.addRect(QRectF(3.7941994, y1, 0.3896932, 15.082388 - y1))

    wedge = QPainterPath()
    wedge.moveTo(xcut, y1)
    wedge.lineTo(x2, ycut)
    wedge.lineTo(x2, y1 + r)
    wedge.arcTo(QRectF(x2 - 2 * r, y1, 2 * r, 2 * r), 0, 90)
    wedge.closeSubpath()
    path.addPath(wedge)

    h = 0.3896931
    rows = [
        (3.0122304, [(4.9621311, 1.946744), (7.2985683, 1.5570509), (9.2453124, 1.7521846)]),
        (3.7910428, [(4.9621311, 2.7255564), (8.0773808, 1.1679316), (9.634432, 1.363065)]),
        (4.5698552, [(4.9621311, 3.1152497), (8.4665, 1.946744), (10.802363, 1.557625)]),
        (5.3486676, [(4.9621311, 2.3364372), (7.6876875, 1.1679317), (9.2453124, 1.5570506), (11.192056, 1.167932)]),
        (6.12748, [(4.9621311, 1.5691032), (6.9088751, 2.7255569)]),
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


def load_bundled_fonts() -> None:
    """Load bundled JetBrains Mono and Roboto .ttf files if present in the workspace."""
    candidate_dirs = [
        APP_DIR / "fonts",
        APP_DIR.parent.parent / "Android" / "scratchpad" / "app" / "src" / "main" / "res" / "font",
        APP_DIR.parent / "Android" / "scratchpad" / "app" / "src" / "main" / "res" / "font",
    ]
    for folder in candidate_dirs:
        if folder.is_dir():
            for ttf in folder.glob("*.ttf"):
                QFontDatabase.addApplicationFont(str(ttf))


def select_font(size: int = DEFAULT_FONT_SIZE, is_monospace: bool = True) -> QFont:
    """Pick the best available modern monospace or sans-serif font on the system."""
    available = set(QFontDatabase.families())
    candidates = PREFERRED_MONO_FONTS if is_monospace else PREFERRED_SANS_FONTS
    for family in candidates:
        if family in available:
            font = QFont(family, size)
            break
    else:
        sys_type = (
            QFontDatabase.SystemFont.FixedFont
            if is_monospace
            else QFontDatabase.SystemFont.GeneralFont
        )
        font = QFontDatabase.systemFont(sys_type)
        font.setPointSize(size)

    if is_monospace:
        font.setStyleHint(QFont.StyleHint.Monospace)
        font.setFixedPitch(True)
    else:
        font.setStyleHint(QFont.StyleHint.SansSerif)
    return font


def format_timestamp(epoch_ms: int) -> str:
    dt = datetime.datetime.fromtimestamp(epoch_ms / 1000.0)
    return dt.strftime("%b %-d, %H:%M")


def auto_generate_title(text: str) -> str:
    for line in text.splitlines():
        cleaned = line.strip().lstrip("#").strip()
        if cleaned:
            return cleaned[:30] + ("…" if len(cleaned) > 30 else "")
    now_str = datetime.datetime.now().strftime("%b %-d, %H:%M")
    return f"Scratchpad {now_str}"


# ---------------------------------------------------------------------------
# Full-Bleed Minimalist Editor (100% Canvas, Zero Chrome)
# ---------------------------------------------------------------------------
class EphemeralEditor(QPlainTextEdit):
    """Full-canvas distraction-free plain text editor with 24px viewport margins."""

    fileDropped = pyqtSignal(str)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)

        # Remove frame and scrollbars completely
        self.setFrameShape(QFrame.Shape.NoFrame)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)

        # Word wrap at widget boundary while keeping clean formatting
        self.setLineWrapMode(QPlainTextEdit.LineWrapMode.WidgetWidth)
        self.setWordWrapMode(QTextOption.WrapMode.WrapAtWordBoundaryOrAnywhere)

        # 24px internal padding around all 4 edges of the text canvas
        self.setViewportMargins(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        self.document().setDocumentMargin(0)

        # Crisp 2px cursor and 4-space tab stop
        self.setCursorWidth(2)
        self.setTabStopDistance(32.0)
        self.setAcceptDrops(True)

    def update_tab_stop(self) -> None:
        metrics = QFontMetricsF(self.font())
        self.setTabStopDistance(metrics.horizontalAdvance(" ") * 4.0)

    def insertFromMimeData(self, source: QMimeData) -> None:
        """Always paste strictly as plain text (or open a dropped .txt/.md file)."""
        if source.hasUrls():
            for url in source.urls():
                if url.isLocalFile():
                    local_path = url.toLocalFile()
                    if Path(local_path).is_file():
                        self.fileDropped.emit(local_path)
                        return
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


# ---------------------------------------------------------------------------
# Zero-Chrome Auto-Clicker-Style Views (Saved Notes Library & Shortcut Help)
# ---------------------------------------------------------------------------
class SavedNotesView(QWidget):
    """
    Zero-chrome Saved Notes Library rendered in the exact Auto-Clicker style:
    - 100% flat #141414 canvas, 24px margins, zero borders/buttons
    - Whisper-quiet #585858 labels and #e0e0e0 values
    - Keyboard driven: Type to search, Up/Down to navigate, Enter to load,
      Ctrl+P to pin, Ctrl+E to export .txt/.md, Del/Ctrl+D to delete, Esc to exit.
    """

    def __init__(
        self,
        repo: ScratchpadRepository,
        on_select_note,
        on_export_note,
        on_delete_note,
        on_close,
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self.repo = repo
        self._on_select_note = on_select_note
        self._on_export_note = on_export_note
        self._on_delete_note = on_delete_note
        self._on_close = on_close

        self._notes: list[SavedNote] = []
        self._selected_idx: int = 0
        self._active_note_id: int | None = None

        layout = QVBoxLayout(self)
        layout.setContentsMargins(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        layout.setSpacing(6)

        # Search row: "search         <flat entry>"
        search_row = QHBoxLayout()
        search_row.setContentsMargins(0, 0, 0, 6)
        search_row.setSpacing(0)

        self.lbl_search_key = QLabel(f"{'search':<15}")
        search_row.addWidget(self.lbl_search_key)

        self.search_input = QLineEdit()
        self.search_input.setFrame(False)
        self.search_input.setPlaceholderText("type to filter notes…")
        self.search_input.textChanged.connect(self._on_search_changed)
        self.search_input.installEventFilter(self)
        search_row.addWidget(self.search_input, 1)
        layout.addLayout(search_row)

        # Notes rows container
        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll.setFrameShape(QFrame.Shape.NoFrame)
        self.scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)

        self.rows_widget = QWidget()
        self.rows_layout = QVBoxLayout(self.rows_widget)
        self.rows_layout.setContentsMargins(0, 0, 0, 0)
        self.rows_layout.setSpacing(4)
        self.scroll.setWidget(self.rows_widget)
        layout.addWidget(self.scroll, 1)

        # Whisper-quiet footer hint in bottom-right (#585858)
        self.hint_label = QLabel(
            "↑↓ select · enter load · ctrl+p pin · ctrl+e export · del remove · esc back"
        )
        self.hint_label.setAlignment(
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        )
        layout.addWidget(self.hint_label)

    def open_view(self, active_note_id: int | None) -> None:
        self._active_note_id = active_note_id
        self.search_input.clear()
        self.refresh_list()
        self.search_input.setFocus()

    def _on_search_changed(self) -> None:
        self._selected_idx = 0
        self.refresh_list()

    def refresh_list(self) -> None:
        while self.rows_layout.count():
            item = self.rows_layout.takeAt(0)
            if item.widget():
                item.widget().deleteLater()

        self._notes = self.repo.list_notes(self.search_input.text())
        if not self._notes:
            self._selected_idx = 0
            empty_lbl = QLabel("no saved notes")
            empty_lbl.setObjectName("metaLabel")
            empty_lbl.setFont(self.search_input.font())
            self.rows_layout.addWidget(empty_lbl)
            self.rows_layout.addStretch()
            return

        self._selected_idx = max(0, min(self._selected_idx, len(self._notes) - 1))
        font = self.search_input.font()
        bold_font = QFont(font)
        bold_font.setBold(True)

        for idx, note in enumerate(self._notes):
            is_sel = idx == self._selected_idx
            row_lbl = QLabel()
            row_lbl.setCursor(Qt.CursorShape.PointingHandCursor)
            row_lbl.setFont(bold_font if is_sel else font)
            row_lbl.setObjectName("activeRow" if is_sel else "metaRow")

            marker = "› " if is_sel else "  "
            pin_tag = "* " if note.is_pinned else ""
            title_col = f"{marker}{pin_tag}{note.title[:22]:<24}"
            preview = " ".join(note.content.strip().splitlines()[:1]).strip() or "empty"
            if len(preview) > 36:
                preview = preview[:36] + "…"
            meta_str = f"{note.word_count}w · {format_timestamp(note.updated_at)}"
            row_lbl.setText(f"{title_col}  {preview:<38}  {meta_str}")

            row_lbl.mousePressEvent = lambda _e, n=note: self._on_select_note(n)  # type: ignore[method-assign]
            self.rows_layout.addWidget(row_lbl)

        self.rows_layout.addStretch()

    def apply_theme_and_font(self, palette: dict[str, str], font: QFont, meta_font: QFont) -> None:
        self.lbl_search_key.setFont(font)
        self.search_input.setFont(font)
        self.hint_label.setFont(meta_font)
        self.setStyleSheet(
            f"""
            QWidget {{
                background-color: {palette['canvas']};
                color: {palette['fg']};
                border: none;
            }}
            QLabel {{
                color: {palette['meta']};
            }}
            QLabel#activeRow {{
                color: {palette['fg']};
            }}
            QLabel#metaRow {{
                color: {palette['meta']};
            }}
            QLineEdit {{
                background-color: {palette['canvas']};
                color: {palette['fg']};
                selection-background-color: {palette['sel_bg']};
                selection-color: {palette['sel_fg']};
                border: none;
            }}
            """
        )
        self.refresh_list()

    def eventFilter(self, watched, event) -> bool:
        if watched is self.search_input and event.type() == QEvent.Type.KeyPress:
            key = event.key()
            mods = event.modifiers()

            if key == Qt.Key.Key_Down:
                if self._notes:
                    self._selected_idx = (self._selected_idx + 1) % len(self._notes)
                    self.refresh_list()
                return True
            if key == Qt.Key.Key_Up:
                if self._notes:
                    self._selected_idx = (self._selected_idx - 1) % len(self._notes)
                    self.refresh_list()
                return True
            if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                if self._notes:
                    self._on_select_note(self._notes[self._selected_idx])
                return True
            if key == Qt.Key.Key_Escape:
                self._on_close()
                return True
            if (mods & Qt.KeyboardModifier.ControlModifier) and key == Qt.Key.Key_P:
                if self._notes:
                    self.repo.toggle_pin(self._notes[self._selected_idx])
                    self.refresh_list()
                return True
            if (mods & Qt.KeyboardModifier.ControlModifier) and key == Qt.Key.Key_E:
                if self._notes:
                    self._on_export_note(self._notes[self._selected_idx])
                return True
            if key == Qt.Key.Key_Delete or (
                (mods & Qt.KeyboardModifier.ControlModifier) and key == Qt.Key.Key_D
            ):
                if self._notes:
                    self._on_delete_note(self._notes[self._selected_idx])
                    self.refresh_list()
                return True
        return super().eventFilter(watched, event)


class ShortcutsHelpView(QWidget):
    """
    Zero-chrome Keyboard Shortcuts reference rendered in the exact Auto-Clicker
    two-column key-value style (#585858 key on left, #e0e0e0 shortcut on right).
    """

    SHORTCUT_ROWS = [
        ("new pad", "ctrl+n"),
        ("open .txt/.md", "ctrl+o"),
        ("save note/file", "ctrl+s"),
        ("save as file", "ctrl+shift+s"),
        ("saved library", "ctrl+l"),
        ("find & replace", "ctrl+f / ctrl+h"),
        ("wipe buffer", "ctrl+w / ctrl+shift+bksp"),
        ("copy all", "ctrl+shift+c"),
        ("timestamp", "ctrl+t"),
        ("checklist", "ctrl+shift+l"),
        ("bullet", "ctrl+shift+b"),
        ("font size", "ctrl+= / ctrl+- / ctrl+0"),
        ("font family", "ctrl+m (mono / sans)"),
        ("surface theme", "ctrl+shift+d (dark / light)"),
        ("cycle stats", "ctrl+i / click counter"),
        ("frameless", "f11 / ctrl+shift+f"),
        ("quit / back", "esc / ctrl+q"),
    ]

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        layout.setSpacing(3)

        self.key_labels: list[QLabel] = []
        self.val_labels: list[QLabel] = []

        for key_text, val_text in self.SHORTCUT_ROWS:
            row = QHBoxLayout()
            row.setContentsMargins(0, 0, 0, 0)
            row.setSpacing(0)
            k_lbl = QLabel(f"{key_text:<18}")
            v_lbl = QLabel(val_text)
            row.addWidget(k_lbl)
            row.addWidget(v_lbl, 1)
            layout.addLayout(row)
            self.key_labels.append(k_lbl)
            self.val_labels.append(v_lbl)

        layout.addStretch()

        self.hint_label = QLabel("shortcuts · press esc or f1 to return")
        self.hint_label.setAlignment(
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        )
        layout.addWidget(self.hint_label)

    def apply_theme_and_font(self, palette: dict[str, str], font: QFont, meta_font: QFont) -> None:
        self.setStyleSheet(f"background-color: {palette['canvas']}; border: none;")
        for k_lbl in self.key_labels:
            k_lbl.setFont(font)
            k_lbl.setStyleSheet(f"color: {palette['meta']}; background: transparent;")
        for v_lbl in self.val_labels:
            v_lbl.setFont(font)
            v_lbl.setStyleSheet(f"color: {palette['fg']}; background: transparent;")
        self.hint_label.setFont(meta_font)
        self.hint_label.setStyleSheet(f"color: {palette['meta']}; background: transparent;")


# ---------------------------------------------------------------------------
# Main Scratchpad Window (100% Writing Space, Zero Permanent Chrome)
# ---------------------------------------------------------------------------
class EphemeralPadWindow(QMainWindow):
    """
    Main window hosting the 100% full-bleed editor and subtle bottom-right metadata.
    All buttons and bars are replaced with direct keyboard shortcuts and minimal
    zero-chrome inline prompts that vanish when not in use.
    """

    def __init__(self, initial_file: str | None = None, start_frameless: bool = False) -> None:
        super().__init__()
        load_bundled_fonts()
        self.repo = ScratchpadRepository()

        # Load persisted typography & theme preferences
        saved_size = self.repo.get_pref("font_size", str(DEFAULT_FONT_SIZE))
        try:
            self._font_size = max(MIN_FONT_SIZE, min(MAX_FONT_SIZE, int(saved_size)))
        except ValueError:
            self._font_size = DEFAULT_FONT_SIZE

        self._is_monospace = self.repo.get_pref("is_monospace", "1") != "0"
        self._is_dark = self.repo.get_pref("is_dark", "1") != "0"
        self._frameless = start_frameless
        self._stat_display_mode = 0

        # Active note / file state
        self._active_note_id: int | None = None
        self._active_note_title: str = "Untitled"
        self._active_file_path: Path | None = None
        self._is_modified: bool = False
        self._suppress_modified: bool = False
        self._last_deleted_note: SavedNote | None = None
        self._transient_status: str = ""

        # Find & Replace state
        self._show_find_bar: bool = False
        self._find_matches: list[tuple[int, int]] = []
        self._current_match_idx: int = -1

        # Inline Save Note prompt state
        self._show_save_prompt: bool = False

        self.setWindowTitle("Scratchpad")
        self.setWindowIcon(load_app_icon())
        self.resize(760, 520)
        self.setMinimumSize(320, 200)

        if self._frameless:
            self.setWindowFlags(
                Qt.WindowType.Window | Qt.WindowType.FramelessWindowHint
            )

        self._build_ui()
        self._apply_theme_and_typography()
        self._setup_shortcuts()

        # Install event filters for Alt+drag and frameless edge drag/resize
        self.installEventFilter(self)
        self.editor.installEventFilter(self)
        self.editor.viewport().installEventFilter(self)

        # Load CLI file if passed, else restore draft if any
        if initial_file and Path(initial_file).is_file():
            self.load_file_from_path(Path(initial_file))
        else:
            draft_text = self.repo.get_pref("draft_text", "")
            draft_title = self.repo.get_pref("draft_title", "Untitled")
            draft_id_str = self.repo.get_pref("draft_note_id", "")
            if draft_text:
                self._suppress_modified = True
                self.editor.setPlainText(draft_text)
                self._suppress_modified = False
                self._active_note_title = draft_title or "Untitled"
                self._active_note_id = int(draft_id_str) if draft_id_str.isdigit() else None
                self._is_modified = self.repo.get_pref("draft_modified", "0") == "1"

        self._update_word_count()
        self.editor.setFocus(Qt.FocusReason.OtherFocusReason)

    # -----------------------------------------------------------------------
    # Zero-Chrome UI Construction
    # -----------------------------------------------------------------------
    def _build_ui(self) -> None:
        central = QWidget(self)
        layout = QVBoxLayout(central)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        # Stacked widget switching between:
        #   0: 100% Full-bleed Editor
        #   1: Zero-chrome Saved Notes Library (Ctrl+L)
        #   2: Zero-chrome Shortcuts Reference (F1 / Ctrl+/)
        self.stack = QStackedWidget(central)

        # Page 0: Editor container
        self.editor_page = QWidget()
        ed_layout = QVBoxLayout(self.editor_page)
        ed_layout.setContentsMargins(0, 0, 0, 0)
        ed_layout.setSpacing(0)

        self.editor = EphemeralEditor(self.editor_page)
        self.editor.textChanged.connect(self._on_editor_text_changed)
        self.editor.fileDropped.connect(lambda p: self.load_file_from_path(Path(p)))
        ed_layout.addWidget(self.editor, 1)

        # Minimal zero-chrome inline Find & Replace strip (hidden unless Ctrl+F/Ctrl+H pressed)
        self.find_bar = QWidget(self.editor_page)
        self.find_bar.setVisible(False)
        find_layout = QHBoxLayout(self.find_bar)
        find_layout.setContentsMargins(PADDING_PX, 4, PADDING_PX, 10)
        find_layout.setSpacing(8)

        self.lbl_find_key = QLabel("find")
        find_layout.addWidget(self.lbl_find_key)

        self.find_input = QLineEdit(self.find_bar)
        self.find_input.setFrame(False)
        self.find_input.setPlaceholderText("text…")
        self.find_input.textChanged.connect(self._recompute_find_matches)
        self.find_input.installEventFilter(self)
        find_layout.addWidget(self.find_input, 1)

        self.match_count_lbl = QLabel("0/0", self.find_bar)
        find_layout.addWidget(self.match_count_lbl)

        self.lbl_replace_key = QLabel("  replace")
        find_layout.addWidget(self.lbl_replace_key)

        self.replace_input = QLineEdit(self.find_bar)
        self.replace_input.setFrame(False)
        self.replace_input.setPlaceholderText("with… (ctrl+enter / ctrl+alt+enter all)")
        self.replace_input.installEventFilter(self)
        find_layout.addWidget(self.replace_input, 1)

        ed_layout.addWidget(self.find_bar)

        # Minimal zero-chrome inline Save Note title strip (hidden unless Ctrl+S on untitled note)
        self.save_bar = QWidget(self.editor_page)
        self.save_bar.setVisible(False)
        save_layout = QHBoxLayout(self.save_bar)
        save_layout.setContentsMargins(PADDING_PX, 4, PADDING_PX, 10)
        save_layout.setSpacing(8)

        self.lbl_save_key = QLabel("save as")
        save_layout.addWidget(self.lbl_save_key)

        self.save_title_input = QLineEdit(self.save_bar)
        self.save_title_input.setFrame(False)
        self.save_title_input.setPlaceholderText(
            "note title (leave blank to auto-name · enter to save · esc cancel)"
        )
        self.save_title_input.installEventFilter(self)
        save_layout.addWidget(self.save_title_input, 1)

        ed_layout.addWidget(self.save_bar)

        # Whisper-quiet bottom-right metadata counter (#585858)
        self.word_count_label = QLabel("0 words", self.editor)
        self.word_count_label.setCursor(Qt.CursorShape.PointingHandCursor)
        self.word_count_label.setAlignment(
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        )
        self.word_count_label.mousePressEvent = lambda _e: self.cycle_stat_display_mode()  # type: ignore[method-assign]

        self.stack.addWidget(self.editor_page)

        # Page 1: Saved Notes Library (Ctrl+L)
        self.library_view = SavedNotesView(
            repo=self.repo,
            on_select_note=self._on_note_selected_from_library,
            on_export_note=self.export_saved_note_to_file,
            on_delete_note=self.delete_saved_note,
            on_close=self._return_to_editor,
            parent=self.stack,
        )
        self.stack.addWidget(self.library_view)

        # Page 2: Shortcuts Help View (F1 / Ctrl+/)
        self.help_view = ShortcutsHelpView(self.stack)
        self.stack.addWidget(self.help_view)

        layout.addWidget(self.stack)
        self.setCentralWidget(central)

        # Transient status timer (clears status notifications after 2.2s)
        self._status_timer = QTimer(self)
        self._status_timer.setSingleShot(True)
        self._status_timer.timeout.connect(self._clear_transient_status)

    # -----------------------------------------------------------------------
    # Palette & Typography (Matte Dark #141414 / Soft Light #f2f2f0)
    # -----------------------------------------------------------------------
    @property
    def palette_dict(self) -> dict[str, str]:
        return PALETTE_DARK if self._is_dark else PALETTE_LIGHT

    def _apply_theme_and_typography(self) -> None:
        p = self.palette_dict
        self._font = select_font(self._font_size, is_monospace=self._is_monospace)
        self._meta_font = QFont(self._font)
        self._meta_font.setPointSize(max(9, min(11, self._font_size - 2)))

        self.editor.setFont(self._font)
        self.editor.update_tab_stop()
        self.word_count_label.setFont(self._meta_font)

        for w in (
            self.lbl_find_key,
            self.find_input,
            self.match_count_lbl,
            self.lbl_replace_key,
            self.replace_input,
            self.lbl_save_key,
            self.save_title_input,
        ):
            w.setFont(self._meta_font)

        self.setStyleSheet(
            f"""
            QMainWindow, QWidget {{
                background-color: {p['canvas']};
                color: {p['fg']};
                border: none;
            }}
            QPlainTextEdit {{
                background-color: {p['canvas']};
                color: {p['fg']};
                selection-background-color: {p['sel_bg']};
                selection-color: {p['sel_fg']};
                border: none;
            }}
            QLineEdit {{
                background-color: {p['canvas']};
                color: {p['fg']};
                selection-background-color: {p['sel_bg']};
                selection-color: {p['sel_fg']};
                border: none;
            }}
            QLabel {{
                background: transparent;
                color: {p['meta']};
                padding: 2px 4px;
            }}
            """
        )

        self.library_view.apply_theme_and_font(p, self._font, self._meta_font)
        self.help_view.apply_theme_and_font(p, self._font, self._meta_font)

        self.repo.set_pref("font_size", str(self._font_size))
        self.repo.set_pref("is_monospace", "1" if self._is_monospace else "0")
        self.repo.set_pref("is_dark", "1" if self._is_dark else "0")

        self._update_word_count()

    def adjust_font_size(self, delta: int) -> None:
        new_size = max(MIN_FONT_SIZE, min(MAX_FONT_SIZE, self._font_size + delta))
        if new_size != self._font_size:
            self._font_size = new_size
            self._apply_theme_and_typography()

    def set_font_size(self, size: int) -> None:
        self.adjust_font_size(size - self._font_size)

    def set_monospace(self, is_mono: bool) -> None:
        if is_mono != self._is_monospace:
            self._is_monospace = is_mono
            self._apply_theme_and_typography()
            mode_name = "monospace" if self._is_monospace else "sans-serif"
            self.show_status(f"font: {mode_name}")

    def toggle_monospace(self) -> None:
        self.set_monospace(not self._is_monospace)

    def reset_typography(self) -> None:
        self._font_size = DEFAULT_FONT_SIZE
        self._is_monospace = True
        self._apply_theme_and_typography()
        self.show_status(f"reset {DEFAULT_FONT_SIZE}pt")

    def toggle_surface_theme(self) -> None:
        self._is_dark = not self._is_dark
        self._apply_theme_and_typography()
        self.show_status("matte dark" if self._is_dark else "soft light")

    def toggle_zen_mode(self) -> None:
        """Closes any open inline bars for pure full-canvas focus."""
        if self._show_find_bar:
            self.toggle_find_bar()
        if self._show_save_prompt:
            self._close_save_prompt()
        if self.stack.currentIndex() != 0:
            self._return_to_editor()

    # -----------------------------------------------------------------------
    # File Open & Save (.txt / .md) + Saved Notes Library Operations
    # -----------------------------------------------------------------------
    def open_file_dialog(self) -> None:
        start_dir = (
            str(self._active_file_path.parent)
            if self._active_file_path is not None
            else str(Path.home())
        )
        file_path, _ = QFileDialog.getOpenFileName(
            self,
            "Open Text or Markdown File",
            start_dir,
            "Text & Markdown Files (*.txt *.md *.markdown);;All Files (*)",
        )
        if file_path:
            self.load_file_from_path(Path(file_path))

    def load_file_from_path(self, path: Path) -> None:
        try:
            content = path.read_text(encoding="utf-8", errors="replace")
        except Exception:
            self.show_status(f"error opening {path.name}")
            return

        self._suppress_modified = True
        self.editor.setPlainText(content)
        self._suppress_modified = False

        self._active_file_path = path
        self._active_note_id = None
        self._active_note_title = path.stem or path.name
        self._is_modified = False
        self._save_draft_state()
        self._return_to_editor()
        self.show_status(f"opened {path.name}")

    def save_to_file_dialog(self, suggested_title: str = "") -> None:
        title = (
            suggested_title.strip()
            or (self._active_note_title if self._active_note_title != "Untitled" else "")
            or auto_generate_title(self.editor.toPlainText())
        )
        safe_stem = (
            "".join(c if c.isalnum() or c in "-_ ." else "_" for c in title).strip()
            or "Scratchpad"
        )

        if self._active_file_path is not None:
            default_path = str(self._active_file_path)
        else:
            if not safe_stem.lower().endswith((".txt", ".md", ".markdown")):
                safe_stem += ".txt"
            default_path = str(Path.home() / safe_stem)

        file_path, selected_filter = QFileDialog.getSaveFileName(
            self,
            "Save Scratchpad to File (.txt / .md)",
            default_path,
            "Text Files (*.txt);;Markdown Files (*.md);;All Files (*)",
        )
        if not file_path:
            return

        target = Path(file_path)
        if not target.suffix:
            target = target.with_suffix(
                ".md" if "Markdown" in selected_filter else ".txt"
            )

        try:
            target.write_text(self.editor.toPlainText(), encoding="utf-8")
        except Exception:
            self.show_status("failed to save file")
            return

        self._active_file_path = target
        self._active_note_title = target.stem or target.name
        self._is_modified = False
        self._save_draft_state()
        self.show_status(f"saved {target.name}")

    def export_saved_note_to_file(self, note: SavedNote) -> None:
        safe_stem = (
            "".join(c if c.isalnum() or c in "-_ ." else "_" for c in note.title).strip()
            or "Scratchpad"
        )
        if not safe_stem.lower().endswith((".txt", ".md")):
            safe_stem += ".txt"
        default_path = str(Path.home() / safe_stem)
        file_path, selected_filter = QFileDialog.getSaveFileName(
            self,
            "Export Note to File (.txt / .md)",
            default_path,
            "Text Files (*.txt);;Markdown Files (*.md);;All Files (*)",
        )
        if not file_path:
            return
        target = Path(file_path)
        if not target.suffix:
            target = target.with_suffix(
                ".md" if "Markdown" in selected_filter else ".txt"
            )
        try:
            target.write_text(note.content, encoding="utf-8")
            self.show_status(f"exported {target.name}")
        except Exception:
            self.show_status("export failed")

    def _handle_ctrl_s(self) -> None:
        """
        Save shortcut (Ctrl+S):
        - If an external .txt/.md file is open, writes directly to that file.
        - If an existing saved library note is active, updates it immediately.
        - Otherwise opens the minimal zero-chrome inline 'save as' title prompt.
        """
        if self._active_file_path is not None:
            try:
                self._active_file_path.write_text(
                    self.editor.toPlainText(), encoding="utf-8"
                )
                self._is_modified = False
                self._save_draft_state()
                self.show_status(f"saved {self._active_file_path.name}")
                return
            except Exception:
                self.show_status("failed to save file")
                return

        if self._active_note_id is not None:
            self.save_current_note(self._active_note_title)
            return

        self._open_save_prompt()

    def _open_save_prompt(self) -> None:
        if self._show_find_bar:
            self.toggle_find_bar()
        self._show_save_prompt = True
        self.save_bar.setVisible(True)
        if self._active_note_title and self._active_note_title != "Untitled":
            self.save_title_input.setText(self._active_note_title)
            self.save_title_input.selectAll()
        else:
            self.save_title_input.clear()
        self.save_title_input.setFocus()
        self._position_word_count()

    def _close_save_prompt(self) -> None:
        self._show_save_prompt = False
        self.save_bar.setVisible(False)
        self.editor.setFocus()
        self._position_word_count()

    def save_current_note(self, custom_title: str = "") -> None:
        content = self.editor.toPlainText()
        final_title = (
            custom_title.strip()
            or (self._active_note_title if self._active_note_title != "Untitled" else "")
            or auto_generate_title(content)
        )
        note_id = self.repo.upsert_note(
            title=final_title,
            content=content,
            note_id=self._active_note_id,
        )
        self._active_note_id = note_id
        self._active_note_title = final_title
        self._is_modified = False
        self._save_draft_state()
        if self._show_save_prompt:
            self._close_save_prompt()
        self.show_status(f"saved {final_title.lower()}")

    def toggle_saved_notes_view(self) -> None:
        if self.stack.currentIndex() == 1:
            self._return_to_editor()
        else:
            self.library_view.open_view(self._active_note_id)
            self.stack.setCurrentIndex(1)

    def toggle_help_view(self) -> None:
        if self.stack.currentIndex() == 2:
            self._return_to_editor()
        else:
            self.stack.setCurrentIndex(2)

    def _return_to_editor(self) -> None:
        self.stack.setCurrentIndex(0)
        self.editor.setFocus()
        self._update_word_count()

    def _on_note_selected_from_library(self, note: SavedNote) -> None:
        self.load_saved_note(note)
        self._return_to_editor()

    def load_saved_note(self, note: SavedNote) -> None:
        self._suppress_modified = True
        self.editor.setPlainText(note.content)
        self._suppress_modified = False

        self._active_note_id = note.id
        self._active_note_title = note.title
        self._active_file_path = None
        self._is_modified = False
        self._save_draft_state()
        self.show_status(f"loaded {note.title.lower()}")

    def delete_saved_note(self, note: SavedNote) -> None:
        self._last_deleted_note = note
        self.repo.delete_note(note.id)
        if self._active_note_id == note.id:
            self._active_note_id = None
            self._active_note_title = "Untitled"
            self._is_modified = self.editor.toPlainText().strip() != ""
        self._save_draft_state()
        self.show_status("deleted · ctrl+z undo")

    def undo_delete_note(self) -> None:
        if self._last_deleted_note is not None:
            restored = self._last_deleted_note
            self._last_deleted_note = None
            self.repo.restore_note(restored)
            if self.stack.currentIndex() == 1:
                self.library_view.refresh_list()
            self.show_status(f"restored {restored.title.lower()}")

    def new_scratchpad(self) -> None:
        self._suppress_modified = True
        self.editor.clear()
        self.editor.document().clearUndoRedoStacks()
        self._suppress_modified = False

        self._active_note_id = None
        self._active_note_title = "Untitled"
        self._active_file_path = None
        self._is_modified = False
        self._save_draft_state()
        self._return_to_editor()
        self.show_status("new pad")

    def wipe_scratchpad_with_undo(self) -> None:
        cursor = self.editor.textCursor()
        cursor.select(QTextCursor.SelectionType.Document)
        cursor.removeSelectedText()
        self._active_note_id = None
        self._active_note_title = "Untitled"
        self._active_file_path = None
        self._is_modified = False
        self._save_draft_state()
        self.show_status("cleared")

    def copy_all_text(self) -> None:
        QApplication.clipboard().setText(self.editor.toPlainText())
        self.show_status("copied all")

    def insert_snippet(self, snippet: str) -> None:
        self.editor.insertPlainText(snippet)
        self.editor.setFocus()

    def insert_timestamp(self) -> None:
        stamp = datetime.datetime.now().strftime("[%Y-%m-%d %H:%M] ")
        self.insert_snippet(stamp)

    # -----------------------------------------------------------------------
    # Minimal Inline Find & Replace (Ctrl+F / Ctrl+H)
    # -----------------------------------------------------------------------
    def toggle_find_bar(self) -> None:
        if self._show_save_prompt:
            self._close_save_prompt()
        self._show_find_bar = not self._show_find_bar
        self.find_bar.setVisible(self._show_find_bar)
        if self._show_find_bar:
            sel = self.editor.textCursor().selectedText()
            if sel and "\u2029" not in sel:
                self.find_input.setText(sel)
            self.find_input.setFocus()
            self.find_input.selectAll()
            self._recompute_find_matches()
        else:
            self.editor.setExtraSelections([])
            self.editor.setFocus()
        self._position_word_count()

    def _recompute_find_matches(self) -> None:
        query = self.find_input.text()
        text = self.editor.toPlainText()
        self._find_matches.clear()

        if query:
            q_low = query.lower()
            t_low = text.lower()
            start = 0
            q_len = len(query)
            while True:
                idx = t_low.find(q_low, start)
                if idx == -1:
                    break
                self._find_matches.append((idx, idx + q_len))
                start = idx + max(1, q_len)

        if self._find_matches:
            if self._current_match_idx < 0 or self._current_match_idx >= len(self._find_matches):
                self._current_match_idx = 0
            self.match_count_lbl.setText(
                f"{self._current_match_idx + 1}/{len(self._find_matches)}"
            )
            self._highlight_find_matches(scroll_to_active=True)
        else:
            self._current_match_idx = -1
            self.match_count_lbl.setText("0/0")
            self.editor.setExtraSelections([])

    def _highlight_find_matches(self, scroll_to_active: bool = True) -> None:
        p = self.palette_dict
        selections: list[QTextEdit.ExtraSelection] = []
        doc = self.editor.document()

        for i, (start, end) in enumerate(self._find_matches):
            sel = QTextEdit.ExtraSelection()
            cur = QTextCursor(doc)
            cur.setPosition(start)
            cur.setPosition(end, QTextCursor.MoveMode.KeepAnchor)
            fmt = QTextCharFormat()
            if i == self._current_match_idx:
                fmt.setBackground(QColor(p["sel_bg"]))
                fmt.setForeground(QColor(p["sel_fg"]))
                if scroll_to_active:
                    self.editor.setTextCursor(cur)
                    self.editor.ensureCursorVisible()
            else:
                fmt.setBackground(QColor(p["match_bg"]))
                fmt.setForeground(QColor(p["fg"]))
            sel.cursor = cur
            sel.format = fmt
            selections.append(sel)

        self.editor.setExtraSelections(selections)

    def find_next(self) -> None:
        if not self._find_matches:
            return
        self._current_match_idx = (self._current_match_idx + 1) % len(self._find_matches)
        self.match_count_lbl.setText(
            f"{self._current_match_idx + 1}/{len(self._find_matches)}"
        )
        self._highlight_find_matches(scroll_to_active=True)

    def find_previous(self) -> None:
        if not self._find_matches:
            return
        self._current_match_idx = (self._current_match_idx - 1) % len(self._find_matches)
        self.match_count_lbl.setText(
            f"{self._current_match_idx + 1}/{len(self._find_matches)}"
        )
        self._highlight_find_matches(scroll_to_active=True)

    def replace_current_match(self) -> None:
        if not self._find_matches or self._current_match_idx < 0:
            return
        start, end = self._find_matches[self._current_match_idx]
        replacement = self.replace_input.text()
        cur = self.editor.textCursor()
        cur.beginEditBlock()
        cur.setPosition(start)
        cur.setPosition(end, QTextCursor.MoveMode.KeepAnchor)
        cur.insertText(replacement)
        cur.endEditBlock()
        self._recompute_find_matches()

    def replace_all_matches(self) -> None:
        if not self._find_matches:
            return
        replacement = self.replace_input.text()
        cur = self.editor.textCursor()
        cur.beginEditBlock()
        for start, end in reversed(self._find_matches):
            cur.setPosition(start)
            cur.setPosition(end, QTextCursor.MoveMode.KeepAnchor)
            cur.insertText(replacement)
        cur.endEditBlock()
        self._recompute_find_matches()
        self.show_status("replaced all")

    # -----------------------------------------------------------------------
    # Whisper-Quiet Bottom-Right Metadata & Status
    # -----------------------------------------------------------------------
    def _on_editor_text_changed(self) -> None:
        if not self._suppress_modified:
            self._is_modified = True
            self._save_draft_state()
        if self._show_find_bar:
            self._recompute_find_matches()
        self._update_word_count()

    def _save_draft_state(self) -> None:
        self.repo.set_pref("draft_text", self.editor.toPlainText())
        self.repo.set_pref("draft_title", self._active_note_title)
        self.repo.set_pref(
            "draft_note_id",
            str(self._active_note_id) if self._active_note_id is not None else "",
        )
        self.repo.set_pref("draft_modified", "1" if self._is_modified else "0")

    def show_status(self, msg: str) -> None:
        self._transient_status = msg
        self._update_word_count()
        self._status_timer.start(2200)

    def _clear_transient_status(self) -> None:
        self._transient_status = ""
        self._update_word_count()

    def cycle_stat_display_mode(self) -> None:
        self._stat_display_mode = (self._stat_display_mode + 1) % 3
        self._update_word_count()

    def _update_word_count(self) -> None:
        text = self.editor.toPlainText()
        words = len(text.split()) if text.strip() else 0
        chars = len(text)
        lines = 1 if not text else text.count("\n") + 1

        if self._stat_display_mode == 0:
            stats_part = "1 word" if words == 1 else f"{words} words"
        elif self._stat_display_mode == 1:
            stats_part = f"{words} words · {chars} chars"
        else:
            stats_part = f"{words}w · {chars}c · {lines}L · {self._font_size}pt"

        parts: list[str] = []
        if self._transient_status:
            parts.append(self._transient_status)
        elif self._active_file_path is not None:
            mod = "*" if self._is_modified else ""
            parts.append(f"{self._active_file_path.name}{mod}")
        elif self._active_note_title and self._active_note_title != "Untitled":
            mod = "*" if self._is_modified else ""
            parts.append(f"{self._active_note_title.lower()}{mod}")

        parts.append(stats_part)
        self.word_count_label.setText(" · ".join(parts))

        win_title = "Scratchpad"
        if self._active_file_path is not None:
            win_title = f"{self._active_file_path.name} — Scratchpad"
        elif self._active_note_title and self._active_note_title != "Untitled":
            win_title = f"{self._active_note_title} — Scratchpad"
        self.setWindowTitle(win_title)

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

    # -----------------------------------------------------------------------
    # Keyboard Shortcuts & Frameless Window Management
    # -----------------------------------------------------------------------
    def _bind_shortcuts(self, sequences: list[QKeySequence], callback) -> None:
        seen: set[str] = set()
        for seq in sequences:
            key_str = seq.toString()
            if key_str and key_str not in seen:
                seen.add(key_str)
                shortcut = QShortcut(seq, self)
                shortcut.setContext(Qt.ShortcutContext.ApplicationShortcut)
                shortcut.activated.connect(callback)

    def _setup_shortcuts(self) -> None:
        # Escape & Ctrl+Q: close active overlay/bar first, else instant quit
        self._bind_shortcuts([QKeySequence(Qt.Key.Key_Escape)], self._handle_escape)
        self._bind_shortcuts([QKeySequence("Ctrl+Q")], self.instant_quit)

        # File & Saved Notes Library shortcuts
        self._bind_shortcuts([QKeySequence("Ctrl+N")], self.new_scratchpad)
        self._bind_shortcuts([QKeySequence("Ctrl+O")], self.open_file_dialog)
        self._bind_shortcuts([QKeySequence("Ctrl+S")], self._handle_ctrl_s)
        self._bind_shortcuts(
            [QKeySequence("Ctrl+Shift+S")], lambda: self.save_to_file_dialog()
        )
        self._bind_shortcuts(
            [QKeySequence("Ctrl+L"), QKeySequence("Ctrl+Shift+O")],
            self.toggle_saved_notes_view,
        )

        # Clear / Wipe buffer & Copy all
        self._bind_shortcuts(
            [QKeySequence("Ctrl+W"), QKeySequence("Ctrl+Shift+Backspace")],
            self.wipe_scratchpad_with_undo,
        )
        self._bind_shortcuts([QKeySequence("Ctrl+Shift+C")], self.copy_all_text)

        # Quick insertions (Timestamp, Checklist, Bullet)
        self._bind_shortcuts([QKeySequence("Ctrl+T")], self.insert_timestamp)
        self._bind_shortcuts(
            [QKeySequence("Ctrl+Shift+L")], lambda: self.insert_snippet("- [ ] ")
        )
        self._bind_shortcuts(
            [QKeySequence("Ctrl+Shift+B")], lambda: self.insert_snippet("• ")
        )

        # Find & Replace
        self._bind_shortcuts(
            [QKeySequence("Ctrl+F"), QKeySequence("Ctrl+H")], self.toggle_find_bar
        )

        # Typography scaling, font family toggle, surface theme toggle, stats cycle
        self._bind_shortcuts(
            [
                QKeySequence("Ctrl+="),
                QKeySequence("Ctrl++"),
                QKeySequence(QKeySequence.StandardKey.ZoomIn),
            ],
            lambda: self.adjust_font_size(1),
        )
        self._bind_shortcuts(
            [
                QKeySequence("Ctrl+-"),
                QKeySequence(QKeySequence.StandardKey.ZoomOut),
            ],
            lambda: self.adjust_font_size(-1),
        )
        self._bind_shortcuts([QKeySequence("Ctrl+0")], self.reset_typography)
        self._bind_shortcuts([QKeySequence("Ctrl+M")], self.toggle_monospace)
        self._bind_shortcuts([QKeySequence("Ctrl+Shift+D")], self.toggle_surface_theme)
        self._bind_shortcuts([QKeySequence("Ctrl+I")], self.cycle_stat_display_mode)

        # Frameless window mode (F11 / Ctrl+Shift+F)
        self._bind_shortcuts(
            [QKeySequence(Qt.Key.Key_F11), QKeySequence("Ctrl+Shift+F")],
            self.toggle_frameless,
        )

        # Shortcuts reference overlay (F1 / Ctrl+/)
        self._bind_shortcuts(
            [QKeySequence(Qt.Key.Key_F1), QKeySequence("Ctrl+/")],
            self.toggle_help_view,
        )

    def _handle_escape(self) -> None:
        if self.stack.currentIndex() != 0:
            self._return_to_editor()
            return
        if self._show_save_prompt:
            self._close_save_prompt()
            return
        if self._show_find_bar:
            self.toggle_find_bar()
            return
        self.instant_quit()

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

    def _detect_resize_edges(self, pos: QPoint) -> Qt.Edge:
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
        # Handle inline Find & Replace and Save prompt keyboard actions
        if event.type() == QEvent.Type.KeyPress:
            key = event.key()
            mods = event.modifiers()

            if watched is self.find_input:
                if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                    if mods & Qt.KeyboardModifier.ShiftModifier:
                        self.find_previous()
                    elif mods & Qt.KeyboardModifier.ControlModifier:
                        self.replace_current_match()
                    else:
                        self.find_next()
                    return True
                if key == Qt.Key.Key_Escape:
                    self.toggle_find_bar()
                    return True

            if watched is self.replace_input:
                if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                    if (mods & Qt.KeyboardModifier.ControlModifier) and (
                        mods & Qt.KeyboardModifier.AltModifier
                    ):
                        self.replace_all_matches()
                    else:
                        self.replace_current_match()
                    return True
                if key == Qt.Key.Key_Escape:
                    self.toggle_find_bar()
                    return True

            if watched is self.save_title_input:
                if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                    self.save_current_note(self.save_title_input.text())
                    return True
                if key == Qt.Key.Key_Escape:
                    self._close_save_prompt()
                    return True

            # Allow Ctrl+Z to undo note deletion when inside the Saved Notes Library
            if (
                self.stack.currentIndex() == 1
                and (mods & Qt.KeyboardModifier.ControlModifier)
                and key == Qt.Key.Key_Z
                and self._last_deleted_note is not None
            ):
                self.undo_delete_note()
                return True

        # Support Alt+LeftDrag to move, and border dragging to move/resize when frameless
        if event.type() == QEvent.Type.MouseButtonPress:
            if event.button() == Qt.MouseButton.LeftButton:
                win_handle = self.windowHandle()
                if win_handle is not None:
                    if event.modifiers() & Qt.KeyboardModifier.AltModifier:
                        win_handle.startSystemMove()
                        return True

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
        doc = self.editor.document()
        doc.clearUndoRedoStacks()
        doc.clear()
        self.editor.clear()
        gc.collect()

    def instant_quit(self) -> None:
        self._save_draft_state()
        self._wipe_memory()
        self.close()
        QApplication.quit()

    def closeEvent(self, event) -> None:
        self._save_draft_state()
        self._wipe_memory()
        event.accept()


def main() -> int:
    QApplication.setDesktopSettingsAware(True)
    app = QApplication(sys.argv)
    app.setApplicationName("ephemeral_pad")
    app.setWindowIcon(load_app_icon())
    app.setCursorFlashTime(1000)

    def commit_data_noop(manager) -> None:
        manager.cancel()

    app.commitDataRequest.connect(commit_data_noop)
    app.saveStateRequest.connect(lambda manager: None)

    start_frameless = "--frameless" in sys.argv or "-f" in sys.argv
    file_args = [
        arg for arg in sys.argv[1:] if not arg.startswith("-") and Path(arg).exists()
    ]
    initial_file = file_args[0] if file_args else None

    window = EphemeralPadWindow(
        initial_file=initial_file, start_frameless=start_frameless
    )
    window.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
