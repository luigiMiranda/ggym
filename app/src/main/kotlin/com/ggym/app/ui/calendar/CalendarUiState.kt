package com.ggym.app.ui.calendar

import com.ggym.app.domain.history.CalendarGrid
import com.ggym.app.domain.model.WorkoutExerciseLog
import com.ggym.app.domain.model.WorkoutSession
import java.time.LocalDate
import java.time.YearMonth

/**
 * One exercise of a logged workout, as the calendar's day card shows it.
 *
 * [maxWeightKg] is the heaviest *completed* set, not the planned weight and not the slot's target:
 * the card is a record of what happened. A skipped exercise keeps its line — [skipped] — because a
 * card that silently drops it would make the workout look shorter than it was.
 */
data class CalendarExerciseLine(
    val name: String,
    val setCount: Int,
    val maxWeightKg: Double?,
    val skipped: Boolean,
)

/** One finished workout on the selected day. */
data class CalendarSessionCard(
    val sessionId: Long,
    val date: LocalDate,
    val dayName: String,
    val routineName: String,
    val durationSeconds: Long,
    val exercises: List<CalendarExerciseLine>,
)

/** The calendar tab: one month, one selected day, and the workouts logged on it. */
data class CalendarUiState(
    /** True until the shown month's sessions have been read; a month change starts a new read. */
    val isLoading: Boolean = true,
    val month: YearMonth,
    val today: LocalDate,
    val selectedDate: LocalDate,
    val grid: CalendarGrid,
    /** The month's finished workouts, bucketed by their local date. Drafts are never in here. */
    val sessionsByDay: Map<LocalDate, List<CalendarSessionCard>> = emptyMap(),
    /** The selected day's workouts, in stored order. */
    val selectedSessions: List<CalendarSessionCard> = emptyList(),
    /** The workout whose deletion is being confirmed. */
    val pendingDelete: CalendarSessionCard? = null,
) {
    /** The dot's condition: at least one finished workout on the day. */
    fun workoutCount(date: LocalDate): Int = sessionsByDay[date]?.size ?: 0

    val hasWorkoutsThisMonth: Boolean get() = sessionsByDay.isNotEmpty()
}

/**
 * A session as the calendar reads it.
 *
 * The counts come from the completed sets, and an exercise with none is "skipped" whether the flag
 * says so or not: history written before the flag existed would otherwise claim every exercise was
 * performed.
 */
internal fun WorkoutSession.toCalendarCard(): CalendarSessionCard = CalendarSessionCard(
    sessionId = id,
    date = date,
    dayName = dayNameSnapshot,
    routineName = routineNameSnapshot,
    durationSeconds = durationSeconds,
    exercises = exercises
        .sortedBy(WorkoutExerciseLog::orderIndex)
        .map { log ->
            val completed = log.sets.filter { it.isCompleted }
            CalendarExerciseLine(
                name = log.exerciseNameSnapshot,
                setCount = completed.size,
                maxWeightKg = completed.maxOfOrNull { it.weight },
                skipped = !log.wasCompleted || completed.isEmpty(),
            )
        },
)
