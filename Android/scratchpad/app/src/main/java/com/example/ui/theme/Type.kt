package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Bundled TTF font families to prevent OEM/Samsung custom system fonts from overriding typography
val ScratchpadMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold)
)

val ScratchpadSansFontFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.SemiBold),
    Font(R.font.roboto_bold, FontWeight.Bold)
)

private val BaseTypography = Typography()

// Modern monospace and clean typography aligned with Linux Ephemeral Pad
val Typography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = ScratchpadSansFontFamily),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = ScratchpadSansFontFamily),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = ScratchpadSansFontFamily),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = ScratchpadSansFontFamily),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = ScratchpadSansFontFamily),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = ScratchpadSansFontFamily),
    titleLarge = TextStyle(
        fontFamily = ScratchpadSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ScratchpadSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = ScratchpadSansFontFamily),
    bodyLarge = TextStyle(
        fontFamily = ScratchpadMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ScratchpadMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = ScratchpadMonoFontFamily),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = ScratchpadSansFontFamily),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = ScratchpadMonoFontFamily),
    labelSmall = TextStyle(
        fontFamily = ScratchpadMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)
