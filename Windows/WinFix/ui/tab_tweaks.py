"""Tab 3: Windows 10 UI & Safe Debloat."""

from __future__ import annotations

from PyQt6.QtWidgets import (
    QFrame,
    QHBoxLayout,
    QLabel,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)

from core import actions
from ui.theme import PRIMARY_ACCENT, STATUS_GREEN, TEXT_MUTED


class TweaksTab(QWidget):
    """Windows 10 familiarity tweaks and ARM64-safe consumer bloat/telemetry cleanup."""

    def __init__(self, run_callable_cb, parent=None):
        super().__init__(parent)
        self._run_callable = run_callable_cb
        self._states: dict[str, object] = {}
        self._build_ui()
        self.refresh_states()

    def _build_ui(self) -> None:
        outer = QVBoxLayout(self)
        outer.setContentsMargins(8, 8, 8, 8)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        container = QWidget()
        layout = QVBoxLayout(container)
        layout.setSpacing(12)

        # Card 1: Windows 10 Familiarity Tweaks
        ui_card = QFrame()
        ui_card.setObjectName("CardPanel")
        ui_vbox = QVBoxLayout(ui_card)
        ui_vbox.setContentsMargins(14, 12, 14, 14)
        ui_vbox.setSpacing(10)

        lbl_ui = QLabel("Windows 10 Familiarity Tweaks")
        lbl_ui.setObjectName("SectionTitle")
        ui_vbox.addWidget(lbl_ui)

        self.row_classic_menu, self.badge_classic_menu, self.btn_classic_menu = self._make_toggle_row(
            "Classic Right-Click Menu",
            "Restores the full Windows 10 context menu via HKCU CLSID and restarts Explorer.",
            self._on_toggle_classic_menu,
        )
        ui_vbox.addWidget(self.row_classic_menu)

        self.row_left_tb, self.badge_left_tb, self.btn_left_tb = self._make_toggle_row(
            "Left-Align Taskbar",
            "Moves Start & pinned taskbar icons to the left side (TaskbarAl = 0).",
            self._on_toggle_left_taskbar,
        )
        ui_vbox.addWidget(self.row_left_tb)

        self.row_start_recs, self.badge_start_recs, self.btn_start_recs = self._make_toggle_row(
            "Hide Start Recommendations",
            "Cleans Start menu recommended items, recent files, and account tips.",
            self._on_toggle_start_recs,
        )
        ui_vbox.addWidget(self.row_start_recs)

        self.row_file_ext, self.badge_file_ext, self.btn_file_ext = self._make_toggle_row(
            "Show File Extensions",
            "Displays .exe, .bat, .zip, and all file extensions in File Explorer (HideFileExt = 0).",
            self._on_toggle_file_ext,
        )
        ui_vbox.addWidget(self.row_file_ext)

        self.row_hidden_files, self.badge_hidden_files, self.btn_hidden_files = self._make_toggle_row(
            "Show Hidden Files",
            "Reveals AppData, ProgramData, and hidden folders in File Explorer (Hidden = 1).",
            self._on_toggle_hidden_files,
        )
        ui_vbox.addWidget(self.row_hidden_files)

        # Text Scaling Row
        scale_frame = QFrame()
        scale_frame.setStyleSheet(
            "QFrame { background-color: #141414; border: 1px solid #2C2C30; border-radius: 6px; }"
        )
        scale_hbox = QHBoxLayout(scale_frame)
        scale_hbox.setContentsMargins(12, 10, 12, 10)
        scale_hbox.setSpacing(10)

        scale_info = QVBoxLayout()
        lbl_scale = QLabel("Text Scaling")
        lbl_scale.setStyleSheet("font-weight: 600; border: none;")
        scale_info.addWidget(lbl_scale)
        lbl_scale_desc = QLabel("Quick accessibility text size presets (100%, 125%, 150%).")
        lbl_scale_desc.setStyleSheet(f"font-size: 10pt; color: {TEXT_MUTED}; border: none;")
        scale_info.addWidget(lbl_scale_desc)
        scale_hbox.addLayout(scale_info, 1)

        self.scale_buttons: dict[int, QPushButton] = {}
        for pct in (100, 125, 150):
            btn = QPushButton(f"{pct}%")
            btn.clicked.connect(lambda _=False, p=pct: self._on_set_scale(p))
            self.scale_buttons[pct] = btn
            scale_hbox.addWidget(btn)

        ui_vbox.addWidget(scale_frame)
        layout.addWidget(ui_card)

        # Card 2: Surgical Bloat & Telemetry Cleanup (ARM64-Safe)
        bloat_card = QFrame()
        bloat_card.setObjectName("CardPanel")
        bloat_vbox = QVBoxLayout(bloat_card)
        bloat_vbox.setContentsMargins(14, 12, 14, 14)
        bloat_vbox.setSpacing(10)

        lbl_bloat = QLabel("Surgical Bloat & Telemetry Cleanup (ARM64-Safe)")
        lbl_bloat.setObjectName("SectionTitle")
        bloat_vbox.addWidget(lbl_bloat)

        lbl_safe = QLabel(
            "Strictly preserves Edge WebView2, Bluetooth services, and ARM64 Prism dependencies."
        )
        lbl_safe.setObjectName("MutedLabel")
        lbl_safe.setWordWrap(True)
        bloat_vbox.addWidget(lbl_safe)

        # Remove Consumer Bloat row
        rem_frame = QFrame()
        rem_frame.setStyleSheet(
            "QFrame { background-color: #141414; border: 1px solid #2C2C30; border-radius: 6px; }"
        )
        rem_hbox = QHBoxLayout(rem_frame)
        rem_hbox.setContentsMargins(12, 10, 12, 10)
        rem_info = QVBoxLayout()
        lbl_rem = QLabel("Remove Consumer Bloat")
        lbl_rem.setStyleSheet("font-weight: 600; border: none;")
        rem_info.addWidget(lbl_rem)
        lbl_rem_desc = QLabel(
            "Uninstalls TikTok, Clipchamp, Bing News/Weather, Solitaire, and Xbox clutter via winget."
        )
        lbl_rem_desc.setStyleSheet(f"font-size: 10pt; color: {TEXT_MUTED}; border: none;")
        lbl_rem_desc.setWordWrap(True)
        rem_info.addWidget(lbl_rem_desc)
        rem_hbox.addLayout(rem_info, 1)

        btn_remove_bloat = QPushButton("Remove Consumer Bloat")
        btn_remove_bloat.setObjectName("DangerButton")
        btn_remove_bloat.clicked.connect(self._on_remove_bloat)
        rem_hbox.addWidget(btn_remove_bloat)
        bloat_vbox.addWidget(rem_frame)

        # Disable Ads & Telemetry row
        self.row_ads, self.badge_ads, self.btn_ads = self._make_toggle_row(
            "Disable Ads & Telemetry",
            "Turns off Windows Advertising ID, Start/Settings suggestions, and Copilot taskbar button.",
            self._on_toggle_ads,
        )
        bloat_vbox.addWidget(self.row_ads)

        layout.addWidget(bloat_card)
        layout.addStretch(1)
        scroll.setWidget(container)
        outer.addWidget(scroll)

    def _make_toggle_row(self, title: str, desc: str, slot) -> tuple[QFrame, QLabel, QPushButton]:
        frame = QFrame()
        frame.setStyleSheet(
            "QFrame { background-color: #141414; border: 1px solid #2C2C30; border-radius: 6px; }"
        )
        hbox = QHBoxLayout(frame)
        hbox.setContentsMargins(12, 10, 12, 10)
        hbox.setSpacing(10)

        info = QVBoxLayout()
        lbl_title = QLabel(title)
        lbl_title.setStyleSheet("font-weight: 600; border: none;")
        info.addWidget(lbl_title)
        lbl_desc = QLabel(desc)
        lbl_desc.setStyleSheet(f"font-size: 10pt; color: {TEXT_MUTED}; border: none;")
        lbl_desc.setWordWrap(True)
        info.addWidget(lbl_desc)
        hbox.addLayout(info, 1)

        badge = QLabel("Disabled")
        hbox.addWidget(badge)

        btn = QPushButton("Toggle")
        btn.setObjectName("PrimaryButton")
        btn.clicked.connect(slot)
        hbox.addWidget(btn)

        return frame, badge, btn

    def _style_badge(self, badge: QLabel, enabled: bool) -> None:
        if enabled:
            badge.setText("Enabled")
            badge.setStyleSheet(
                f"font-size: 9.5pt; font-weight: 700; padding: 3px 9px; border-radius: 4px; "
                f"color: #141414; background-color: {STATUS_GREEN}; border: none;"
            )
        else:
            badge.setText("Disabled")
            badge.setStyleSheet(
                "font-size: 9.5pt; font-weight: 700; padding: 3px 9px; border-radius: 4px; "
                "color: #E0E0E0; background-color: #2C2C30; border: none;"
            )

    def refresh_states(self) -> None:
        """Read current registry states and update badges/buttons."""
        self._states = actions.get_tweak_states()

        classic = bool(self._states.get("classic_menu"))
        self._style_badge(self.badge_classic_menu, classic)
        self.btn_classic_menu.setText("Disable" if classic else "Enable")

        left_tb = bool(self._states.get("left_taskbar"))
        self._style_badge(self.badge_left_tb, left_tb)
        self.btn_left_tb.setText("Center" if left_tb else "Enable Left")

        start_recs = bool(self._states.get("hide_start_recs"))
        self._style_badge(self.badge_start_recs, start_recs)
        self.btn_start_recs.setText("Show" if start_recs else "Hide")

        file_ext = bool(self._states.get("show_file_ext"))
        self._style_badge(self.badge_file_ext, file_ext)
        self.btn_file_ext.setText("Hide" if file_ext else "Show")

        hidden_files = bool(self._states.get("show_hidden_files"))
        self._style_badge(self.badge_hidden_files, hidden_files)
        self.btn_hidden_files.setText("Hide" if hidden_files else "Show")

        ads_off = bool(self._states.get("ads_disabled"))
        self._style_badge(self.badge_ads, ads_off)
        self.btn_ads.setText("Restore" if ads_off else "Disable Ads")

        active_scale = int(self._states.get("text_scale", 100))
        for pct, btn in self.scale_buttons.items():
            if pct == active_scale:
                btn.setObjectName("PrimaryButton")
                btn.setStyleSheet(f"background-color: {PRIMARY_ACCENT}; color: #141414; font-weight: 700;")
            else:
                btn.setObjectName("")
                btn.setStyleSheet("")

    # --- Handlers ---

    def _on_toggle_classic_menu(self) -> None:
        target = not bool(self._states.get("classic_menu"))
        self._run_callable(
            "Classic Right-Click Menu",
            lambda log: actions.task_toggle_classic_menu(target, log),
            on_done=lambda _ok, _msg: self.refresh_states(),
        )

    def _on_toggle_left_taskbar(self) -> None:
        target = not bool(self._states.get("left_taskbar"))
        self._run_callable(
            "Left-Align Taskbar",
            lambda log: actions.task_toggle_left_taskbar(target, log),
            on_done=lambda _ok, _msg: self.refresh_states(),
        )

    def _on_toggle_start_recs(self) -> None:
        target = not bool(self._states.get("hide_start_recs"))
        self._run_callable(
            "Hide Start Recommendations",
            lambda log: actions.task_toggle_hide_start_recommendations(target, log),
            on_done=lambda _ok, _msg: self.refresh_states(),
        )

    def _on_toggle_file_ext(self) -> None:
        target = not bool(self._states.get("show_file_ext"))
        self._run_callable(
            "Show File Extensions",
            lambda log: actions.task_toggle_file_extensions(target, log),
            on_done=lambda _ok, _msg: self.refresh_states(),
        )

    def _on_toggle_hidden_files(self) -> None:
        target = not bool(self._states.get("show_hidden_files"))
        self._run_callable(
            "Show Hidden Files",
            lambda log: actions.task_toggle_hidden_files(target, log),
            on_done=lambda _ok, _msg: self.refresh_states(),
        )

    def _on_set_scale(self, percent: int) -> None:
        self._run_callable(
            f"Set Text Scaling ({percent}%)",
            lambda log: actions.task_set_text_scaling(percent, log),
            on_done=lambda _ok, _msg: self.refresh_states(),
        )

    def _on_remove_bloat(self) -> None:
        self._run_callable("Remove Consumer Bloat", actions.task_remove_consumer_bloat)

    def _on_toggle_ads(self) -> None:
        target = not bool(self._states.get("ads_disabled"))
        self._run_callable(
            "Ads & Telemetry Toggle",
            lambda log: actions.task_toggle_ads_telemetry(target, log),
            on_done=lambda _ok, _msg: self.refresh_states(),
        )
