"""Matte dark theme palette and QSS stylesheet for WinFix."""

CANVAS_BG = "#141414"
CARD_BG = "#1B1B1D"
BORDER_COLOR = "#2C2C30"
PRIMARY_ACCENT = "#88C0D0"
SECONDARY_BG = "#242428"
TEXT_PRIMARY = "#E0E0E0"
TEXT_MUTED = "#9A9AA0"
STATUS_GREEN = "#98C379"
STATUS_RED = "#E06C75"
STATUS_AMBER = "#E5C07B"

UI_FONT_FAMILY = '"Segoe UI", "Roboto", sans-serif'
MONO_FONT_FAMILY = '"JetBrains Mono", "Consolas", monospace'

DARK_STYLESHEET = f"""
QMainWindow, QWidget {{
    background-color: {CANVAS_BG};
    color: {TEXT_PRIMARY};
    font-family: {UI_FONT_FAMILY};
    font-size: 11pt;
}}

QFrame#TelemetryBar {{
    background-color: {CARD_BG};
    border: 1px solid {BORDER_COLOR};
    border-radius: 6px;
}}

QFrame#CardPanel {{
    background-color: {CARD_BG};
    border: 1px solid {BORDER_COLOR};
    border-radius: 6px;
}}

QFrame#SubCard {{
    background-color: {CANVAS_BG};
    border: 1px solid {BORDER_COLOR};
    border-radius: 6px;
}}

QWidget#TransparentCell {{
    background-color: transparent;
}}

QFrame#LogDrawer {{
    background-color: {CARD_BG};
    border: 1px solid {BORDER_COLOR};
    border-radius: 6px;
}}

QLabel#SectionTitle {{
    font-size: 12pt;
    font-weight: 700;
    color: {PRIMARY_ACCENT};
    background-color: transparent;
    border: none;
}}

QLabel#CardTitle {{
    font-size: 11pt;
    font-weight: 600;
    color: {TEXT_PRIMARY};
    background-color: transparent;
    border: none;
}}

QLabel#MutedLabel {{
    font-size: 10pt;
    color: {TEXT_MUTED};
    background-color: transparent;
    border: none;
}}

QLabel#TelemetryMono {{
    font-family: {MONO_FONT_FAMILY};
    font-size: 10pt;
    color: {TEXT_PRIMARY};
    background-color: transparent;
    border: none;
}}

QTabWidget::pane {{
    border: 1px solid {BORDER_COLOR};
    background-color: {CANVAS_BG};
    border-radius: 6px;
    top: -1px;
}}

QTabBar::tab {{
    background-color: {CARD_BG};
    color: {TEXT_MUTED};
    border: 1px solid {BORDER_COLOR};
    border-bottom: none;
    padding: 8px 16px;
    margin-right: 4px;
    border-top-left-radius: 6px;
    border-top-right-radius: 6px;
    font-weight: 600;
}}

QTabBar::tab:selected {{
    background-color: {SECONDARY_BG};
    color: {PRIMARY_ACCENT};
    border-bottom: 2px solid {PRIMARY_ACCENT};
}}

QTabBar::tab:hover:!selected {{
    color: {TEXT_PRIMARY};
    background-color: #202024;
}}

QPushButton {{
    background-color: {SECONDARY_BG};
    color: {TEXT_PRIMARY};
    border: 1px solid {BORDER_COLOR};
    border-radius: 5px;
    padding: 7px 14px;
    font-size: 11pt;
    font-weight: 500;
}}

QPushButton:hover {{
    background-color: #2D2D33;
    border-color: #3E3E44;
}}

QPushButton:pressed {{
    background-color: #1A1A1E;
}}

QPushButton:disabled {{
    color: #66666C;
    background-color: #19191B;
    border-color: #232326;
}}

QPushButton#PrimaryButton {{
    background-color: {PRIMARY_ACCENT};
    color: {CANVAS_BG};
    border: 1px solid {PRIMARY_ACCENT};
    font-weight: 700;
}}

QPushButton#PrimaryButton:hover {{
    background-color: #9DD0DE;
    border-color: #9DD0DE;
}}

QPushButton#PrimaryButton:pressed {{
    background-color: #72ACC0;
}}

QPushButton#DangerButton {{
    background-color: #2B1D20;
    color: {STATUS_RED};
    border: 1px solid #4A282D;
    font-weight: 600;
}}

QPushButton#DangerButton:hover {{
    background-color: #3A2327;
}}

QPushButton#SmallButton {{
    padding: 5px 10px;
    font-size: 10pt;
}}

QPushButton#SmallPrimary {{
    background-color: {PRIMARY_ACCENT};
    color: {CANVAS_BG};
    border: 1px solid {PRIMARY_ACCENT};
    padding: 5px 12px;
    font-size: 10pt;
    font-weight: 700;
}}

QPushButton#SmallPrimary:hover {{
    background-color: #9DD0DE;
    border-color: #9DD0DE;
}}

QPushButton#SmallDanger {{
    background-color: #2B1D20;
    color: {STATUS_RED};
    border: 1px solid #4A282D;
    padding: 5px 10px;
    font-size: 10pt;
    font-weight: 600;
}}

QPushButton#SmallDanger:hover {{
    background-color: #3A2327;
}}

QLineEdit {{
    background-color: {CANVAS_BG};
    color: {TEXT_PRIMARY};
    border: 1px solid {BORDER_COLOR};
    border-radius: 5px;
    padding: 6px 10px;
    font-size: 10.5pt;
}}

QLineEdit:focus {{
    border-color: {PRIMARY_ACCENT};
}}

QComboBox {{
    background-color: {CANVAS_BG};
    color: {TEXT_PRIMARY};
    border: 1px solid {BORDER_COLOR};
    border-radius: 5px;
    padding: 5px 10px;
    font-size: 10.5pt;
    min-width: 170px;
}}

QComboBox:hover {{
    border-color: #3E3E44;
}}

QComboBox::drop-down {{
    border: none;
    width: 20px;
}}

QComboBox QAbstractItemView {{
    background-color: {CARD_BG};
    color: {TEXT_PRIMARY};
    border: 1px solid {BORDER_COLOR};
    selection-background-color: {SECONDARY_BG};
    selection-color: {PRIMARY_ACCENT};
}}

QPlainTextEdit#LogConsole {{
    background-color: #0E0E10;
    color: {TEXT_PRIMARY};
    border: 1px solid {BORDER_COLOR};
    border-radius: 4px;
    font-family: {MONO_FONT_FAMILY};
    font-size: 10pt;
    padding: 6px;
}}

QScrollArea {{
    border: none;
    background-color: transparent;
}}

QScrollBar:vertical {{
    background: {CANVAS_BG};
    width: 10px;
    margin: 0px;
}}

QScrollBar::handle:vertical {{
    background: #333338;
    min-height: 24px;
    border-radius: 5px;
}}

QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{
    height: 0px;
}}

QCheckBox {{
    spacing: 8px;
    background-color: transparent;
    border: none;
}}

QCheckBox::indicator {{
    width: 16px;
    height: 16px;
    border-radius: 3px;
    border: 1px solid #4A4A52;
    background-color: {CANVAS_BG};
}}

QCheckBox::indicator:checked {{
    background-color: {PRIMARY_ACCENT};
    border-color: {PRIMARY_ACCENT};
}}
"""
