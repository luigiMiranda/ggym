package com.ggym.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale. Deliberately generous compared to baseline M3 — Expressive leans on visible,
 * friendly corner radii — and *deliberately mixed*: soft containers versus squarer controls.
 */
val GgymShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),        // chips, badges
    small = RoundedCornerShape(GgymRadii.setChip), // list rows, small controls
    medium = RoundedCornerShape(18.dp),           // inputs, steppers
    large = RoundedCornerShape(24.dp),            // cards
    extraLarge = RoundedCornerShape(32.dp),       // hero cards, sheets
)

/**
 * The radii a component animates *between*, in dp.
 *
 * Shape encodes state in this app: a set chip is a squarer control while it is still a plan and
 * rounds into a pill once it has been performed. A `Shape` cannot be interpolated, so the morph is
 * an animated radius — which means the two endpoints have to be readable by both the scale above
 * and the component that animates them.
 */
object GgymRadii {
    /** A set chip before it is performed — the same corner as [GgymShapes.small]. */
    val setChip = 12.dp

    /** A set chip once it is performed: the full round of something done. */
    val setChipCompleted = 29.dp
}
