package com.ggym.app.domain.exercise

import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RenameExerciseResult
import com.ggym.app.domain.repository.RoutineRepository

/**
 * The writes behind the exercise form, in one place.
 *
 * The form edits two rows that mean different things: the dictionary entry (the name, the weight to
 * load next time) and the day's slot (how many sets, which reps, how long to rest, and the weight
 * this day plans for itself). Putting both writes here is what keeps them in a fixed order — the
 * name first, because it can be refused and a refused name must leave the slot untouched.
 *
 * [create] is the one-way trip: it makes the dictionary entry and plans it, in that order, so the
 * slot can never point at an exercise that was not written.
 */
class ExerciseFormUseCases(
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
) {

    /**
     * Creates the dictionary entry (or reuses the one already answering to this name) and plans it
     * in [routineDayId], returning the new slot's id.
     *
     * [startingWeightKg] becomes the exercise's **base** weight, and the slot is left with no weight
     * of its own so it follows the exercise. That is the whole point of the create form: a weight
     * typed here is the number the next workout starts from, and the one after that follows whatever
     * the user actually lifts.
     *
     * An existing name wins, weight and all (see [ExerciseRepository.findOrCreate]): creating
     * “bench press” when “Bench Press” exists must not reset a number the user has been progressing
     * for months.
     */
    suspend fun create(
        routineDayId: Long,
        name: String,
        sets: Int,
        reps: String,
        restSeconds: Int,
        startingWeightKg: Double?,
    ): Long {
        val exercise = exercises.findOrCreate(
            name = name,
            defaultRestSeconds = restSeconds,
            currentWorkingWeight = startingWeightKg,
        )
        return routines.addPlannedExercise(
            routineDayId = routineDayId,
            exerciseId = exercise.id,
            targetSets = sets,
            targetReps = reps,
            restSeconds = restSeconds,
            weightKg = null,
        )
    }

    /**
     * Rewrites both rows for a slot that already exists: the exercise's name, then the day's
     * numbers.
     *
     * The name goes first and a collision stops everything, so a `NameTaken` never leaves a slot
     * saved with the new sets and the old name — the two writes are not one transaction, and the
     * order is what keeps a refusal from being a half-save.
     *
     * [plannedExercise] is re-read by the caller, not trusted from the form: the slot may have been
     * changed on another screen while the form was open, and the form only owns the five fields it
     * shows.
     */
    suspend fun updateSlot(
        plannedExercise: PlannedExercise,
        name: String,
        sets: Int,
        reps: String,
        restSeconds: Int,
        weightKg: Double?,
    ): UpdateSlotResult {
        when (exercises.renameExercise(plannedExercise.exerciseId, name)) {
            RenameExerciseResult.NameTaken -> return UpdateSlotResult.NameTaken
            RenameExerciseResult.NotFound -> return UpdateSlotResult.NotFound
            is RenameExerciseResult.Renamed, RenameExerciseResult.Unchanged -> Unit
        }

        routines.updatePlannedExercise(
            plannedExercise.copy(
                targetSets = sets,
                targetReps = reps,
                restSeconds = restSeconds,
                weightKg = weightKg,
            ),
        )
        return UpdateSlotResult.Saved
    }
}

/** Outcome of [ExerciseFormUseCases.updateSlot]. */
sealed interface UpdateSlotResult {

    /** Both the dictionary name and the slot are written. */
    data object Saved : UpdateSlotResult

    /** Another entry already holds that name, case-insensitively: nothing was written at all. */
    data object NameTaken : UpdateSlotResult

    /** The dictionary entry is gone — and with it, by `RESTRICT`, the slot that planned it. */
    data object NotFound : UpdateSlotResult
}
