package com.ggym.app.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * The attendance chart's two pure halves: which twelve months the window covers, and how many
 * **distinct days** were trained in each.
 */
class AttendanceTest {

    @Test
    fun `the window is the last twelve months, oldest first, ending with today's`() {
        val months = CalendarMonths.last(CalendarMonths.ATTENDANCE_MONTHS, LocalDate.of(2026, 10, 19))

        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2026, 10), months.last())
        assertEquals(YearMonth.of(2025, 11), months.first())
        assertEquals("months are consecutive", 12, months.distinct().size)
    }

    @Test
    fun `the window crosses a year boundary and keeps a leap February`() {
        val months = CalendarMonths.last(12, LocalDate.of(2028, 2, 29))

        assertEquals(YearMonth.of(2028, 2), months.last())
        assertEquals(YearMonth.of(2027, 3), months.first())
        assertTrue(months.contains(YearMonth.of(2028, 2)))
    }

    @Test
    fun `a leap day is a training day like any other`() {
        val months = CalendarMonths.last(12, LocalDate.of(2028, 3, 1))

        val counts = AttendanceCounts.from(listOf(LocalDate.of(2028, 2, 29)), months)

        assertEquals(1, counts.first { it.month == YearMonth.of(2028, 2) }.workoutDays)
    }

    @Test
    fun `no months requested is no months returned`() {
        assertTrue(CalendarMonths.last(0, LocalDate.of(2026, 10, 19)).isEmpty())
        assertTrue(CalendarMonths.last(-3, LocalDate.of(2026, 10, 19)).isEmpty())
    }

    @Test
    fun `two workouts on one day count as one training day`() {
        val months = listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2026, 8))
        val dates = listOf(
            LocalDate.of(2026, 10, 14),
            LocalDate.of(2026, 10, 14),
            LocalDate.of(2026, 10, 3),
            LocalDate.of(2026, 9, 30),
        )

        val counts = AttendanceCounts.from(dates, months)

        assertEquals(listOf(2, 1, 0), counts.map { it.workoutDays })
        assertEquals(months, counts.map { it.month })
    }

    @Test
    fun `a month with no training keeps its zero bar`() {
        val months = CalendarMonths.last(12, LocalDate.of(2026, 10, 19))

        val counts = AttendanceCounts.from(emptyList(), months)

        assertEquals(12, counts.size)
        assertTrue(counts.all { it.workoutDays == 0 })
    }

    @Test
    fun `a date outside the window is not counted into any bar`() {
        val months = listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9))

        val counts = AttendanceCounts.from(
            listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2025, 10, 1)),
            months,
        )

        assertEquals(listOf(1, 0), counts.map { it.workoutDays })
    }
}
