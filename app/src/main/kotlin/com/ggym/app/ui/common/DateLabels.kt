package com.ggym.app.ui.common

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dates and month names, as text.
 *
 * The app stores days, not labels, so every name on a screen is produced here from a
 * `DateTimeFormatter` and the active locale — a hardcoded "October" would be a second thing to
 * translate, and an Italian phone already orders and cases its dates differently.
 *
 * The patterns are structural and never localized: `EEEE d MMMM` is "Wednesday 14 October" in
 * English and "mercoledì 14 ottobre" in Italian, and nothing in between needs the app's help.
 */
object DateLabels {

    /** The calendar header: "October 2026". */
    fun monthTitle(month: YearMonth, locale: Locale): String =
        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

    /** Just the month's name, for a summary that already names the year elsewhere: "October". */
    fun monthName(month: YearMonth, locale: Locale): String =
        month.format(DateTimeFormatter.ofPattern("LLLL", locale))

    /** The short month, for a chart axis: "Oct". */
    fun monthShort(month: YearMonth, locale: Locale): String =
        month.format(DateTimeFormatter.ofPattern("LLL", locale))

    /** A day with its weekday, for a section header: "Wednesday 14 October". */
    fun dayLong(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))

    /** A day without its weekday, for a card or a dialog: "14 October". */
    fun dayMonth(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern("d MMMM", locale))

    /** The compact day a log row uses: "14 Oct". */
    fun dayShort(date: LocalDate, locale: Locale): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", locale))

    /**
     * The weekday initials above the grid, Monday first.
     *
     * Monday first is a layout decision (the preview and the primary market), not a per-device
     * preference: the grid is the same shape on every phone the app runs on. Narrow names are what
     * a calendar's heading has room for — "M T W T F S S" in English, "L M M G V S D" in Italian.
     */
    fun weekdayInitials(locale: Locale): List<String> =
        MONDAY_FIRST.map { day -> day.getDisplayName(TextStyle.NARROW, locale) }

    private val MONDAY_FIRST = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )
}
