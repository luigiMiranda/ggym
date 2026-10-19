package com.ggym.app.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line reduction: it may drop points, but never the ones that carry the shape.
 *
 * The first, last, minimum and maximum stay; order is preserved; and the result never exceeds the
 * requested budget — the stats above the chart are computed elsewhere and are not affected.
 */
class DownsampleTest {

    private val points = (0 until 500).map { it.toDouble() }

    @Test
    fun `a short series is returned untouched`() {
        val short = listOf(3.0, 1.0, 2.0)

        assertEquals(short, downsample(short, 240) { it })
    }

    @Test
    fun `a long series keeps its first, last, minimum and maximum`() {
        // The points are indices, so order can be checked; the value function supplies the shape.
        val indices = (0 until 500).toList()
        val shape = indices.associateWith { index ->
            when (index) {
                0 -> 10.0
                499 -> 20.0
                137 -> 1.0 // the minimum, at an arbitrary interior index
                411 -> 99.0 // the maximum
                else -> 50.0 + (index % 7)
            }
        }

        val reduced = downsample(indices, 240) { shape.getValue(it) }

        assertTrue("the budget is respected", reduced.size <= 240)
        assertEquals(0, reduced.first())
        assertEquals(499, reduced.last())
        assertTrue("the minimum's point is kept", 137 in reduced)
        assertTrue("the maximum's point is kept", 411 in reduced)
        assertEquals("order is preserved", reduced, reduced.sorted())
        assertEquals("no point is duplicated", reduced.distinct(), reduced)
    }

    @Test
    fun `a single-point budget keeps the first point`() {
        assertEquals(listOf(points.first()), downsample(points, 1) { it })
    }

    @Test
    fun `no budget is no line`() {
        assertTrue(downsample(points, 0) { it }.isEmpty())
        assertTrue(downsample(points, -10) { it }.isEmpty())
    }

    @Test
    fun `a budget of four keeps exactly the extremes that matter`() {
        val reduced = downsample(points, 4) { it }

        assertTrue(reduced.size <= 4)
        assertEquals(points.first(), reduced.first(), 0.0)
        assertEquals(points.last(), reduced.last(), 0.0)
    }
}
