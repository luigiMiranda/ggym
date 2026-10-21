package com.ggym.app.ui.exercises

import com.ggym.app.domain.model.Exercise

/**
 * One dictionary entry, as the picker lists it.
 *
 * The weight and the rest time ride along because the suggestion row is where the user recognises
 * an exercise they already use: the name alone cannot distinguish "Bench Press" at 82.5 kg from the
 * one they have not touched since March.
 */
data class ExerciseRow(
    val id: Long,
    val name: String,
    val weightKg: Double? = null,
    val restSeconds: Int = Exercise.DEFAULT_REST_SECONDS,
)

/** The exercise picker: search the dictionary, reuse an entry, or create a new one by name. */
data class ExercisePickerUiState(
    /** True until the first database emission. */
    val isLoading: Boolean = true,
    val query: String = "",
    val results: List<ExerciseRow> = emptyList(),
    /**
     * The typed name is not in the dictionary yet (case-insensitively), so "Create «name»" is
     * offered. A name that already exists must not be creatable: the dictionary's whole point is
     * that one exercise has one history.
     */
    val canCreate: Boolean = false,
    val pendingDelete: ExerciseRow? = null,
    /** Set when a delete was refused: the routines that still plan the exercise. */
    val blockedDeleteRoutines: List<String> = emptyList(),
    /** The create form, while it is open; `null` means the list is all there is. */
    val form: ExerciseFormState? = null,
)

/** One-shot outcomes of the picker. */
sealed interface ExercisePickerEffect {

    /** The exercise is now planned in the day the picker was opened for; the caller goes back. */
    data object Added : ExercisePickerEffect
}
