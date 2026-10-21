package com.ggym.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
import kotlin.math.pow
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The launcher mark's geometry, asserted instead of eyeballed — the same idea as
 * [BrandPaletteTest], one dimension over.
 *
 * An adaptive icon is drawn on a 108x108dp canvas, but only a **66dp circle in the middle is
 * guaranteed to survive every mask a launcher may apply**. Content outside that circle is kept by a
 * squircle and cut by a circle, which is exactly what happened at M0–M6: the mark was scaled to
 * 70.2dp, its outer barbell plates sat 35.10dp from the centre against a 33dp radius, and a circular
 * launcher sliced each of them down to roughly a third of its width. M6.5 reduced the scale to 64dp
 * so the whole barbell fits.
 *
 * The test renders the real vector drawables under Robolectric NATIVE graphics and measures the
 * farthest opaque pixel from the canvas centre, so the next person to "make the logo bigger" gets a
 * red test rather than a clipped icon on someone's launcher.
 *
 * It also measures the plates' contrast against the background they sit on: the mark was whole but
 * invisible at the original dark-metal gradient, which is the other half of "more visible".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class IconGeometryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The radius of the circle no mask may clip, in dp, on the 108dp canvas. */
    private val safeRadiusDp = 33.0

    @Test
    fun `the coloured mark stays inside the circle every launcher mask must show`() {
        assertFitsInSafeZone(R.drawable.ic_launcher_foreground)
    }

    @Test
    fun `the monochrome mark stays inside the same circle`() {
        assertFitsInSafeZone(R.drawable.ic_launcher_monochrome)
    }

    /**
     * The plates must read as metal, not as a shadow. M6.5 made them whole; this pins that they are
     * also **legible** on the near-black background, so a later tidy-up cannot dim the steel back
     * to the `#4A4B53 -> #18181C` it started as (which sat near 1.3:1 where it faded out).
     */
    @Test
    fun `the barbell plates read against the icon background`() {
        val foreground = render(R.drawable.ic_launcher_foreground)
        val background = render(R.drawable.ic_launcher_background)

        // The outer-left plate's bounding box in canvas pixels: SVG x 62..100, y 398..626 at 4x.
        var darkest = Double.MAX_VALUE
        var sampled = 0
        for (x in 88..99) {
            for (y in 184..248) {
                val plate = foreground.getPixel(x, y)
                // Fully opaque only: rounded corners and antialiased edges are not the plate's fill.
                if (plate ushr 24 < 250) continue
                sampled++
                darkest = minOf(darkest, contrastRatio(plate, background.getPixel(x, y)))
            }
        }

        assertTrue("the plate region sampled no fully-opaque pixels — the box is wrong", sampled >= 100)
        assertTrue(
            "the darkest plate pixel is only " + "%.2f".format(darkest) +
                ":1 against the icon background — the plates read as a shadow rather than metal",
            darkest >= 2.0,
        )
    }

    /** WCAG relative-luminance contrast, the measure the brand palette is chosen by too. */
    private fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val s = value / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel((color shr 16) and 0xFF) +
            0.7152 * channel((color shr 8) and 0xFF) +
            0.0722 * channel(color and 0xFF)
    }

    /**
     * Renders [drawableRes] at [SIZE_PX], then asserts two things about where its opaque pixels
     * land: every one of them is inside the safe circle, and the mark is still big enough to be the
     * mark (a scale change that overshoots downwards fails here too).
     */
    private fun assertFitsInSafeZone(drawableRes: Int) {
        val bitmap = render(drawableRes)
        val centre = SIZE_PX / 2.0
        var farthestSquared = 0.0
        var minX = SIZE_PX
        var maxX = -1

        for (y in 0 until SIZE_PX) {
            for (x in 0 until SIZE_PX) {
                // Half-opaque is the threshold: antialiased edges are not the mark's geometry.
                if (bitmap.getPixel(x, y) ushr 24 < 128) continue
                val dx = x + 0.5 - centre
                val dy = y + 0.5 - centre
                farthestSquared = maxOf(farthestSquared, dx * dx + dy * dy)
                if (x < minX) minX = x
                if (x > maxX) maxX = x
            }
        }

        val farthestDp = sqrt(farthestSquared) * DP_PER_CANVAS / SIZE_PX
        val widthDp = (maxX - minX + 1) * DP_PER_CANVAS / SIZE_PX

        assertTrue(
            "the mark reaches ${"%.1f".format(farthestDp)}dp from the centre, past the ${safeRadiusDp}dp " +
                "radius a circular launcher keeps — it would be clipped",
            farthestDp <= safeRadiusDp,
        )
        assertTrue(
            "the mark is only ${"%.1f".format(widthDp)}dp wide: it has been shrunk past being the mark",
            widthDp >= 60.0,
        )
    }

    /** The drawable drawn into a transparent square, at 4x the 108dp canvas for pixel accuracy. */
    private fun render(drawableRes: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val drawable = requireNotNull(context.getDrawable(drawableRes)) { "missing drawable $drawableRes" }
        drawable.setBounds(0, 0, SIZE_PX, SIZE_PX)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        /** 108dp at 4x: fine enough that a tenth of a dp is still several pixels. */
        const val SIZE_PX = 432
        const val DP_PER_CANVAS = 108.0
    }
}
