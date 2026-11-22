package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.CalculatorThemeMode

@Immutable
data class CalculatorPalette(
    val isClassicLcd: Boolean,
    val canvasBackground: Color,
    val displayBackground: Color,
    val displayBorder: Color,
    val displayPrimaryText: Color,
    val displaySecondaryText: Color,
    val displayHighlightBg: Color,
    val displaySlotBg: Color,
    val cursorAccent: Color,
    val statusBadgeBg: Color,
    val statusBadgeText: Color,
    val sciKeyBg: Color,
    val sciKeyBorder: Color,
    val sciKeyText: Color,
    val numKeyBg: Color,
    val numKeyText: Color,
    val actionBlueBg: Color,
    val actionBlueText: Color,
    val shiftGold: Color,
    val alphaRed: Color,
    val dpadBg: Color,
    val dpadRing: Color,
    val dpadArrow: Color
)

val MatteDarkPalette = CalculatorPalette(
    isClassicLcd = false,
    canvasBackground = MatteDarkCanvas,
    displayBackground = MatteDarkDisplayBg,
    displayBorder = MatteDarkDisplayBorder,
    displayPrimaryText = MatteDarkDisplayText,
    displaySecondaryText = MatteDarkDisplaySecondary,
    displayHighlightBg = Color(0xFF28343C),
    displaySlotBg = Color(0xFF262A30),
    cursorAccent = MatteDarkCursorCyan,
    statusBadgeBg = Color(0xFF282A30),
    statusBadgeText = MatteDarkDisplayText,
    sciKeyBg = MatteDarkSciKey,
    sciKeyBorder = MatteDarkSciKeyBorder,
    sciKeyText = MatteDarkSciKeyText,
    numKeyBg = MatteDarkNumKey,
    numKeyText = MatteDarkNumKeyText,
    actionBlueBg = MatteDarkActionBlue,
    actionBlueText = MatteDarkActionBlueText,
    shiftGold = MatteDarkShiftGold,
    alphaRed = MatteDarkAlphaRed,
    dpadBg = MatteDarkDpadBg,
    dpadRing = MatteDarkDpadRing,
    dpadArrow = Color(0xFFD6DBE4)
)

val ClassicLcdPalette = CalculatorPalette(
    isClassicLcd = true,
    canvasBackground = ClassicCanvas,
    displayBackground = ClassicDisplayBg,
    displayBorder = ClassicDisplayBorder,
    displayPrimaryText = ClassicDisplayText,
    displaySecondaryText = ClassicDisplaySecondary,
    displayHighlightBg = Color(0xFFAEB9AA),
    displaySlotBg = Color(0xFFB5C0B1),
    cursorAccent = ClassicCursorInk,
    statusBadgeBg = Color(0xFF1C221C),
    statusBadgeText = Color(0xFFC8D1C4),
    sciKeyBg = ClassicSciKey,
    sciKeyBorder = ClassicSciKeyBorder,
    sciKeyText = ClassicSciKeyText,
    numKeyBg = ClassicNumKey,
    numKeyText = ClassicNumKeyText,
    actionBlueBg = ClassicActionBlue,
    actionBlueText = ClassicActionBlueText,
    shiftGold = ClassicShiftGold,
    alphaRed = ClassicAlphaRed,
    dpadBg = ClassicDpadBg,
    dpadRing = ClassicDpadRing,
    dpadArrow = Color(0xFFE0E2E6)
)

val LocalCalculatorPalette = staticCompositionLocalOf { MatteDarkPalette }

private val DarkM3Scheme = darkColorScheme(
    primary = MatteDarkCursorCyan,
    secondary = MatteDarkShiftGold,
    tertiary = MatteDarkAlphaRed,
    background = MatteDarkCanvas,
    surface = MatteDarkDisplayBg,
    onBackground = MatteDarkDisplayText,
    onSurface = MatteDarkDisplayText
)

@Composable
fun Fx991DeXTheme(
    themeMode: CalculatorThemeMode = CalculatorThemeMode.MATTE_DARK,
    content: @Composable () -> Unit
) {
    val palette = when (themeMode) {
        CalculatorThemeMode.MATTE_DARK -> MatteDarkPalette
        CalculatorThemeMode.CLASSIC_LCD -> ClassicLcdPalette
    }
    CompositionLocalProvider(LocalCalculatorPalette provides palette) {
        MaterialTheme(
            colorScheme = DarkM3Scheme,
            typography = Typography,
            content = content
        )
    }
}
