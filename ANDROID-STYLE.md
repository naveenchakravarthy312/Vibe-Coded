# Android Design System & Style Specification (Jetpack Compose)

This document defines the mobile styling, architecture, and interaction guidelines for Android applications in the **Vibe-Coding** ecosystem. It provides comprehensive support for adaptive theming: **System Auto**, **Matte Dark**, **Pure AMOLED**, and **Crisp Light** modes, with full support for Android 12+ **Material You Dynamic Colors**.

---

## 1. Core Principles

* **Distraction-Free Full-Bleed Canvas:** Edge-to-edge rendering across all modes. The canvas spans the full screen, adapting between `#141414` matte charcoal, `#000000` pitch black for OLED efficiency, and `#F5F5F7` crisp neutral light.
* **Low-Profile Chrome:** Compact top bars (`48dp`) and software keyboard accessory strips that yield maximum vertical screen estate to content.
* **Immunity to OEM Font Overrides:** All core typography bundles local TrueType fonts (`JetBrains Mono` and `Roboto`) so custom manufacturer skins (e.g., Samsung OneUI) cannot degrade line heights or monospace alignments.
* **Adaptive Theme Engine:** First-class user control over display modes:
  * **System Default:** Automatically switches between Light and Dark based on OS preferences.
  * **Matte Dark:** Linux-inspired `#141414` charcoal aesthetic.
  * **AMOLED:** True `#000000` pitch black with ultra-low power consumption on OLED panels.
  * **Light Mode:** High-contrast, clean `#F5F5F7` canvas with tailored WCAG AA-compliant accent colors.
  * **Dynamic System Color:** Seamless integration with Android 12+ (API 31+) Material You wallpaper extraction.
* **Bottom Sheet Overlays:** Non-modal sheets (`SavedNotesSheet`, `EditSheet`) anchor smoothly to the bottom edge without jarring full-screen route shifts.

---

## 2. Color Palette & Jetpack Compose Tokens

Defined in `ui/theme/Color.kt`:

### 2.1 Static Color Tokens

```kotlin
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
```

---

## 3. Theme Configuration & Dynamic System Color

Defined in `ui/theme/Theme.kt`:

### 3.1 Theme Mode Enum

```kotlin
package com.example.ui.theme

enum class AppThemeMode {
    SYSTEM,  // Follow system Dark / Light mode setting
    LIGHT,   // Force crisp light palette
    DARK,    // Force Linux matte charcoal (#141414)
    AMOLED   // Force true pitch-black OLED palette (#000000)
}
```

### 3.2 Material 3 Color Schemes

```kotlin
package com.example.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
```

### 3.3 Dynamic Theme Composable (`AppTheme`)

The theme wrapper resolves user preferences against Android 12+ dynamic color capabilities and system settings:

```kotlin
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScratchpadTypography,
        content = content
    )
}
```

---

## 4. Typography & Bundled Fonts

Defined in `ui/theme/Type.kt`:

### Bundled Font Families (`res/font/`)

Local TrueType fonts prevent OEM skin customization (e.g., Samsung OneUI or Xiaomi HyperOS) from altering line heights or alignment:

```kotlin
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
```

### Text Styles Hierarchy

* **Editor Body Large (`bodyLarge`):** `15.sp`, line height `22.sp`, `ScratchpadMonoFontFamily`
* **Editor Body Medium (`bodyMedium`):** `13.sp`, line height `18.sp`, `ScratchpadMonoFontFamily`
* **Peripheral Counter / Quiet Meta (`labelSmall`):** `11.sp`, line height `14.sp`, `ScratchpadMonoFontFamily`, color mapped to `MaterialTheme.colorScheme.onSurfaceVariant`
* **Headers & Section Titles (`titleLarge`):** `20.sp`, line height `26.sp`, `ScratchpadSansFontFamily` (SemiBold)

---

## 5. Mobile Layout & Inset Handling

```
+-----------------------------------------------------------+
| [Status Bar Inset: statusBarsPadding()]                   |
| [ScratchpadTopBar: Title · Mode · Export · Menu]          |
+-----------------------------------------------------------+
|                                                           |
|                     SCROLLABLE CANVAS                     |
|                                                           |
+-----------------------------------------------------------+
| [Accessory Bar: Action Chips · Counters · Metrics]        |
| [IME / Soft Keyboard Inset: imePadding()]                 |
| [Navigation Bar Inset: navigationBarsPadding()]           |
+-----------------------------------------------------------+
```

* **Edge-to-Edge:** Activity sets `enableEdgeToEdge()`. Root containers strictly bind `statusBarsPadding()`, `imePadding()`, and `navigationBarsPadding()`.
* **Top Bar (`TopAppBar`):** Fixed `48dp` height, background bound to `MaterialTheme.colorScheme.background` with a single 1dp bottom divider using `MaterialTheme.colorScheme.outline`.
* **Keyboard Accessory Bar:** Sits directly above the soft keyboard:
  * Quick checklist / action injection
  * Theme switcher / AMOLED quick toggle
  * Live metrics and word/item counters using `labelSmall`

---

## 6. Overlay Sheets & Dialogs

* **Modal Bottom Sheets (`ModalBottomSheet`):**
  * Container color bound to `MaterialTheme.colorScheme.surface` (`SurfaceDark`, `SurfaceAmoled`, or `SurfaceLight`).
  * Instant search filter fields styled with `MaterialTheme.colorScheme.surfaceVariant`.
  * Top sheet corners rounded to `16.dp`.
* **Action Bars & Chips:**
  * Active indicators styled with `MaterialTheme.colorScheme.primary`.
  * Success/completed day indicators use `SuccessGreenDark` in dark/AMOLED modes and `SuccessGreenLight` in light mode.

---

## 7. Storage & State Architecture

* **Theme Persistence:** Store `themeMode` (`SYSTEM`, `LIGHT`, `DARK`, `AMOLED`) and `dynamicColor: Boolean` in Jetpack DataStore (`preferencesDataStore`). The state flows into the root `MainActivity` as a Compose `State<ThemeConfig>`.
* **Database:** Android Room (`@Database`, `@Dao`, `@Entity`) for zero-latency local caching.
* **File Export/Import:** Android Storage Access Framework (`ActivityResultContracts.CreateDocument` and `OpenDocument`) for universal UTF-8 JSON or text interchange readable across Linux, macOS, Windows, iOS, and Android.
* **Lifecycle:** ViewModel preserves state across configuration changes and dynamic theme toggles.