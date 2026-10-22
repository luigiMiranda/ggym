package com.ggym.app.domain.model

/**
 * A named collection of training days ("schede").
 *
 * A routine always owns its [days], and a day always owns its [RoutineDay.plannedExercises]:
 * both are loaded together, because no screen has a use for half of the tree. The database
 * cascades the same way, so deleting a routine cannot leave orphan days behind.
 */
data class Routine(
    val id: Long = 0,
    val name: String,
    /** Epoch millis at creation time; only used to keep the list in a stable order. */
    val createdAt: Long,
    val days: List<RoutineDay> = emptyList(),
)

/**
 * A routine's identity, for a list that only needs to name it and open it — the delete-refusal
 * dialog offers the first routine that still plans an exercise, and needs its id to do it.
 */
data class RoutineRef(
    val id: Long,
    val name: String,
)

/**
 * One placement of a dictionary exercise in a routine: the day that plans it, and the numbers.
 *
 * This is a *projection* of the routine tree, not the tree itself. The exercise library reads it for
 * the one question the dictionary cannot answer about itself — "where is this exercise used?" — and
 * names the days rather than loading every routine's exercises to find them.
 */
data class ExercisePlan(
    val exerciseId: Long,
    val routineId: Long,
    val routineName: String,
    val routineDayId: Long,
    val dayName: String,
    val targetSets: Int,
    val targetReps: String,
)

/** One training day inside a [Routine]; [orderIndex] is the user-visible order. */
data class RoutineDay(
    val id: Long = 0,
    val routineId: Long,
    val dayName: String,
    val orderIndex: Int,
    val plannedExercises: List<PlannedExercise> = emptyList(),
)

/**
 * An exercise slot inside a [RoutineDay]: what the user intends to do, not what they did.
 *
 * [exerciseId] points into the dictionary, while [targetReps] stays a `String` because a target
 * is written the way a coach writes it ("8-10", "10", "AMRAP") and parsing it belongs to the one
 * place that needs a number, not to the storage layer.
 *
 * The slot owns its [weightKg]: the same exercise can be planned at 80 kg on Push A and 70 kg on
 * Push B, because a training day is a recipe, not a lookup. The dictionary and the logged history
 * stay the global record of the exercise — a slot never writes to either.
 */
data class PlannedExercise(
    val id: Long = 0,
    val routineDayId: Long,
    val exerciseId: Long,
    val targetSets: Int,
    val targetReps: String,
    val restSeconds: Int,
    val orderIndex: Int,
    /**
     * The weight planned for this slot in kg, or `null` to inherit the exercise's resolved
     * default (`Exercise.currentWorkingWeight`, then the last completed set).
     */
    val weightKg: Double? = null,
)
