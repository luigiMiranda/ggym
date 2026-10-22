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
 * - **The slot editor never touches the global record.** [record] never touches
 *   `exercise.currentWorkingWeight` or a logged set, so the dictionary and the history stay the
 *   single global record of the exercise the user can trust from anywhere in the app.
 * - **History is a fallback for display, never a write.** When nothing is set, the last performed
 *   set is shown; it is not copied into the dictionary or the slot behind the user's back.
 *
 * M3's other half of the rule lives here too: [recordCompletedSetWeight] is what turns a set the
 * user finished into the exercise's new working weight — and, since M10, into the weight of the
 * *slot that produced the workout*. M3 deliberately forbade the slot write ("the plan is the
 * plan"); the user's progression is also the plan, so a completion now moves the day it actually
 * trained and leaves the other days' slots of the same exercise alone. The two writes share one
 * guard, so a bodyweight completion and a log with no dictionary entry stay no-ops for both halves.
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
     * The other half of the rule: the weight a set was just completed at becomes the exercise's
     * working weight *and* the planned weight of the slot the workout was built from, so the
     * number follows the user both globally and on the day that produced it.
     *
     * One guard covers both writes: a set completed at `0.0` is bodyweight work, not a number to
     * load next time, so it must not erase a real one in either place. A `null` [exerciseId] means
     * the dictionary entry was deleted mid-workout (the name snapshot is all that is left of it)
     * and a `null` [plannedExerciseId] means the log has no slot behind it — history written before
     * the pointer existed, or a draft restored from a backup that predates it — so each half is
     * simply skipped when its target is unknown.
     *
     * Writing the slot is a deliberate change of direction from M3, which kept the plan immutable:
     * only the slot that was actually trained moves, so a deliberate per-day difference survives
     * (Push A trained to 85 kg, Push B still planned at 70 kg). A bodyweight or abandoned workout
     * behaves exactly as it did before.
     */
    suspend fun recordCompletedSetWeight(exerciseId: Long?, plannedExerciseId: Long?, weightKg: Double) {
        if (weightKg <= 0.0) return
        exerciseId?.let { exercises.updateCurrentWorkingWeight(it, weightKg) }
        plannedExerciseId?.let { routines.updatePlannedExerciseWeight(it, weightKg) }
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
