package com.ggym.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
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
