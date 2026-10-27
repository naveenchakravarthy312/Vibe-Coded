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

val VibeHabitsDarkColorScheme = darkColorScheme(
    primary = DefaultDarkAccentCyan,
    onPrimary = DefaultDarkCanvas,
    primaryContainer = DefaultDarkSelection,
    onPrimaryContainer = DefaultDarkTextPrimary,
    secondary = DefaultDarkAccentBlue,
    onSecondary = DefaultDarkCanvas,
    tertiary = DefaultDarkAccentFrost,
    onTertiary = DefaultDarkCanvas,
    background = DefaultDarkCanvas,
    onBackground = DefaultDarkTextPrimary,
    surface = DefaultDarkSurface,
    onSurface = DefaultDarkTextPrimary,
    surfaceVariant = DefaultDarkSurfaceVariant,
    onSurfaceVariant = DefaultDarkTextSecondary,
    outline = Color(0xFF8E8E93),
    outlineVariant = DefaultDarkBorder,
    error = DefaultDarkErrorRed,
    onError = Color.White
)

val VibeHabitsLightColorScheme = lightColorScheme(
    primary = Color(0xFF006684),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDE9FF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF4D616C),
    onSecondary = Color.White,
    tertiary = Color(0xFF5E5A7D),
    onTertiary = Color.White,
    background = Color(0xFFF6FAFD),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF6FAFD),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDCE4E8),
    onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70787D),
    outlineVariant = Color(0xFFC0C8CD),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun VibeHabitsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> VibeHabitsDarkColorScheme
        else -> VibeHabitsLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VibeHabitsTypography,
        content = content
    )
}
