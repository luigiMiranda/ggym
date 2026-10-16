package com.ggym.app.data.room.mappers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The date/timestamp boundary, tested without Android: it is plain `java.time` arithmetic and the
 * only interesting cases are the ones where "local midnight" is not obvious.
 */
class DateConversionsTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `a stored date is the local midnight of that day`() {
        val date = LocalDate.of(2026, 1, 5)
        val millis = date.toLocalMidnightMillis(utc)

        assertEquals(date, millis.toLocalDate(utc))
        assertEquals("00:00", java.time.Instant.ofEpochMilli(millis).atZone(utc).toLocalTime().toString().take(5))
    }

    @Test
    fun `the zone decides which instant a date means`() {
        val date = LocalDate.of(2026, 1, 5)

        val inUtc = date.toLocalMidnightMillis(utc)
        val inTokyo = date.toLocalMidnightMillis(ZoneId.of("Asia/Tokyo"))

        assertEquals(
            "Tokyo is nine hours ahead, so its midnight is nine hours earlier in absolute time",
            inTokyo,
            inUtc - 9 * 60 * 60 * 1000,
        )
        assertEquals(date, inTokyo.toLocalDate(ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun `an instant near midnight belongs to the date the zone says it does`() {
        // 23:00 on the 5th in UTC is already the 6th in Berlin.
        val lateEvening = LocalDate.of(2026, 1, 5).toLocalMidnightMillis(utc) + 23 * 60 * 60 * 1000

        assertEquals(LocalDate.of(2026, 1, 5), lateEvening.toLocalDate(utc))
        assertEquals(LocalDate.of(2026, 1, 6), lateEvening.toLocalDate(ZoneId.of("Europe/Berlin")))
    }

    @Test
    fun `every date survives the round trip, including days whose midnight does not exist`() {
        // Cuba starts daylight saving time at midnight, so on that day 00:00 never happens.
        val zones = listOf(utc, ZoneId.of("America/Havana"), ZoneId.of("Pacific/Chatham"), ZoneId.of("Europe/Rome"))
        val dates = listOf(
            LocalDate.of(2026, 1, 5),
            LocalDate.of(2026, 3, 8),
            LocalDate.of(2026, 9, 6),
            LocalDate.of(2026, 10, 25),
            LocalDate.of(2026, 12, 31),
        )

        for (zone in zones) {
            for (date in dates) {
                assertEquals(
                    "round trip through storage failed for $date in $zone",
                    date,
                    date.toLocalMidnightMillis(zone).toLocalDate(zone),
                )
            }
        }
    }

    @Test
    fun `a day range covers the whole of the last day`() {
        val range = dayRangeMillis(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), utc)

        assertEquals(LocalDate.of(2026, 1, 5).toLocalMidnightMillis(utc), range.fromInclusive)
        assertEquals("the range stops at the midnight after the last day", LocalDate.of(2026, 1, 8).toLocalMidnightMillis(utc), range.toExclusive)

        val lastMomentOfTheRange = LocalDate.of(2026, 1, 7).toLocalMidnightMillis(utc) + 23 * 60 * 60 * 1000
        assertTrue("a timestamp inside the last day is still in range", lastMomentOfTheRange < range.toExclusive)
    }
}
