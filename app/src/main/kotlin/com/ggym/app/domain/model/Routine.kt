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
 */
data class PlannedExercise(
    val id: Long = 0,
    val routineDayId: Long,
    val exerciseId: Long,
    val targetSets: Int,
    val targetReps: String,
    val restSeconds: Int,
    val orderIndex: Int,
)
