package com.ggym.app.domain.stats

import com.ggym.app.domain.model.LoggedSetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * "Is this exercise going up?": one point per date, the day's maximum completed weight, and a
 * least-squares line only once three points make a slope mean something.
 */
class ExerciseProgressionTest {

    @Test
    fun `one point per date carries the day's heaviest set`() {
        val progression = ExerciseProgression.from(
            listOf(
                point(2026, 10, 1, 80.0),
                point(2026, 10, 1, 85.0),
                point(2026, 10, 1, 82.5),
                point(2026, 10, 8, 87.5),
                point(2026, 9, 24, 78.0),
            ),
        )

        assertEquals(
            listOf(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8)),
            progression.points.map { it.date },
        )
        assertEquals(listOf(78.0, 85.0, 87.5), progression.points.map { it.maxWeightKg })
    }

    @Test
    fun `fewer than three points have no trend`() {
        assertNull(ExerciseProgression.from(emptyList()).trend)
        assertNull(ExerciseProgression.from(listOf(point(2026, 10, 1, 80.0))).trend)
        assertNull(
            ExerciseProgression.from(
                listOf(point(2026, 10, 1, 80.0), point(2026, 10, 8, 85.0)),
            ).trend,
        )
    }

    @Test
    fun `three points produce a least-squares line through the data`() {
        val progression = ExerciseProgression.from(
            listOf(
                point(2026, 10, 1, 80.0),
                point(2026, 10, 8, 85.0),
                point(2026, 10, 15, 90.0),
            ),
        )

        val trend = progression.trend!!
        assertEquals(LocalDate.of(2026, 10, 1), trend.from.date)
        assertEquals(LocalDate.of(2026, 10, 15), trend.to.date)
        assertEquals(80.0, trend.from.maxWeightKg, 0.0001)
        assertEquals(90.0, trend.to.maxWeightKg, 0.0001)
    }

    @Test
    fun `the trend is real arithmetic when the points do not line up perfectly`() {
        // x in days 0, 10, 20; y 80, 90, 85: the mean is 85 and the fitted line is 82.5 → 87.5.
        val progression = ExerciseProgression.from(
            listOf(
                point(2026, 10, 1, 80.0),
                point(2026, 10, 11, 90.0),
                point(2026, 10, 21, 85.0),
            ),
        )

        val trend = progression.trend!!
        assertEquals(82.5, trend.from.maxWeightKg, 0.0001)
        assertEquals(87.5, trend.to.maxWeightKg, 0.0001)
    }

    @Test
    fun `a flat history is a flat trend, not a missing one`() {
        val progression = ExerciseProgression.from(
            listOf(
                point(2026, 10, 1, 80.0),
                point(2026, 10, 8, 80.0),
                point(2026, 10, 15, 80.0),
            ),
        )

        val trend = progression.trend!!
        assertEquals(80.0, trend.from.maxWeightKg, 0.0)
        assertEquals(80.0, trend.to.maxWeightKg, 0.0)
        assertEquals(3, progression.points.size)
    }

    @Test
    fun `an exercise with no completed set has nothing to plot`() {
        val progression = ExerciseProgression.from(emptyList())

        assertTrue(progression.isEmpty)
        assertTrue(progression.points.isEmpty())
        assertEquals(ExerciseProgression.EMPTY, progression)
    }

    private fun point(year: Int, month: Int, day: Int, kg: Double): LoggedSetPoint = LoggedSetPoint(
        date = LocalDate.of(year, month, day),
        weightKg = kg,
        reps = 8,
        sessionId = 1,
    )
}
