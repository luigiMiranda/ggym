package com.ggym.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The two duration shapes: a running clock and a whole-minute summary. */
class DurationFormatTest {

    @Test
    fun `the clock pads both fields and never goes negative`() {
        assertEquals("00:00", DurationFormat.clock(0, Locale.ENGLISH))
        assertEquals("00:00", DurationFormat.clock(-1_000, Locale.ENGLISH))
        assertEquals("00:00", DurationFormat.clock(999, Locale.ENGLISH))
        assertEquals("00:01", DurationFormat.clock(1_000, Locale.ENGLISH))
        assertEquals("00:59", DurationFormat.clock(59_999, Locale.ENGLISH))
        assertEquals("01:00", DurationFormat.clock(60_000, Locale.ENGLISH))
        assertEquals("23:45", DurationFormat.clock(23 * 60_000L + 45_000, Locale.ENGLISH))
    }

    @Test
    fun `past an hour the clock gains an hour field`() {
        assertEquals("1:00:00", DurationFormat.clock(3_600_000, Locale.ENGLISH))
        assertEquals("1:02:05", DurationFormat.clock(3_725_000, Locale.ENGLISH))
        assertEquals("12:00:00", DurationFormat.clock(12 * 3_600_000L, Locale.ENGLISH))
    }

    @Test
    fun `a countdown rounds up so it says one second until it is over`() {
        assertEquals(0, DurationFormat.ceilingSeconds(-500))
        assertEquals(0, DurationFormat.ceilingSeconds(0))
        assertEquals(1, DurationFormat.ceilingSeconds(1))
        assertEquals(1, DurationFormat.ceilingSeconds(1_000))
        assertEquals(2, DurationFormat.ceilingSeconds(1_001))
        assertEquals(90, DurationFormat.ceilingSeconds(90_000))
    }

    @Test
    fun `summaries count whole minutes, floored`() {
        assertEquals(0, DurationFormat.minutes(59_999))
        assertEquals(1, DurationFormat.minutes(60_000))
        assertEquals(23, DurationFormat.minutes(23 * 60_000L + 59_000))
        assertEquals(1, DurationFormat.hours(3_600_000))
        assertEquals(1, DurationFormat.hours(3_900_000))
        assertEquals(0, DurationFormat.hours(3_599_999))
        assertEquals(5, DurationFormat.minutesWithinHour(3_900_000))
        assertEquals(5, DurationFormat.minutesWithinHour(5 * 60_000L))
    }

    @Test
    fun `the clock follows the locale's digits without changing its shape`() {
        assertEquals("01:00", DurationFormat.clock(60_000, Locale.ITALIAN))
        assertEquals("01:00", DurationFormat.clock(60_000, Locale.forLanguageTag("en-US")))
    }
}
