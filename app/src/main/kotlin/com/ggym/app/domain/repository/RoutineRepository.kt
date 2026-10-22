package com.ggym.app.domain.repository

import com.ggym.app.domain.model.ExercisePlan
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

    /**
     * Every placement of every exercise in a routine day, across all routines, ordered by routine
     * creation and then day order.
     *
     * The exercise library reads this once and groups it by `exerciseId` to answer "where is this
     * used?" for a whole list, instead of one query per row.
     */
    fun observeExercisePlans(): Flow<List<ExercisePlan>>

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

    /**
     * Moves one slot's planned weight, leaving every other field alone. This is the write a
     * completed set makes on the day that produced it (see `WorkingWeightUseCases`); the slot
     * editor's own write is [updatePlannedExercise], which rewrites the whole slot.
     */
    suspend fun updatePlannedExerciseWeight(plannedExerciseId: Long, weightKg: Double)

    suspend fun deletePlannedExercise(plannedExerciseId: Long)

    /** Counterpart of [reorderDays], for the exercises inside one day. */
    suspend fun reorderPlannedExercises(routineDayId: Long, orderedPlannedExerciseIds: List<Long>)
}
