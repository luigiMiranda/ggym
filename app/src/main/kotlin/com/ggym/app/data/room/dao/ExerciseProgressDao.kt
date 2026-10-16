package com.ggym.app.data.room.dao

import androidx.room.Dao
import androidx.room.Query

/** Row of [ExerciseProgressDao.latestCompletedSet]: the performed values plus where they happened. */
internal data class LastCompletedSetRow(
    val exerciseId: Long,
    val weight: Double,
    val reps: Int,
    val setNumber: Int,
    val sessionId: Long,
    val sessionDateTimestamp: Long,
)

/**
 * Progression queries that span the dictionary and the history.
 *
 * They live in their own DAO because they belong to neither: the dictionary owns the exercise,
 * the history owns the sets, and this asks a question about both.
 */
@Dao
internal interface ExerciseProgressDao {

    /**
     * The last set the user actually completed for one exercise.
     *
     * Ordered by session date (then session id, then set number) rather than by insertion order,
     * so correcting an old workout or backdating a session cannot promote a stale set to
     * "current". Incomplete sets are ignored: a pre-filled set is an intention, not a lift.
     */
    @Query(
        """
        SELECT workout_exercise_log.exerciseId AS exerciseId,
               workout_set_log.weight AS weight,
               workout_set_log.reps AS reps,
               workout_set_log.setNumber AS setNumber,
               workout_session.id AS sessionId,
               workout_session.dateTimestamp AS sessionDateTimestamp
        FROM workout_set_log
        JOIN workout_exercise_log ON workout_exercise_log.id = workout_set_log.workoutExerciseLogId
        JOIN workout_session ON workout_session.id = workout_exercise_log.sessionId
        WHERE workout_exercise_log.exerciseId = :exerciseId AND workout_set_log.isCompleted = 1
        ORDER BY workout_session.dateTimestamp DESC, workout_session.id DESC, workout_set_log.setNumber DESC
        LIMIT 1
        """,
    )
    suspend fun latestCompletedSet(exerciseId: Long): LastCompletedSetRow?
}
