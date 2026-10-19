package com.ggym.app.ui.history

import java.time.LocalDate

/**
 * One logged set, as the history screen lists it.
 *
 * [exerciseLogId] is carried even though the row does not show it: a set is written back through
 * `WorkoutHistoryRepository.updateSet`, which takes the whole model, and the screen never keeps a
 * second copy of the session to look the parent up in.
 */
data class HistorySetRow(
    val id: Long,
    val exerciseLogId: Long,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val isCompleted: Boolean,
)

/** One exercise of a past workout, with every set that was logged for it. */
data class HistoryExerciseBlock(
    val id: Long,
    val name: String,
    val wasCompleted: Boolean,
    val sets: List<HistorySetRow>,
)

/** The open set editor, prefilled from the row it was opened on. */
data class SetEditorState(
    val set: HistorySetRow,
    val error: SetEditError? = null,
)

/** What is wrong with the numbers in the set editor. The first problem is the one shown. */
enum class SetEditError {
    WeightInvalid,
    RepsInvalid,
}

/** The destructive action waiting for its confirmation. */
sealed interface HistoryDeleteTarget {

    data class Set(val set: HistorySetRow) : HistoryDeleteTarget

    data object Workout : HistoryDeleteTarget
}

/**
 * One past workout, as the history screen shows it.
 *
 * The screen can only ever *correct* this record: there is no state for a new set, a new exercise
 * or a different date, because a workout is what happened. [isMissing] covers the workout that was
 * deleted while the screen was open — the honest answer is the gone state, not a blank page.
 */
data class HistoryDetailUiState(
    /** True until the first read lands, so the missing state never flashes. */
    val isLoading: Boolean = true,
    val isMissing: Boolean = false,
    val sessionId: Long = 0,
    /** `null` only while [isLoading]; the header needs a real date to format. */
    val date: LocalDate? = null,
    val dayName: String = "",
    val routineName: String = "",
    val durationSeconds: Long = 0,
    val exercises: List<HistoryExerciseBlock> = emptyList(),
    val editor: SetEditorState? = null,
    val pendingDelete: HistoryDeleteTarget? = null,
)

/** What the screen must do after the workout stops existing. */
sealed interface HistoryDetailEffect {

    /** The workout was deleted from this screen: there is nothing left to go back to. */
    data object WorkoutDeleted : HistoryDetailEffect
}
