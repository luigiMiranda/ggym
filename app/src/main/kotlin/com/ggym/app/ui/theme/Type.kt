package com.ggym.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive typography: hierarchy is carried by **weight and size**, not colour.
 *
 * The expressive signature is heavy, tight display type. Hero numeric styles (working weight,
 * rest countdown) are added alongside the screens that need them: they require tabular figures
 * so digits do not reflow while you type, which is a property of the screen, not the palette.
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
