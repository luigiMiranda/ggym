package com.ggym.app.ui.exercises

import com.ggym.app.domain.model.ExercisePlan
import com.ggym.app.domain.model.RoutineRef

/**
 * One dictionary entry, as the library lists it.
 *
 * The weight and the rest time are the entry's own (`currentWorkingWeight`, `defaultRestSeconds`),
 * so a row is the app's answer to "what will this load next time". [plans] is where the exercise is
 * used, flattened by the repository's one read rather than a tree load per row.
 */
data class LibraryExercise(
    val id: Long,
    val name: String,
    val weightKg: Double?,
    val restSeconds: Int,
    val plans: List<ExercisePlan> = emptyList(),
) {
    /** The training days that plan it, in routine-then-day order, without repeats. */
    val dayNames: List<String> get() = plans.map { it.dayName }.distinct()

    val isPlanned: Boolean get() = plans.isNotEmpty()
}

/**
 * The exercise library: the dictionary with the two facts a row can carry (what it loads, where it
 * is used), a search that reuses the picker's own query, and the shared form in its dictionary mode.
 */
data class ExerciseLibraryUiState(
    /** True until the first database emission, so "no exercises" is never shown before the read. */
    val isLoading: Boolean = true,
    val query: String = "",
    val exercises: List<LibraryExercise> = emptyList(),
    /** Every entry, whatever the search: what the Workouts card and the header count. */
    val totalCount: Int = 0,
    /** Entries with no base weight yet — the card's "needs attention" figure. A hint, not a task. */
    val withoutWeightCount: Int = 0,
    /** The shared form, while it is open: the library only ever hosts its two dictionary modes. */
    val form: ExerciseFormState? = null,
    /** The plans of the exercise the dictionary form is on, for its read-only "Planned in" list. */
    val formPlans: List<ExercisePlan> = emptyList(),
    val pendingDelete: LibraryExercise? = null,
    /** Set when a delete was refused: the routines that still plan the exercise, id and name. */
    val blockedDelete: List<RoutineRef> = emptyList(),
)
