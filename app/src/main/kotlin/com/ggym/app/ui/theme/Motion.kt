package com.ggym.app.ui.theme

import androidx.compose.material3.MotionScheme

/**
 * The single motion source for the whole app.
 *
 * `MotionScheme.expressive()` gives spatial transitions a low damping ratio — they overshoot
 * slightly and settle visibly — while effects (colour, alpha) stay fast and damped.
 *
 * Where each scheme is applied, so the app feels consistent rather than uniformly springy:
 *  - **spatial** (overshoots a little, so the eye follows it): the set chip's corner and width as
 *    a set is completed and its remove button goes, the rest banner entering and leaving, the page
 *    dots,
 *    the big action's label growing into place, pager swipes, sheet entrances;
 *  - **effects** (fast and damped): selection and completion colours on a set chip, the action
 *    label fading out, and numeric values — precision matters more than bounce while mid-set.
 *
 * Navigation between destinations is deliberately a cross-fade rather than a spatial slide: the
 * four tabs and the routine tree have no left-to-right order, so a direction would be invented.
 * Spatial motion is reserved for what has a place — a page, a strip, a banner appearing below.
 */
val GgymMotionScheme = MotionScheme.expressive()
