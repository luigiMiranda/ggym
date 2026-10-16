package com.ggym.app.ui.exercises

/** One dictionary entry, as the picker lists it. */
data class ExerciseRow(
    val id: Long,
    val name: String,
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
)

/** One-shot outcomes of the picker. */
sealed interface ExercisePickerEffect {

    /** The exercise is now planned in the day the picker was opened for; the caller goes back. */
    data object Added : ExercisePickerEffect
}

/** Defaults for a slot created from the picker; the sheet is where they get edited. */
object NewSlotDefaults {
    const val TARGET_SETS = 3
    const val TARGET_REPS = "10"
}
