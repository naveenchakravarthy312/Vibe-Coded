#!/usr/bin/env python3
"""
VibeWatch Native PyQt6 Dashboard (`dashboard.py`)
Zero-chrome KDE Plasma 6 desktop viewer reading directly from `~/.local/share/vibewatch/activity.db`.

Design System (`DESKTOP-STYLE.md`):
- Dark Mode (Default): canvas #141414, card #1B1B1D, bar_bg #242428, border #2C2C30,
  fg #E0E0E0, meta #585858, accent_primary #88C0D0, accent_secondary #826cb6, success #98C379.
- Light Mode (Ctrl+Shift+D): canvas #f2f2f0, card #ffffff, bar_bg #e4e4e2, border #d4d4d0,
  fg #1e1e1e, meta #888888, accent_primary #2563EB.
- Typography: JetBrains Mono for metrics/telemetry, Plus Jakarta Sans / Inter / Roboto for labels.
- Dynamic Zoom: Ctrl+= / Ctrl+- / Ctrl+0 (8pt–48pt).
"""

from __future__ import annotations

import argparse
import sys
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

from vibewatch_core import (
    DEFAULT_CATEGORIES_PATH,
    DEFAULT_DB_PATH,
    ActivityDatabase,
    AnalyticsSnapshot,
    AppBreakdown,
    CategoryBreakdown,
    CategoryEngine,
    TimelineSegment,
    compute_analytics,
    date_range_bounds,
    ensure_venv,
    format_duration,
    read_daemon_status,
)

# Self-healing .venv check before PyQt6 import
ensure_venv(Path(__file__))

from PyQt6.QtCore import QEvent, QPoint, QRect, QRectF, QSize, Qt, QTimer, pyqtSignal
from PyQt6.QtGui import (
    QColor,
    QCursor,
    QFont,
    QKeyEvent,
    QKeySequence,
    QMouseEvent,
    QPainter,
    QPainterPath,
    QPen,
    QShortcut,
    QWheelEvent,
)
from PyQt6.QtWidgets import (
    QApplication,
    QDialog,
    QFrame,
    QHBoxLayout,
    QLabel,
    QMainWindow,
    QPlainTextEdit,
    QPushButton,
    QScrollArea,
    QSizePolicy,
    QToolTip,
    QVBoxLayout,
    QWidget,
)


THEMES: Dict[str, Dict[str, str]] = {
    "dark": {
        "canvas": "#141414",
        "card": "#1B1B1D",
        "bar_bg": "#242428",
        "border": "#2C2C30",
        "fg": "#E0E0E0",
        "meta": "#585858",
        "meta_soft": "#8A8A8E",
        "accent_primary": "#88C0D0",
        "accent_secondary": "#826cb6",
        "success": "#98C379",
        "hover": "#222226",
        "afk_gap": "#1F1F23",
    },
    "light": {
        "canvas": "#f2f2f0",
        "card": "#ffffff",
        "bar_bg": "#e4e4e2",
        "border": "#d4d4d0",
        "fg": "#1e1e1e",
        "meta": "#888888",
        "meta_soft": "#666666",
        "accent_primary": "#2563EB",
        "accent_secondary": "#6D28D9",
        "success": "#16A34A",
        "hover": "#eaeae7",
        "afk_gap": "#dcdcd8",
    },
}

MONO_FONT_FAMILIES = ["JetBrains Mono", "Fira Code", "Cascadia Code", "monospace"]
SANS_FONT_FAMILIES = ["Plus Jakarta Sans", "Inter", "Roboto", "sans-serif"]


def make_mono_font(pt: int, bold: bool = False) -> QFont:
    font = QFont(MONO_FONT_FAMILIES[0], pt)
    font.setStyleHint(QFont.StyleHint.Monospace)
    font.setBold(bold)
    return font


def make_sans_font(pt: int, bold: bool = False) -> QFont:
    font = QFont(SANS_FONT_FAMILIES[0], pt)
    font.setBold(bold)
    return font


class _ElidedLabel(QLabel):
    """Single-line label that smoothly elides long text with '…' instead of stretching layouts."""

    def __init__(self, text: str = "", parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        self._full_text = text
        self._text_color: Optional[QColor] = None
        self.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred)
        self.setMinimumWidth(0)
        self.setToolTip(text if len(text) > 48 else "")

    def set_text_color(self, color: str) -> None:
        self._text_color = QColor(color)
        self.update()

    def setText(self, text: str) -> None:  # noqa: N802
        self._full_text = text
        self.setToolTip(text if len(text) > 48 else "")
        self.update()

    def text(self) -> str:  # noqa: N802
        return self._full_text

    def minimumSizeHint(self) -> QSize:  # noqa: N802
        fm = self.fontMetrics()
        return QSize(20, fm.height() + 2)

    def sizeHint(self) -> QSize:  # noqa: N802
        fm = self.fontMetrics()
        return QSize(max(40, fm.horizontalAdvance(self._full_text)), fm.height() + 2)

    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setFont(self.font())
        pen_color = self._text_color or self.palette().color(self.foregroundRole())
        painter.setPen(pen_color)
        elided = painter.fontMetrics().elidedText(
            self._full_text, Qt.TextElideMode.ElideRight, max(1, self.width())
        )
        painter.drawText(self.rect(), Qt.AlignmentFlag.AlignVCenter | Qt.AlignmentFlag.AlignLeft, elided)
        painter.end()


class _ProgressBarWidget(QWidget):
    """Zero-lag painted horizontal progress bar that always matches its container width."""

    def __init__(self, percent: float, bar_color: str, track_color: str, parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        self._percent = max(0.0, min(100.0, percent))
        self._bar_color = bar_color
        self._track_color = track_color
        self.setFixedHeight(6)

    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        painter.setPen(Qt.PenStyle.NoPen)

        w = float(self.width())
        h = float(self.height())
        painter.setBrush(QColor(self._track_color))
        painter.drawRoundedRect(QRectF(0.0, 0.0, w, h), 3.0, 3.0)

        if self._percent > 0.0 and w > 0.0:
            fill_w = min(w, max(4.0, w * (self._percent / 100.0)))
            painter.setBrush(QColor(self._bar_color))
            painter.drawRoundedRect(QRectF(0.0, 0.0, fill_w, h), 3.0, 3.0)
        painter.end()


class ActivityTimelineWidget(QWidget):
    """
    Custom `QPainter` horizontal activity timeline supporting 24h, 12h, 4h, 2h, and 1h
    windows with mouse-wheel zoom, drag/button panning, category filtering, and tooltips.
    """

    SPAN_HOURS = (24, 12, 4, 2, 1)
    categoryClicked = pyqtSignal(str)
    viewChanged = pyqtSignal()

    def __init__(self, parent: Optional[QWidget] = None) -> None:
        super().__init__(parent)
        self.setMouseTracking(True)
        self.setMinimumHeight(88)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        self._segments: List[TimelineSegment] = []
        self._day_start_ts: float = 0.0
        self._theme: Dict[str, str] = THEMES["dark"]
        self._font_pt: int = 10
        self._selected_category: Optional[str] = None
        self._hovered_idx: int = -1
        self._bar_rects: List[QRectF] = []
        self._span_hours: int = 24
        self._view_start_sec: float = 0.0
        self._pan_press_x: Optional[float] = None
        self._pan_origin_sec: float = 0.0
        self._did_pan: bool = False

    def span_hours(self) -> int:
        return self._span_hours

    def view_window_bounds(self) -> Tuple[float, float]:
        win_sec = float(self._span_hours * 3600)
        start = max(0.0, min(86400.0 - win_sec, self._view_start_sec))
        return (start, start + win_sec)

    def _default_anchor_end_sec(self) -> float:
        if self._day_start_ts <= 0.0:
            return 86400.0
        now_rel = datetime.now().timestamp() - self._day_start_ts
        if 0.0 <= now_rel <= 86400.0:
            return now_rel
        active_ends = [
            seg.end_ts - self._day_start_ts
            for seg in self._segments
            if not seg.is_afk and seg.end_ts > self._day_start_ts
        ]
        if active_ends:
            return max(0.0, min(86400.0, max(active_ends)))
        return 43200.0 + (self._span_hours * 1800.0)

    def set_span_hours(self, hours: int, anchor_ratio: Optional[float] = None) -> None:
        if hours not in self.SPAN_HOURS:
            return
        old_win = float(self._span_hours * 3600)
        new_win = float(hours * 3600)

        if self._span_hours == 24 and hours < 24:
            if anchor_ratio is not None:
                anchor_sec = anchor_ratio * 86400.0
                new_start = anchor_sec - anchor_ratio * new_win
            else:
                end_sec = self._default_anchor_end_sec()
                new_start = end_sec - new_win
        elif hours == 24:
            new_start = 0.0
        else:
            ratio = 0.5 if anchor_ratio is None else max(0.0, min(1.0, anchor_ratio))
            anchor_sec = self._view_start_sec + ratio * old_win
            new_start = anchor_sec - ratio * new_win

        self._span_hours = hours
        self._view_start_sec = max(0.0, min(86400.0 - new_win, new_start))
        self._hovered_idx = -1
        self.viewChanged.emit()
        self.update()

    def pan_by_fraction(self, fraction: float) -> None:
        if self._span_hours >= 24:
            return
        win_sec = float(self._span_hours * 3600)
        self._view_start_sec = max(0.0, min(86400.0 - win_sec, self._view_start_sec + fraction * win_sec))
        self._hovered_idx = -1
        self.viewChanged.emit()
        self.update()

    def can_pan(self) -> Tuple[bool, bool]:
        if self._span_hours >= 24:
            return (False, False)
        start, end = self.view_window_bounds()
        return (start > 1.0, end < 86399.0)

    def set_data(
        self,
        segments: List[TimelineSegment],
        day_start_ts: float,
        theme: Dict[str, str],
        font_pt: int,
        selected_category: Optional[str] = None,
    ) -> None:
        day_changed = abs(day_start_ts - self._day_start_ts) > 1.0
        self._segments = segments
        self._day_start_ts = day_start_ts
        self._theme = theme
        self._font_pt = font_pt
        self._selected_category = selected_category
        self._hovered_idx = -1
        if day_changed and self._span_hours < 24:
            win_sec = float(self._span_hours * 3600)
            end_sec = self._default_anchor_end_sec()
            self._view_start_sec = max(0.0, min(86400.0 - win_sec, end_sec - win_sec))
        self.viewChanged.emit()
        self.update()

    def _tick_step_seconds(self) -> int:
        if self._span_hours >= 24:
            return 10800  # 3h
        if self._span_hours >= 12:
            return 7200   # 2h
        if self._span_hours >= 4:
            return 1800   # 30m
        if self._span_hours >= 2:
            return 900    # 15m
        return 600        # 10m

    def paintEvent(self, event) -> None:  # noqa: N802
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)

        rect = self.rect()
        pad_x = 12
        track_top = 12
        track_h = 34
        track_w = max(1, rect.width() - pad_x * 2)
        track_rect = QRectF(pad_x, track_top, track_w, track_h)

        track_path = QPainterPath()
        track_path.addRoundedRect(track_rect, 4.0, 4.0)

        # Base timeline track
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor(self._theme["bar_bg"]))
        painter.drawPath(track_path)

        view_start, view_end = self.view_window_bounds()
        view_span = max(1.0, view_end - view_start)

        # Segments clipped cleanly to the rounded track
        painter.save()
        painter.setClipPath(track_path)
        self._bar_rects = []
        min_seg_w = 1.5 if self._span_hours <= 4 else 2.0
        for idx, seg in enumerate(self._segments):
            rel_start = max(view_start, min(view_end, seg.start_ts - self._day_start_ts))
            rel_end = max(rel_start, min(view_end, seg.end_ts - self._day_start_ts))
            if rel_end <= rel_start:
                self._bar_rects.append(QRectF())
                continue
            x0 = pad_x + ((rel_start - view_start) / view_span) * track_w
            x1 = pad_x + ((rel_end - view_start) / view_span) * track_w
            seg_w = max(min_seg_w, x1 - x0)
            if x0 + seg_w > pad_x + track_w:
                x0 = max(float(pad_x), (pad_x + track_w) - seg_w)
            seg_rect = QRectF(x0, track_top, seg_w, track_h)
            self._bar_rects.append(seg_rect)

            color = QColor(self._theme["afk_gap"] if seg.is_afk else seg.color)
            if (
                self._selected_category is not None
                and not seg.is_afk
                and seg.category != self._selected_category
            ):
                color.setAlpha(64)
            elif idx == self._hovered_idx:
                color = color.lighter(118)
            painter.setBrush(color)
            painter.drawRect(seg_rect)
        painter.restore()

        # Adaptive tick marks and HH:MM labels for the visible window
        tick_pen = QPen(QColor(self._theme["border"]))
        tick_pen.setWidth(1)
        label_pen = QPen(QColor(self._theme["meta_soft"]))
        painter.setFont(make_mono_font(max(8, self._font_pt - 2)))
        fm = painter.fontMetrics()

        step = self._tick_step_seconds()
        first_tick = int((view_start + step - 1) // step) * step
        last_drawn_right = -1000
        tick_sec = first_tick
        while tick_sec <= int(view_end) + 1:
            if view_start <= tick_sec <= view_end:
                x = pad_x + ((tick_sec - view_start) / view_span) * track_w
                painter.setPen(tick_pen)
                painter.drawLine(int(x), track_top + track_h + 2, int(x), track_top + track_h + 7)
                hh, mm = divmod(tick_sec // 60, 60)
                label = f"{hh:02d}:{mm:02d}"
                tw = fm.horizontalAdvance(label)
                tx = int(x - tw / 2)
                tx = max(pad_x, min(rect.width() - pad_x - tw, tx))
                if tx >= last_drawn_right + 12:
                    painter.setPen(label_pen)
                    painter.drawText(tx, track_top + track_h + 22, label)
                    last_drawn_right = tx + tw
            tick_sec += step

        # Outer hairline around track
        border_pen = QPen(QColor(self._theme["border"]))
        border_pen.setWidth(1)
        painter.setPen(border_pen)
        painter.setBrush(Qt.BrushStyle.NoBrush)
        painter.drawRoundedRect(track_rect, 4.0, 4.0)
        painter.end()

    def wheelEvent(self, event: QWheelEvent) -> None:  # noqa: N802
        delta = event.angleDelta().y()
        if delta == 0:
            super().wheelEvent(event)
            return
        idx = self.SPAN_HOURS.index(self._span_hours)
        next_idx = min(len(self.SPAN_HOURS) - 1, idx + 1) if delta > 0 else max(0, idx - 1)
        if next_idx != idx:
            pad_x = 12.0
            track_w = max(1.0, self.width() - pad_x * 2)
            ratio = max(0.0, min(1.0, (event.position().x() - pad_x) / track_w))
            self.set_span_hours(self.SPAN_HOURS[next_idx], anchor_ratio=ratio)
        event.accept()

    def mouseMoveEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        pos = event.position()
        if self._pan_press_x is not None and self._span_hours < 24:
            dx = pos.x() - self._pan_press_x
            if abs(dx) >= 5.0 or self._did_pan:
                self._did_pan = True
                track_w = max(1.0, self.width() - 24.0)
                win_sec = float(self._span_hours * 3600)
                delta_sec = -(dx / track_w) * win_sec
                self._view_start_sec = max(0.0, min(86400.0 - win_sec, self._pan_origin_sec + delta_sec))
                self.setCursor(QCursor(Qt.CursorShape.ClosedHandCursor))
                QToolTip.hideText()
                self.viewChanged.emit()
                self.update()
                return

        hit = -1
        for idx, r in enumerate(self._bar_rects):
            if not r.isNull() and r.contains(pos):
                hit = idx
                break

        if hit != self._hovered_idx:
            self._hovered_idx = hit
            self.update()

        if hit >= 0:
            seg = self._segments[hit]
            self.setCursor(
                QCursor(Qt.CursorShape.ArrowCursor if seg.is_afk else Qt.CursorShape.PointingHandCursor)
            )
            fmt = "%H:%M:%S" if self._span_hours <= 4 else "%H:%M"
            t0 = datetime.fromtimestamp(seg.start_ts).strftime(fmt)
            t1 = datetime.fromtimestamp(seg.end_ts).strftime(fmt)
            dur = format_duration(seg.end_ts - seg.start_ts)
            title = seg.window_title or seg.app_binary
            tip = f"{t0}–{t1} ({dur})\n{seg.app_binary} · {seg.category}\n{title}"
            QToolTip.showText(event.globalPosition().toPoint(), tip, self)
        else:
            self.setCursor(
                QCursor(Qt.CursorShape.OpenHandCursor if self._span_hours < 24 else Qt.CursorShape.ArrowCursor)
            )
            QToolTip.hideText()

    def mousePressEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        if event.button() == Qt.MouseButton.LeftButton:
            self._pan_press_x = event.position().x()
            self._pan_origin_sec = self._view_start_sec
            self._did_pan = False
            return
        super().mousePressEvent(event)

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        if event.button() == Qt.MouseButton.LeftButton:
            was_pan = self._did_pan
            self._pan_press_x = None
            self._did_pan = False
            if not was_pan and 0 <= self._hovered_idx < len(self._segments):
                seg = self._segments[self._hovered_idx]
                if not seg.is_afk:
                    self.categoryClicked.emit(seg.category)
                    return
            elif was_pan:
                self.setCursor(QCursor(Qt.CursorShape.OpenHandCursor if self._span_hours < 24 else Qt.CursorShape.ArrowCursor))
                return
        super().mouseReleaseEvent(event)

    def leaveEvent(self, event) -> None:  # noqa: N802
        self._hovered_idx = -1
        self._pan_press_x = None
        self._did_pan = False
        self.setCursor(QCursor(Qt.CursorShape.ArrowCursor))
        QToolTip.hideText()
        self.update()


class ProgressRowButton(QFrame):
    """Clickable zero-chrome row with ranked horizontal progress bar."""

    clicked = pyqtSignal()

    def __init__(
        self,
        label: str,
        duration_text: str,
        percent: float,
        bar_color: str,
        theme: Dict[str, str],
        font_pt: int,
        selected: bool = False,
        expandable: bool = False,
        expanded: bool = False,
        parent: Optional[QWidget] = None,
    ) -> None:
        super().__init__(parent)
        self.setObjectName("vwRowBtn")
        self.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        self.setAttribute(Qt.WidgetAttribute.WA_Hover, True)
        self._theme = theme

        layout = QVBoxLayout(self)
        layout.setContentsMargins(10, 8, 10, 8)
        layout.setSpacing(6)

        top = QHBoxLayout()
        top.setContentsMargins(0, 0, 0, 0)
        top.setSpacing(8)

        prefix = ("▾ " if expanded else "▸ ") if expandable else ""
        name_lbl = _ElidedLabel(f"{prefix}{label}")
        name_lbl.setFont(make_sans_font(font_pt, bold=selected))
        name_lbl.set_text_color(theme["fg"])
        name_lbl.setStyleSheet("background: transparent; border: none;")
        top.addWidget(name_lbl, 1)

        meta_lbl = QLabel(f"{duration_text}  {percent:4.1f}%")
        meta_lbl.setFont(make_mono_font(max(8, font_pt - 1)))
        meta_lbl.setStyleSheet(f"color: {theme['meta_soft']}; background: transparent; border: none;")
        top.addWidget(meta_lbl, 0)

        layout.addLayout(top)
        layout.addWidget(_ProgressBarWidget(percent, bar_color, theme["bar_bg"]))

        border_color = theme["accent_primary"] if selected else "transparent"
        bg_color = theme["hover"] if selected else "transparent"
        self.setStyleSheet(
            f"QFrame#vwRowBtn {{ background: {bg_color}; border: 1px solid {border_color}; border-radius: 6px; }}"
            f"QFrame#vwRowBtn:hover {{ background: {theme['hover']}; }}"
        )

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        if event.button() == Qt.MouseButton.LeftButton and self.rect().contains(event.position().toPoint()):
            self.clicked.emit()
            return
        super().mouseReleaseEvent(event)

    def keyPressEvent(self, event: QKeyEvent) -> None:  # noqa: N802
        if event.key() in (Qt.Key.Key_Return, Qt.Key.Key_Enter, Qt.Key.Key_Space):
            self.clicked.emit()
            return
        super().keyPressEvent(event)


class CategoryEditorDialog(QDialog):
    """Inline `categories.json` rule editor (`Ctrl+E`) with live validation and immediate reload."""

    def __init__(self, categorizer: CategoryEngine, theme: Dict[str, str], font_pt: int, parent=None) -> None:
        super().__init__(parent)
        self.categorizer = categorizer
        self.setWindowTitle("Edit categories.json")
        self.resize(640, 480)
        self.setStyleSheet(
            f"QDialog {{ background: {theme['card']}; color: {theme['fg']}; border: 1px solid {theme['border']}; }}"
        )

        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(12)

        header = QLabel(f"Categories Rules — {categorizer.path}")
        header.setFont(make_sans_font(font_pt + 1, bold=True))
        header.setStyleSheet(f"color: {theme['fg']};")
        layout.addWidget(header)

        self.editor = QPlainTextEdit()
        self.editor.setFont(make_mono_font(font_pt))
        self.editor.setPlainText(categorizer.read_raw_json())
        self.editor.setStyleSheet(
            f"QPlainTextEdit {{ background: {theme['canvas']}; color: {theme['fg']}; "
            f"border: 1px solid {theme['border']}; border-radius: 6px; padding: 10px; }}"
        )
        layout.addWidget(self.editor, 1)

        self.error_lbl = QLabel("")
        self.error_lbl.setStyleSheet("color: #E06C75;")
        self.error_lbl.setWordWrap(True)
        layout.addWidget(self.error_lbl)

        btn_row = QHBoxLayout()
        btn_row.addStretch(1)
        cancel_btn = QPushButton("Cancel (Esc)")
        save_btn = QPushButton("Save & Reload")
        cancel_btn.clicked.connect(self.reject)
        save_btn.clicked.connect(self._on_save)
        for btn, primary in ((cancel_btn, False), (save_btn, True)):
            btn.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))
            btn.setFont(make_sans_font(font_pt, bold=primary))
            if primary:
                btn.setStyleSheet(
                    f"QPushButton {{ background: {theme['accent_primary']}; color: #141414; "
                    f"border: none; border-radius: 6px; padding: 6px 14px; }}"
                )
            else:
                btn.setStyleSheet(
                    f"QPushButton {{ background: {theme['bar_bg']}; color: {theme['fg']}; "
                    f"border: 1px solid {theme['border']}; border-radius: 6px; padding: 6px 14px; }}"
                )
        btn_row.addWidget(cancel_btn)
        btn_row.addWidget(save_btn)
        layout.addLayout(btn_row)

    def _on_save(self) -> None:
        try:
            self.categorizer.save_raw_json(self.editor.toPlainText())
            self.accept()
        except Exception as exc:
            self.error_lbl.setText(f"Invalid categories.json: {exc}")


class ShortcutsOverlay(QDialog):
    """Compact `F1` keyboard shortcuts overlay."""

    SHORTCUTS = [
        ("Left / Right", "Step backward / forward by 1 day"),
        ("T", "Jump back to Today"),
        ("1 / 2 / 3", "Switch between Day, 7 Days, and 30 Days"),
        ("A", "Toggle Include AFK"),
        ("Ctrl + E", "Open inline categories.json rule editor"),
        ("Ctrl + R / F5", "Refresh data from SQLite"),
        ("Ctrl + Shift + D", "Toggle Dark / Light theme"),
        ("Ctrl + = / - / 0", "Zoom UI font size (8pt–48pt)"),
        ("F11 / Ctrl+Shift+F", "Toggle frameless window mode (Alt+Drag to move)"),
        ("F1", "Toggle this shortcuts overlay"),
        ("Escape", "Dismiss filter/overlay, or exit at root view"),
        ("Ctrl + Q", "Immediate exit"),
    ]

    def __init__(self, theme: Dict[str, str], font_pt: int, parent=None) -> None:
        super().__init__(parent)
        self.setWindowTitle("VibeWatch Shortcuts")
        self.setWindowFlags(Qt.WindowType.FramelessWindowHint | Qt.WindowType.Dialog)
        self.setStyleSheet(
            f"QDialog {{ background: {theme['card']}; color: {theme['fg']}; "
            f"border: 1px solid {theme['border']}; border-radius: 8px; }}"
        )

        layout = QVBoxLayout(self)
        layout.setContentsMargins(20, 18, 20, 18)
        layout.setSpacing(8)

        title = QLabel("Keyboard Shortcuts")
        title.setFont(make_sans_font(font_pt + 2, bold=True))
        layout.addWidget(title)

        for key, desc in self.SHORTCUTS:
            row = QHBoxLayout()
            k_lbl = QLabel(key)
            k_lbl.setFont(make_mono_font(max(8, font_pt - 1), bold=True))
            k_lbl.setStyleSheet(f"color: {theme['accent_primary']};")
            k_lbl.setFixedWidth(160)
            d_lbl = QLabel(desc)
            d_lbl.setFont(make_sans_font(max(8, font_pt - 1)))
            d_lbl.setStyleSheet(f"color: {theme['fg']};")
            row.addWidget(k_lbl)
            row.addWidget(d_lbl, 1)
            layout.addLayout(row)

        hint = QLabel("Press F1 or Escape to close")
        hint.setFont(make_mono_font(max(8, font_pt - 2)))
        hint.setStyleSheet(f"color: {theme['meta']}; padding-top: 6px;")
        layout.addWidget(hint)

        QShortcut(QKeySequence("F1"), self, self.close)
        QShortcut(QKeySequence("Escape"), self, self.close)


class VibeWatchWindow(QMainWindow):
    """Main zero-chrome VibeWatch PyQt6 Dashboard Window."""

    EDGE_MARGIN = 8

    def __init__(self, db_path: Path = DEFAULT_DB_PATH) -> None:
        super().__init__()
        self.setWindowTitle("VibeWatch")
        self.resize(1240, 820)
        self.setMinimumSize(860, 620)

        self.db = ActivityDatabase(db_path=db_path)
        self.categorizer = CategoryEngine(DEFAULT_CATEGORIES_PATH)

        # Interactive UI state
        self.theme_name: str = "dark"
        self.font_pt: int = 11
        self.anchor_date: datetime = datetime.now()
        self.range_mode: str = "day"  # "day" | "7d" | "30d"
        self.include_afk: bool = False
        self.selected_category: Optional[str] = None
        self.expanded_apps: Set[str] = set()
        self.frameless: bool = False
        self._shortcuts_dialog: Optional[ShortcutsOverlay] = None
        self._drag_pos: Optional[QPoint] = None
        self._resize_edges: Qt.Edge = Qt.Edge(0)
        self._last_event_marker: Optional[Tuple[int, float]] = None

        self._build_ui()
        self._register_shortcuts()
        self.apply_theme()
        self.refresh_data()

        # Lightweight 5s timer to hot-reload categories.json, SQLite updates & daemon status anchor
        self._poll_timer = QTimer(self)
        self._poll_timer.timeout.connect(self._on_background_tick)
        self._poll_timer.start(5000)

    def _build_ui(self) -> None:
        root = QWidget()
        root.setObjectName("vwRoot")
        self.setCentralWidget(root)
        self.root_layout = QVBoxLayout(root)
        self.root_layout.setContentsMargins(24, 24, 24, 18)
        self.root_layout.setSpacing(16)

        # 1. Top Control Strip (date selector, range selector, Include AFK, Edit Categories)
        top_bar = QHBoxLayout()
        top_bar.setSpacing(10)

        self.prev_btn = QPushButton("◄")
        self.date_lbl = QLabel("Today")
        self.next_btn = QPushButton("►")
        self.prev_btn.clicked.connect(lambda: self.step_day(-1))
        self.next_btn.clicked.connect(lambda: self.step_day(1))
        top_bar.addWidget(self.prev_btn)
        top_bar.addWidget(self.date_lbl)
        top_bar.addWidget(self.next_btn)
        top_bar.addStretch(1)

        self.range_btns: Dict[str, QPushButton] = {}
        for mode, text in (("day", "Day"), ("7d", "7 Days"), ("30d", "30 Days")):
            btn = QPushButton(text)
            btn.clicked.connect(lambda _checked=False, m=mode: self.set_range_mode(m))
            self.range_btns[mode] = btn
            top_bar.addWidget(btn)

        self.afk_btn = QPushButton("Include AFK")
        self.afk_btn.clicked.connect(self.toggle_afk)
        top_bar.addWidget(self.afk_btn)

        self.edit_cat_btn = QPushButton("Edit Categories")
        self.edit_cat_btn.clicked.connect(self.open_category_editor)
        top_bar.addWidget(self.edit_cat_btn)
        self.root_layout.addLayout(top_bar)

        # Summary KPI strip (Active Time, Top Category, Top App, AFK Time)
        self.kpi_frame = QFrame()
        kpi_layout = QHBoxLayout(self.kpi_frame)
        kpi_layout.setContentsMargins(16, 12, 16, 12)
        kpi_layout.setSpacing(24)
        self.kpi_values: Dict[str, QLabel] = {}
        self.kpi_labels: Dict[str, QLabel] = {}
        for key, label in (
            ("active", "Active Time"),
            ("top_cat", "Top Category"),
            ("top_app", "Top App"),
            ("afk", "AFK Time"),
        ):
            col = QVBoxLayout()
            col.setSpacing(2)
            l_lbl = QLabel(label)
            v_lbl: QLabel = _ElidedLabel("—") if key in ("top_cat", "top_app") else QLabel("—")
            self.kpi_labels[key] = l_lbl
            self.kpi_values[key] = v_lbl
            col.addWidget(l_lbl)
            col.addWidget(v_lbl)
            kpi_layout.addLayout(col, 1)
        self.root_layout.addWidget(self.kpi_frame)

        # 2. Activity Timeline Card (24h / 12h / 4h / 2h / 1h)
        self.timeline_card = QFrame()
        tl_layout = QVBoxLayout(self.timeline_card)
        tl_layout.setContentsMargins(16, 12, 16, 10)
        tl_layout.setSpacing(6)

        tl_hdr_row = QHBoxLayout()
        tl_hdr_row.setContentsMargins(0, 0, 0, 0)
        tl_hdr_row.setSpacing(6)
        self.timeline_header = QLabel("24-Hour Activity Timeline")
        tl_hdr_row.addWidget(self.timeline_header, 1)

        self.tl_pan_left_btn = QPushButton("‹")
        self.tl_pan_right_btn = QPushButton("›")
        self.tl_pan_left_btn.setToolTip("Pan timeline earlier")
        self.tl_pan_right_btn.setToolTip("Pan timeline later")
        self.tl_pan_left_btn.clicked.connect(lambda: self.timeline_widget.pan_by_fraction(-0.5))
        self.tl_pan_right_btn.clicked.connect(lambda: self.timeline_widget.pan_by_fraction(0.5))
        tl_hdr_row.addWidget(self.tl_pan_left_btn)
        tl_hdr_row.addWidget(self.tl_pan_right_btn)

        self.tl_span_btns: Dict[int, QPushButton] = {}
        for hrs in (1, 2, 4, 12, 24):
            btn = QPushButton(f"{hrs}h")
            btn.setToolTip(f"Show {hrs}-hour timeline window (or scroll wheel on timeline)")
            btn.clicked.connect(lambda _c=False, h=hrs: self.set_timeline_span(h))
            self.tl_span_btns[hrs] = btn
            tl_hdr_row.addWidget(btn)

        self.timeline_widget = ActivityTimelineWidget()
        self.timeline_widget.categoryClicked.connect(self.toggle_category_filter)
        self.timeline_widget.viewChanged.connect(self._sync_timeline_controls)
        tl_layout.addLayout(tl_hdr_row)
        tl_layout.addWidget(self.timeline_widget)
        self.root_layout.addWidget(self.timeline_card)

        # 3. Two-Column Breakdown Grid (Left: Top Categories, Right: Top Applications)
        cols = QHBoxLayout()
        cols.setSpacing(16)

        self.cat_card = QFrame()
        cat_card_layout = QVBoxLayout(self.cat_card)
        cat_card_layout.setContentsMargins(16, 14, 16, 14)
        cat_card_layout.setSpacing(10)
        self.cat_header = QLabel("Top Categories")
        cat_card_layout.addWidget(self.cat_header)
        self.cat_scroll = QScrollArea()
        self.cat_scroll.setWidgetResizable(True)
        self.cat_scroll.setFrameShape(QFrame.Shape.NoFrame)
        self.cat_list_host = QWidget()
        self.cat_list_host.setObjectName("vwListHost")
        self.cat_list_layout = QVBoxLayout(self.cat_list_host)
        self.cat_list_layout.setContentsMargins(0, 0, 0, 0)
        self.cat_list_layout.setSpacing(6)
        self.cat_scroll.setWidget(self.cat_list_host)
        cat_card_layout.addWidget(self.cat_scroll, 1)

        self.app_card = QFrame()
        app_card_layout = QVBoxLayout(self.app_card)
        app_card_layout.setContentsMargins(16, 14, 16, 14)
        app_card_layout.setSpacing(10)
        app_hdr_row = QHBoxLayout()
        self.app_header = QLabel("Top Applications")
        self.clear_filter_btn = QPushButton("Clear Filter")
        self.clear_filter_btn.clicked.connect(self.clear_category_filter)
        self.clear_filter_btn.setVisible(False)
        app_hdr_row.addWidget(self.app_header, 1)
        app_hdr_row.addWidget(self.clear_filter_btn)
        app_card_layout.addLayout(app_hdr_row)

        self.app_scroll = QScrollArea()
        self.app_scroll.setWidgetResizable(True)
        self.app_scroll.setFrameShape(QFrame.Shape.NoFrame)
        self.app_list_host = QWidget()
        self.app_list_host.setObjectName("vwListHost")
        self.app_list_layout = QVBoxLayout(self.app_list_host)
        self.app_list_layout.setContentsMargins(0, 0, 0, 0)
        self.app_list_layout.setSpacing(6)
        self.app_scroll.setWidget(self.app_list_host)
        app_card_layout.addWidget(self.app_scroll, 1)

        cols.addWidget(self.cat_card, 42)
        cols.addWidget(self.app_card, 58)
        self.root_layout.addLayout(cols, 1)

        # 4. Whisper-Quiet Status Anchor (Bottom-Right in #585858)
        footer = QHBoxLayout()
        footer.addStretch(1)
        self.status_anchor = QLabel("daemon: checking · active: — · F1 shortcuts")
        footer.addWidget(self.status_anchor)
        self.root_layout.addLayout(footer)

    def _register_shortcuts(self) -> None:
        bindings = [
            ("Left", lambda: self.step_day(-1)),
            ("Right", lambda: self.step_day(1)),
            ("T", self.jump_today),
            ("1", lambda: self.set_range_mode("day")),
            ("2", lambda: self.set_range_mode("7d")),
            ("3", lambda: self.set_range_mode("30d")),
            ("A", self.toggle_afk),
            ("Ctrl+E", self.open_category_editor),
            ("Ctrl+R", self.refresh_data),
            ("F5", self.refresh_data),
            ("Ctrl+Shift+D", self.toggle_theme),
            ("Ctrl+=", lambda: self.adjust_zoom(1)),
            ("Ctrl++", lambda: self.adjust_zoom(1)),
            ("Ctrl+-", lambda: self.adjust_zoom(-1)),
            ("Ctrl+0", self.reset_zoom),
            ("F11", self.toggle_frameless),
            ("Ctrl+Shift+F", self.toggle_frameless),
            ("F1", self.toggle_shortcuts_overlay),
            ("Escape", self.handle_escape),
            ("Ctrl+Q", self.close),
        ]
        for seq, handler in bindings:
            sc = QShortcut(QKeySequence(seq), self)
            sc.activated.connect(handler)

    def _theme(self) -> Dict[str, str]:
        return THEMES[self.theme_name]

    def apply_theme(self) -> None:
        t = self._theme()
        pt = self.font_pt
        self.setStyleSheet(
            f"QMainWindow, #vwRoot {{ background: {t['canvas']}; color: {t['fg']}; }}"
            f"QLabel {{ background: transparent; border: none; color: {t['fg']}; }}"
            f"QScrollArea, #vwListHost {{ background: {t['card']}; border: none; }}"
        )

        card_css = (
            f"QFrame#vwCard {{ background: {t['card']}; border: 1px solid {t['border']}; border-radius: 8px; }}"
        )
        for frame in (self.kpi_frame, self.timeline_card, self.cat_card, self.app_card):
            frame.setObjectName("vwCard")
            frame.setStyleSheet(card_css)

        btn_base = (
            f"QPushButton {{ background: {t['card']}; color: {t['fg']}; border: 1px solid {t['border']}; "
            f"border-radius: 6px; padding: 5px 12px; }}"
            f"QPushButton:hover {{ background: {t['hover']}; }}"
            f"QPushButton:disabled {{ color: {t['meta']}; }}"
        )
        btn_active = (
            f"QPushButton {{ background: {t['accent_primary']}; color: {'#141414' if self.theme_name == 'dark' else '#ffffff'}; "
            f"border: 1px solid {t['accent_primary']}; border-radius: 6px; padding: 5px 12px; font-weight: 600; }}"
        )

        for btn in (self.prev_btn, self.next_btn, self.edit_cat_btn, self.clear_filter_btn):
            btn.setFont(make_sans_font(max(8, pt - 1)))
            btn.setStyleSheet(btn_base)
            btn.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))

        for mode, btn in self.range_btns.items():
            btn.setFont(make_sans_font(max(8, pt - 1), bold=(mode == self.range_mode)))
            btn.setStyleSheet(btn_active if mode == self.range_mode else btn_base)
            btn.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))

        self.afk_btn.setFont(make_sans_font(max(8, pt - 1), bold=self.include_afk))
        self.afk_btn.setStyleSheet(btn_active if self.include_afk else btn_base)
        self.afk_btn.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))

        for btn in (self.tl_pan_left_btn, self.tl_pan_right_btn):
            btn.setFont(make_mono_font(max(8, pt - 2)))
            btn.setStyleSheet(
                f"QPushButton {{ background: {t['canvas']}; color: {t['fg']}; border: 1px solid {t['border']}; "
                f"border-radius: 4px; padding: 2px 7px; }}"
                f"QPushButton:hover {{ background: {t['hover']}; }}"
                f"QPushButton:disabled {{ color: {t['meta']}; }}"
            )
            btn.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))

        self._style_timeline_span_buttons()

        self.date_lbl.setFont(make_sans_font(pt + 2, bold=True))
        self.date_lbl.setStyleSheet(f"color: {t['fg']}; background: transparent; padding: 0 8px;")

        for lbl in (self.timeline_header, self.cat_header, self.app_header):
            lbl.setFont(make_sans_font(pt, bold=True))
            lbl.setStyleSheet(f"color: {t['fg']}; background: transparent; border: none;")

        for key, lbl in self.kpi_labels.items():
            lbl.setFont(make_sans_font(max(8, pt - 2)))
            lbl.setStyleSheet(f"color: {t['meta_soft']}; background: transparent; border: none;")
        for key, val in self.kpi_values.items():
            val.setFont(make_mono_font(pt + 4, bold=True))
            accent = t["accent_primary"] if key == "active" else t["fg"]
            val.setStyleSheet(f"color: {accent}; background: transparent; border: none;")
            if isinstance(val, _ElidedLabel):
                val.set_text_color(accent)

        self.status_anchor.setFont(make_mono_font(max(8, pt - 2)))
        self.status_anchor.setStyleSheet(f"color: {t['meta']}; background: transparent;")

    def _style_timeline_span_buttons(self) -> None:
        t = self._theme()
        pt = self.font_pt
        cur_span = self.timeline_widget.span_hours()
        pill_idle = (
            f"QPushButton {{ background: {t['canvas']}; color: {t['meta_soft']}; border: 1px solid {t['border']}; "
            f"border-radius: 4px; padding: 2px 8px; }}"
            f"QPushButton:hover {{ background: {t['hover']}; color: {t['fg']}; }}"
        )
        pill_active = (
            f"QPushButton {{ background: {t['accent_primary']}; color: {'#141414' if self.theme_name == 'dark' else '#ffffff'}; "
            f"border: 1px solid {t['accent_primary']}; border-radius: 4px; padding: 2px 8px; font-weight: 600; }}"
        )
        for hrs, btn in self.tl_span_btns.items():
            active = hrs == cur_span
            btn.setFont(make_mono_font(max(8, pt - 2), bold=active))
            btn.setStyleSheet(pill_active if active else pill_idle)
            btn.setCursor(QCursor(Qt.CursorShape.PointingHandCursor))

    def _sync_timeline_controls(self) -> None:
        span_h = self.timeline_widget.span_hours()
        if span_h >= 24:
            self.timeline_header.setText("24-Hour Activity Timeline")
        else:
            s_sec, e_sec = self.timeline_widget.view_window_bounds()
            sh, sm = divmod(int(round(s_sec)) // 60, 60)
            eh, em = divmod(int(round(e_sec)) // 60, 60)
            self.timeline_header.setText(
                f"Activity Timeline · {span_h}h ({sh:02d}:{sm:02d}–{eh:02d}:{em:02d})"
            )
        can_left, can_right = self.timeline_widget.can_pan()
        self.tl_pan_left_btn.setVisible(span_h < 24)
        self.tl_pan_right_btn.setVisible(span_h < 24)
        self.tl_pan_left_btn.setEnabled(can_left)
        self.tl_pan_right_btn.setEnabled(can_right)
        self._style_timeline_span_buttons()

    def set_timeline_span(self, hours: int) -> None:
        self.timeline_widget.set_span_hours(hours)

    def _clear_layout(self, layout: QVBoxLayout) -> None:
        while layout.count():
            item = layout.takeAt(0)
            w = item.widget()
            if w is not None:
                w.deleteLater()

    def _format_anchor_label(self) -> str:
        today = datetime.now().date()
        d = self.anchor_date.date()
        formatted = d.strftime("%a, %b %d")
        if d == today:
            return f"Today · {formatted}"
        if d == today - timedelta(days=1):
            return f"Yesterday · {formatted}"
        return formatted

    def refresh_data(self) -> None:
        cat_scroll_val = self.cat_scroll.verticalScrollBar().value()
        app_scroll_val = self.app_scroll.verticalScrollBar().value()

        self.categorizer.reload_if_changed()
        latest = self.db.latest_event()
        self._last_event_marker = (latest.id, latest.end_ts) if latest is not None else None

        start_ts, end_ts = date_range_bounds(self.anchor_date, self.range_mode)
        events = self.db.query_events(start_ts, end_ts)
        snap = compute_analytics(
            events=events,
            categorizer=self.categorizer,
            include_afk=self.include_afk,
            category_filter=self.selected_category,
        )

        # Always compute 24-hour timeline for the anchored day
        day_start_ts, day_end_ts = date_range_bounds(self.anchor_date, "day")
        day_events = events if self.range_mode == "day" else self.db.query_events(day_start_ts, day_end_ts)
        day_snap = (
            snap
            if self.range_mode == "day"
            else compute_analytics(day_events, self.categorizer, include_afk=True)
        )

        self.date_lbl.setText(self._format_anchor_label())
        self.next_btn.setEnabled(self.anchor_date.date() < datetime.now().date())
        self.kpi_values["active"].setText(format_duration(snap.active_seconds))
        self.kpi_values["top_cat"].setText(snap.top_category)
        self.kpi_values["top_app"].setText(snap.top_app)
        self.kpi_values["afk"].setText(format_duration(snap.afk_seconds))

        t = self._theme()
        self.timeline_widget.set_data(
            day_snap.timeline,
            day_start_ts,
            t,
            self.font_pt,
            selected_category=self.selected_category,
        )

        # Left column: Top Categories
        self._clear_layout(self.cat_list_layout)
        if not snap.categories:
            empty = QLabel("No activity recorded for this range.")
            empty.setFont(make_sans_font(self.font_pt - 1))
            empty.setStyleSheet(f"color: {t['meta_soft']}; background: transparent; padding: 12px;")
            self.cat_list_layout.addWidget(empty)
        else:
            for cat in snap.categories:
                row = ProgressRowButton(
                    label=cat.category,
                    duration_text=format_duration(cat.duration),
                    percent=cat.percentage,
                    bar_color=cat.color,
                    theme=t,
                    font_pt=self.font_pt,
                    selected=(self.selected_category == cat.category),
                )
                row.clicked.connect(lambda _c=False, name=cat.category: self.toggle_category_filter(name))
                self.cat_list_layout.addWidget(row)
        self.cat_list_layout.addStretch(1)

        # Right column: Top Applications (Expandable inline window_title breakdown)
        self.clear_filter_btn.setVisible(self.selected_category is not None)
        self.app_header.setText(
            f"Top Applications · {self.selected_category}"
            if self.selected_category
            else "Top Applications"
        )
        self._clear_layout(self.app_list_layout)
        if not snap.apps:
            empty = QLabel("No applications match the current view.")
            empty.setFont(make_sans_font(self.font_pt - 1))
            empty.setStyleSheet(f"color: {t['meta_soft']}; background: transparent; padding: 12px;")
            self.app_list_layout.addWidget(empty)
        else:
            for app in snap.apps:
                is_expanded = app.app_binary in self.expanded_apps
                row = ProgressRowButton(
                    label=app.app_binary,
                    duration_text=format_duration(app.duration),
                    percent=app.percentage,
                    bar_color=app.color,
                    theme=t,
                    font_pt=self.font_pt,
                    expandable=len(app.titles) > 0,
                    expanded=is_expanded,
                )
                row.clicked.connect(lambda _c=False, name=app.app_binary: self.toggle_app_expand(name))
                self.app_list_layout.addWidget(row)

                if is_expanded and app.titles:
                    sub_box = QWidget()
                    sub_box.setStyleSheet(f"background: {t['canvas']}; border-radius: 6px;")
                    sub_layout = QVBoxLayout(sub_box)
                    sub_layout.setContentsMargins(16, 6, 12, 8)
                    sub_layout.setSpacing(4)
                    for item in app.titles[:15]:
                        line = QHBoxLayout()
                        t_lbl = _ElidedLabel(item.window_title)
                        t_lbl.setFont(make_sans_font(max(8, self.font_pt - 2)))
                        t_lbl.set_text_color(t["fg"])
                        t_lbl.setStyleSheet("background: transparent;")
                        d_lbl = QLabel(f"{format_duration(item.duration)} · {item.percentage:4.1f}%")
                        d_lbl.setFont(make_mono_font(max(8, self.font_pt - 2)))
                        d_lbl.setStyleSheet(f"color: {t['meta_soft']}; background: transparent;")
                        line.addWidget(t_lbl, 1)
                        line.addWidget(d_lbl, 0)
                        sub_layout.addLayout(line)
                    if len(app.titles) > 15:
                        more_lbl = QLabel(f"+{len(app.titles) - 15} more window titles")
                        more_lbl.setFont(make_mono_font(max(8, self.font_pt - 2)))
                        more_lbl.setStyleSheet(f"color: {t['meta']}; background: transparent;")
                        sub_layout.addWidget(more_lbl)
                    self.app_list_layout.addWidget(sub_box)
        self.app_list_layout.addStretch(1)

        QTimer.singleShot(0, lambda: self._restore_scroll(cat_scroll_val, app_scroll_val))
        self._update_status_anchor()

    def _restore_scroll(self, cat_val: int, app_val: int) -> None:
        self.cat_scroll.verticalScrollBar().setValue(cat_val)
        self.app_scroll.verticalScrollBar().setValue(app_val)

    def _update_status_anchor(self) -> None:
        status = read_daemon_status()
        state = "running" if status["running"] else "stopped"
        active = status["active_app"] if status["running"] else "idle"
        self.status_anchor.setText(f"daemon: {state} · active: {active} · F1 shortcuts")

    def _on_background_tick(self) -> None:
        cat_changed = self.categorizer.reload_if_changed()
        latest = self.db.latest_event()
        marker = (latest.id, latest.end_ts) if latest is not None else None
        db_changed = marker != self._last_event_marker

        if cat_changed or (db_changed and QApplication.activeModalWidget() is None):
            self.refresh_data()
        else:
            self._update_status_anchor()

    # --- Actions & Keyboard Handlers ---
    def step_day(self, delta: int) -> None:
        today = datetime.now().date()
        target = (self.anchor_date + timedelta(days=delta)).date()
        if target > today:
            self.anchor_date = datetime.now()
        else:
            self.anchor_date = self.anchor_date + timedelta(days=delta)
        self.refresh_data()

    def jump_today(self) -> None:
        self.anchor_date = datetime.now()
        self.refresh_data()

    def set_range_mode(self, mode: str) -> None:
        self.range_mode = mode
        self.apply_theme()
        self.refresh_data()

    def toggle_afk(self) -> None:
        self.include_afk = not self.include_afk
        self.apply_theme()
        self.refresh_data()

    def toggle_category_filter(self, category: str) -> None:
        self.selected_category = None if self.selected_category == category else category
        self.refresh_data()

    def clear_category_filter(self) -> None:
        self.selected_category = None
        self.refresh_data()

    def toggle_app_expand(self, app_binary: str) -> None:
        if app_binary in self.expanded_apps:
            self.expanded_apps.remove(app_binary)
        else:
            self.expanded_apps.add(app_binary)
        self.refresh_data()

    def toggle_theme(self) -> None:
        self.theme_name = "light" if self.theme_name == "dark" else "dark"
        self.apply_theme()
        self.refresh_data()

    def adjust_zoom(self, delta: int) -> None:
        self.font_pt = max(8, min(48, self.font_pt + delta))
        self.apply_theme()
        self.refresh_data()

    def reset_zoom(self) -> None:
        self.font_pt = 11
        self.apply_theme()
        self.refresh_data()

    def open_category_editor(self) -> None:
        dlg = CategoryEditorDialog(self.categorizer, self._theme(), self.font_pt, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self.refresh_data()

    def toggle_shortcuts_overlay(self) -> None:
        if self._shortcuts_dialog is not None and self._shortcuts_dialog.isVisible():
            self._shortcuts_dialog.close()
            self._shortcuts_dialog = None
            return
        self._shortcuts_dialog = ShortcutsOverlay(self._theme(), self.font_pt, self)
        self._shortcuts_dialog.show()

    def toggle_frameless(self) -> None:
        self.frameless = not self.frameless
        geom = self.geometry()
        self.setWindowFlag(Qt.WindowType.FramelessWindowHint, self.frameless)
        self.resize(geom.size())
        self.show()

    def handle_escape(self) -> None:
        if self._shortcuts_dialog is not None and self._shortcuts_dialog.isVisible():
            self._shortcuts_dialog.close()
            self._shortcuts_dialog = None
            return
        if self.selected_category is not None:
            self.selected_category = None
            self.refresh_data()
            return
        if self.expanded_apps:
            self.expanded_apps.clear()
            self.refresh_data()
            return
        self.close()

    # --- Frameless Window Alt+Drag Move & 8px Edge Resize ---
    def _edges_at(self, pos: QPoint) -> Qt.Edge:
        edges = Qt.Edge(0)
        m = self.EDGE_MARGIN
        if pos.x() <= m:
            edges |= Qt.Edge.LeftEdge
        elif pos.x() >= self.width() - m:
            edges |= Qt.Edge.RightEdge
        if pos.y() <= m:
            edges |= Qt.Edge.TopEdge
        elif pos.y() >= self.height() - m:
            edges |= Qt.Edge.BottomEdge
        return edges

    def mousePressEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        if self.frameless and event.button() == Qt.MouseButton.LeftButton:
            pos = event.position().toPoint()
            edges = self._edges_at(pos)
            handle = self.windowHandle()
            if edges and handle is not None:
                handle.startSystemResize(edges)
                return
            if event.modifiers() & Qt.KeyboardModifier.AltModifier:
                if handle is not None:
                    handle.startSystemMove()
                else:
                    self._drag_pos = event.globalPosition().toPoint() - self.frameGeometry().topLeft()
                return
        super().mousePressEvent(event)

    def mouseMoveEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        if self.frameless and self._drag_pos is not None:
            self.move(event.globalPosition().toPoint() - self._drag_pos)
            return
        super().mouseMoveEvent(event)

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:  # noqa: N802
        self._drag_pos = None
        super().mouseReleaseEvent(event)


def main() -> None:
    parser = argparse.ArgumentParser(description="VibeWatch native PyQt6 Activity & Screen-Time Dashboard.")
    parser.add_argument("--install", action="store_true", help="Install systemd --user service & .desktop launcher.")
    parser.add_argument("--db", type=Path, default=DEFAULT_DB_PATH, help="Custom SQLite database path.")
    args = parser.parse_args()

    if args.install:
        import setup as vw_setup
        vw_setup.install_all()
        return

    app = QApplication(sys.argv)
    app.setApplicationName("VibeWatch")
    app.setDesktopFileName("vibewatch")
    window = VibeWatchWindow(db_path=args.db)
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
