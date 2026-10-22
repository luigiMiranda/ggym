package com.ggym.app.domain.model

import java.time.LocalDate

/**
 * One workout: either a finished one that history shows, or one that is still running.
 *
 * The routine and day names are *snapshots*: renaming or deleting a routine must not rewrite
 * history, let alone delete it. Only the date and the duration are inherent to the session
 * itself.
 *
 * [isDraft] is what separates the two states. A draft is the workout the user is in the middle of:
 * it is written the moment the workout starts and updated set by set, and it is invisible to every
 * history read — the calendar, the charts and "what did I lift last" all mean *finished* workouts.
 * It becomes history only when the workout is finished, and then as a new session row; discarding
 * it leaves none. See `WorkoutHistoryRepository`.
 *
 * [startedAtMillis] is when the workout began, epoch millis, and is `null` on history written
 * before drafts existed. Only a draft is required to carry one.
 */
data class WorkoutSession(
    val id: Long = 0,
    /** The day the session belongs to, as a date — never a time of day. */
    val date: LocalDate,
    val routineNameSnapshot: String,
    val dayNameSnapshot: String,
    val durationSeconds: Long,
    val isDraft: Boolean = false,
    val startedAtMillis: Long? = null,
    val exercises: List<WorkoutExerciseLog> = emptyList(),
)

/**
 * One exercise inside a [WorkoutSession], including the ones the user skipped
 * ([wasCompleted] is `false` for those).
 *
 * [exerciseId] is nullable: deleting a dictionary entry detaches it here rather than destroying
 * logged work, and [exerciseNameSnapshot] keeps the line readable afterwards.
 */
data class WorkoutExerciseLog(
    val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long?,
    val exerciseNameSnapshot: String,
    val wasCompleted: Boolean,
    val orderIndex: Int,
    /**
     * The rest time to use between this exercise's sets, snapshotted when the workout started, so
     * editing or deleting the routine mid-workout cannot change it.
     */
    val restSeconds: Int = Exercise.DEFAULT_REST_SECONDS,
    /**
     * The written target reps snapshotted at the same moment, shown next to the actual being
     * entered. Blank means the workout has no target to show — history written before the column
     * existed, or a slot that was somehow planned without one.
     */
    val targetReps: String = "",
    /**
     * The routine slot this log was materialized from, so a completion can move that day's weight
     * too (`WorkingWeightUseCases`). `null` when there is no slot behind it: history written before
     * the pointer existed, a restored draft from an older backup, or a slot deleted mid-workout.
     */
    val plannedExerciseId: Long? = null,
    val sets: List<WorkoutSetLog> = emptyList(),
)

/** One performed set: what the user actually lifted, which is what the charts plot. */
data class WorkoutSetLog(
    val id: Long = 0,
    val workoutExerciseLogId: Long,
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val isCompleted: Boolean,
)

/**
 * The most recent completed set of one exercise — the fact behind "persistent current weight".
 *
 * It is deliberately a query result rather than a stored flag: the number the user sees must be
 * the last thing they lifted, not a value that some other code path could have failed to update.
 */
data class LastCompletedSet(
    val exerciseId: Long,
    val weightKg: Double,
    val reps: Int,
    val setNumber: Int,
    val sessionId: Long,
    val date: LocalDate,
)

/**
 * One performed set of one exercise, with the day it happened.
 *
 * This is the raw material of the progression chart: the maximum weight per date is the number a
 * lifter reads as "my bench that day", so the query that produces these rows filters to finished
 * workouts and completed sets and leaves every decision about grouping to the domain.
 */
data class LoggedSetPoint(
    val date: LocalDate,
    val weightKg: Double,
    val reps: Int,
    val sessionId: Long,
)
