package com.ggym.app.domain.stats

import com.ggym.app.domain.model.BodyWeightLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The body-weight window and its statistics.
 *
 * The window is anchored to today (not to the latest measurement), both ends are inclusive, and
 * the numbers are computed from every point in the window — never from a drawn, reduced line.
 */
class BodyWeightStatsTest {

    private val today = LocalDate.of(2026, 10, 19)

    @Test
    fun `the windows are relative to today and include both ends`() {
        assertEquals(LocalDate.of(2026, 9, 19), BodyWeightRange.ONE_MONTH.from(today))
        assertEquals(LocalDate.of(2026, 7, 19), BodyWeightRange.THREE_MONTHS.from(today))
        assertEquals(LocalDate.of(2026, 4, 19), BodyWeightRange.SIX_MONTHS.from(today))
        assertEquals(LocalDate.of(2025, 10, 19), BodyWeightRange.ONE_YEAR.from(today))

        assertTrue(BodyWeightRange.THREE_MONTHS.contains(LocalDate.of(2026, 7, 19), today))
        assertTrue(BodyWeightRange.THREE_MONTHS.contains(today, today))
        assertTrue(!BodyWeightRange.THREE_MONTHS.contains(LocalDate.of(2026, 7, 18), today))
        assertTrue(!BodyWeightRange.THREE_MONTHS.contains(LocalDate.of(2026, 10, 20), today))
        assertTrue(BodyWeightRange.ALL.contains(LocalDate.of(1999, 1, 1), today))
    }

    @Test
    fun `the window keeps only its own points, oldest first`() {
        val entries = listOf(
            weight(2026, 10, 19, 81.0),
            weight(2026, 7, 19, 80.0), // exactly the 3M boundary: included
            weight(2026, 7, 18, 79.0), // one day too old
            weight(2026, 10, 20, 90.0), // after today
        )

        val stats = BodyWeightStats.from(entries, today, BodyWeightRange.THREE_MONTHS)

        assertEquals(2, stats.count)
        assertEquals(listOf(LocalDate.of(2026, 7, 19), LocalDate.of(2026, 10, 19)), stats.points.map { it.date })
    }

    @Test
    fun `min, average and max come from all the points`() {
        val entries = listOf(
            weight(2026, 9, 1, 78.4),
            weight(2026, 9, 15, 84.0),
            weight(2026, 10, 1, 81.2),
        )

        val stats = BodyWeightStats.from(entries, today, BodyWeightRange.THREE_MONTHS)

        assertEquals(78.4, stats.minKg!!, 0.0001)
        assertEquals(84.0, stats.maxKg!!, 0.0001)
        assertEquals((78.4 + 84.0 + 81.2) / 3, stats.averageKg!!, 0.0001)
        assertEquals(3, stats.count)
    }

    @Test
    fun `one point is its own minimum, average and maximum`() {
        val stats = BodyWeightStats.from(listOf(weight(2026, 10, 19, 82.4)), today, BodyWeightRange.ONE_MONTH)

        assertEquals(1, stats.count)
        assertEquals(82.4, stats.minKg!!, 0.0)
        assertEquals(82.4, stats.averageKg!!, 0.0)
        assertEquals(82.4, stats.maxKg!!, 0.0)
    }

    @Test
    fun `an empty window has no numbers at all`() {
        val stats = BodyWeightStats.from(emptyList(), today, BodyWeightRange.THREE_MONTHS)

        assertEquals(0, stats.count)
        assertNull(stats.minKg)
        assertNull(stats.averageKg)
        assertNull(stats.maxKg)
        assertTrue(stats.points.isEmpty())
    }

    @Test
    fun `the all window reaches the oldest measurement`() {
        val stats = BodyWeightStats.from(
            listOf(weight(1999, 1, 1, 70.0), weight(2026, 10, 1, 81.0)),
            today,
            BodyWeightRange.ALL,
        )

        assertEquals(2, stats.count)
        assertEquals(70.0, stats.minKg!!, 0.0)
    }

    private fun weight(year: Int, month: Int, day: Int, kg: Double): BodyWeightLog =
        BodyWeightLog(date = LocalDate.of(year, month, day), weightKg = kg)
}
