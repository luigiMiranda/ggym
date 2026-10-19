package com.ggym.app.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Row of [ExerciseProgressDao.latestCompletedSet]: the performed values plus where they happened. */
internal data class LastCompletedSetRow(
    val exerciseId: Long,
    val weight: Double,
    val reps: Int,
    val setNumber: Int,
    val sessionId: Long,
    val sessionDateTimestamp: Long,
)

/** Row of [ExerciseProgressDao.observeCompletedSets]: a performed set and the day it happened. */
internal data class LoggedSetRow(
    val sessionDateTimestamp: Long,
    val weight: Double,
    val reps: Int,
    val sessionId: Long,
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
     * Drafts are ignored too: the workout in progress answers "what am I lifting now", and that
     * number reaches the dictionary the moment the set is completed.
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
              AND workout_session.isDraft = 0
        ORDER BY workout_session.dateTimestamp DESC, workout_session.id DESC, workout_set_log.setNumber DESC
        LIMIT 1
        """,
    )
    suspend fun latestCompletedSet(exerciseId: Long): LastCompletedSetRow?

    /**
     * Every completed set of one exercise, oldest session first.
     *
     * The progression chart reads this raw and does its own grouping (one point per date, the
     * maximum of the day). Drafts are excluded because a workout in progress has not happened yet,
     * and pre-filled sets are excluded because they are an intention — `isCompleted` is what
     * separates "I lifted this" from "I planned this".
     */
    @Query(
        """
        SELECT workout_session.dateTimestamp AS sessionDateTimestamp,
               workout_set_log.weight AS weight,
               workout_set_log.reps AS reps,
               workout_session.id AS sessionId
        FROM workout_set_log
        JOIN workout_exercise_log ON workout_exercise_log.id = workout_set_log.workoutExerciseLogId
        JOIN workout_session ON workout_session.id = workout_exercise_log.sessionId
        WHERE workout_exercise_log.exerciseId = :exerciseId
              AND workout_set_log.isCompleted = 1
              AND workout_session.isDraft = 0
        ORDER BY workout_session.dateTimestamp ASC, workout_session.id ASC, workout_set_log.setNumber ASC
        """,
    )
    fun observeCompletedSets(exerciseId: Long): Flow<List<LoggedSetRow>>
}
