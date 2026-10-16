package com.ggym.app.ui.theme

import androidx.compose.material3.MotionScheme

/**
 * The single motion source for the whole app.
 *
 * `MotionScheme.expressive()` gives spatial transitions a low damping ratio — they overshoot
 * slightly and settle visibly — while effects (colour, alpha) stay fast and damped.
 *
 * Where each scheme is applied, so the app feels consistent rather than uniformly springy:
 *  - **spatial**: pager swipes between exercises, set-completion shape morph, nav destination
 *    change, sheet/FAB entrances;
 *  - **effects**: weight and rep value changes (fade + scale), timer ring and digits —
 *    precision matters more than bounce while you are mid-set.
 */
val GgymMotionScheme = MotionScheme.expressive()
