package com.ggym.app.testing

import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository

/*
 * Fixtures for the workout tests: a dictionary entry and a training day, built through the real
 * repositories so a test reads like training data.
 */

/** A dictionary entry with a name, and optionally the working weight the user last chose for it. */
internal suspend fun ExerciseRepository.givenExerciseNamed(name: String, workingWeight: Double? = null): Long {
    val created = findOrCreate(name)
    workingWeight?.let { updateCurrentWorkingWeight(created.id, it) }
    return created.id
}

/** One planned exercise: the shape of a routine slot, in the order the routine editor writes it. */
internal data class PlannedSlot(
    val exerciseId: Long,
    val sets: Int = 4,
    val reps: String = "8-10",
    val restSeconds: Int = 120,
    val weightKg: Double? = null,
)

/** A routine "Push Day" with one "Push A" day holding [slots]; returns the day's id. */
internal suspend fun RoutineRepository.givenPlannedDay(vararg slots: PlannedSlot): Long {
    val routineId = createRoutine("Push Day")
    val dayId = addDay(routineId, "Push A")
    slots.forEach { slot ->
        addPlannedExercise(
            routineDayId = dayId,
            exerciseId = slot.exerciseId,
            targetSets = slot.sets,
            targetReps = slot.reps,
            restSeconds = slot.restSeconds,
            weightKg = slot.weightKg,
        )
    }
    return dayId
}
