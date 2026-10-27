# Android Design System & Style Specification (Jetpack Compose)

This document defines the mobile styling, architecture, and interaction guidelines for Android applications in the **Vibe-Coding** ecosystem, aligned directly with the desktop Linux `#141414` matte dark design language.

---

## 1. Core Principles

* **Distraction-Free Full-Bleed Canvas:** Edge-to-edge layout with pure `#141414` matte dark background matching the Linux desktop aesthetic.
* **Low-Profile Chrome:** Compact top bars and keyboard accessory strips that yield maximum vertical screen estate to content.
* **Immunity to OEM Font Overrides:** All core typography bundles local TTF files so custom manufacturer fonts (e.g., Samsung OneUI) cannot alter line heights or monospace alignments.
* **Bottom Sheet Overlays:** Non-modal sheets (`SavedNotesSheet`, `FindReplaceBar`) anchor smoothly to the bottom edge without jarring full-screen route shifts.

---

## 2. Color Palette & Jetpack Compose Tokens

Defined in `ui/theme/Color.kt` and `ui/theme/Theme.kt`:

### Color Tokens

```kotlin
package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Minimalist Dark Scratchpad Palette (aligned with Linux Ephemeral Pad)
val CanvasDark          = Color(0xFF141414) // 100% flat matte charcoal canvas
val SurfaceDark         = Color(0xFF1B1B1D) // Bottom sheets & dialog surfaces
val SurfaceVariantDark  = Color(0xFF242428) // Input fields & card surfaces
val BorderSubtle        = Color(0xFF2C2C30) // Subtle outlines & divider lines

val TextPrimary         = Color(0xFFE0E0E0) // Soft off-white for high legibility
val TextSecondary       = Color(0xFF8E8E93) // Muted secondary descriptions & timestamps
val TextMuted           = Color(0xFF585858) // Whisper-quiet peripheral indicators

val AccentCyan          = Color(0xFF88C0D0) // Nord cyan for active pills & primary action
val AccentBlue          = Color(0xFF7AA2F7) // Tokyo-night blue accent
val AccentFrost         = Color(0xFF81A1C1) // Slate-blue secondary accent
val SelectionHighlight  = Color(0xFF323842) // Muted slate text selection
val ErrorRed            = Color(0xFFE06C75) // Destructive action & delete warnings
val SuccessGreen        = Color(0xFF98C379) // Save indicators & success chips
```

### Material 3 Dark Scheme Mapping

```kotlin
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
```

---

## 3. Typography & Bundled Fonts

Defined in `ui/theme/Type.kt`:

### Bundled Font Families (`res/font/`)

To prevent OEM skin customization from degrading typography, apps bundle TrueType fonts:

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
* **Peripheral Counter (`labelSmall`):** `11.sp`, line height `14.sp`, `ScratchpadMonoFontFamily`, color `TextMuted` (`#585858`)
* **Headers & Titles (`titleLarge`):** `20.sp`, line height `26.sp`, `ScratchpadSansFontFamily` (SemiBold)

---

## 4. Mobile Layout & Inset Handling

```
+-----------------------------------------------------------+
| [Status Bar Inset: statusBarsPadding()]                   |
| [ScratchpadTopBar: Title · Save · Search · Menu]          |
+-----------------------------------------------------------+
|                                                           |
|                     SCROLLABLE CANVAS                     |
|                                                           |
+-----------------------------------------------------------+
| [Accessory Bar: - [ ]  •  [Time]  Copy  Font  Stats]      |
| [IME / Soft Keyboard Inset: imePadding()]                 |
| [Navigation Bar Inset: navigationBarsPadding()]           |
+-----------------------------------------------------------+
```

* **Edge-to-Edge:** Activity enables edge-to-edge drawing. Root containers bind `statusBarsPadding()`, `imePadding()`, and `navigationBarsPadding()`.
* **ScratchpadTopBar:** Minimal height (`48dp`), flat `CanvasDark` background with single subtle `HorizontalDivider` (`BorderSubtle`).
* **Keyboard Accessory Bar:** Sits directly above the active software keyboard:
  * Quick checklist insertion (`- [ ] `)
  * Bullet insertion (`• `)
  * Timestamp insertion (`[YYYY-MM-DD HH:MM] `)
  * Font scale toggle (+ / -)
  * Live word / character counter in `TextMuted` (`#585858`)

---

## 5. Overlay Sheets & Dialogs

* **Saved Notes Sheet (`SavedNotesSheet`):**
  * Opens as a bottom sheet styled in `SurfaceDark` (`#1B1B1D`).
  * Instant search filter field at top with `SurfaceVariantDark` background.
  * Notes display title in `TextPrimary`, snippet in `TextSecondary`, timestamp in `TextMuted`.
  * Swipe or tap actions for Pin/Unpin, Export, and Delete.
* **Find & Replace Strip (`FindReplaceBar`):**
  * Slides in above the keyboard rather than popping up as an alert dialog.
  * Compact match counters (`1 of 4`) rendered in `AccentCyan`.

---

## 6. Storage & State Architecture

* **Database:** Android Room (`@Database`, `@Dao`, `@Entity`) for fast local persistence without external servers.
* **File Export/Import:** Integrated with Android Storage Access Framework (`FileStorageHelper`) for zero-permission SAF export to `.txt` and `.md` files.
* **Clean Lifecycle:** ViewModel (`ScratchpadViewModel`) preserves undo/redo history stacks and active note session across configuration/orientation changes.
