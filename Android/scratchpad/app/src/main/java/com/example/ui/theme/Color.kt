package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Default Fallback Minimalist Dark Scratchpad Palette (Classic Nordic Cyan)
val DefaultCanvasDark = Color(0xFF141414)
val DefaultSurfaceDark = Color(0xFF1B1B1D)
val DefaultSurfaceVariantDark = Color(0xFF242428)
val DefaultBorderSubtle = Color(0xFF2C2C30)

val DefaultTextPrimary = Color(0xFFE0E0E0)
val DefaultTextSecondary = Color(0xFF8E8E93)
val DefaultTextMuted = Color(0xFF585858)

val DefaultAccentBlue = Color(0xFF7AA2F7)
val DefaultAccentCyan = Color(0xFF88C0D0)
val DefaultAccentFrost = Color(0xFF81A1C1)
val DefaultSelectionHighlight = Color(0xFF323842)
val DefaultErrorRed = Color(0xFFE06C75)
val DefaultSuccessGreen = Color(0xFF98C379)

data class ScratchpadColorPalette(
    val canvas: Color = DefaultCanvasDark,
    val surface: Color = DefaultSurfaceDark,
    val surfaceVariant: Color = DefaultSurfaceVariantDark,
    val border: Color = DefaultBorderSubtle,
    val textPrimary: Color = DefaultTextPrimary,
    val textSecondary: Color = DefaultTextSecondary,
    val textMuted: Color = DefaultTextMuted,
    val accentCyan: Color = DefaultAccentCyan,
    val accentBlue: Color = DefaultAccentBlue,
    val accentFrost: Color = DefaultAccentFrost,
    val selectionHighlight: Color = DefaultSelectionHighlight,
    val error: Color = DefaultErrorRed,
    val success: Color = DefaultSuccessGreen,
    val isDark: Boolean = true,
    val isAmoled: Boolean = false
)

val LocalScratchpadColors = staticCompositionLocalOf {
    ScratchpadColorPalette()
}

val CanvasDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.canvas

val SurfaceDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.surface

val SurfaceVariantDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.surfaceVariant

val BorderSubtle: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.border

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.textSecondary

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.textMuted

val AccentCyan: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.accentCyan

val AccentBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.accentBlue

val AccentFrost: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.accentFrost

val SelectionHighlight: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.selectionHighlight

val ErrorRed: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.error

val SuccessGreen: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalScratchpadColors.current.success
