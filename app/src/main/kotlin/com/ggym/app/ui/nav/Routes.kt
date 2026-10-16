package com.ggym.app.ui.nav

import androidx.navigation.NavBackStackEntry

/**
 * The destinations that carry arguments, kept apart from the four static tabs.
 *
 * Everything is expressed as an id: a routine, a day, a planned exercise. That is deliberate —
 * the back stack survives process death, and an id re-reads the current state from the database
 * instead of restoring a stale copy of a screen's contents.
 */
object Routes {

    const val ROUTINE_ID = "routineId"
    const val DAY_ID = "dayId"
    const val PLANNED_EXERCISE_ID = "plannedExerciseId"

    const val ROUTINE = "routine/{$ROUTINE_ID}"
    const val DAY = "day/{$ROUTINE_ID}/{$DAY_ID}"
    const val EXERCISE_PICKER = "picker/{$DAY_ID}"

    fun routine(routineId: Long): String = "routine/$routineId"

    fun day(routineId: Long, dayId: Long): String = "day/$routineId/$dayId"

    fun exercisePicker(routineDayId: Long): String = "picker/$routineDayId"
}

/** Reads a long argument of the current destination, so each screen parses the route once. */
fun NavBackStackEntry.longArgument(name: String): Long = requireNotNull(arguments) {
    "the destination $destination has no arguments"
}.getLong(name)
