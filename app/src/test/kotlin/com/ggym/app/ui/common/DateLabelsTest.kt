package com.ggym.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Every date and month name on a screen comes from the locale, never from a literal.
 *
 * The Italian assertions are the point: an English month name baked into the app would still pass
 * every English test, and would be wrong on half the phones the app is for.
 */
class DateLabelsTest {

    private val october = YearMonth.of(2026, 10)
    private val fourteenth = LocalDate.of(2026, 10, 14)

    @Test
    fun `the month title is the locale's own name and spelling`() {
        assertEquals("October 2026", DateLabels.monthTitle(october, Locale.ENGLISH))
        assertEquals("ottobre 2026", DateLabels.monthTitle(october, Locale.ITALIAN))
        assertEquals("Oct", DateLabels.monthShort(october, Locale.ENGLISH))
        assertEquals("October", DateLabels.monthName(october, Locale.ENGLISH))
    }

    @Test
    fun `a day with its weekday is spelled by the locale`() {
        assertEquals("Wednesday 14 October", DateLabels.dayLong(fourteenth, Locale.ENGLISH))
        assertEquals("mercoledì 14 ottobre", DateLabels.dayLong(fourteenth, Locale.ITALIAN))
        assertEquals("14 October", DateLabels.dayMonth(fourteenth, Locale.ENGLISH))
        assertEquals("14 Oct", DateLabels.dayShort(fourteenth, Locale.ENGLISH))
    }

    @Test
    fun `the weekday initials start on Monday in both locales`() {
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), DateLabels.weekdayInitials(Locale.ENGLISH))
        assertEquals(listOf("L", "M", "M", "G", "V", "S", "D"), DateLabels.weekdayInitials(Locale.ITALIAN))
    }
}
