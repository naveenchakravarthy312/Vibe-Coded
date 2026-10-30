package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode {
    SYSTEM,  // Follow system Dark / Light mode setting
    LIGHT,   // Force crisp light palette
    DARK,    // Force Linux matte charcoal (#141414)
    AMOLED   // Force true pitch-black OLED palette (#000000)
}

// 1. Matte Dark Scheme
val MatteDarkColorScheme = darkColorScheme(
    primary = AccentCyanDark,
    onPrimary = CanvasDark,
    primaryContainer = SelectionHighlightDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = AccentBlueDark,
    onSecondary = CanvasDark,
    background = CanvasDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderSubtleDark,
    error = ErrorRedDark,
    onError = Color.White
)

// 2. Pure AMOLED Scheme
val AmoledColorScheme = darkColorScheme(
    primary = AccentCyanDark,
    onPrimary = CanvasAmoled,
    primaryContainer = SelectionHighlightDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = AccentBlueDark,
    onSecondary = CanvasAmoled,
    background = CanvasAmoled,
    onBackground = TextPrimaryDark,
    surface = SurfaceAmoled,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantAmoled,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderSubtleAmoled,
    error = ErrorRedDark,
    onError = Color.White
)

// 3. Crisp Light Scheme
val LightColorScheme = lightColorScheme(
    primary = AccentCyanLight,
    onPrimary = Color.White,
    primaryContainer = SelectionHighlightLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = AccentBlueLight,
    onSecondary = Color.White,
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderSubtleLight,
    error = ErrorRedLight,
    onError = Color.White
)

@Composable
fun VibeTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = false, // Default to false for authentic terminal dark experience, user toggleable
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemInDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }

    val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        // Dynamic Color: Android 12+ System Wallpaper Scheme
        supportsDynamic && isDark && themeMode == AppThemeMode.AMOLED -> {
            dynamicDarkColorScheme(context).copy(
                background = CanvasAmoled,
                surface = SurfaceAmoled,
                surfaceVariant = SurfaceVariantAmoled,
                outline = BorderSubtleAmoled
            )
        }
        supportsDynamic && isDark -> dynamicDarkColorScheme(context).copy(
            background = CanvasDark,
            surface = SurfaceDark,
            surfaceVariant = SurfaceVariantDark,
            outline = BorderSubtleDark
        )
        supportsDynamic && !isDark -> dynamicLightColorScheme(context).copy(
            background = CanvasLight,
            surface = SurfaceLight,
            surfaceVariant = SurfaceVariantLight,
            outline = BorderSubtleLight
        )

        // Static Custom Color Schemes
        themeMode == AppThemeMode.AMOLED -> AmoledColorScheme
        isDark -> MatteDarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScratchpadTypography,
        content = content
    )
}

val androidx.compose.material3.ColorScheme.success: Color
    @Composable
    get() = if (background.red < 0.5f) SuccessGreenDark else SuccessGreenLight

val androidx.compose.material3.ColorScheme.warning: Color
    @Composable
    get() = if (background.red < 0.5f) WarningYellowDark else WarningYellowLight

// Alias for compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    VibeTheme(
        themeMode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
        dynamicColor = dynamicColor,
        content = content
    )
}
