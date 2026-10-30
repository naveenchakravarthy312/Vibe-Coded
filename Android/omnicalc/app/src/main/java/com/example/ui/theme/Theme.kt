package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode {
    SYSTEM,  // Follow system Dark / Light mode setting
    LIGHT,   // Force crisp light palette
    DARK,    // Force Linux matte charcoal (#141414)
    AMOLED   // Force true pitch-black OLED palette (#000000)
}

data class ThemeConfig(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = true
)

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
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true, // Android 12+ Material You dynamic theming
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
            // AMOLED Hybrid: Pull dynamic accents from Android OS, force background to #000000
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScratchpadTypography,
        content = content
    )
}

@Composable
fun OmniCalcTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    VibeTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        content = content
    )
}
