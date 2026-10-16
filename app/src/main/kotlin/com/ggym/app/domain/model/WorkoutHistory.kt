package com.ggym.app.domain.model

import java.time.LocalDate

/**
 * One completed workout.
 *
 * The routine and day names are *snapshots*: renaming or deleting a routine must not rewrite
 * history, let alone delete it. Only the date and the duration are inherent to the session
 * itself.
 */
data class WorkoutSession(
    val id: Long = 0,
    /** The day the session belongs to, as a date — never a time of day. */
    val date: LocalDate,
    val routineNameSnapshot: String,
    val dayNameSnapshot: String,
    val durationSeconds: Long,
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
