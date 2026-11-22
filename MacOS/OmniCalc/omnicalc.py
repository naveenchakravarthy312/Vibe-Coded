#!/usr/bin/env python3
"""
OmniCalc — Ultra-minimalist, zero-chrome, 100% keyboard-driven math & unit scratchpad
for Linux and macOS.

Design Aesthetic (matching Scratchpad & DESKTOP-STYLE.md):
- 100% Full-Bleed Matte Dark Canvas (#141414), Pure AMOLED (#000000), or Soft Light (#f2f2f0)
- Zero buttons, zero toolbars, zero menu bars, zero scrollbars, zero borders, zero dividers
- Only the math is visible: type math expressions, variables, scientific functions, or
  unit conversions on any line and see live right-aligned results (= ...) rendered inline.
- 24px Viewport Padding around all four edges and crisp 2px blinking cursor (1000ms cycle)
- 100% Keyboard-Driven across Linux (Wayland / X11) and macOS (Cocoa).
"""

from __future__ import annotations

from dataclasses import dataclass
import datetime
import gc
import json
import math
import os
from pathlib import Path
import re
import sqlite3
import sys
from typing import Any, Callable

try:
    from PyQt6.QtCore import (  # type: ignore[import-not-found,reportMissingImports]
        QEvent,
        QMimeData,
        QPoint,
        QRect,
        QRectF,
        Qt,
        QTimer,
        pyqtSignal,
    )
except ModuleNotFoundError:
    # Self-healing virtualenv detection for Linux and macOS
    _app_dir = Path(__file__).resolve().parent
    _candidate_venvs = [
        _app_dir / ".venv",
        _app_dir.parent / "Notepad" / ".venv",
        _app_dir.parent / "MegaPad" / ".venv",
        _app_dir.parent.parent / "Linux" / "Notepad" / ".venv",
    ]
    for _venv_dir in _candidate_venvs:
        _venv_python = _venv_dir / "bin" / "python3"
        if _venv_python.exists() and Path(sys.prefix).resolve() != _venv_dir.resolve():
            os.execv(str(_venv_python), [str(_venv_python), *sys.argv])
    raise

from PyQt6.QtGui import (  # type: ignore[import-not-found,reportMissingImports]
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
from PyQt6.QtWidgets import (  # type: ignore[import-not-found,reportMissingImports]
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
# Color Palettes (Exact match with Scratchpad / DESKTOP-STYLE.md / ANDROID-STYLE.md)
# ---------------------------------------------------------------------------
PALETTE_DARK = {
    "name": "matte dark",
    "canvas": "#141414",       # Matte Dark Canvas (100% flat charcoal, zero borders)
    "fg": "#e0e0e0",           # Soft Off-White Foreground
    "result": "#88c0d0",       # Nord Cyan live math result
    "sel_bg": "#323842",       # Muted Slate Selection background
    "sel_fg": "#ffffff",       # Muted Slate Selection text
    "meta": "#585858",         # Whisper-Quiet Metadata
    "active": "#e0e0e0",       # Crisp active element state
    "match_bg": "#242932",     # Inactive find match highlight
}

PALETTE_AMOLED = {
    "name": "pure amoled",
    "canvas": "#000000",       # Pitch Black AMOLED Canvas
    "fg": "#e0e0e0",           # Soft Off-White Foreground
    "result": "#88c0d0",       # Nord Cyan live math result
    "sel_bg": "#323842",       # Muted Slate Selection background
    "sel_fg": "#ffffff",       # Muted Slate Selection text
    "meta": "#585858",         # Whisper-Quiet Metadata
    "active": "#e0e0e0",       # Crisp active element state
    "match_bg": "#141416",     # Inactive find match highlight
}

PALETTE_LIGHT = {
    "name": "crisp light",
    "canvas": "#f2f2f0",       # Warm, soft off-white surface
    "fg": "#1e1e1e",           # Deep charcoal text
    "result": "#28798d",       # Deepened WCAG AA Nord Cyan for light mode
    "sel_bg": "#323842",       # Slate selection background
    "sel_fg": "#ffffff",       # White selection text
    "meta": "#888888",         # Soft gray metadata
    "active": "#141414",       # Crisp dark active state
    "match_bg": "#dcdcdc",     # Inactive find match highlight
}

PALETTES = (PALETTE_DARK, PALETTE_AMOLED, PALETTE_LIGHT)


# ---------------------------------------------------------------------------
# Platform Paths, Typography & Layout Constants
# ---------------------------------------------------------------------------
IS_MACOS = sys.platform == "darwin"
APP_DIR = Path(__file__).resolve().parent

if IS_MACOS:
    DATA_DIR = Path.home() / "Library" / "Application Support" / "OmniCalc"
else:
    DATA_DIR = Path.home() / ".local" / "share" / "ephemeral-omnicalc"

DB_PATH = DATA_DIR / "omnicalc.db"
ICON_CANDIDATES = ("icon.png", "icon.svg", "icon.ico")

PREFERRED_MONO_FONTS = [
    "JetBrains Mono",
    "SF Mono",
    "Fira Code",
    "Cascadia Code",
    "Iosevka",
    "Menlo",
    "Source Code Pro",
    "Ubuntu Mono",
    "DejaVu Sans Mono",
    "Monaco",
    "Monospace",
]

PREFERRED_SANS_FONTS = [
    "Roboto",
    "SF Pro Text",
    "Inter",
    "Cantarell",
    "Ubuntu",
    "Noto Sans",
    "Helvetica Neue",
    "DejaVu Sans",
    "Sans Serif",
]

DEFAULT_FONT_SIZE = 14
MIN_FONT_SIZE = 8
MAX_FONT_SIZE = 48
PADDING_PX = 24
RESIZE_MARGIN_PX = 8


# ---------------------------------------------------------------------------
# 11-Category Unit Converter Engine (Ported 1:1 from Android OmniCalc)
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class UnitDef:
    id: str
    name: str
    symbol: str
    category: str
    aliases: tuple[str, ...]
    to_base: Callable[[float], float]
    from_base: Callable[[float], float]


def _linear_unit(
    unit_id: str,
    name: str,
    symbol: str,
    category: str,
    ratio: float,
    aliases: tuple[str, ...] = (),
) -> UnitDef:
    return UnitDef(
        id=unit_id,
        name=name,
        symbol=symbol,
        category=category,
        aliases=aliases,
        to_base=lambda v, r=ratio: v * r,
        from_base=lambda v, r=ratio: v / r,
    )


ALL_UNITS: list[UnitDef] = [
    # 1. Length (base: meter)
    _linear_unit("m", "Meter", "m", "length", 1.0, ("meter", "meters", "metre", "metres")),
    _linear_unit("km", "Kilometer", "km", "length", 1000.0, ("kilometer", "kilometers", "kilometre", "kilometres")),
    _linear_unit("cm", "Centimeter", "cm", "length", 0.01, ("centimeter", "centimeters", "centimetre", "centimetres")),
    _linear_unit("mm", "Millimeter", "mm", "length", 0.001, ("millimeter", "millimeters", "millimetre", "millimetres")),
    _linear_unit("um", "Micrometer", "µm", "length", 1e-6, ("µm", "micrometer", "micrometers", "micron", "microns")),
    _linear_unit("nm", "Nanometer", "nm", "length", 1e-9, ("nanometer", "nanometers")),
    _linear_unit("mi", "Mile", "mi", "length", 1609.344, ("mile", "miles")),
    _linear_unit("yd", "Yard", "yd", "length", 0.9144, ("yard", "yards")),
    _linear_unit("ft", "Foot", "ft", "length", 0.3048, ("foot", "feet")),
    _linear_unit("in", "Inch", "in", "length", 0.0254, ("inch", "inches")),
    _linear_unit("nmi", "Nautical Mile", "NM", "length", 1852.0, ("nm_naut", "nauticalmile", "nauticalmiles")),

    # 2. Mass (base: kilogram)
    _linear_unit("kg", "Kilogram", "kg", "mass", 1.0, ("kilogram", "kilograms", "kilo", "kilos")),
    _linear_unit("g", "Gram", "g", "mass", 0.001, ("gram", "grams")),
    _linear_unit("mg", "Milligram", "mg", "mass", 1e-6, ("milligram", "milligrams")),
    _linear_unit("t", "Metric Ton", "t", "mass", 1000.0, ("ton", "tons", "tonne", "tonnes")),
    _linear_unit("lb", "Pound", "lb", "mass", 0.45359237, ("lbs", "pound", "pounds")),
    _linear_unit("oz", "Ounce", "oz", "mass", 0.028349523125, ("ounce", "ounces")),
    _linear_unit("st", "Stone", "st", "mass", 6.35029318, ("stone", "stones")),
    _linear_unit("ct", "Carat", "ct", "mass", 0.0002, ("carat", "carats")),

    # 3. Temperature (base: Celsius)
    UnitDef(
        id="c",
        name="Celsius",
        symbol="°C",
        category="temperature",
        aliases=("°c", "celsius", "centigrade", "degc"),
        to_base=lambda v: v,
        from_base=lambda v: v,
    ),
    UnitDef(
        id="f",
        name="Fahrenheit",
        symbol="°F",
        category="temperature",
        aliases=("°f", "fahrenheit", "degf"),
        to_base=lambda v: (v - 32.0) * (5.0 / 9.0),
        from_base=lambda v: v * (9.0 / 5.0) + 32.0,
    ),
    UnitDef(
        id="k",
        name="Kelvin",
        symbol="K",
        category="temperature",
        aliases=("kelvin", "kelvins"),
        to_base=lambda v: v - 273.15,
        from_base=lambda v: v + 273.15,
    ),

    # 4. Area (base: square meter)
    _linear_unit("sq_m", "Square Meter", "m²", "area", 1.0, ("m2", "m²", "sqm", "sqmeter", "sqmeters")),
    _linear_unit("sq_km", "Square Kilometer", "km²", "area", 1e6, ("km2", "km²", "sqkm")),
    _linear_unit("sq_cm", "Square Centimeter", "cm²", "area", 1e-4, ("cm2", "cm²", "sqcm")),
    _linear_unit("ha", "Hectare", "ha", "area", 10000.0, ("hectare", "hectares")),
    _linear_unit("ac", "Acre", "ac", "area", 4046.8564224, ("acre", "acres")),
    _linear_unit("sq_ft", "Square Foot", "ft²", "area", 0.09290304, ("ft2", "ft²", "sqft", "sqfoot", "sqfeet")),
    _linear_unit("sq_in", "Square Inch", "in²", "area", 0.00064516, ("in2", "in²", "sqin", "sqinch")),
    _linear_unit("sq_yd", "Square Yard", "yd²", "area", 0.83612736, ("yd2", "yd²", "sqyd", "sqyard")),
    _linear_unit("sq_mi", "Square Mile", "mi²", "area", 2589988.110336, ("mi2", "mi²", "sqmi", "sqmile")),

    # 5. Volume (base: liter)
    _linear_unit("l", "Liter", "L", "volume", 1.0, ("liter", "liters", "litre", "litres")),
    _linear_unit("ml", "Milliliter", "mL", "volume", 0.001, ("milliliter", "milliliters", "millilitre", "millilitres")),
    _linear_unit("cu_m", "Cubic Meter", "m³", "volume", 1000.0, ("m3", "m³", "cum")),
    _linear_unit("cu_cm", "Cubic Centimeter", "cm³", "volume", 0.001, ("cm3", "cm³", "cc")),
    _linear_unit("gal_us", "Gallon (US)", "gal", "volume", 3.785411784, ("gal", "gallon", "gallons")),
    _linear_unit("qt_us", "Quart (US)", "qt", "volume", 0.946352946, ("qt", "quart", "quarts")),
    _linear_unit("pt_us", "Pint (US)", "pt", "volume", 0.473176473, ("pt", "pint", "pints")),
    _linear_unit("cup_us", "Cup (US)", "cup", "volume", 0.2365882365, ("cup", "cups")),
    _linear_unit("fl_oz_us", "Fluid Ounce (US)", "fl oz", "volume", 0.0295735295625, ("floz", "fl_oz", "fluidounce")),
    _linear_unit("tbsp_us", "Tablespoon (US)", "tbsp", "volume", 0.01478676478125, ("tbsp", "tablespoon", "tablespoons")),
    _linear_unit("tsp_us", "Teaspoon (US)", "tsp", "volume", 0.00492892159375, ("tsp", "teaspoon", "teaspoons")),
    _linear_unit("gal_uk", "Gallon (UK)", "imp gal", "volume", 4.54609, ("impgal", "ukgal")),

    # 6. Speed (base: m/s)
    _linear_unit("m_s", "Meter / second", "m/s", "speed", 1.0, ("m/s", "mps")),
    _linear_unit("km_h", "Kilometer / hour", "km/h", "speed", 1.0 / 3.6, ("km/h", "kmh", "kph")),
    _linear_unit("mph", "Mile / hour", "mph", "speed", 0.44704, ("mi/h",)),
    _linear_unit("kn", "Knot", "kn", "speed", 0.514444, ("knot", "knots", "kt")),
    _linear_unit("ft_s", "Foot / second", "ft/s", "speed", 0.3048, ("ft/s", "fps")),

    # 7. Digital Data (base: byte)
    _linear_unit("b", "Bit", "b", "digital", 0.125, ("bit", "bits")),
    _linear_unit("B", "Byte", "B", "digital", 1.0, ("byte", "bytes")),
    _linear_unit("KB", "Kilobyte", "KB", "digital", 1000.0, ("kb", "kilobyte", "kilobytes")),
    _linear_unit("MB", "Megabyte", "MB", "digital", 1e6, ("mb", "megabyte", "megabytes")),
    _linear_unit("GB", "Gigabyte", "GB", "digital", 1e9, ("gb", "gigabyte", "gigabytes")),
    _linear_unit("TB", "Terabyte", "TB", "digital", 1e12, ("tb", "terabyte", "terabytes")),
    _linear_unit("PB", "Petabyte", "PB", "digital", 1e15, ("pb", "petabyte", "petabytes")),
    _linear_unit("KiB", "Kibibyte", "KiB", "digital", 1024.0, ("kib", "kibibyte", "kibibytes")),
    _linear_unit("MiB", "Mebibyte", "MiB", "digital", 1048576.0, ("mib", "mebibyte", "mebibytes")),
    _linear_unit("GiB", "Gibibyte", "GiB", "digital", 1073741824.0, ("gib", "gibibyte", "gibibytes")),

    # 8. Time (base: second)
    _linear_unit("s", "Second", "s", "time", 1.0, ("sec", "secs", "second", "seconds")),
    _linear_unit("ms", "Millisecond", "ms", "time", 0.001, ("msec", "millisecond", "milliseconds")),
    _linear_unit("us", "Microsecond", "µs", "time", 1e-6, ("µs", "usec", "microsecond", "microseconds")),
    _linear_unit("min", "Minute", "min", "time", 60.0, ("mins", "minute", "minutes")),
    _linear_unit("h", "Hour", "h", "time", 3600.0, ("hr", "hrs", "hour", "hours")),
    _linear_unit("d", "Day", "d", "time", 86400.0, ("day", "days")),
    _linear_unit("wk", "Week", "wk", "time", 604800.0, ("week", "weeks")),
    _linear_unit("yr", "Year", "yr", "time", 31536000.0, ("year", "years")),

    # 9. Energy (base: joule)
    _linear_unit("j", "Joule", "J", "energy", 1.0, ("joule", "joules")),
    _linear_unit("kj", "Kilojoule", "kJ", "energy", 1000.0, ("kilojoule", "kilojoules")),
    _linear_unit("cal", "Calorie", "cal", "energy", 4.184, ("calorie", "calories")),
    _linear_unit("kcal", "Kilocalorie", "kcal", "energy", 4184.0, ("kilocalorie", "kilocalories")),
    _linear_unit("wh", "Watt-hour", "Wh", "energy", 3600.0, ("watthour", "watthours")),
    _linear_unit("kwh", "Kilowatt-hour", "kWh", "energy", 3.6e6, ("kilowatthour", "kilowatthours")),
    _linear_unit("ev", "Electronvolt", "eV", "energy", 1.602176634e-19, ("electronvolt", "electronvolts")),
    _linear_unit("btu", "BTU", "BTU", "energy", 1055.06, ()),

    # 10. Pressure (base: Pascal)
    _linear_unit("pa", "Pascal", "Pa", "pressure", 1.0, ("pascal", "pascals")),
    _linear_unit("kpa", "Kilopascal", "kPa", "pressure", 1000.0, ("kilopascal", "kilopascals")),
    _linear_unit("mpa", "Megapascal", "MPa", "pressure", 1e6, ("megapascal", "megapascals")),
    _linear_unit("bar", "Bar", "bar", "pressure", 100000.0, ("bars",)),
    _linear_unit("mbar", "Millibar", "mbar", "pressure", 100.0, ("millibar", "millibars")),
    _linear_unit("psi", "PSI", "psi", "pressure", 6894.757293168, ()),
    _linear_unit("atm", "Atmosphere", "atm", "pressure", 101325.0, ("atmosphere", "atmospheres")),
    _linear_unit("torr", "Torr", "mmHg", "pressure", 133.322, ("mmhg",)),

    # 11. Fuel Economy (base: L/100km)
    UnitDef(
        id="l_100km",
        name="L/100km",
        symbol="L/100km",
        category="fuel",
        aliases=("l/100km", "l100km"),
        to_base=lambda v: v,
        from_base=lambda v: v,
    ),
    UnitDef(
        id="mpg_us",
        name="MPG (US)",
        symbol="mpg",
        category="fuel",
        aliases=("mpg", "mpgus"),
        to_base=lambda v: 235.214583 / v if v > 0 else 0.0,
        from_base=lambda v: 235.214583 / v if v > 0 else 0.0,
    ),
    UnitDef(
        id="mpg_imp",
        name="MPG (Imp)",
        symbol="mpg Imp",
        category="fuel",
        aliases=("mpgimp", "imp_mpg"),
        to_base=lambda v: 282.481 / v if v > 0 else 0.0,
        from_base=lambda v: 282.481 / v if v > 0 else 0.0,
    ),
    UnitDef(
        id="km_l",
        name="km/L",
        symbol="km/L",
        category="fuel",
        aliases=("km/l", "kml"),
        to_base=lambda v: 100.0 / v if v > 0 else 0.0,
        from_base=lambda v: 100.0 / v if v > 0 else 0.0,
    ),
]

# Build lookup tables (case-sensitive first for 'b' bit vs 'B' byte, then lowercase)
_UNIT_BY_EXACT: dict[str, UnitDef] = {}
_UNIT_BY_LOWER: dict[str, UnitDef] = {}

for _u in ALL_UNITS:
    _UNIT_BY_EXACT[_u.id] = _u
    _UNIT_BY_EXACT[_u.symbol] = _u
    _UNIT_BY_LOWER[_u.id.lower()] = _u
    _UNIT_BY_LOWER[_u.symbol.lower()] = _u
    _UNIT_BY_LOWER[_u.name.lower()] = _u
    for _alias in _u.aliases:
        _UNIT_BY_LOWER[_alias.lower()] = _u


def lookup_unit(token: str) -> UnitDef | None:
    cleaned = token.strip()
    if not cleaned:
        return None
    if cleaned in _UNIT_BY_EXACT:
        return _UNIT_BY_EXACT[cleaned]
    return _UNIT_BY_LOWER.get(cleaned.lower())


# ---------------------------------------------------------------------------
# Math & Unit Conversion Evaluation Engine
# ---------------------------------------------------------------------------
def format_number(value: float, max_decimals: int = 8) -> str:
    if math.isnan(value):
        return "undefined"
    if math.isinf(value):
        return "infinity" if value > 0 else "-infinity"
    if abs(value) < 1e-12:
        return "0"
    if abs(value) >= 1e11 or (0.0 < abs(value) < 1e-5):
        formatted = f"{value:.6E}"
        mantissa, exp = formatted.split("E")
        mantissa = mantissa.rstrip("0").rstrip(".")
        exp_sign = "-" if exp.startswith("-") else ""
        exp_digits = exp.lstrip("+-").lstrip("0") or "0"
        return f"{mantissa}E{exp_sign}{exp_digits}"
    rounded = round(value, max_decimals)
    if abs(rounded - round(rounded)) < 1e-12:
        return str(int(round(rounded)))
    text = f"{rounded:.{max_decimals}f}".rstrip("0").rstrip(".")
    return "0" if text in ("-0", "") else text


class ExpressionParser:
    """
    Zero-eval recursive-descent mathematical expression parser supporting:
    - Arithmetic: +, -, *, /, ^, %, !
    - Implicit multiplication: 2pi, 5sin(30), 2(3), (2)(3), 3x
    - Scientific notation: 1E12, 1.5E-6
    - Functions (DEG / RAD): sin, cos, tan, asin, acos, atan, sinh, cosh, tanh,
      sqrt, cbrt, ln, log, log10, log2, exp, abs, floor, ceil, round, min, max, pow, gcd, lcm
    - Constants & dynamic variables: pi, e, tau, phi, ans, sum, avg, and user variables.
    """

    def __init__(
        self,
        text: str,
        angle_mode: str = "DEG",
        variables: dict[str, float] | None = None,
    ) -> None:
        self.str = text
        self.angle_mode = angle_mode.upper()
        self.variables = variables if variables is not None else {}
        self.pos = -1
        self.ch = ""

    def _next_char(self) -> None:
        self.pos += 1
        self.ch = self.str[self.pos] if self.pos < len(self.str) else "\0"

    def _eat(self, target: str) -> bool:
        while self.ch == " ":
            self._next_char()
        if self.ch == target:
            self._next_char()
            return True
        return False

    def parse(self) -> float:
        self._next_char()
        val = self._parse_expression()
        while self.ch == " ":
            self._next_char()
        if self.pos < len(self.str):
            raise ValueError(f"Unexpected '{self.ch}'")
        return val

    def _parse_expression(self) -> float:
        x = self._parse_term()
        while True:
            if self._eat("+"):
                x += self._parse_term()
            elif self._eat("-"):
                x -= self._parse_term()
            else:
                return x

    def _parse_term(self) -> float:
        x = self._parse_unary()
        while True:
            while self.ch == " ":
                self._next_char()
            if self._eat("*"):
                x *= self._parse_unary()
            elif self._eat("/"):
                divisor = self._parse_unary()
                if abs(divisor) < 1e-15:
                    raise ZeroDivisionError("Cannot divide by zero")
                x /= divisor
            elif self.ch == "(" or self.ch.isalpha() or self.ch == "_":
                # Check for binary 'mod' keyword
                saved_pos = self.pos
                saved_ch = self.ch
                ident = self._peek_identifier()
                if ident.lower() == "mod":
                    for _ in range(len(ident)):
                        self._next_char()
                    divisor = self._parse_unary()
                    if abs(divisor) < 1e-15:
                        raise ZeroDivisionError("Cannot divide by zero")
                    x = math.fmod(x, divisor)
                else:
                    self.pos = saved_pos
                    self.ch = saved_ch
                    x *= self._parse_power()
            else:
                return x

    def _peek_identifier(self) -> str:
        i = self.pos
        while i < len(self.str) and (self.str[i].isalnum() or self.str[i] == "_"):
            i += 1
        return self.str[self.pos : i]

    def _parse_unary(self) -> float:
        if self._eat("+"):
            return +self._parse_unary()
        if self._eat("-"):
            return -self._parse_unary()
        return self._parse_power()

    def _parse_power(self) -> float:
        x = self._parse_postfix()
        if self._eat("^"):
            exp = self._parse_unary()
            x = math.pow(x, exp)
        return x

    def _parse_postfix(self) -> float:
        x = self._parse_primary()
        while True:
            if self._eat("!"):
                x = self._factorial(x)
            elif self._eat("%"):
                x /= 100.0
            else:
                break
        return x

    def _parse_primary(self) -> float:
        while self.ch == " ":
            self._next_char()
        start_pos = self.pos

        if self._eat("("):
            x = self._parse_expression()
            self._eat(")")
            return x

        if self._eat("|"):
            x = abs(self._parse_expression())
            self._eat("|")
            return x

        if self.ch.isdigit() or self.ch == ".":
            while self.ch.isdigit() or self.ch == ".":
                self._next_char()
            if self.ch in ("E", "e"):
                saved_pos = self.pos
                saved_ch = self.ch
                self._next_char()
                if self.ch in ("+", "-"):
                    self._next_char()
                if self.ch.isdigit():
                    while self.ch.isdigit():
                        self._next_char()
                else:
                    self.pos = saved_pos
                    self.ch = saved_ch
            num_str = self.str[start_pos : self.pos]
            return float(num_str)

        if self.ch.isalpha() or self.ch == "_":
            while self.ch.isalnum() or self.ch == "_":
                self._next_char()
            name = self.str[start_pos : self.pos]
            lower = name.lower()

            # Check if followed by '(' for function call
            while self.ch == " ":
                self._next_char()
            if self.ch == "(" and lower in self._FUNCTIONS:
                self._eat("(")
                args: list[float] = []
                if not self._eat(")"):
                    while True:
                        args.append(self._parse_expression())
                        if self._eat(")"):
                            break
                        if not self._eat(","):
                            break
                return self._apply_function(lower, args)

            # Check variables & constants
            if name in self.variables:
                return self.variables[name]
            if lower in self.variables:
                return self.variables[lower]
            if lower == "pi":
                return math.pi
            if lower == "e":
                return math.e
            if lower == "tau":
                return math.tau
            if lower == "phi":
                return (1.0 + math.sqrt(5.0)) / 2.0

            if lower in self._FUNCTIONS:
                arg = self._parse_unary()
                return self._apply_function(lower, [arg])

            raise ValueError(f"Unknown symbol: {name}")

        raise ValueError(f"Unexpected '{self.ch}'")

    _FUNCTIONS = {
        "sin", "cos", "tan", "asin", "acos", "atan",
        "sinh", "cosh", "tanh", "deg", "rad",
        "sqrt", "cbrt", "ln", "log", "log10", "log2", "exp",
        "abs", "floor", "ceil", "round", "fact",
        "min", "max", "pow", "gcd", "lcm",
    }

    def _apply_function(self, name: str, args: list[float]) -> float:
        if not args:
            raise ValueError(f"Missing argument for {name}")
        a = args[0]

        if name == "sqrt":
            if a < 0:
                raise ValueError("Negative square root")
            return math.sqrt(a)
        if name == "cbrt":
            return math.copysign(abs(a) ** (1.0 / 3.0), a)
        if name == "sin":
            rad = math.radians(a) if self.angle_mode == "DEG" else a
            return math.sin(rad)
        if name == "cos":
            rad = math.radians(a) if self.angle_mode == "DEG" else a
            return math.cos(rad)
        if name == "tan":
            rad = math.radians(a) if self.angle_mode == "DEG" else a
            return math.tan(rad)
        if name == "asin":
            if a < -1.0 or a > 1.0:
                raise ValueError("Domain error for asin")
            res = math.asin(a)
            return math.degrees(res) if self.angle_mode == "DEG" else res
        if name == "acos":
            if a < -1.0 or a > 1.0:
                raise ValueError("Domain error for acos")
            res = math.acos(a)
            return math.degrees(res) if self.angle_mode == "DEG" else res
        if name == "atan":
            res = math.atan(a)
            return math.degrees(res) if self.angle_mode == "DEG" else res
        if name == "sinh":
            return math.sinh(a)
        if name == "cosh":
            return math.cosh(a)
        if name == "tanh":
            return math.tanh(a)
        if name == "deg":
            return math.degrees(a)
        if name == "rad":
            return math.radians(a)
        if name == "ln":
            if a <= 0:
                raise ValueError("Domain error for ln")
            return math.log(a)
        if name in ("log", "log10"):
            if a <= 0:
                raise ValueError("Domain error for log")
            return math.log10(a)
        if name == "log2":
            if a <= 0:
                raise ValueError("Domain error for log2")
            return math.log2(a)
        if name == "exp":
            return math.exp(a)
        if name == "abs":
            return abs(a)
        if name == "floor":
            return float(math.floor(a))
        if name == "ceil":
            return float(math.ceil(a))
        if name == "round":
            return float(round(a, int(args[1]) if len(args) > 1 else 0))
        if name == "fact":
            return self._factorial(a)
        if name == "min":
            return min(args)
        if name == "max":
            return max(args)
        if name == "pow":
            if len(args) < 2:
                raise ValueError("pow requires 2 arguments")
            return math.pow(args[0], args[1])
        if name == "gcd":
            if len(args) < 2:
                raise ValueError("gcd requires 2 arguments")
            return float(math.gcd(int(args[0]), int(args[1])))
        if name == "lcm":
            if len(args) < 2:
                raise ValueError("lcm requires 2 arguments")
            return float(math.lcm(int(args[0]), int(args[1])))

        raise ValueError(f"Unknown function: {name}")

    @staticmethod
    def _factorial(n: float) -> float:
        if n < 0 or n != math.floor(n):
            raise ValueError("Factorial requires non-negative integer")
        if n > 170:
            return float("inf")
        return float(math.factorial(int(n)))


_CONVERSION_PATTERN = re.compile(
    r"^(?P<expr>.+?)\s+(?P<from_unit>[°µa-zA-Z0-9_/\²\³]+)\s+(?:in|to|as|->|→)\s+(?P<to_unit>[°µa-zA-Z0-9_/\²\³]+(?:\s+imp|\s+us|\s+oz)?)$",
    re.IGNORECASE,
)

_ASSIGNMENT_PATTERN = re.compile(r"^(?P<var>[a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*(?P<expr>.+)$")


@dataclass
class LineEval:
    line_index: int
    raw_text: str
    display_result: str | None = None
    numeric_value: float | None = None
    assigned_var: str | None = None
    is_conversion: bool = False


class OmniCalcEngine:
    """
    Evaluates a multi-line math & unit conversion document line-by-line,
    maintaining running variables (`ans`, `prev`, `_`, `sum`, `avg`, and user assignments).
    """

    @staticmethod
    def sanitize_expression(raw: str) -> str:
        return (
            raw.replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("–", "-")
            .replace("**", "^")
            .replace("π", "pi")
            .replace("τ", "tau")
            .replace("φ", "phi")
            .replace("√", "sqrt")
            .replace("∛", "cbrt")
            .strip()
        )

    @classmethod
    def evaluate_document(cls, text: str, angle_mode: str = "DEG") -> list[LineEval]:
        lines = text.splitlines()
        results: list[LineEval] = []
        variables: dict[str, float] = {}
        numeric_history: list[float] = []

        for idx, raw_line in enumerate(lines):
            # Strip inline comments (# or //)
            code_part = raw_line.split("#", 1)[0].split("//", 1)[0].strip()
            if not code_part:
                results.append(LineEval(line_index=idx, raw_text=raw_line))
                continue

            # Update aggregate variables before evaluating this line
            if numeric_history:
                variables["ans"] = numeric_history[-1]
                variables["prev"] = numeric_history[-1]
                variables["_"] = numeric_history[-1]
                variables["sum"] = sum(numeric_history)
                variables["total"] = variables["sum"]
                variables["avg"] = variables["sum"] / len(numeric_history)
                variables["mean"] = variables["avg"]

            # Check variable assignment: e.g. "r = 5" or "tax = 200 * 15%"
            assigned_var: str | None = None
            assign_match = _ASSIGNMENT_PATTERN.match(code_part)
            if assign_match:
                candidate_var = assign_match.group("var")
                rhs = assign_match.group("expr").strip()
                # If rhs is non-empty, treat as assignment; if trailing '=' (e.g. "2 + 2 ="), strip '='
                if rhs and candidate_var.lower() not in ("pi", "e", "tau", "phi"):
                    assigned_var = candidate_var
                    code_part = rhs
            elif code_part.endswith("="):
                code_part = code_part[:-1].strip()
            elif "=" in code_part:
                # If the user committed "2 + 2 = 4" on a line, evaluate the left-hand side
                lhs_part = code_part.split("=", 1)[0].strip()
                if lhs_part:
                    code_part = lhs_part

            if not code_part:
                results.append(LineEval(line_index=idx, raw_text=raw_line))
                continue

            # Continuation line starting with binary operator (+, *, /, ^) applies to 'ans'
            if (
                numeric_history
                and code_part[0] in ("*", "×", "/", "÷", "^")
            ) or (
                numeric_history
                and code_part[0] in ("+", "-", "−")
                and len(code_part) > 1
                and code_part[1] == " "
            ):
                code_part = f"ans {code_part}"

            # 1. Try Unit Conversion: e.g. "100 km in mi" or "(12 + 8) C to F"
            conv_match = _CONVERSION_PATTERN.match(code_part)
            if conv_match:
                from_u = lookup_unit(conv_match.group("from_unit"))
                to_u = lookup_unit(conv_match.group("to_unit"))
                if from_u is not None and to_u is not None and from_u.category == to_u.category:
                    expr_str = cls.sanitize_expression(conv_match.group("expr"))
                    try:
                        val = ExpressionParser(expr_str, angle_mode, variables).parse()
                        base_val = from_u.to_base(val)
                        out_val = to_u.from_base(base_val)
                        if not (math.isnan(out_val) or math.isinf(out_val)):
                            formatted_conv = f"{format_number(out_val, 6)} {to_u.symbol}"
                            if assigned_var:
                                variables[assigned_var] = out_val
                            numeric_history.append(out_val)
                            results.append(
                                LineEval(
                                    line_index=idx,
                                    raw_text=raw_line,
                                    display_result=formatted_conv,
                                    numeric_value=out_val,
                                    assigned_var=assigned_var,
                                    is_conversion=True,
                                )
                            )
                            continue
                    except Exception:
                        pass

            # 2. Standard / Scientific Math Expression
            sanitized = cls.sanitize_expression(code_part)
            # Don't show live result for a single standalone number literal unless it's a variable assignment or constant
            is_plain_number = bool(re.fullmatch(r"[+-]?\d+(?:\.\d+)?", sanitized))
            try:
                val = ExpressionParser(sanitized, angle_mode, variables).parse()
                if not (math.isnan(val) or math.isinf(val)):
                    formatted_val = format_number(val, 8)
                    if assigned_var:
                        variables[assigned_var] = val
                    numeric_history.append(val)
                    show_display = (not is_plain_number) or (assigned_var is not None)
                    results.append(
                        LineEval(
                            line_index=idx,
                            raw_text=raw_line,
                            display_result=formatted_val if show_display else None,
                            numeric_value=val,
                            assigned_var=assigned_var,
                            is_conversion=False,
                        )
                    )
                    continue
            except Exception:
                pass

            results.append(LineEval(line_index=idx, raw_text=raw_line))

        return results


# ---------------------------------------------------------------------------
# SQLite Repository (Saved Calculations Library & Preferences)
# ---------------------------------------------------------------------------
@dataclass
class SavedSheet:
    id: int
    title: str
    content: str
    last_result: str
    created_at: int
    updated_at: int
    is_pinned: bool


class OmniCalcRepository:
    """SQLite persistence for Saved Math Sheets, Calculation History, and preferences."""

    def __init__(self, db_path: Path | None = None) -> None:
        self.db_path = db_path if db_path is not None else DB_PATH
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._conn = sqlite3.connect(str(self.db_path), check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA journal_mode=WAL;")
        self._conn.execute("PRAGMA synchronous=NORMAL;")
        self._init_db()

    def _init_db(self) -> None:
        with self._conn:
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS sheets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    last_result TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    is_pinned INTEGER NOT NULL DEFAULT 0
                )
                """
            )
            self._conn.execute(
                """
                CREATE TABLE IF NOT EXISTS preferences (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """
            )

    def get_pref(self, key: str, default: str = "") -> str:
        row = self._conn.execute(
            "SELECT value FROM preferences WHERE key = ?", (key,)
        ).fetchone()
        return str(row["value"]) if row else default

    def set_pref(self, key: str, value: str) -> None:
        with self._conn:
            self._conn.execute(
                "INSERT OR REPLACE INTO preferences (key, value) VALUES (?, ?)",
                (key, str(value)),
            )

    def set_prefs(self, mapping: dict[str, str]) -> None:
        with self._conn:
            self._conn.executemany(
                "INSERT OR REPLACE INTO preferences (key, value) VALUES (?, ?)",
                mapping.items(),
            )

    def list_sheets(self, search_query: str = "") -> list[SavedSheet]:
        q = search_query.strip()
        if q:
            like = f"%{q}%"
            rows = self._conn.execute(
                """
                SELECT * FROM sheets
                WHERE title LIKE ? OR content LIKE ? OR last_result LIKE ?
                ORDER BY is_pinned DESC, updated_at DESC
                """,
                (like, like, like),
            ).fetchall()
        else:
            rows = self._conn.execute(
                "SELECT * FROM sheets ORDER BY is_pinned DESC, updated_at DESC"
            ).fetchall()
        return [
            SavedSheet(
                id=int(r["id"]),
                title=str(r["title"]),
                content=str(r["content"]),
                last_result=str(r["last_result"]),
                created_at=int(r["created_at"]),
                updated_at=int(r["updated_at"]),
                is_pinned=bool(r["is_pinned"]),
            )
            for r in rows
        ]

    def get_sheet_by_id(self, sheet_id: int) -> SavedSheet | None:
        r = self._conn.execute(
            "SELECT * FROM sheets WHERE id = ?", (sheet_id,)
        ).fetchone()
        if not r:
            return None
        return SavedSheet(
            id=int(r["id"]),
            title=str(r["title"]),
            content=str(r["content"]),
            last_result=str(r["last_result"]),
            created_at=int(r["created_at"]),
            updated_at=int(r["updated_at"]),
            is_pinned=bool(r["is_pinned"]),
        )

    def upsert_sheet(
        self,
        title: str,
        content: str,
        last_result: str = "",
        sheet_id: int | None = None,
        is_pinned: bool = False,
    ) -> int:
        now_ms = int(datetime.datetime.now().timestamp() * 1000)
        existing = self.get_sheet_by_id(sheet_id) if sheet_id is not None else None
        with self._conn:
            if existing is not None:
                self._conn.execute(
                    """
                    UPDATE sheets
                    SET title = ?, content = ?, last_result = ?, updated_at = ?, is_pinned = ?
                    WHERE id = ?
                    """,
                    (
                        title,
                        content,
                        last_result,
                        now_ms,
                        int(existing.is_pinned),
                        existing.id,
                    ),
                )
                return existing.id
            cur = self._conn.execute(
                """
                INSERT INTO sheets (title, content, last_result, created_at, updated_at, is_pinned)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                (title, content, last_result, now_ms, now_ms, int(is_pinned)),
            )
            return int(cur.lastrowid or 0)

    def restore_sheet(self, sheet: SavedSheet) -> int:
        with self._conn:
            cur = self._conn.execute(
                """
                INSERT OR REPLACE INTO sheets (id, title, content, last_result, created_at, updated_at, is_pinned)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    sheet.id,
                    sheet.title,
                    sheet.content,
                    sheet.last_result,
                    sheet.created_at,
                    sheet.updated_at,
                    int(sheet.is_pinned),
                ),
            )
            return int(cur.lastrowid or sheet.id)

    def toggle_pin(self, sheet: SavedSheet) -> None:
        with self._conn:
            self._conn.execute(
                "UPDATE sheets SET is_pinned = ? WHERE id = ?",
                (0 if sheet.is_pinned else 1, sheet.id),
            )

    def delete_sheet(self, sheet_id: int) -> None:
        with self._conn:
            self._conn.execute("DELETE FROM sheets WHERE id = ?", (sheet_id,))


# ---------------------------------------------------------------------------
# Icon & Bundled Font Helpers
# ---------------------------------------------------------------------------
def _render_calculator_icon_pixmap() -> QPixmap:
    """Render a 256x256 YAMIS-style monochrome calculator icon in memory."""
    pixmap = QPixmap(256, 256)
    pixmap.fill(Qt.GlobalColor.transparent)
    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.RenderHint.Antialiasing)

    path = QPainterPath()
    path.setFillRule(Qt.FillRule.OddEvenFill)

    # Calculator outer body
    path.addRoundedRect(QRectF(44, 24, 168, 208), 24, 24)
    # Screen cutout
    path.addRoundedRect(QRectF(64, 48, 128, 44), 10, 10)
    # 2x2 operator button cutouts
    path.addRoundedRect(QRectF(64, 112, 56, 44), 10, 10)
    path.addRoundedRect(QRectF(136, 112, 56, 44), 10, 10)
    path.addRoundedRect(QRectF(64, 168, 56, 44), 10, 10)
    path.addRoundedRect(QRectF(136, 168, 56, 44), 10, 10)

    painter.setPen(Qt.PenStyle.NoPen)
    painter.setBrush(QColor("#d8dee9"))
    painter.drawPath(path)
    painter.end()
    return pixmap


def load_app_icon() -> QIcon:
    for filename in ICON_CANDIDATES:
        icon_path = APP_DIR / filename
        if icon_path.is_file():
            icon = QIcon(str(icon_path))
            if not icon.isNull():
                return icon
    return QIcon(_render_calculator_icon_pixmap())


_fonts_loaded = False


def load_bundled_fonts() -> None:
    global _fonts_loaded
    if _fonts_loaded or QApplication.instance() is None:
        return
    _fonts_loaded = True

    candidate_dirs = [
        APP_DIR / "fonts",
        APP_DIR.parent.parent / "Android" / "omnicalc" / "app" / "src" / "main" / "res" / "font",
        APP_DIR.parent.parent / "Android" / "scratchpad" / "app" / "src" / "main" / "res" / "font",
    ]
    for folder in candidate_dirs:
        if folder.is_dir():
            for ttf in sorted(folder.glob("*.ttf")):
                QFontDatabase.addApplicationFont(str(ttf))


def select_font(size: int = DEFAULT_FONT_SIZE, is_monospace: bool = True) -> QFont:
    candidates = PREFERRED_MONO_FONTS if is_monospace else PREFERRED_SANS_FONTS
    font = QFont()
    font.setPointSize(size)
    font.setFamilies(candidates)
    if is_monospace:
        font.setStyleHint(QFont.StyleHint.Monospace)
        font.setFixedPitch(True)
    else:
        font.setStyleHint(QFont.StyleHint.SansSerif)
    return font


def format_timestamp(epoch_ms: int) -> str:
    dt = datetime.datetime.fromtimestamp(epoch_ms / 1000.0)
    return dt.strftime("%b %d, %H:%M")


def auto_generate_title(text: str) -> str:
    for line in text.splitlines():
        cleaned = line.strip().lstrip("#").strip()
        if cleaned:
            return cleaned[:28] + ("…" if len(cleaned) > 28 else "")
    now_str = datetime.datetime.now().strftime("%b %d, %H:%M")
    return f"Calc {now_str}"


# ---------------------------------------------------------------------------
# Full-Bleed Math Scratchpad Editor (Only Math & Live Inline Results Visible)
# ---------------------------------------------------------------------------
class OmniCalcEditor(QPlainTextEdit):
    """
    100% Full-canvas, zero-chrome math scratchpad with 24px viewport margins.
    Paints live right-aligned '= <result>' evaluations directly onto each line
    of the viewport without polluting the plain-text buffer.
    """

    fileDropped = pyqtSignal(str)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self._line_evals: list[LineEval] = []
        self._result_color = QColor(PALETTE_DARK["result"])

        # Remove frame and scrollbars completely
        self.setFrameShape(QFrame.Shape.NoFrame)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)

        # Keep each calculation on its own line or wrap at widget width
        self.setLineWrapMode(QPlainTextEdit.LineWrapMode.WidgetWidth)
        self.setWordWrapMode(QTextOption.WrapMode.WrapAtWordBoundaryOrAnywhere)

        # Strict 24px internal padding around all 4 edges
        self.setViewportMargins(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        doc = self.document()
        if doc is not None:
            doc.setDocumentMargin(0)

        # Crisp 2px cursor and 4-space tab stop
        self.setCursorWidth(2)
        self.setTabStopDistance(32.0)
        self.setAcceptDrops(True)

    def set_evaluations(self, evals: list[LineEval], result_hex: str) -> None:
        self._line_evals = evals
        self._result_color = QColor(result_hex)
        vp = self.viewport()
        if vp is not None:
            vp.update()

    def update_tab_stop(self) -> None:
        metrics = QFontMetricsF(self.font())
        self.setTabStopDistance(metrics.horizontalAdvance(" ") * 4.0)

    def paintEvent(self, e: Any) -> None:
        super().paintEvent(e)
        if not self._line_evals:
            return

        vp = self.viewport()
        if vp is None:
            return

        painter = QPainter(vp)
        painter.setRenderHint(QPainter.RenderHint.TextAntialiasing)
        result_font = QFont(self.font())
        result_font.setBold(True)
        painter.setFont(result_font)
        painter.setPen(self._result_color)
        fm = QFontMetricsF(result_font)
        input_fm = QFontMetricsF(self.font())

        block = self.firstVisibleBlock()
        block_num = block.blockNumber()
        offset = self.contentOffset()
        vp_width = vp.width()
        vp_height = vp.height()

        while block.isValid():
            rect = self.blockBoundingGeometry(block).translated(offset)
            if rect.top() > vp_height:
                break

            if block.isVisible() and rect.bottom() >= 0 and block_num < len(self._line_evals):
                ev = self._line_evals[block_num]
                if ev.display_result:
                    res_text = f"= {ev.display_result}"
                    res_width = fm.horizontalAdvance(res_text)
                    line_text = block.text()
                    input_width = input_fm.horizontalAdvance(line_text)

                    # Only draw right-aligned result if it does not collide with typed math
                    available_x = vp_width - res_width - 4
                    if available_x > input_width + 24:
                        draw_rect = QRect(
                            int(available_x),
                            int(rect.top()),
                            int(res_width + 4),
                            int(rect.height()),
                        )
                        painter.drawText(
                            draw_rect,
                            int(Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignTop),
                            res_text,
                        )

            block = block.next()
            block_num += 1

        painter.end()

    def insertFromMimeData(self, source: QMimeData | None) -> None:
        if source is None:
            return
        if source.hasUrls():
            for url in source.urls():
                if url.isLocalFile():
                    local_path = url.toLocalFile()
                    if Path(local_path).is_file():
                        self.fileDropped.emit(local_path)
                        return
        if source.hasText():
            cleaned = source.text().replace("\r\n", "\n").replace("\r", "\n")
            self.insertPlainText(cleaned)
        else:
            super().insertFromMimeData(source)

    def wheelEvent(self, e: Any) -> None:
        if e.modifiers() & Qt.KeyboardModifier.ControlModifier:
            delta = e.angleDelta().y()
            window = self.window()
            adjust = getattr(window, "adjust_font_size", None)
            if callable(adjust):
                if delta > 0:
                    adjust(1)
                elif delta < 0:
                    adjust(-1)
            e.accept()
            return
        super().wheelEvent(e)


class ClickableLabel(QLabel):
    clicked = pyqtSignal()

    def mousePressEvent(self, ev: Any) -> None:
        if ev is not None and ev.button() == Qt.MouseButton.LeftButton:
            self.clicked.emit()
            ev.accept()
            return
        super().mousePressEvent(ev)


# ---------------------------------------------------------------------------
# Zero-Chrome Views: Saved Calculations Library (Ctrl+L) & Shortcuts Help (F1)
# ---------------------------------------------------------------------------
class SavedCalculationsView(QWidget):
    """
    Zero-chrome Saved Calculations / History Library (100% keyboard-driven, zero buttons):
    - Type to filter calculations
    - Up/Down to select, Enter to load into canvas
    - Ctrl+P to pin/star, Ctrl+E to export, Del/Ctrl+D to delete, Ctrl+Z to undo, Esc to exit
    """

    def __init__(
        self,
        repo: OmniCalcRepository,
        on_select_sheet: Callable[[SavedSheet], None],
        on_export_sheet: Callable[[SavedSheet], None],
        on_delete_sheet: Callable[[SavedSheet], None],
        on_close: Callable[[], None],
        on_undo_delete: Callable[[], bool] | None = None,
        parent: QWidget | None = None,
    ) -> None:
        super().__init__(parent)
        self.repo = repo
        self._on_select_sheet = on_select_sheet
        self._on_export_sheet = on_export_sheet
        self._on_delete_sheet = on_delete_sheet
        self._on_close = on_close
        self._on_undo_delete = on_undo_delete

        self._sheets: list[SavedSheet] = []
        self._selected_idx: int = 0
        self._row_labels: list[ClickableLabel] = []

        layout = QVBoxLayout(self)
        layout.setContentsMargins(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
        layout.setSpacing(6)

        search_row = QHBoxLayout()
        search_row.setContentsMargins(0, 0, 0, 6)
        search_row.setSpacing(0)

        self.lbl_search_key = QLabel(f"{'search':<15}")
        search_row.addWidget(self.lbl_search_key)

        self.search_input = QLineEdit()
        self.search_input.setFrame(False)
        self.search_input.setPlaceholderText("type to filter saved calculations…")
        self.search_input.textChanged.connect(self._on_search_changed)
        self.search_input.installEventFilter(self)
        search_row.addWidget(self.search_input, 1)
        layout.addLayout(search_row)

        self.scroll_area = QScrollArea()
        self.scroll_area.setWidgetResizable(True)
        self.scroll_area.setFrameShape(QFrame.Shape.NoFrame)
        self.scroll_area.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.scroll_area.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)

        self.rows_widget = QWidget()
        self.rows_layout = QVBoxLayout(self.rows_widget)
        self.rows_layout.setContentsMargins(0, 0, 0, 0)
        self.rows_layout.setSpacing(4)
        self.scroll_area.setWidget(self.rows_widget)
        layout.addWidget(self.scroll_area, 1)

        self.hint_label = QLabel(
            "↑↓ select · enter load · ctrl+p pin · ctrl+e export · del remove · esc back"
        )
        self.hint_label.setAlignment(
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        )
        layout.addWidget(self.hint_label)

    def open_view(self) -> None:
        self.search_input.clear()
        self.refresh_list()
        self.search_input.setFocus()

    def _on_search_changed(self) -> None:
        self._selected_idx = 0
        self.refresh_list()

    def _activate_row(self, index: int) -> None:
        if 0 <= index < len(self._sheets):
            self._on_select_sheet(self._sheets[index])

    def refresh_list(self) -> None:
        self._sheets = self.repo.list_sheets(self.search_input.text())

        if not self._sheets:
            self._selected_idx = 0
            for lbl in self._row_labels:
                lbl.hide()
            if not hasattr(self, "_empty_lbl"):
                self._empty_lbl = QLabel("no saved calculations", self.rows_widget)
                self._empty_lbl.setObjectName("metaLabel")
                self.rows_layout.addWidget(self._empty_lbl)
                self.rows_layout.addStretch()
            self._empty_lbl.setFont(self.search_input.font())
            self._empty_lbl.show()
            return

        if hasattr(self, "_empty_lbl"):
            self._empty_lbl.hide()

        self._selected_idx = max(0, min(self._selected_idx, len(self._sheets) - 1))
        font = self.search_input.font()
        bold_font = QFont(font)
        bold_font.setBold(True)

        while len(self._row_labels) < len(self._sheets):
            row_idx = len(self._row_labels)
            lbl = ClickableLabel(self.rows_widget)
            lbl.setCursor(Qt.CursorShape.PointingHandCursor)
            lbl.clicked.connect(lambda i=row_idx: self._activate_row(i))
            last_item = (
                self.rows_layout.itemAt(self.rows_layout.count() - 1)
                if self.rows_layout.count() > 0
                else None
            )
            if last_item is not None and last_item.spacerItem() is not None:
                self.rows_layout.insertWidget(self.rows_layout.count() - 1, lbl)
            else:
                self.rows_layout.addWidget(lbl)
            self._row_labels.append(lbl)

        for idx, sheet in enumerate(self._sheets):
            is_sel = idx == self._selected_idx
            row_lbl = self._row_labels[idx]
            row_lbl.setFont(bold_font if is_sel else font)
            row_lbl.setObjectName("activeRow" if is_sel else "metaRow")
            lbl_style = row_lbl.style()
            if lbl_style is not None:
                lbl_style.unpolish(row_lbl)
                lbl_style.polish(row_lbl)

            marker = "› " if is_sel else "  "
            pin_tag = "* " if sheet.is_pinned else ""
            title_col = f"{marker}{pin_tag}{sheet.title[:22]:<24}"
            preview = " ; ".join(
                line.strip() for line in sheet.content.strip().splitlines() if line.strip()
            )[:32]
            res_str = f"= {sheet.last_result}" if sheet.last_result else ""
            meta_str = f"{res_str:<16}  {format_timestamp(sheet.updated_at)}"
            row_lbl.setText(f"{title_col}  {preview:<34}  {meta_str}")
            row_lbl.show()

        for idx in range(len(self._sheets), len(self._row_labels)):
            self._row_labels[idx].hide()

        last_item = (
            self.rows_layout.itemAt(self.rows_layout.count() - 1)
            if self.rows_layout.count() > 0
            else None
        )
        if self.rows_layout.count() == 0 or (
            last_item is not None and last_item.spacerItem() is None
        ):
            self.rows_layout.addStretch()

    def apply_theme_and_font(
        self, palette: dict[str, str], font: QFont, meta_font: QFont
    ) -> None:
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

    def eventFilter(self, a0: Any, a1: Any) -> bool:
        watched, event = a0, a1
        if watched is self.search_input and event.type() == QEvent.Type.KeyPress:
            key = event.key()
            mods = event.modifiers()

            if key == Qt.Key.Key_Down:
                if self._sheets:
                    self._selected_idx = (self._selected_idx + 1) % len(self._sheets)
                    self.refresh_list()
                return True
            if key == Qt.Key.Key_Up:
                if self._sheets:
                    self._selected_idx = (self._selected_idx - 1) % len(self._sheets)
                    self.refresh_list()
                return True
            if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                if self._sheets:
                    self._on_select_sheet(self._sheets[self._selected_idx])
                return True
            if key == Qt.Key.Key_Escape:
                self._on_close()
                return True
            if (mods & Qt.KeyboardModifier.ControlModifier) and key == Qt.Key.Key_P:
                if self._sheets:
                    self.repo.toggle_pin(self._sheets[self._selected_idx])
                    self.refresh_list()
                return True
            if (mods & Qt.KeyboardModifier.ControlModifier) and key == Qt.Key.Key_E:
                if self._sheets:
                    self._on_export_sheet(self._sheets[self._selected_idx])
                return True
            if key == Qt.Key.Key_Delete or (
                (mods & Qt.KeyboardModifier.ControlModifier) and key == Qt.Key.Key_D
            ):
                if self._sheets:
                    self._on_delete_sheet(self._sheets[self._selected_idx])
                    self.refresh_list()
                return True
            if (mods & Qt.KeyboardModifier.ControlModifier) and key == Qt.Key.Key_Z:
                if self._on_undo_delete is not None and self._on_undo_delete():
                    return True
        return super().eventFilter(watched, event)


class ShortcutsHelpView(QWidget):
    """
    Zero-chrome Keyboard Shortcuts & Math Syntax reference rendered in the
    two-column key-value style (#585858 key on left, #e0e0e0 value on right).
    """

    MOD_KEY = "cmd" if IS_MACOS else "ctrl"

    SHORTCUT_ROWS = [
        ("math syntax", "2 + 2 · sin(30)^2 · sqrt(144) · 5! · 200 * 15%"),
        ("unit convert", "100 km in mi · 100 C to F · 2.5 kg in lb · 30 mpg in L/100km"),
        ("variables", "r = 5 · pi * r^2 · ans · sum · avg"),
        ("commit result", f"{MOD_KEY}+enter / {MOD_KEY}+return (insert = result)"),
        ("copy line result", f"{MOD_KEY}+c (when no selection)"),
        ("copy all + results", f"{MOD_KEY}+shift+c"),
        ("angle mode", f"{MOD_KEY}+d / alt+d (deg / rad)"),
        ("new math pad", f"{MOD_KEY}+n"),
        ("open file", f"{MOD_KEY}+o"),
        ("save calculation", f"{MOD_KEY}+s / {MOD_KEY}+shift+s"),
        ("saved history", f"{MOD_KEY}+l / {MOD_KEY}+h"),
        ("find in pad", f"{MOD_KEY}+f"),
        ("wipe buffer", f"{MOD_KEY}+w / {MOD_KEY}+shift+bksp"),
        ("font size", f"{MOD_KEY}+= / {MOD_KEY}+- / {MOD_KEY}+0"),
        ("font family", f"{MOD_KEY}+m (mono / sans)"),
        ("surface theme", f"{MOD_KEY}+shift+d (dark / amoled / light)"),
        ("telemetry corner", f"{MOD_KEY}+i (hidden / minimal / full)"),
        ("frameless mode", f"f11 / {MOD_KEY}+shift+f"),
        ("quit / back", f"esc / {MOD_KEY}+q"),
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
            k_lbl = QLabel(f"{key_text:<20}")
            v_lbl = QLabel(val_text)
            row.addWidget(k_lbl)
            row.addWidget(v_lbl, 1)
            layout.addLayout(row)
            self.key_labels.append(k_lbl)
            self.val_labels.append(v_lbl)

        layout.addStretch()

        self.hint_label = QLabel("omnicalc reference · press esc or f1 to return")
        self.hint_label.setAlignment(
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        )
        layout.addWidget(self.hint_label)

    def apply_theme_and_font(
        self, palette: dict[str, str], font: QFont, meta_font: QFont
    ) -> None:
        self.setStyleSheet(f"background-color: {palette['canvas']}; border: none;")
        for k_lbl in self.key_labels:
            k_lbl.setFont(font)
            k_lbl.setStyleSheet(f"color: {palette['meta']}; background: transparent;")
        for v_lbl in self.val_labels:
            v_lbl.setFont(font)
            v_lbl.setStyleSheet(f"color: {palette['fg']}; background: transparent;")
        self.hint_label.setFont(meta_font)
        self.hint_label.setStyleSheet(
            f"color: {palette['meta']}; background: transparent;"
        )


# ---------------------------------------------------------------------------
# Main OmniCalc Window (100% Math Canvas, Zero Visible Buttons)
# ---------------------------------------------------------------------------
class OmniCalcWindow(QMainWindow):
    """
    100% Full-bleed math & unit scratchpad window for Linux and macOS.
    No buttons, no toolbars, no menu bars — only the math and live inline results
    are visible on the canvas.
    """

    def __init__(
        self, initial_file: str | None = None, start_frameless: bool = False
    ) -> None:
        super().__init__()
        load_bundled_fonts()
        self.repo = OmniCalcRepository()

        # Persisted preferences
        saved_size = self.repo.get_pref("font_size", str(DEFAULT_FONT_SIZE))
        try:
            self._font_size = max(MIN_FONT_SIZE, min(MAX_FONT_SIZE, int(saved_size)))
        except ValueError:
            self._font_size = DEFAULT_FONT_SIZE

        self._is_monospace = self.repo.get_pref("is_monospace", "1") != "0"
        saved_theme_idx = self.repo.get_pref("theme_idx", "0")
        self._theme_idx = int(saved_theme_idx) % len(PALETTES) if saved_theme_idx.isdigit() else 0
        self._angle_mode = self.repo.get_pref("angle_mode", "DEG")
        if self._angle_mode not in ("DEG", "RAD"):
            self._angle_mode = "DEG"

        self._frameless = start_frameless
        # Default 0 = pure math mode (corner label hidden unless transient status or toggled via Ctrl+I)
        self._stat_display_mode = 0

        # Active calculation sheet state
        self._active_sheet_id: int | None = None
        self._active_sheet_title: str = "Untitled"
        self._active_file_path: Path | None = None
        self._is_modified: bool = False
        self._suppress_modified: bool = False
        self._last_deleted_sheet: SavedSheet | None = None
        self._transient_status: str = ""
        self._current_evals: list[LineEval] = []

        # Inline Find & Save states
        self._show_find_bar: bool = False
        self._find_matches: list[tuple[int, int]] = []
        self._current_match_idx: int = -1
        self._suppress_find_recompute: bool = False
        self._show_save_prompt: bool = False
        self._has_closed: bool = False

        self.setWindowTitle("OmniCalc")
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
        viewport = self.editor.viewport()
        if viewport is not None:
            viewport.installEventFilter(self)

        # Load CLI file if passed, else restore draft if any
        if initial_file and Path(initial_file).is_file():
            self.load_file_from_path(Path(initial_file))
        else:
            draft_text = self.repo.get_pref("draft_text", "")
            draft_title = self.repo.get_pref("draft_title", "Untitled")
            draft_id_str = self.repo.get_pref("draft_sheet_id", "")
            if draft_text:
                self._suppress_modified = True
                self.editor.setPlainText(draft_text)
                self._suppress_modified = False
                self._active_sheet_title = draft_title or "Untitled"
                self._active_sheet_id = (
                    int(draft_id_str) if draft_id_str.isdigit() else None
                )
                self._is_modified = self.repo.get_pref("draft_modified", "0") == "1"

        self._recalculate_math()
        self.editor.setFocus(Qt.FocusReason.OtherFocusReason)

    # -----------------------------------------------------------------------
    # Zero-Chrome UI Construction
    # -----------------------------------------------------------------------
    def _build_ui(self) -> None:
        central = QWidget(self)
        layout = QVBoxLayout(central)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        self.stack = QStackedWidget(central)

        # Page 0: 100% Full-bleed Math Scratchpad
        self.editor_page = QWidget()
        ed_layout = QVBoxLayout(self.editor_page)
        ed_layout.setContentsMargins(0, 0, 0, 0)
        ed_layout.setSpacing(0)

        self.editor = OmniCalcEditor(self.editor_page)
        self.editor.textChanged.connect(self._on_editor_text_changed)
        self.editor.fileDropped.connect(lambda p: self.load_file_from_path(Path(p)))
        ed_layout.addWidget(self.editor, 1)

        # Zero-chrome inline Find bar (hidden unless Ctrl+F pressed)
        self.find_bar = QWidget(self.editor_page)
        self.find_bar.setVisible(False)
        find_layout = QHBoxLayout(self.find_bar)
        find_layout.setContentsMargins(PADDING_PX, 4, PADDING_PX, 10)
        find_layout.setSpacing(8)

        self.lbl_find_key = QLabel("find")
        find_layout.addWidget(self.lbl_find_key)

        self.find_input = QLineEdit(self.find_bar)
        self.find_input.setFrame(False)
        self.find_input.setPlaceholderText("expression or number…")
        self.find_input.textChanged.connect(lambda _t: self._recompute_find_matches())
        self.find_input.installEventFilter(self)
        find_layout.addWidget(self.find_input, 1)

        self.match_count_lbl = QLabel("0/0", self.find_bar)
        find_layout.addWidget(self.match_count_lbl)

        ed_layout.addWidget(self.find_bar)

        # Zero-chrome inline Save Calculation prompt (hidden unless Ctrl+S pressed)
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
            "calculation title (leave blank to auto-name · enter to save · esc cancel)"
        )
        self.save_title_input.installEventFilter(self)
        save_layout.addWidget(self.save_title_input, 1)

        ed_layout.addWidget(self.save_bar)

        # Subtle bottom-right telemetry label (hidden by default so only math is visible!)
        self.status_label = ClickableLabel("", self.editor)
        self.status_label.setAttribute(
            Qt.WidgetAttribute.WA_TransparentForMouseEvents, True
        )
        self.status_label.setAlignment(
            Qt.AlignmentFlag.AlignRight | Qt.AlignmentFlag.AlignVCenter
        )
        self.status_label.hide()

        self.stack.addWidget(self.editor_page)

        # Page 1: Saved Calculations / History Library (Ctrl+L / Ctrl+H)
        self.library_view = SavedCalculationsView(
            repo=self.repo,
            on_select_sheet=self._on_sheet_selected_from_library,
            on_export_sheet=self.export_saved_sheet_to_file,
            on_delete_sheet=self.delete_saved_sheet,
            on_close=self._return_to_editor,
            on_undo_delete=self.undo_delete_sheet,
            parent=self.stack,
        )
        self.stack.addWidget(self.library_view)

        # Page 2: Shortcuts & Syntax Reference (F1 / Ctrl+/)
        self.help_view = ShortcutsHelpView(self.stack)
        self.stack.addWidget(self.help_view)

        layout.addWidget(self.stack)
        self.setCentralWidget(central)

        self._status_timer = QTimer(self)
        self._status_timer.setSingleShot(True)
        self._status_timer.timeout.connect(self._clear_transient_status)

        self._draft_timer = QTimer(self)
        self._draft_timer.setSingleShot(True)
        self._draft_timer.timeout.connect(self._flush_draft_state)

    # -----------------------------------------------------------------------
    # Palette & Typography
    # -----------------------------------------------------------------------
    @property
    def palette_dict(self) -> dict[str, str]:
        return PALETTES[self._theme_idx]

    def _apply_theme_and_typography(self) -> None:
        p = self.palette_dict
        self._font = select_font(self._font_size, is_monospace=self._is_monospace)
        self._meta_font = QFont(self._font)
        self._meta_font.setPointSize(max(9, min(11, self._font_size - 2)))

        self.editor.setFont(self._font)
        self.editor.update_tab_stop()
        self.status_label.setFont(self._meta_font)

        for w in (
            self.lbl_find_key,
            self.find_input,
            self.match_count_lbl,
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

        self.repo.set_prefs(
            {
                "font_size": str(self._font_size),
                "is_monospace": "1" if self._is_monospace else "0",
                "theme_idx": str(self._theme_idx),
                "angle_mode": self._angle_mode,
            }
        )

        self._recalculate_math()

    def adjust_font_size(self, delta: int) -> None:
        new_size = max(MIN_FONT_SIZE, min(MAX_FONT_SIZE, self._font_size + delta))
        if new_size != self._font_size:
            self._font_size = new_size
            self._apply_theme_and_typography()

    def reset_typography(self) -> None:
        self._font_size = DEFAULT_FONT_SIZE
        self._is_monospace = True
        self._apply_theme_and_typography()
        self.show_status(f"reset {DEFAULT_FONT_SIZE}pt")

    def toggle_monospace(self) -> None:
        self._is_monospace = not self._is_monospace
        self._apply_theme_and_typography()
        self.show_status("monospace" if self._is_monospace else "sans-serif")

    def toggle_surface_theme(self) -> None:
        self._theme_idx = (self._theme_idx + 1) % len(PALETTES)
        self._apply_theme_and_typography()
        self.show_status(self.palette_dict["name"])

    def toggle_angle_mode(self) -> None:
        self._angle_mode = "RAD" if self._angle_mode == "DEG" else "DEG"
        self.repo.set_pref("angle_mode", self._angle_mode)
        self._recalculate_math()
        self.show_status(f"angle: {self._angle_mode.lower()}")

    # -----------------------------------------------------------------------
    # Live Multi-Line Math & Unit Evaluation
    # -----------------------------------------------------------------------
    def _on_editor_text_changed(self) -> None:
        if not self._suppress_modified:
            self._is_modified = True
            self._draft_timer.start(750)
        if self._show_find_bar and not self._suppress_find_recompute:
            self._recompute_find_matches(scroll_to_active=False)
        self._recalculate_math()

    def _recalculate_math(self) -> None:
        text = self.editor.toPlainText()
        self._current_evals = OmniCalcEngine.evaluate_document(text, self._angle_mode)
        self.editor.set_evaluations(self._current_evals, self.palette_dict["result"])
        self._update_status_label()

    def commit_current_line_result(self) -> None:
        """
        Ctrl+Enter / Ctrl+Return: Appends ' = <result>' to the current line,
        or inserts the numeric result if the line already ends with '='.
        """
        cursor = self.editor.textCursor()
        block = cursor.block()
        line_idx = block.blockNumber()
        if 0 <= line_idx < len(self._current_evals):
            ev = self._current_evals[line_idx]
            if ev.display_result:
                line_text = block.text()
                cursor.movePosition(QTextCursor.MoveOperation.EndOfBlock)
                if line_text.rstrip().endswith("="):
                    cursor.insertText(f" {ev.display_result}")
                elif "=" not in line_text or ev.assigned_var is not None:
                    cursor.insertText(f" = {ev.display_result}")
                self.editor.setTextCursor(cursor)

    def copy_smart(self) -> None:
        """
        Ctrl+C: If text is selected, copy selection as normal.
        If no text is selected, copy the current line's evaluated result!
        """
        cursor = self.editor.textCursor()
        if cursor.hasSelection():
            self.editor.copy()
            return
        line_idx = cursor.block().blockNumber()
        if 0 <= line_idx < len(self._current_evals):
            ev = self._current_evals[line_idx]
            if ev.display_result:
                cb = QApplication.clipboard()
                if cb is not None:
                    cb.setText(ev.display_result)
                self.show_status(f"copied {ev.display_result}")
                return
        # Fallback: copy the most recent result in document
        for ev in reversed(self._current_evals):
            if ev.display_result:
                cb = QApplication.clipboard()
                if cb is not None:
                    cb.setText(ev.display_result)
                self.show_status(f"copied {ev.display_result}")
                return

    def copy_all_with_results(self) -> None:
        """Ctrl+Shift+C: Copies all lines along with their '= result' values."""
        out_lines: list[str] = []
        for ev in self._current_evals:
            raw = ev.raw_text
            if ev.display_result and "=" not in raw:
                out_lines.append(f"{raw} = {ev.display_result}")
            else:
                out_lines.append(raw)
        cb = QApplication.clipboard()
        if cb is not None:
            cb.setText("\n".join(out_lines))
        self.show_status("copied all with results")

    # -----------------------------------------------------------------------
    # File & Saved Calculation Operations
    # -----------------------------------------------------------------------
    def _last_result_summary(self) -> str:
        for ev in reversed(self._current_evals):
            if ev.display_result:
                return ev.display_result
        return ""

    def open_file_dialog(self) -> None:
        start_dir = (
            str(self._active_file_path.parent)
            if self._active_file_path is not None
            else str(Path.home())
        )
        file_path, _ = QFileDialog.getOpenFileName(
            self,
            "Open Math Sheet or JSON History",
            start_dir,
            "Math & Text Files (*.txt *.calc *.md *.json);;All Files (*)",
        )
        if file_path:
            self.load_file_from_path(Path(file_path))

    def load_file_from_path(self, path: Path) -> None:
        try:
            raw = path.read_text(encoding="utf-8", errors="replace")
        except Exception:
            self.show_status(f"error opening {path.name}")
            return

        # Support loading Android OmniCalc exported JSON history directly into the math canvas!
        content = raw
        if path.suffix.lower() == ".json" and raw.lstrip().startswith("["):
            try:
                arr = json.loads(raw)
                if isinstance(arr, list):
                    lines = []
                    for item in arr:
                        if isinstance(item, dict) and "expression" in item:
                            expr = str(item.get("expression", "")).strip()
                            note = str(item.get("note", "")).strip()
                            if expr:
                                lines.append(f"{expr}  # {note}" if note else expr)
                    if lines:
                        content = "\n".join(lines)
            except Exception:
                pass

        self._suppress_modified = True
        self.editor.setPlainText(content)
        self._suppress_modified = False

        self._active_file_path = path
        self._active_sheet_id = None
        self._active_sheet_title = path.stem or path.name
        self._is_modified = False
        self._save_draft_state()
        self._return_to_editor()
        self.show_status(f"opened {path.name}")

    def save_to_file_dialog(self) -> None:
        title = (
            (self._active_sheet_title if self._active_sheet_title != "Untitled" else "")
            or auto_generate_title(self.editor.toPlainText())
        )
        safe_stem = (
            "".join(c if c.isalnum() or c in "-_ ." else "_" for c in title).strip()
            or "omnicalc"
        )
        if self._active_file_path is not None:
            default_path = str(self._active_file_path)
        else:
            if not safe_stem.lower().endswith((".txt", ".calc", ".md", ".json")):
                safe_stem += ".txt"
            default_path = str(Path.home() / safe_stem)

        file_path, selected_filter = QFileDialog.getSaveFileName(
            self,
            "Save Math Sheet",
            default_path,
            "Text Math Sheet (*.txt);;OmniCalc JSON (*.json);;All Files (*)",
        )
        if not file_path:
            return

        target = Path(file_path)
        if not target.suffix:
            target = target.with_suffix(
                ".json" if "JSON" in selected_filter else ".txt"
            )

        try:
            if target.suffix.lower() == ".json":
                payload = []
                now_ms = int(datetime.datetime.now().timestamp() * 1000)
                for ev in self._current_evals:
                    if ev.raw_text.strip() and ev.display_result:
                        payload.append(
                            {
                                "expression": ev.raw_text.strip(),
                                "result": ev.display_result,
                                "category": "CONVERTER" if ev.is_conversion else "CALCULATOR",
                                "details": f"Result: {ev.display_result}",
                                "note": "",
                                "isFavorite": False,
                                "timestamp": now_ms,
                            }
                        )
                target.write_text(json.dumps(payload, indent=2), encoding="utf-8")
            else:
                out_lines = [
                    f"{ev.raw_text} = {ev.display_result}"
                    if (ev.display_result and "=" not in ev.raw_text)
                    else ev.raw_text
                    for ev in self._current_evals
                ]
                target.write_text("\n".join(out_lines), encoding="utf-8")
        except Exception:
            self.show_status("failed to save file")
            return

        self._active_file_path = target
        self._active_sheet_id = None
        self._active_sheet_title = target.stem or target.name
        self._is_modified = False
        self._save_draft_state()
        self.show_status(f"saved {target.name}")

    def export_saved_sheet_to_file(self, sheet: SavedSheet) -> None:
        safe_stem = (
            "".join(c if c.isalnum() or c in "-_ ." else "_" for c in sheet.title).strip()
            or "omnicalc"
        )
        if not safe_stem.lower().endswith(".txt"):
            safe_stem += ".txt"
        default_path = str(Path.home() / safe_stem)
        file_path, _ = QFileDialog.getSaveFileName(
            self,
            "Export Saved Calculation",
            default_path,
            "Text Files (*.txt);;All Files (*)",
        )
        if not file_path:
            return
        target = Path(file_path)
        try:
            target.write_text(sheet.content, encoding="utf-8")
            self.show_status(f"exported {target.name}")
        except Exception:
            self.show_status("export failed")

    def _handle_ctrl_s(self) -> None:
        if self._active_file_path is not None and self._active_file_path.suffix.lower() != ".json":
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

        if self._active_sheet_id is not None:
            self.save_current_sheet(self._active_sheet_title)
            return

        self._open_save_prompt()

    def _open_save_prompt(self) -> None:
        if self._show_find_bar:
            self.toggle_find_bar()
        self._show_save_prompt = True
        self.save_bar.setVisible(True)
        if self._active_sheet_title and self._active_sheet_title != "Untitled":
            self.save_title_input.setText(self._active_sheet_title)
            self.save_title_input.selectAll()
        else:
            self.save_title_input.clear()
        self.save_title_input.setFocus()
        self._position_status_label()

    def _close_save_prompt(self) -> None:
        self._show_save_prompt = False
        self.save_bar.setVisible(False)
        self.editor.setFocus()
        self._position_status_label()

    def save_current_sheet(self, custom_title: str = "") -> None:
        content = self.editor.toPlainText()
        final_title = (
            custom_title.strip()
            or (self._active_sheet_title if self._active_sheet_title != "Untitled" else "")
            or auto_generate_title(content)
        )
        sheet_id = self.repo.upsert_sheet(
            title=final_title,
            content=content,
            last_result=self._last_result_summary(),
            sheet_id=self._active_sheet_id,
        )
        self._active_sheet_id = sheet_id
        self._active_sheet_title = final_title
        self._is_modified = False
        self._save_draft_state()
        if self._show_save_prompt:
            self._close_save_prompt()
        self.show_status(f"saved {final_title.lower()}")

    def toggle_saved_sheets_view(self) -> None:
        if self.stack.currentIndex() == 1:
            self._return_to_editor()
        else:
            self.library_view.open_view()
            self.stack.setCurrentIndex(1)

    def toggle_help_view(self) -> None:
        if self.stack.currentIndex() == 2:
            self._return_to_editor()
        else:
            self.stack.setCurrentIndex(2)

    def _return_to_editor(self) -> None:
        self.stack.setCurrentIndex(0)
        self.editor.setFocus()
        self._recalculate_math()

    def _on_sheet_selected_from_library(self, sheet: SavedSheet) -> None:
        self._suppress_modified = True
        self.editor.setPlainText(sheet.content)
        self._suppress_modified = False

        self._active_sheet_id = sheet.id
        self._active_sheet_title = sheet.title
        self._active_file_path = None
        self._is_modified = False
        self._save_draft_state()
        self._return_to_editor()
        self.show_status(f"loaded {sheet.title.lower()}")

    def delete_saved_sheet(self, sheet: SavedSheet) -> None:
        self._last_deleted_sheet = sheet
        self.repo.delete_sheet(sheet.id)
        if self._active_sheet_id == sheet.id:
            self._active_sheet_id = None
            self._active_sheet_title = "Untitled"
            self._is_modified = self.editor.toPlainText().strip() != ""
        self._save_draft_state()
        self.show_status("deleted · ctrl+z undo")

    def undo_delete_sheet(self) -> bool:
        if self._last_deleted_sheet is not None:
            restored = self._last_deleted_sheet
            self._last_deleted_sheet = None
            self.repo.restore_sheet(restored)
            if self.stack.currentIndex() == 1:
                self.library_view.refresh_list()
            self.show_status(f"restored {restored.title.lower()}")
            return True
        return False

    def new_math_pad(self) -> None:
        self._suppress_modified = True
        self.editor.clear()
        doc = self.editor.document()
        if doc is not None:
            doc.clearUndoRedoStacks()
        self._suppress_modified = False

        self._active_sheet_id = None
        self._active_sheet_title = "Untitled"
        self._active_file_path = None
        self._is_modified = False
        self._save_draft_state()
        self._return_to_editor()

    def wipe_pad_with_undo(self) -> None:
        cursor = self.editor.textCursor()
        cursor.select(QTextCursor.SelectionType.Document)
        cursor.removeSelectedText()
        self._active_sheet_id = None
        self._active_sheet_title = "Untitled"
        self._active_file_path = None
        self._is_modified = False
        self._save_draft_state()

    # -----------------------------------------------------------------------
    # Minimal Inline Find (Ctrl+F)
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
        self._position_status_label()

    def _recompute_find_matches(self, scroll_to_active: bool = True) -> None:
        query = self.find_input.text()
        self._find_matches.clear()

        if query:
            doc = self.editor.document()
            if doc is not None:
                pos = 0
                while True:
                    cursor = doc.find(query, pos)
                    if cursor.isNull():
                        break
                    start = cursor.selectionStart()
                    end = cursor.selectionEnd()
                    self._find_matches.append((start, end))
                    pos = end if end > pos else pos + 1

        if self._find_matches:
            if self._current_match_idx < 0 or self._current_match_idx >= len(self._find_matches):
                self._current_match_idx = 0
            self.match_count_lbl.setText(
                f"{self._current_match_idx + 1}/{len(self._find_matches)}"
            )
            self._highlight_find_matches(scroll_to_active=scroll_to_active)
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

    # -----------------------------------------------------------------------
    # Telemetry & Draft Persistence (Hidden by default so only math is visible)
    # -----------------------------------------------------------------------
    def _save_draft_state(self) -> None:
        self._draft_timer.stop()
        self._flush_draft_state()

    def _flush_draft_state(self) -> None:
        self.repo.set_prefs(
            {
                "draft_text": self.editor.toPlainText(),
                "draft_title": self._active_sheet_title,
                "draft_sheet_id": str(self._active_sheet_id)
                if self._active_sheet_id is not None
                else "",
                "draft_modified": "1" if self._is_modified else "0",
            }
        )

    def show_status(self, msg: str) -> None:
        self._transient_status = msg
        self._update_status_label()
        self._status_timer.start(1800)

    def _clear_transient_status(self) -> None:
        self._transient_status = ""
        self._update_status_label()

    def cycle_stat_display_mode(self) -> None:
        self._stat_display_mode = (self._stat_display_mode + 1) % 3
        self._update_status_label()

    def _update_status_label(self) -> None:
        # Mode 0 (default): Completely hidden unless a transient shortcut notification is active,
        # ensuring 100% pure math visibility on the canvas.
        if self._stat_display_mode == 0 and not self._transient_status:
            self.status_label.hide()
            return

        vals = [ev.numeric_value for ev in self._current_evals if ev.numeric_value is not None]
        total_sum = sum(vals) if vals else 0.0
        avg_val = (total_sum / len(vals)) if vals else 0.0

        parts: list[str] = []
        if self._transient_status:
            parts.append(self._transient_status)

        if self._stat_display_mode == 1:
            parts.append(self._angle_mode.lower())
            if vals:
                parts.append(f"sum {format_number(total_sum, 4)}")
        elif self._stat_display_mode == 2:
            parts.append(self._angle_mode.lower())
            parts.append(f"{len(vals)} exprs")
            if vals:
                parts.append(f"sum {format_number(total_sum, 4)}")
                parts.append(f"avg {format_number(avg_val, 4)}")

        if parts:
            self.status_label.setText(" · ".join(parts))
            self.status_label.show()
            self._position_status_label()
        else:
            self.status_label.hide()

    def _position_status_label(self) -> None:
        if not self.status_label.isVisible():
            return
        self.status_label.adjustSize()
        margin_right = 16
        margin_bottom = 10
        x = max(0, self.editor.width() - self.status_label.width() - margin_right)
        y = max(0, self.editor.height() - self.status_label.height() - margin_bottom)
        self.status_label.move(x, y)

    def resizeEvent(self, a0: Any) -> None:
        super().resizeEvent(a0)
        self._position_status_label()

    def showEvent(self, a0: Any) -> None:
        super().showEvent(a0)
        self._position_status_label()
        self.editor.setFocus()

    # -----------------------------------------------------------------------
    # Keyboard Shortcuts & Frameless Window Management
    # -----------------------------------------------------------------------
    def _bind_shortcuts(self, sequences: list[QKeySequence], callback: Callable[[], None]) -> None:
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

        # File & Saved Calculations Library shortcuts
        self._bind_shortcuts([QKeySequence("Ctrl+N")], self.new_math_pad)
        self._bind_shortcuts([QKeySequence("Ctrl+O")], self.open_file_dialog)
        self._bind_shortcuts([QKeySequence("Ctrl+S")], self._handle_ctrl_s)
        self._bind_shortcuts([QKeySequence("Ctrl+Shift+S")], self.save_to_file_dialog)
        self._bind_shortcuts(
            [QKeySequence("Ctrl+L"), QKeySequence("Ctrl+H"), QKeySequence("Ctrl+Shift+O")],
            self.toggle_saved_sheets_view,
        )

        # Math operations: Commit result, Toggle DEG/RAD, Copy All with results
        self._bind_shortcuts(
            [QKeySequence("Ctrl+Return"), QKeySequence("Ctrl+Enter")],
            self.commit_current_line_result,
        )
        self._bind_shortcuts(
            [QKeySequence("Ctrl+D"), QKeySequence("Alt+D")],
            self.toggle_angle_mode,
        )
        self._bind_shortcuts([QKeySequence("Ctrl+Shift+C")], self.copy_all_with_results)

        # Clear / Wipe buffer
        self._bind_shortcuts(
            [QKeySequence("Ctrl+W"), QKeySequence("Ctrl+Shift+Backspace")],
            self.wipe_pad_with_undo,
        )

        # Find in pad
        self._bind_shortcuts([QKeySequence("Ctrl+F")], self.toggle_find_bar)

        # Typography scaling, font family toggle, surface theme toggle, telemetry cycle
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

        # Shortcuts & syntax reference overlay (F1 / Ctrl+/)
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

    def eventFilter(self, a0: Any, a1: Any) -> bool:
        watched, event = a0, a1
        if event.type() == QEvent.Type.KeyPress:
            key = event.key()
            mods = event.modifiers()

            # Smart Ctrl+C on editor: copy line result when no text is selected
            if (
                watched is self.editor
                and (mods & Qt.KeyboardModifier.ControlModifier)
                and not (mods & Qt.KeyboardModifier.ShiftModifier)
                and key == Qt.Key.Key_C
            ):
                if not self.editor.textCursor().hasSelection():
                    self.copy_smart()
                    return True

            if watched is self.find_input:
                if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                    if mods & Qt.KeyboardModifier.ShiftModifier:
                        self.find_previous()
                    else:
                        self.find_next()
                    return True
                if key == Qt.Key.Key_Escape:
                    self.toggle_find_bar()
                    return True

            if watched is self.save_title_input:
                if key in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
                    self.save_current_sheet(self.save_title_input.text())
                    return True
                if key == Qt.Key.Key_Escape:
                    self._close_save_prompt()
                    return True

            if (
                self.stack.currentIndex() == 1
                and (mods & Qt.KeyboardModifier.ControlModifier)
                and key == Qt.Key.Key_Z
                and self._last_deleted_sheet is not None
            ):
                self.undo_delete_sheet()
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
        self._suppress_modified = True
        try:
            doc = self.editor.document()
            if doc is not None:
                doc.clearUndoRedoStacks()
                doc.clear()
            self.editor.clear()
        finally:
            self._suppress_modified = False
        gc.collect()

    def _finalize_close(self) -> None:
        if self._has_closed:
            return
        self._has_closed = True
        self._save_draft_state()
        self._wipe_memory()

    def instant_quit(self) -> None:
        self._finalize_close()
        self.close()
        QApplication.quit()

    def closeEvent(self, a0: Any) -> None:
        event = a0
        self._finalize_close()
        event.accept()


def run_self_tests() -> int:
    """Verify the OmniCalc math & unit conversion engine."""
    sample = "\n".join(
        [
            "2 + 2",
            "10 - 4.5 * 2",
            "(12 + 8) / 4",
            "sqrt(144) + 5!",
            "sin(30)^2",
            "200 * 15%",
            "1E12 + 5",
            "r = 5",
            "pi * r^2",
            "+ 10",
            "100 km in mi",
            "100 C to F",
            "0 C to K",
            "2.5 kg in g",
        ]
    )
    evals = OmniCalcEngine.evaluate_document(sample, angle_mode="DEG")
    expected = {
        0: "4",
        1: "1",
        2: "5",
        3: "132",
        4: "0.25",
        5: "30",
        6: "1E12",
        7: "5",
        8: "78.53981634",
        9: "88.53981634",
        10: "62.137119 mi",
        11: "212 °F",
        12: "273.15 K",
        13: "2500 g",
    }
    for idx, exp_str in expected.items():
        got = evals[idx].display_result
        assert got == exp_str, f"Line {idx} ({evals[idx].raw_text}): expected '{exp_str}', got '{got}'"
    print("OmniCalc self-test passed (14/14 assertions).")
    return 0


def main() -> int:
    if "--self-test" in sys.argv:
        return run_self_tests()

    QApplication.setDesktopSettingsAware(True)
    app = QApplication(sys.argv)
    app.setApplicationName("omnicalc")
    app.setWindowIcon(load_app_icon())
    app.setCursorFlashTime(1000)

    def commit_data_noop(manager: Any) -> None:
        manager.cancel()

    app.commitDataRequest.connect(commit_data_noop)
    app.saveStateRequest.connect(lambda manager: None)

    start_frameless = "--frameless" in sys.argv or "-f" in sys.argv
    file_args = [
        arg for arg in sys.argv[1:] if not arg.startswith("-") and Path(arg).exists()
    ]
    initial_file = file_args[0] if file_args else None

    window = OmniCalcWindow(
        initial_file=initial_file, start_frameless=start_frameless
    )
    window.show()
    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
