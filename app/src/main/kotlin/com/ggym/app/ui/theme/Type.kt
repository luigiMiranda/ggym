package com.ggym.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive typography: hierarchy is carried by **weight and size**, not colour.
 *
 * The expressive signature is heavy, tight display type. Hero numeric styles are built on top of
 * this scale, in the screens that need them: they require tabular figures so digits do not reflow
 * while the user types, which is a property of the number, not of the palette.
 */
val GgymTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-1.5).sp,
        ),
        displayMedium = base.displayMedium.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-1.5).sp,
        ),
        displaySmall = base.displaySmall.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
        ),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

/**
 * The working weight, at hero size: the number a user is at the screen to read, and the only one
 * that changes while the editor is open.
 *
 * `tnum` (tabular figures) is why this is a style of its own: with proportional digits, typing
 * "82.5" makes the text jump sideways at every character, which is exactly the moment the user is
 * looking at it.
 */
val GgymHeroWeight: TextStyle = GgymTypography.headlineMedium.copy(
    fontWeight = FontWeight.Black,
    fontFeatureSettings = "tnum",
    letterSpacing = (-0.5).sp,
)

/**
 * The hero weight at the size the focused view uses it: the largest interactive element in the app.
 *
 * Same reasons as [GgymHeroWeight] for the tabular figures, at `displayMedium`. It is editable in
 * place, so a value that reflows while it is typed is not a cosmetic problem — it moves the number
 * out from under the finger that is entering it.
 */
val GgymHeroWeightDisplay: TextStyle = GgymTypography.displayMedium.copy(
    fontWeight = FontWeight.Black,
    fontFeatureSettings = "tnum",
    letterSpacing = (-1.5).sp,
)
