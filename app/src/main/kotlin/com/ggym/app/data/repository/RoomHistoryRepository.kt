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
 * numbers do not add up. A draft is written the same way, and finishing one writes the finished
 * session and deletes the draft in a single transaction, so a workout can be lost at no point in
 * between.
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

    override fun observeDraft(): Flow<WorkoutSession?> =
        dao.observeDraft().map { row -> row?.let { WorkoutMapper.toDomain(it, zoneId) } }

    override fun observeDraft(sessionId: Long): Flow<WorkoutSession?> =
        dao.observeDraftById(sessionId).map { row -> row?.let { WorkoutMapper.toDomain(it, zoneId) } }

    override suspend fun startDraft(draft: WorkoutSession): Long = database.withTransaction {
        insertSessionTree(draft, isDraft = true)
    }

    override suspend fun finishDraft(draftId: Long, session: WorkoutSession): Long = database.withTransaction {
        // Order matters only for readability — the transaction is what makes it safe. The finished
        // session is a new row with its own id; the draft, and everything the cascade hangs off it,
        // is what disappears.
        val sessionId = insertSessionTree(session, isDraft = false)
        dao.deleteDraftById(draftId)
        sessionId
    }

    override suspend fun discardDraft(draftId: Long) {
        dao.deleteDraftById(draftId)
    }

    override suspend fun appendSet(workoutExerciseLogId: Long, set: WorkoutSetLog): Long =
        // The log id comes from the caller and the set's id is ignored: a set is always *added to*
        // an exercise, and a stale id in the model must not silently move it somewhere else.
        dao.insertSet(
            WorkoutMapper.toEntity(set).copy(id = 0, workoutExerciseLogId = workoutExerciseLogId),
        )

    override suspend fun saveSession(session: WorkoutSession): Long = database.withTransaction {
        insertSessionTree(session, isDraft = false)
    }

    /**
     * Writes a session's three tables in one transaction.
     *
     * The session ids handed in on the models are ignored and fresh ones are created, so a write
     * can only ever add a workout, never overwrite one. [isDraft] is the method's decision rather
     * than the model's: `saveSession` writes history, `startDraft` writes a workout in progress,
     * and neither can be mistaken for the other at the call site.
     */
    private suspend fun insertSessionTree(session: WorkoutSession, isDraft: Boolean): Long {
        val sessionId = dao.insertSession(
            WorkoutMapper.toEntity(session, zoneId).copy(id = 0, isDraft = isDraft),
        )

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

        return sessionId
    }

    override suspend fun deleteSession(sessionId: Long) {
        dao.deleteById(sessionId)
    }

    override suspend fun updateSet(set: WorkoutSetLog) {
        dao.updateSet(WorkoutMapper.toEntity(set))
    }

    override suspend fun updateSets(sets: List<WorkoutSetLog>) {
        // Room performs a collection update in a transaction of its own.
        dao.updateSets(sets.map(WorkoutMapper::toEntity))
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
