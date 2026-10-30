package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ScratchpadDarkColorScheme = darkColorScheme(
    primary = DefaultAccentCyan,
    onPrimary = DefaultCanvasDark,
    primaryContainer = DefaultSelectionHighlight,
    onPrimaryContainer = DefaultTextPrimary,
    secondary = DefaultAccentBlue,
    onSecondary = DefaultCanvasDark,
    background = DefaultCanvasDark,
    onBackground = DefaultTextPrimary,
    surface = DefaultSurfaceDark,
    onSurface = DefaultTextPrimary,
    surfaceVariant = DefaultSurfaceVariantDark,
    onSurfaceVariant = DefaultTextSecondary,
    outline = DefaultBorderSubtle,
    error = DefaultErrorRed,
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val effectiveDark = if (amoledMode) true else darkTheme

    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        effectiveDark -> ScratchpadDarkColorScheme
        else -> ScratchpadLightColorScheme
    }

    val finalColorScheme = if (amoledMode && effectiveDark) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF121212),
            surfaceContainer = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainerHigh = Color(0xFF121212),
            surfaceContainerLowest = Color.Black,
            outline = baseColorScheme.outline.copy(alpha = 0.35f),
            outlineVariant = Color(0xFF222222)
        )
    } else {
        baseColorScheme
    }

    val customPalette = if (amoledMode && effectiveDark) {
        ScratchpadColorPalette(
            canvas = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF121212),
            border = Color(0xFF222222),
            textPrimary = finalColorScheme.onBackground,
            textSecondary = finalColorScheme.onSurfaceVariant,
            textMuted = finalColorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            accentCyan = finalColorScheme.primary,
            accentBlue = finalColorScheme.secondary,
            accentFrost = finalColorScheme.tertiary,
            selectionHighlight = finalColorScheme.primaryContainer.copy(alpha = 0.45f),
            error = finalColorScheme.error,
            success = DefaultSuccessGreen,
            isDark = true,
            isAmoled = true
        )
    } else if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ScratchpadColorPalette(
            canvas = finalColorScheme.background,
            surface = finalColorScheme.surface,
            surfaceVariant = finalColorScheme.surfaceVariant,
            border = finalColorScheme.outlineVariant,
            textPrimary = finalColorScheme.onBackground,
            textSecondary = finalColorScheme.onSurfaceVariant,
            textMuted = finalColorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            accentCyan = finalColorScheme.primary,
            accentBlue = finalColorScheme.secondary,
            accentFrost = finalColorScheme.tertiary,
            selectionHighlight = finalColorScheme.primaryContainer,
            error = finalColorScheme.error,
            success = DefaultSuccessGreen,
            isDark = effectiveDark,
            isAmoled = false
        )
    } else if (effectiveDark) {
        ScratchpadColorPalette(
            canvas = DefaultCanvasDark,
            surface = DefaultSurfaceDark,
            surfaceVariant = DefaultSurfaceVariantDark,
            border = DefaultBorderSubtle,
            textPrimary = DefaultTextPrimary,
            textSecondary = DefaultTextSecondary,
            textMuted = DefaultTextMuted,
            accentCyan = DefaultAccentCyan,
            accentBlue = DefaultAccentBlue,
            accentFrost = DefaultAccentFrost,
            selectionHighlight = DefaultSelectionHighlight,
            error = DefaultErrorRed,
            success = DefaultSuccessGreen,
            isDark = true,
            isAmoled = false
        )
    } else {
        ScratchpadColorPalette(
            canvas = Color(0xFFF7F8FA),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE2E4E8),
            border = Color(0xFFC4C7CC),
            textPrimary = Color(0xFF191C1E),
            textSecondary = Color(0xFF45494F),
            textMuted = Color(0xFF757575),
            accentCyan = Color(0xFF2C5D88),
            accentBlue = Color(0xFF3B5973),
            accentFrost = Color(0xFF4F6F8F),
            selectionHighlight = Color(0xFFD8E2EC),
            error = Color(0xFFBA1A1A),
            success = DefaultSuccessGreen,
            isDark = false,
            isAmoled = false
        )
    }

    CompositionLocalProvider(LocalScratchpadColors provides customPalette) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            content = content
        )
    }
}
