package com.ggym.app.domain.repository

import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.domain.model.Routine
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes routines, their days and their planned exercises.
 *
 * Every read returns the whole tree, because that is what every screen shows; every write is
 * expressed in terms of one node of that tree, so callers never have to re-save a parent to
 * change a child.
 *
 * A slot's working weight belongs to the slot: [addPlannedExercise] and [updatePlannedExercise]
 * write it, and neither ever touches the dictionary's `currentWorkingWeight` or the history.
 */
interface RoutineRepository {

    /** All routines with their days and planned exercises, oldest first. */
    fun observeRoutines(): Flow<List<Routine>>

    /** A single routine with its days and planned exercises, or `null` once it is deleted. */
    fun observeRoutine(routineId: Long): Flow<Routine?>

    suspend fun createRoutine(name: String): Long

    suspend fun renameRoutine(routineId: Long, name: String)

    suspend fun deleteRoutine(routineId: Long)

    suspend fun addDay(routineId: Long, dayName: String): Long

    suspend fun renameDay(routineDayId: Long, dayName: String)

    suspend fun deleteDay(routineDayId: Long)

    /**
     * Rewrites the day order of one routine: the first id in [orderedDayIds] becomes index 0, and
     * so on. Ids that do not belong to [routineId] are ignored rather than rejected, so a stale
     * drag can never move a day of another routine.
     */
    suspend fun reorderDays(routineId: Long, orderedDayIds: List<Long>)

    suspend fun addPlannedExercise(
        routineDayId: Long,
        exerciseId: Long,
        targetSets: Int,
        targetReps: String,
        restSeconds: Int,
        /**
         * The weight to plan for this slot; `null` (the default) leaves it to be inherited from
         * the exercise's resolved default until the user sets one.
         */
        weightKg: Double? = null,
    ): Long

    /** Updates a slot in place, including its `orderIndex`; use [reorderPlannedExercises] to move several. */
    suspend fun updatePlannedExercise(plannedExercise: PlannedExercise)

    suspend fun deletePlannedExercise(plannedExerciseId: Long)

    /** Counterpart of [reorderDays], for the exercises inside one day. */
    suspend fun reorderPlannedExercises(routineDayId: Long, orderedPlannedExerciseIds: List<Long>)
}
