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
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_session
        WHERE dateTimestamp >= :fromInclusive AND dateTimestamp < :toExclusive
        ORDER BY dateTimestamp ASC, id ASC
        """,
    )
    fun observeSessionsInRange(fromInclusive: Long, toExclusive: Long): Flow<List<SessionWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<SessionWithExercises?>

    /** One-shot read for the backup exporter. */
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

    @Update
    suspend fun updateSet(set: WorkoutSetLogEntity)

    @Query("DELETE FROM workout_set_log WHERE id = :setId")
    suspend fun deleteSetById(setId: Long)

    @Query("DELETE FROM workout_set_log")
    suspend fun deleteAllSets()

    @Query("DELETE FROM workout_exercise_log")
    suspend fun deleteAllExerciseLogs()

    @Query("DELETE FROM workout_session")
    suspend fun deleteAllSessions()
}
