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
    /** The routine the day belongs to, for the header's supporting line. */
    val routineName: String = "",
    val slots: List<SlotRow> = emptyList(),
    val pendingDelete: SlotRow? = null,
    /** Set while the resume-or-discard question about a running workout is open. */
    val startConflict: StartConflict? = null,
) {
    /** The shape of the day, for the "Start workout" container: "5 exercises · 14 sets". */
    val plannedSetCount: Int get() = slots.sumOf { it.targetSets }
}

/** The workout that already runs, named so the question says what would be discarded. */
data class StartConflict(
    val sessionId: Long,
    val runningDayName: String,
    val runningRoutineName: String,
)

/** What the day screen must do after a start, resume or discard. */
sealed interface DayDetailEffect {
    data class OpenWorkout(val sessionId: Long) : DayDetailEffect
}
