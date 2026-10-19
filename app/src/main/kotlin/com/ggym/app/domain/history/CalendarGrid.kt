package com.ggym.app.domain.history

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * One square of the month grid.
 *
 * [inMonth] is `false` for the leading and trailing days that belong to the months on either side:
 * they are drawn so the weeks line up, dimmed because the selected month is the subject. The
 * "today" and "selected" marks are deliberately *not* here — those are facts about the moment the
 * screen is drawn, not about the month, so the builder stays a pure function of its one input.
 */
data class CalendarCell(
    val date: LocalDate,
    val inMonth: Boolean,
)

/**
 * A month laid out as six Monday-first weeks.
 *
 * Six rows **always**, even when the month would fit in four or five: the grid is the anchor of
 * the screen and a calendar whose body jumps by 48dp when the user pages through months is worse
 * than a few dimmed days. The extra days are the adjacent months' own dates, so tapping one is a
 * meaningful tap.
 *
 * Calendar arithmetic is where the bugs are — a leap February, a 31-day month that starts on a
 * Sunday, the year boundary — so it lives in one tested function rather than in the screen.
 */
data class CalendarGrid(
    val month: YearMonth,
    val weeks: List<List<CalendarCell>>,
) {
    /** The 42 cells in reading order, for a renderer that does not care about the week breaks. */
    val cells: List<CalendarCell> get() = weeks.flatten()

    companion object {
        const val WEEK_COUNT = 6
        const val DAYS_PER_WEEK = 7

        /** The grid of [month]: Monday first, six weeks, adjacent days dimmed. */
        fun of(month: YearMonth): CalendarGrid {
            val gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

            val weeks = (0 until WEEK_COUNT).map { week ->
                (0 until DAYS_PER_WEEK).map { day ->
                    val date = gridStart.plusDays((week * DAYS_PER_WEEK + day).toLong())
                    CalendarCell(date = date, inMonth = YearMonth.from(date) == month)
                }
            }
            return CalendarGrid(month = month, weeks = weeks)
        }
    }
}
