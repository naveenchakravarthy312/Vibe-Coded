package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// 1. Matte Dark Mode (#141414 Linux Aesthetic)
// ==========================================
val CanvasDark            = Color(0xFF141414) // 100% flat matte charcoal canvas
val SurfaceDark           = Color(0xFF1B1B1D) // Bottom sheets & dialog surfaces
val SurfaceVariantDark    = Color(0xFF242428) // Input fields & card surfaces
val BorderSubtleDark      = Color(0xFF2C2C30) // Subtle outlines & divider lines

val TextPrimaryDark       = Color(0xFFE0E0E0) // Soft off-white for high legibility
val TextSecondaryDark     = Color(0xFF8E8E93) // Muted secondary descriptions & timestamps
val TextMutedDark         = Color(0xFF585858) // Whisper-quiet peripheral indicators

// ==========================================
// 2. Pure AMOLED Mode (#000000 Pitch Black)
// ==========================================
val CanvasAmoled          = Color(0xFF000000) // True pitch black canvas (pixels off)
val SurfaceAmoled         = Color(0xFF0A0A0C) // Ultra-deep sheet/dialog surface
val SurfaceVariantAmoled  = Color(0xFF141416) // Subtle elevated card surfaces
val BorderSubtleAmoled    = Color(0xFF1E1E22) // High-contrast hairline dividers

// ==========================================
// 3. Crisp Light Mode (#F5F5F7 Neutral)
// ==========================================
val CanvasLight           = Color(0xFFF5F5F7) // Neutral off-white canvas
val SurfaceLight          = Color(0xFFFFFFFF) // Pure white card & sheet surfaces
val SurfaceVariantLight   = Color(0xFFEBEBF0) // Subtle input field backgrounds
val BorderSubtleLight     = Color(0xFFD6D6DC) // Clean hairline dividers

val TextPrimaryLight      = Color(0xFF18181B) // Deep slate black for crisp readability
val TextSecondaryLight    = Color(0xFF636366) // Balanced secondary gray
val TextMutedLight        = Color(0xFF8E8E93) // Peripheral indicators & quiet labels

// ==========================================
// 4. Accent Tokens (Dark / AMOLED Schemes)
// ==========================================
val AccentCyanDark        = Color(0xFF88C0D0) // Nord cyan for active pills & primary CTA
val AccentBlueDark        = Color(0xFF7AA2F7) // Tokyo-night blue accent
val AccentFrostDark       = Color(0xFF81A1C1) // Slate-blue secondary accent
val SelectionHighlightDark= Color(0xFF323842) // Muted slate text selection
val ErrorRedDark          = Color(0xFFE06C75) // Destructive action & delete warnings
val SuccessGreenDark      = Color(0xFF98C379) // Save indicators & completed streak chips

// ==========================================
// 5. Accent Tokens (Light Scheme - WCAG AA)
// ==========================================
val AccentCyanLight       = Color(0xFF28798D) // Deepened Nord cyan for 4.5:1+ contrast
val AccentBlueLight       = Color(0xFF3B66BC) // Deepened Tokyo blue
val AccentFrostLight      = Color(0xFF4C6F94) // Slate-blue secondary accent
val SelectionHighlightLight = Color(0xFFD8E4F0)// Soft blue-gray text selection
val ErrorRedLight         = Color(0xFFC53B44) // High-contrast destructive warning
val SuccessGreenLight     = Color(0xFF2F7D32) // High-contrast success green
