package com.ggym.app.ui.exercises

/**
 * The slot editor: what the exercise is planned at, for one training day.
 *
 * The weight field is prefilled with what the row already shows — the slot's own weight, or the
 * exercise's resolved default — because "what you see is what gets saved" is the only rule a user
 * can predict. Blanking it is deliberate: it means the slot carries no weight of its own again and
 * follows the exercise's default.
 */
data class SlotEditorUiState(
    /** True until the slot and its resolved weight have been read. */
    val isLoading: Boolean = true,
    /** The slot was already gone when the sheet opened, so there is nothing to edit. */
    val isMissing: Boolean = false,
    val exerciseName: String = "",
    val sets: String = "",
    val reps: String = "",
    val restSeconds: String = "",
    val weight: String = "",
    /** True when [weight] is the slot's own number, false when it is inherited from the exercise. */
    val isOwnWeight: Boolean = false,
    /** The first thing wrong with the form, if anything: saving stops there. */
    val error: SlotEditorError? = null,
)

/** Why the form cannot be saved yet. Mapped to copy by the sheet, so it stays testable. */
enum class SlotEditorError {
    SetsTooLow,
    RepsBlank,
    RestInvalid,
    WeightInvalid,
}

/** One-shot outcomes. The sheet closes on either; nothing else listens. */
sealed interface SlotEditorEffect {

    /** The slot was written. */
    data object Saved : SlotEditorEffect

    /** The slot was removed from the day. */
    data object Removed : SlotEditorEffect
}
