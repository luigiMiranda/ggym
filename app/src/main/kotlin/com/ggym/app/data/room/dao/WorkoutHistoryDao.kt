package com.ggym.app.data.room.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.ggym.app.data.room.entity.WorkoutExerciseLogEntity
import com.ggym.app.data.room.entity.WorkoutSessionEntity
import com.ggym.app.data.room.entity.WorkoutSetLogEntity
import kotlinx.coroutines.flow.Flow

/** A session with the exercises performed in it, each carrying its sets. */
internal data class SessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = WorkoutExerciseLogEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<WorkoutExerciseLogWithSets>,
)

/** One exercise log with its sets. */
internal data class WorkoutExerciseLogWithSets(
    @Embedded val log: WorkoutExerciseLogEntity,
    @Relation(entity = WorkoutSetLogEntity::class, parentColumn = "id", entityColumn = "workoutExerciseLogId")
    val sets: List<WorkoutSetLogEntity>,
)

@Dao
internal interface WorkoutHistoryDao {

    /**
     * Sessions in a half-open millisecond range, `[fromInclusive, toExclusive)`, newest last.
     * The repository turns a pair of dates into this range, so a range ends where the caller
     * means it to: at the end of the last day, not at its midnight.
     *
     * Drafts are excluded: for the calendar, "a workout happened" means a finished one.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_session
        WHERE dateTimestamp >= :fromInclusive AND dateTimestamp < :toExclusive AND isDraft = 0
        ORDER BY dateTimestamp ASC, id ASC
        """,
    )
    fun observeSessionsInRange(fromInclusive: Long, toExclusive: Long): Flow<List<SessionWithExercises>>

    /**
     * The dates of finished sessions in the range, with no exercise or set trees.
     *
     * The attendance chart asks "which days did I train", and loading a year of sets to answer it
     * would be the expensive way to count days. A session's date is stored at local midnight, so
     * the bucket is exact; drafts are excluded for the same reason as everywhere else.
     */
    @Query(
        """
        SELECT dateTimestamp FROM workout_session
        WHERE dateTimestamp >= :fromInclusive AND dateTimestamp < :toExclusive AND isDraft = 0
        ORDER BY dateTimestamp ASC, id ASC
        """,
    )
    fun observeSessionDates(fromInclusive: Long, toExclusive: Long): Flow<List<Long>>

    /** One finished session, for inspecting or correcting a past workout. Drafts are not history. */
    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :sessionId AND isDraft = 0")
    fun observeSession(sessionId: Long): Flow<SessionWithExercises?>

    /**
     * The workout in progress, if there is one.
     *
     * There can be at most one: two concurrent sessions have no meaning for a single lifter, and
     * the screens ask resume-or-discard rather than starting a second. The ordering is a
     * belt-and-braces tie-break for a database that somehow held two.
     */
    @Transaction
    @Query("SELECT * FROM workout_session WHERE isDraft = 1 ORDER BY id DESC LIMIT 1")
    fun observeDraft(): Flow<SessionWithExercises?>

    /** One draft by id: the active-workout screen keeps observing the workout it was opened on. */
    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :sessionId AND isDraft = 1")
    fun observeDraftById(sessionId: Long): Flow<SessionWithExercises?>

    /**
     * One-shot read for the backup exporter, drafts included: the file is the whole database, so
     * restoring on a new phone can resume a workout instead of silently losing it.
     */
    @Transaction
    @Query("SELECT * FROM workout_session ORDER BY id ASC")
    suspend fun getAllSessions(): List<SessionWithExercises>

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    /** Inserts the logs of one session; the returned ids line up with the input order. */
    @Insert
    suspend fun insertExerciseLogs(logs: List<WorkoutExerciseLogEntity>): List<Long>

    @Insert
    suspend fun insertExerciseLog(log: WorkoutExerciseLogEntity): Long

    /** Inserts the sets of one exercise log; the returned ids line up with the input order. */
    @Insert
    suspend fun insertSets(sets: List<WorkoutSetLogEntity>): List<Long>

    @Insert
    suspend fun insertSet(set: WorkoutSetLogEntity): Long

    @Query("DELETE FROM workout_session WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long)

    /**
     * Removes a workout that is still running. The draft flag is part of the condition so that
     * discarding can never delete a finished workout, whatever id it is handed.
     */
    @Query("DELETE FROM workout_session WHERE id = :sessionId AND isDraft = 1")
    suspend fun deleteDraftById(sessionId: Long)

    @Update
    suspend fun updateSet(set: WorkoutSetLogEntity)

    /** Writes several sets as one transaction: a set completion lands with the prefills it moves. */
    @Update
    suspend fun updateSets(sets: List<WorkoutSetLogEntity>)

    @Query("DELETE FROM workout_set_log WHERE id = :setId")
    suspend fun deleteSetById(setId: Long)

    @Query("DELETE FROM workout_set_log")
    suspend fun deleteAllSets()

    @Query("DELETE FROM workout_exercise_log")
    suspend fun deleteAllExerciseLogs()

    @Query("DELETE FROM workout_session")
    suspend fun deleteAllSessions()
}
