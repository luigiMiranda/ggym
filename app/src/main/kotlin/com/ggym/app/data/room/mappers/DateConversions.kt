package com.ggym.app.data.room.mappers

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single place where a stored timestamp becomes a date and back.
 *
 * Date-only values are stored as the millis of **local midnight** so that calendar bucketing (a
 * workout or body-weight entry belongs to a day) is exact, and so that a chart never shows a
 * measurement on the wrong day because of the time of day it was saved at.
 *
 * [ZoneId] is a parameter rather than `ZoneId.systemDefault()` so the conversion is testable and
 * so a caller can decide which local day it means.
 */
internal fun LocalDate.toLocalMidnightMillis(zone: ZoneId): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Inverse of [toLocalMidnightMillis].
 *
 * Values that are *not* exactly midnight (a row written by an older build, or a hand-edited
 * backup file) still resolve to the day they fall on, which is the day the user means.
 */
internal fun Long.toLocalDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/**
 * A half-open millisecond range, `[fromInclusive, toExclusive)`.
 *
 * Callers think in days — "this month", "the last 90 days" — and a range that ended at the
 * midnight *starting* the last day would silently drop that day, so the translation to millis
 * happens once, here.
 */
internal data class MillisRange(val fromInclusive: Long, val toExclusive: Long)

/** The millis covering [from] up to and including [to]. */
internal fun dayRangeMillis(from: LocalDate, to: LocalDate, zone: ZoneId): MillisRange = MillisRange(
    fromInclusive = from.toLocalMidnightMillis(zone),
    toExclusive = to.plusDays(1).toLocalMidnightMillis(zone),
)
