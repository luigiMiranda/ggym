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
    const val SESSION_ID = "sessionId"
    const val EXERCISE_ID = "exerciseId"

    const val ROUTINE = "routine/{$ROUTINE_ID}"
    const val DAY = "day/{$ROUTINE_ID}/{$DAY_ID}"
    const val EXERCISE_PICKER = "picker/{$DAY_ID}"

    /**
     * The exercise library: the dictionary's own screen, a drill-down of the Workouts tab (no fifth
     * tab, no Settings row). It carries no argument — it lists everything and searches.
     */
    const val LIBRARY = "library"

    /**
     * The workout that is running. Its id is the *draft* session's id, which stops meaning
     * anything the moment the workout is finished or discarded — which is exactly why the screen
     * watches it by id and shows that the workout is over instead of adopting the next one.
     */
    const val WORKOUT = "workout/{$SESSION_ID}"

    /** One past workout, opened from the calendar to correct or delete it. */
    const val HISTORY = "history/{$SESSION_ID}"

    /**
     * The Stats tab, with the exercise whose progression to preselect. The argument is optional:
     * plain `stats` (the bottom bar's own route) matches the same destination with no exercise
     * chosen, which is why `Destination` matches tabs on the route's base rather than its pattern.
     *
     * Sent as a query because a progression is a *view* of the Stats tab, not a screen of its own:
     * one tap opens the chart that is already there instead of a second copy of the layout.
     */
    const val STATS = "stats?$EXERCISE_ID={$EXERCISE_ID}"

    /** The tab route's own argument default: no exercise has been asked for. */
    const val NO_EXERCISE_ID = -1L

    fun routine(routineId: Long): String = "routine/$routineId"

    fun day(routineId: Long, dayId: Long): String = "day/$routineId/$dayId"

    fun exercisePicker(routineDayId: Long): String = "picker/$routineDayId"

    fun workout(sessionId: Long): String = "workout/$sessionId"

    fun history(sessionId: Long): String = "history/$sessionId"

    fun stats(exerciseId: Long): String = "stats?$EXERCISE_ID=$exerciseId"
}

/** Reads a long argument of the current destination, so each screen parses the route once. */
fun NavBackStackEntry.longArgument(name: String): Long = requireNotNull(arguments) {
    "the destination $destination has no arguments"
}.getLong(name)
