package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ScratchpadDarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = CanvasDark,
    primaryContainer = SelectionHighlight,
    onPrimaryContainer = TextPrimary,
    secondary = AccentBlue,
    onSecondary = CanvasDark,
    background = CanvasDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = ErrorRed,
    onError = Color.White
)

private val ScratchpadLightColorScheme = lightColorScheme(
    primary = Color(0xFF2C5D88),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2EC),
    onPrimaryContainer = Color(0xFF141414),
    secondary = Color(0xFF3B5973),
    onSecondary = Color.White,
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE2E4E8),
    onSurfaceVariant = Color(0xFF45494F),
    outline = Color(0xFFC4C7CC),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun ScratchpadTheme(
    darkTheme: Boolean = true, // Default to dark aesthetic as in Linux Python scratchpad
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ScratchpadDarkColorScheme else ScratchpadLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
