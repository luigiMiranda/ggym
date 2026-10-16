package com.ggym.app.ui.workouts

/**
 * One exercise slot as the day list shows it.
 *
 * [weightKg] is already resolved (the slot's own, the dictionary, the last completed set), because
 * the row must not be able to disagree with what the editor sheet opens with. [hasOwnWeight] is
 * kept only so an inherited number can be marked as such.
 */
data class SlotRow(
    val id: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val targetSets: Int,
    val targetReps: String,
    val restSeconds: Int,
    val weightKg: Double?,
    val hasOwnWeight: Boolean,
)

/** The exercises of one training day. */
data class DayDetailUiState(
    /** True until the first database emission. */
    val isLoading: Boolean = true,
    /** The day (or its routine) is gone, so the screen says so instead of showing an empty day. */
    val isMissing: Boolean = false,
    val dayName: String = "",
    val slots: List<SlotRow> = emptyList(),
    val pendingDelete: SlotRow? = null,
)
