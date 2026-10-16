package com.ggym.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ggym.app.R
import java.util.Locale

/**
 * Durations, as text.
 *
 * Two shapes cover everything the app shows: a **clock** (`mm:ss`, `h:mm:ss` once it runs past an
 * hour) for the elapsed time and the rest countdown, and whole-minute **phrases** for summaries
 * ("23 min", "1 h 05 min"). Both are pure arithmetic — the unit words live in `strings.xml`,
 * because "23 min" versus "23m" is a translation question, not a numeric one — and both are
 * zero-padded, because digits that jump sideways are unreadable on the screens that show them.
 *
 * A countdown rounds **up** ([ceilingSeconds]): the banner keeps saying "1 s" until the rest is
 * genuinely over, rather than showing "0 s" while the timer still runs.
 */
object DurationFormat {

    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L

    /** The whole seconds a duration holds, floored, never negative. */
    fun seconds(millis: Long): Long = (millis / MILLIS_PER_SECOND).coerceAtLeast(0)

    /** What a countdown is still counting: seconds left, rounded up, never negative. */
    fun ceilingSeconds(millis: Long): Long =
        (millis.coerceAtLeast(0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND

    /**
     * A running clock: `"23:45"`, or `"1:02:05"` once past the hour. [locale] only decides how the
     * digits are written — the separators are structural, so a countdown reads the same everywhere.
     */
    fun clock(millis: Long, locale: Locale): String {
        val total = seconds(millis)
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE

        return if (total >= SECONDS_PER_HOUR) {
            String.format(locale, "%d:%02d:%02d", total / SECONDS_PER_HOUR, minutes, seconds)
        } else {
            String.format(locale, "%02d:%02d", minutes, seconds)
        }
    }

    /** Whole minutes, floored: what a "23 min" summary counts. */
    fun minutes(millis: Long): Long = seconds(millis) / SECONDS_PER_MINUTE

    /** The hours of a duration, for the "1 h 05 min" form. */
    fun hours(millis: Long): Long = seconds(millis) / SECONDS_PER_HOUR

    /** The minutes inside the hour of a duration, for the "1 h 05 min" form. */
    fun minutesWithinHour(millis: Long): Long = (seconds(millis) % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
}

/**
 * A duration as a summary phrase: "23 min" for a workout under an hour, "1 h 05 min" past it.
 *
 * Under a minute it still says "0 min": a workout's length is a fact about the workout, and
 * "40 s" would be a precision nobody asked for.
 */
@Composable
fun durationSummary(millis: Long): String = if (DurationFormat.hours(millis) > 0) {
    stringResource(
        R.string.duration_hours_minutes,
        DurationFormat.hours(millis),
        DurationFormat.minutesWithinHour(millis),
    )
} else {
    stringResource(R.string.duration_minutes, DurationFormat.minutes(millis))
}
