package com.ggym.app.domain.stats

import java.time.LocalDate
import java.time.YearMonth

/** One bar of the attendance chart: a month and how many distinct days were trained in it. */
data class MonthAttendance(
    val month: YearMonth,
    val workoutDays: Int,
)

/**
 * "How many days did I train each month?"
 *
 * The count is **distinct days**, not sessions: two workouts on one day is one day of attendance,
 * which is what the number means to a lifter. Months in the window with no training are kept with
 * a zero — a gap is data, not an absence of data.
 */
object AttendanceCounts {

    fun from(dates: List<LocalDate>, months: List<YearMonth>): List<MonthAttendance> {
        val trained = dates.distinct()
        return months.map { month ->
            MonthAttendance(
                month = month,
                workoutDays = trained.count { YearMonth.from(it) == month },
            )
        }
    }
}
