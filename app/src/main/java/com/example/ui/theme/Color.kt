package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Media Master colour system.
 *
 * A full Material 3 role set for light and dark, derived from an amber brand hue
 * and hand-tuned so that every foreground/background text pairing meets WCAG 2.1
 * AA (>= 4.5:1 for body text, >= 3:1 for large text and non-text). Verified with
 * ColorUtils.calculateContrast during the design-system phase.
 *
 * The bright amber the app is known for lives on `primaryContainer` /
 * `inversePrimary` (light) and on `primary` (dark); filled controls in light
 * theme use a deep amber-brown `primary` with white text rather than white text
 * on bright yellow (which failed AA badly before).
 */

// ---------------------------------------------------------------------------
// Light
// ---------------------------------------------------------------------------
val md_primary_light = Color(0xFF785A00)
val md_onPrimary_light = Color(0xFFFFFFFF)
val md_primaryContainer_light = Color(0xFFFFE08B)
val md_onPrimaryContainer_light = Color(0xFF5B4300)

val md_secondary_light = Color(0xFF6B5D3F)
val md_onSecondary_light = Color(0xFFFFFFFF)
val md_secondaryContainer_light = Color(0xFFF5E0BB)
val md_onSecondaryContainer_light = Color(0xFF241A04)

val md_tertiary_light = Color(0xFF46664C)
val md_onTertiary_light = Color(0xFFFFFFFF)
val md_tertiaryContainer_light = Color(0xFFC8ECCC)
val md_onTertiaryContainer_light = Color(0xFF032111)

val md_error_light = Color(0xFFBA1A1A)
val md_onError_light = Color(0xFFFFFFFF)
val md_errorContainer_light = Color(0xFFFFDAD6)
val md_onErrorContainer_light = Color(0xFF410002)

val md_background_light = Color(0xFFFFF9EE)
val md_onBackground_light = Color(0xFF1F1B13)
val md_surface_light = Color(0xFFFFF9EE)
val md_onSurface_light = Color(0xFF1F1B13)
val md_surfaceVariant_light = Color(0xFFEDE1CF)
val md_onSurfaceVariant_light = Color(0xFF4D4639)
val md_outline_light = Color(0xFF7F7767)
val md_outlineVariant_light = Color(0xFFD0C5B4)

val md_surfaceContainerLowest_light = Color(0xFFFFFFFF)
val md_surfaceContainerLow_light = Color(0xFFFDF2E1)
val md_surfaceContainer_light = Color(0xFFF7ECDB)
val md_surfaceContainerHigh_light = Color(0xFFF1E7D5)
val md_surfaceContainerHighest_light = Color(0xFFEBE1D0)

val md_inverseSurface_light = Color(0xFF34302A)
val md_inverseOnSurface_light = Color(0xFFF8EFE1)
val md_inversePrimary_light = Color(0xFFF2BF48)
val md_scrim_light = Color(0xFF000000)

// ---------------------------------------------------------------------------
// Dark
// ---------------------------------------------------------------------------
val md_primary_dark = Color(0xFFF2BF48)
val md_onPrimary_dark = Color(0xFF3F2E00)
val md_primaryContainer_dark = Color(0xFF5B4300)
val md_onPrimaryContainer_dark = Color(0xFFFFE08B)

val md_secondary_dark = Color(0xFFD8C4A0)
val md_onSecondary_dark = Color(0xFF3B2F15)
val md_secondaryContainer_dark = Color(0xFF52452A)
val md_onSecondaryContainer_dark = Color(0xFFF5E0BB)

val md_tertiary_dark = Color(0xFFACD0B1)
val md_onTertiary_dark = Color(0xFF183720)
val md_tertiaryContainer_dark = Color(0xFF2F4E36)
val md_onTertiaryContainer_dark = Color(0xFFC8ECCC)

val md_error_dark = Color(0xFFFFB4AB)
val md_onError_dark = Color(0xFF690005)
val md_errorContainer_dark = Color(0xFF93000A)
val md_onErrorContainer_dark = Color(0xFFFFDAD6)

val md_background_dark = Color(0xFF17130B)
val md_onBackground_dark = Color(0xFFEBE1D0)
val md_surface_dark = Color(0xFF17130B)
val md_onSurface_dark = Color(0xFFEBE1D0)
val md_surfaceVariant_dark = Color(0xFF4D4639)
val md_onSurfaceVariant_dark = Color(0xFFD0C5B4)
val md_outline_dark = Color(0xFF998F80)
val md_outlineVariant_dark = Color(0xFF4D4639)

val md_surfaceContainerLowest_dark = Color(0xFF110E07)
val md_surfaceContainerLow_dark = Color(0xFF1F1B13)
val md_surfaceContainer_dark = Color(0xFF231F17)
val md_surfaceContainerHigh_dark = Color(0xFF2E2921)
val md_surfaceContainerHighest_dark = Color(0xFF39332B)

val md_inverseSurface_dark = Color(0xFFEBE1D0)
val md_inverseOnSurface_dark = Color(0xFF34302A)
val md_inversePrimary_dark = Color(0xFF785A00)
val md_scrim_dark = Color(0xFF000000)

// ---------------------------------------------------------------------------
// Media viewer surface — deliberately dark in both themes (photos/videos read
// best on a near-black ground). Use these instead of raw Color.Black/White.
// ---------------------------------------------------------------------------
val ViewerScrim = Color(0xFF000000)
val ViewerSurface = Color(0xFF0B0B0B)
val ViewerOnSurface = Color(0xFFF5F5F5)
val ViewerOnSurfaceVariant = Color(0xFFC7C7C7)
val ViewerChromeContainer = Color(0xCC0B0B0B) // ~80% opaque bar over media
