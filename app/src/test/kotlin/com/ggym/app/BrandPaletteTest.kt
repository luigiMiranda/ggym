package com.ggym.app

import androidx.compose.ui.graphics.Color
import com.ggym.app.ui.theme.GgymBrand
import com.ggym.app.ui.theme.GgymDarkColorScheme
import com.ggym.app.ui.theme.GgymLightColorScheme
import com.ggym.app.ui.theme.GgymTypography
import androidx.compose.material3.ColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrast is the one part of a palette that is objectively wrong or right, so it is asserted
 * rather than eyeballed: a future tweak to `Color.kt` that makes text unreadable fails here
 * instead of shipping.
 *
 * Thresholds are WCAG 2.1 relative-luminance ratios: 4.5:1 for body text, 3:1 for large
 * display text and for controls/outlines that only need to be perceivable.
 */
class BrandPaletteTest {

    @Test
    fun `dark scheme text pairs meet AA`() {
        assertPairsMeetAa(
            scheme = GgymDarkColorScheme,
            bodyPairs = listOf(
                "onSurface on surface" to (GgymDarkColorScheme.onSurface to GgymDarkColorScheme.surface),
                "onSurface on surfaceContainerHigh" to
                    (GgymDarkColorScheme.onSurface to GgymDarkColorScheme.surfaceContainerHigh),
                "onSurface on surfaceContainerHighest" to
                    (GgymDarkColorScheme.onSurface to GgymDarkColorScheme.surfaceContainerHighest),
                "onSurfaceVariant on surface" to
                    (GgymDarkColorScheme.onSurfaceVariant to GgymDarkColorScheme.surface),
                "onSurfaceVariant on surfaceContainerHigh" to
                    (GgymDarkColorScheme.onSurfaceVariant to GgymDarkColorScheme.surfaceContainerHigh),
                "onPrimary on primary" to
                    (GgymDarkColorScheme.onPrimary to GgymDarkColorScheme.primary),
                "onPrimaryContainer on primaryContainer" to
                    (GgymDarkColorScheme.onPrimaryContainer to GgymDarkColorScheme.primaryContainer),
                "onErrorContainer on errorContainer" to
                    (GgymDarkColorScheme.onErrorContainer to GgymDarkColorScheme.errorContainer),
            ),
        )
    }

    @Test
    fun `light scheme text pairs meet AA`() {
        assertPairsMeetAa(
            scheme = GgymLightColorScheme,
            bodyPairs = listOf(
                "onSurface on surface" to (GgymLightColorScheme.onSurface to GgymLightColorScheme.surface),
                "onSurface on surfaceContainerHigh" to
                    (GgymLightColorScheme.onSurface to GgymLightColorScheme.surfaceContainerHigh),
                "onSurfaceVariant on surface" to
                    (GgymLightColorScheme.onSurfaceVariant to GgymLightColorScheme.surface),
                "onSurfaceVariant on surfaceContainerHigh" to
                    (GgymLightColorScheme.onSurfaceVariant to GgymLightColorScheme.surfaceContainerHigh),
                "onPrimary on primary" to
                    (GgymLightColorScheme.onPrimary to GgymLightColorScheme.primary),
                "onPrimaryContainer on primaryContainer" to
                    (GgymLightColorScheme.onPrimaryContainer to GgymLightColorScheme.primaryContainer),
                "onError on error" to (GgymLightColorScheme.onError to GgymLightColorScheme.error),
            ),
        )
    }

    @Test
    fun `primary is perceivable against its own surface`() {
        // A filled button, an active nav pill and a chart line all sit directly on the
        // background: 3:1 is the floor for a non-text UI element.
        assertRatioAtLeast(3.0, "dark primary on surface", GgymDarkColorScheme.primary, GgymDarkColorScheme.surface)
        assertRatioAtLeast(3.0, "light primary on surface", GgymLightColorScheme.primary, GgymLightColorScheme.surface)
    }

    @Test
    fun `brand chartreuse is never used as body text on light surfaces`() {
        // Documents *why* the light scheme uses olive: this pair is deliberately bad, and the
        // test fails if someone "fixes" the palette by aliasing light primary to chartreuse.
        val ratio = contrastRatio(GgymBrand.Chartreuse, GgymLightColorScheme.surface)
        assertTrue(
            "chartreuse on a light surface is expected to be unreadable (was ${"%.2f".format(ratio)}:1) — " +
                "if this ever passes, the palette has changed and light mode needs re-review",
            ratio < 3.0,
        )
    }

    @Test
    fun `display roles are heavy`() {
        // The expressive signature: hierarchy carried by weight. A silent regression to
        // Normal weight would flatten the whole app.
        assertTrue(GgymTypography.displayMedium.fontWeight!!.weight >= 800)
        assertTrue(GgymTypography.displayLarge.fontWeight!!.weight >= 800)
        assertTrue(GgymTypography.headlineMedium.fontWeight!!.weight >= 700)
    }

    private fun assertPairsMeetAa(scheme: ColorScheme, bodyPairs: List<Pair<String, Pair<Color, Color>>>) {
        bodyPairs.forEach { (name, pair) ->
            val (foreground, background) = pair
            assertRatioAtLeast(4.5, "$name (${scheme.hashCode()})", foreground, background)
        }
    }

    private fun assertRatioAtLeast(minimum: Double, name: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$name: expected >= $minimum:1 but was ${"%.2f".format(ratio)}:1",
            ratio >= minimum,
        )
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** WCAG 2.1 relative luminance, computed on sRGB channels as specified. */
    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent)
}
