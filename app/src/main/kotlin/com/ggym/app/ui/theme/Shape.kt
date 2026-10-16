package com.ggym.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale. Deliberately generous compared to baseline M3 — Expressive leans on visible,
 * friendly corner radii — and *deliberately mixed*: soft containers versus squarer controls.
 *
 * Shape also encodes state. When the set-complete button lands in M2 it morphs pill → cookie
 * (~29dp radius, squircle-ish); that target belongs to the workout feature rather than to this
 * scale, so it is defined next to the component that morphs.
 */
val GgymShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // chips, badges
    small = RoundedCornerShape(12.dp),       // list rows, small controls
    medium = RoundedCornerShape(18.dp),      // inputs, steppers
    large = RoundedCornerShape(24.dp),       // cards
    extraLarge = RoundedCornerShape(32.dp),  // hero cards, sheets
)
