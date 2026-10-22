package com.ggym.app.data.repository

import androidx.room.withTransaction
import com.ggym.app.data.room.GgymDatabase
import com.ggym.app.data.room.entity.PlannedExerciseEntity
import com.ggym.app.data.room.entity.RoutineDayEntity
import com.ggym.app.data.room.entity.RoutineEntity
import com.ggym.app.data.room.mappers.RoutineMapper
import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.domain.model.Routine
import com.ggym.app.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Routines and their trees, on top of Room.
 *
 * Every write that depends on a previous read — "append after the last day", "renumber these
 * days" — runs in a transaction, so a half-applied reorder cannot survive a process death and
 * leave two days claiming the same index.
 */
internal class RoomRoutineRepository(
    private val database: GgymDatabase,
    /** Used only for the timestamp of a new routine; injected so tests can pin it. */
    private val now: () -> Long = System::currentTimeMillis,
) : RoutineRepository {

    private val routineDao = database.routineDao()
    private val dayDao = database.routineDayDao()
    private val plannedExerciseDao = database.plannedExerciseDao()

    override fun observeRoutines(): Flow<List<Routine>> =
        routineDao.observeRoutinesWithDays().map { rows -> rows.map(RoutineMapper::toDomain) }

    override fun observeRoutine(routineId: Long): Flow<Routine?> =
        routineDao.observeRoutineWithDays(routineId).map { row -> row?.let(RoutineMapper::toDomain) }

    override suspend fun createRoutine(name: String): Long =
        routineDao.insert(RoutineEntity(name = name.trim(), createdAt = now()))

    override suspend fun renameRoutine(routineId: Long, name: String) {
        routineDao.rename(routineId, name.trim())
    }

    override suspend fun deleteRoutine(routineId: Long) {
        routineDao.deleteById(routineId)
    }

    override suspend fun addDay(routineId: Long, dayName: String): Long = database.withTransaction {
        dayDao.insert(
            RoutineDayEntity(
                routineId = routineId,
                dayName = dayName.trim(),
                orderIndex = dayDao.nextOrderIndex(routineId),
            ),
        )
    }

    override suspend fun renameDay(routineDayId: Long, dayName: String) {
        dayDao.rename(routineDayId, dayName.trim())
    }

    override suspend fun deleteDay(routineDayId: Long) {
        dayDao.deleteById(routineDayId)
    }

    override suspend fun reorderDays(routineId: Long, orderedDayIds: List<Long>) {
        database.withTransaction {
            orderedDayIds.forEachIndexed { index, dayId ->
                dayDao.updateOrderIndex(routineId = routineId, routineDayId = dayId, orderIndex = index)
            }
        }
    }

    override suspend fun addPlannedExercise(
        routineDayId: Long,
        exerciseId: Long,
        targetSets: Int,
        targetReps: String,
        restSeconds: Int,
        weightKg: Double?,
    ): Long = database.withTransaction {
        plannedExerciseDao.insert(
            PlannedExerciseEntity(
                routineDayId = routineDayId,
                exerciseId = exerciseId,
                targetSets = targetSets,
                targetReps = targetReps.trim(),
                restSeconds = restSeconds,
                orderIndex = plannedExerciseDao.nextOrderIndex(routineDayId),
                weightKg = weightKg,
            ),
        )
    }

    override suspend fun updatePlannedExercise(plannedExercise: PlannedExercise) {
        plannedExerciseDao.update(RoutineMapper.toEntity(plannedExercise))
    }

    override suspend fun updatePlannedExerciseWeight(plannedExerciseId: Long, weightKg: Double) {
        plannedExerciseDao.updateWeight(plannedExerciseId, weightKg)
    }

    override suspend fun deletePlannedExercise(plannedExerciseId: Long) {
        plannedExerciseDao.deleteById(plannedExerciseId)
    }

    override suspend fun reorderPlannedExercises(
        routineDayId: Long,
        orderedPlannedExerciseIds: List<Long>,
    ) {
        database.withTransaction {
            orderedPlannedExerciseIds.forEachIndexed { index, plannedExerciseId ->
                plannedExerciseDao.updateOrderIndex(
                    routineDayId = routineDayId,
                    plannedExerciseId = plannedExerciseId,
                    orderIndex = index,
                )
            }
        }
    }
}
