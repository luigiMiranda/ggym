package com.ggym.app.data.repository

import androidx.room.withTransaction
import com.ggym.app.data.room.GgymDatabase
import com.ggym.app.data.room.mappers.WorkoutMapper
import com.ggym.app.data.room.mappers.dayRangeMillis
import com.ggym.app.data.room.mappers.toLocalDate
import com.ggym.app.domain.model.LastCompletedSet
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.model.WorkoutSetLog
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/**
 * Training history, on top of Room.
 *
 * Saving a session writes its three tables in one transaction: a session with missing sets would
 * be worse than no session at all, because the user would see a workout in the calendar whose
 * numbers do not add up.
 *
 * The [ZoneId] is a constructor parameter so the mapping between a session's day and its stored
 * midnight timestamp never depends on the machine running the test.
 */
internal class RoomHistoryRepository(
    private val database: GgymDatabase,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : WorkoutHistoryRepository {

    private val dao = database.workoutHistoryDao()

    /** Progression questions span the dictionary and the history, so they have their own DAO. */
    private val progressDao = database.exerciseProgressDao()

    override fun observeSessionsInRange(from: LocalDate, to: LocalDate): Flow<List<WorkoutSession>> {
        val range = dayRangeMillis(from, to, zoneId)
        return dao.observeSessionsInRange(range.fromInclusive, range.toExclusive)
            .map { rows -> rows.map { WorkoutMapper.toDomain(it, zoneId) } }
    }

    override fun observeSession(sessionId: Long): Flow<WorkoutSession?> =
        dao.observeSession(sessionId).map { row -> row?.let { WorkoutMapper.toDomain(it, zoneId) } }

    override suspend fun saveSession(session: WorkoutSession): Long = database.withTransaction {
        // A session is written once, when the workout ends: the id it may carry is ignored and a
        // fresh row is created, so a retried save cannot silently overwrite an earlier workout.
        val sessionId = dao.insertSession(WorkoutMapper.toEntity(session, zoneId).copy(id = 0))

        val logIds = dao.insertExerciseLogs(
            session.exercises.map { log ->
                WorkoutMapper.toEntity(log).copy(id = 0, sessionId = sessionId)
            },
        )

        val sets = session.exercises.flatMapIndexed { index, log ->
            log.sets.map { set ->
                WorkoutMapper.toEntity(set).copy(id = 0, workoutExerciseLogId = logIds[index])
            }
        }
        dao.insertSets(sets)

        sessionId
    }

    override suspend fun deleteSession(sessionId: Long) {
        dao.deleteById(sessionId)
    }

    override suspend fun updateSet(set: WorkoutSetLog) {
        dao.updateSet(WorkoutMapper.toEntity(set))
    }

    override suspend fun deleteSet(setId: Long) {
        dao.deleteSetById(setId)
    }

    override suspend fun latestCompletedSet(exerciseId: Long): LastCompletedSet? =
        progressDao.latestCompletedSet(exerciseId)?.let { row ->
            LastCompletedSet(
                exerciseId = row.exerciseId,
                weightKg = row.weight,
                reps = row.reps,
                setNumber = row.setNumber,
                sessionId = row.sessionId,
                date = row.sessionDateTimestamp.toLocalDate(zoneId),
            )
        }
}
