package com.ggym.app.domain.stats

import java.time.LocalDate

/**
 * The body-weight chart's windows.
 *
 * A window is **relative to today**, not to the latest measurement: "the last 3 months" must not
 * silently stretch when the user stops weighing in. Both ends are inclusive, so a measurement on
 * the boundary day belongs to the window.
 *
 * [ALL] reaches back to the beginning of the calendar — the app has no measurements before that,
 * and an imported file with an older date would still be shown rather than quietly dropped.
 */
enum class BodyWeightRange(private val monthsBack: Long?) {
    ONE_MONTH(1),
    THREE_MONTHS(3),
    SIX_MONTHS(6),
    ONE_YEAR(12),
    ALL(null),
    ;

    /** The first day in the window that ends with [today]. */
    fun from(today: LocalDate): LocalDate = when (monthsBack) {
        null -> EARLIEST_DAY
        else -> today.minusMonths(monthsBack)
    }

    /** Whether [date] falls in the window ending with [today]. */
    fun contains(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(from(today)) && !date.isAfter(today)

    companion object {
        /** What the screen opens on: the preview highlights this one. */
        val DEFAULT = THREE_MONTHS

        private val EARLIEST_DAY: LocalDate = LocalDate.of(1, 1, 1)
    }
}
