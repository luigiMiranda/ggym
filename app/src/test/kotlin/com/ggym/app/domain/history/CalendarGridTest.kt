package com.ggym.app.domain.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The month grid, as arithmetic: six Monday-first weeks, the adjacent months' days included and
 * marked, and no reliance on a clock, a locale or a database.
 *
 * The cases are the ones that actually break calendars: a leap February, a 31-day month that needs
 * six rows, a 28-day month that would fit in four, and the year boundary.
 */
class CalendarGridTest {

    @Test
    fun `the grid always has six Monday-first weeks and 42 cells`() {
        YearMonth.of(2026, 1).let { month ->
            val grid = CalendarGrid.of(month)

            assertEquals(6, grid.weeks.size)
            assertTrue(grid.weeks.all { it.size == 7 })
            assertEquals(42, grid.cells.size)
            assertEquals(DayOfWeek.MONDAY, grid.cells.first().date.dayOfWeek)
        }

        // A 28-day February that starts on a Monday would fit in four rows; it still gets six.
        val shortFebruary = CalendarGrid.of(YearMonth.of(2027, 2))
        assertEquals(6, shortFebruary.weeks.size)
        assertEquals(42, shortFebruary.cells.size)
        assertTrue(shortFebruary.cells.takeLast(14).none { it.inMonth })
    }

    @Test
    fun `a month that starts mid-week begins with the previous month's days, dimmed`() {
        // 1 October 2026 is a Thursday, so the grid opens on Monday 28 September.
        val grid = CalendarGrid.of(YearMonth.of(2026, 10))

        assertEquals(LocalDate.of(2026, 9, 28), grid.cells.first().date)
        assertFalse(grid.cells.first().inMonth)
        assertEquals(LocalDate.of(2026, 10, 1), grid.cells[3].date)
        assertTrue(grid.cells[3].inMonth)
        assertEquals(LocalDate.of(2026, 11, 8), grid.cells.last().date)
        assertFalse(grid.cells.last().inMonth)
    }

    @Test
    fun `a 31-day month starting on a Sunday needs all six rows`() {
        // 1 March 2026 is a Sunday: six days of February, then 31 days, pushing into April.
        val grid = CalendarGrid.of(YearMonth.of(2026, 3))
        val marchDays = grid.cells.filter { it.inMonth }

        assertEquals(31, marchDays.size)
        assertEquals(LocalDate.of(2026, 3, 1), marchDays.first().date)
        assertEquals(LocalDate.of(2026, 3, 31), marchDays.last().date)
        assertEquals(LocalDate.of(2026, 2, 23), grid.cells.first().date)
        assertEquals(LocalDate.of(2026, 4, 5), grid.cells.last().date)
        assertTrue("the 31st lands in the sixth week", marchDays.last().date in grid.weeks[5].map { it.date })
    }

    @Test
    fun `a leap February keeps its 29th day`() {
        val leap = CalendarGrid.of(YearMonth.of(2028, 2))
        val februaryDays = leap.cells.filter { it.inMonth }

        assertEquals(29, februaryDays.size)
        assertEquals(LocalDate.of(2028, 2, 29), februaryDays.last().date)
        assertTrue(leap.cells.any { it.date == LocalDate.of(2028, 2, 29) && it.inMonth })

        val common = CalendarGrid.of(YearMonth.of(2027, 2))
        assertEquals(28, common.cells.count { it.inMonth })
        assertFalse(common.cells.any { it.inMonth && it.date.dayOfMonth == 29 })
    }

    @Test
    fun `the year boundary is just another month`() {
        val january = CalendarGrid.of(YearMonth.of(2027, 1))

        assertTrue("January's grid may begin in December", january.cells.first().date.year == 2026)
        assertEquals(LocalDate.of(2027, 1, 1), january.cells.first { it.inMonth }.date)
        assertTrue(january.cells.any { it.date == LocalDate.of(2027, 1, 31) && it.inMonth })
    }

    @Test
    fun `every day of the month appears exactly once`() {
        val month = YearMonth.of(2026, 10)
        val cells = CalendarGrid.of(month).cells.filter { it.inMonth }

        assertEquals((1..month.lengthOfMonth()).map { month.atDay(it) }, cells.map { it.date })
    }
}
