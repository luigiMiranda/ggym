package com.ggym.app.domain.workout

import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import kotlinx.coroutines.flow.first

/**
 * The app's rule for "which weight do I load?", in one place.
 *
 * A slot answers for itself first: if the user planned *this* slot at 80 kg, that is the number,
 * whatever the dictionary or the history say. Only a slot with no weight of its own inherits the
 * exercise's global default — the dictionary's `currentWorkingWeight`, then the last set actually
 * completed — which is what makes a fresh routine follow the exercise and two slots of the same
 * exercise free to differ (80 kg on Push A, 70 kg on Push B).
 *
 * Two properties this type exists to keep true:
 *
 * - **A slot write is a slot write.** [record] never touches `exercise.currentWorkingWeight` or a
 *   logged set, so the dictionary and the history stay the single global record of the exercise
 *   the user can trust from anywhere in the app.
 * - **History is a fallback for display, never a write.** When nothing is set, the last performed
 *   set is shown; it is not copied into the dictionary or the slot behind the user's back.
 *
 * M3 adds the other half — a completed set updating the dictionary — next to this class, so the
 * rule has exactly one implementation for both screens.
 */
class WorkingWeightUseCases(
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val history: WorkoutHistoryRepository,
) {

    /** The exercise's global default, or `null` when the user has never set or performed a weight. */
    suspend fun resolve(exerciseId: Long): Double? =
        exercises.findById(exerciseId)?.currentWorkingWeight
            ?: history.latestCompletedSet(exerciseId)?.weightKg

    /** What a row shows: the slot's own weight, else the exercise's global default. */
    suspend fun resolve(plannedExercise: PlannedExercise): Double? =
        plannedExercise.weightKg ?: resolve(plannedExercise.exerciseId)

    /**
     * Resolves a whole day in one pass — the dictionary once, the history only for the slots left
     * unanswered — so a list of ten slots costs one dictionary read instead of ten.
     *
     * Keyed by slot id, so two slots of the same exercise can carry two different numbers.
     */
    suspend fun resolveAll(plannedExercises: List<PlannedExercise>): Map<Long, Double?> {
        if (plannedExercises.isEmpty()) return emptyMap()
        val dictionary = exercises.observeAll().first().associateBy { it.id }
        return plannedExercises.associate { slot ->
            slot.id to (
                slot.weightKg
                    ?: dictionary[slot.exerciseId]?.currentWorkingWeight
                    ?: history.latestCompletedSet(slot.exerciseId)?.weightKg
                )
        }
    }

    /**
     * Stores the slot's own weight — `null` hands it back to the exercise's default — and returns
     * the stored slot.
     */
    suspend fun record(plannedExercise: PlannedExercise, weightKg: Double?): PlannedExercise {
        val updated = plannedExercise.copy(weightKg = weightKg)
        routines.updatePlannedExercise(updated)
        return updated
    }
}
