package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Explicit Material Design 3 shape scale (v1.9.0 MD3 remake).
 *
 * Previously the theme omitted `shapes=`, so every component silently used
 * the M3 defaults. Wiring the same values explicitly keeps behaviour
 * identical today while making the token source of truth visible and
 * letting future Expressive corner tokens land in one place.
 *
 * Mapping follows `--md-sys-shape-corner-*`: chips/snackbar 4dp,
 * text fields/menus 8dp (small), cards 12dp (medium), FAB/drawer 16dp
 * (large), dialogs/sheets 28dp (extraLarge).
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
