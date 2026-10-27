package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fallback palette constants for pre-Android 12 (API < 31)
val DefaultDarkCanvas         = Color(0xFF141414)
val DefaultDarkSurface        = Color(0xFF1B1B1D)
val DefaultDarkSurfaceVariant = Color(0xFF242428)
val DefaultDarkBorder         = Color(0xFF2C2C30)
val DefaultDarkTextPrimary    = Color(0xFFE0E0E0)
val DefaultDarkTextSecondary  = Color(0xFF8E8E93)
val DefaultDarkAccentCyan     = Color(0xFF88C0D0)
val DefaultDarkAccentBlue     = Color(0xFF7AA2F7)
val DefaultDarkAccentFrost    = Color(0xFF81A1C1)
val DefaultDarkSelection      = Color(0xFF323842)
val DefaultDarkErrorRed       = Color(0xFFE06C75)

// System-aware semantic colors mapped directly to MaterialTheme.colorScheme
val CanvasDark: Color
    @Composable get() = MaterialTheme.colorScheme.background

val SurfaceDark: Color
    @Composable get() = MaterialTheme.colorScheme.surface

val SurfaceVariantDark: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val BorderSubtle: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant

val TextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

val TextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val TextMuted: Color
    @Composable get() = MaterialTheme.colorScheme.outline

val AccentCyan: Color
    @Composable get() = MaterialTheme.colorScheme.primary

val OnAccentCyan: Color
    @Composable get() = MaterialTheme.colorScheme.onPrimary

val AccentBlue: Color
    @Composable get() = MaterialTheme.colorScheme.secondary

val AccentFrost: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary

val SelectionHighlight: Color
    @Composable get() = MaterialTheme.colorScheme.primaryContainer

val ErrorRed: Color
    @Composable get() = MaterialTheme.colorScheme.error

val OnErrorRed: Color
    @Composable get() = MaterialTheme.colorScheme.onError

val SuccessGreen: Color
    @Composable get() = MaterialTheme.colorScheme.primary

val OnSuccessGreen: Color
    @Composable get() = MaterialTheme.colorScheme.onPrimary
