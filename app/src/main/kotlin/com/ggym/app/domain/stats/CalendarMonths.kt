package com.ggym.app.domain.stats

import java.time.LocalDate
import java.time.YearMonth

/**
 * The months the attendance chart counts in.
 *
 * Twelve, including the current one: a window long enough to show a season or a break, short
 * enough that a bar is still a month rather than a smear. The list is oldest first, which is the
 * order the chart draws in.
 */
object CalendarMonths {

    const val ATTENDANCE_MONTHS = 12

    /** The last [count] months ending with [today]'s month, oldest first. */
    fun last(count: Int, today: LocalDate): List<YearMonth> {
        if (count <= 0) return emptyList()
        val current = YearMonth.from(today)
        return (count - 1 downTo 0).map { monthsBack -> current.minusMonths(monthsBack.toLong()) }
    }
}
