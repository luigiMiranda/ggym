package com.ggym.app.ui.stats

import com.ggym.app.domain.model.BodyWeightLog
import com.ggym.app.domain.stats.BodyWeightRange
import com.ggym.app.domain.stats.BodyWeightStats
import com.ggym.app.domain.stats.ExerciseProgression
import com.ggym.app.domain.stats.MonthAttendance
import java.time.LocalDate

/** One entry of the exercise selector: the dictionary is the only list of names. */
data class ExerciseOption(
    val id: Long,
    val name: String,
)

/**
 * The body-weight sheet, in both of its verbs.
 *
 * [entryId] `null` means "log a new measurement"; an id means the sheet was opened on an existing
 * row and saving corrects it (including its date, subject to the one-per-day rule). [invalid] is
 * the backstop: the sheet already disables Save for a value it can read, but a rejected write is
 * an ordinary outcome that the form has to be able to explain.
 */
data class BodyWeightSheetState(
    val entryId: Long?,
    val date: LocalDate,
    val initialWeightKg: Double?,
    val invalid: Boolean = false,
) {
    val isEditing: Boolean get() = entryId != null
}

/**
 * The Stats tab: four sections over one clock and one locale, in the preview's order.
 *
 * The body-weight sheet and the two confirmations are small nullable values in the state rather
 * than a second screen, which is what keeps the chart and the log behind them in sync with the
 * form: they are all built from the same database flows.
 */
data class StatsUiState(
    /** True until the first reads land, so the first-run states never flash. */
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.ofEpochDay(0),
    /** The last twelve months, oldest first, zero-filled. */
    val attendance: List<MonthAttendance> = emptyList(),
    val bodyRange: BodyWeightRange = BodyWeightRange.DEFAULT,
    val bodyStats: BodyWeightStats = BodyWeightStats.EMPTY,
    /** Every measurement up to today, newest first. */
    val bodyLog: List<BodyWeightLog> = emptyList(),
    val exercises: List<ExerciseOption> = emptyList(),
    val selectedExerciseId: Long? = null,
    val selectedExerciseName: String = "",
    val progression: ExerciseProgression = ExerciseProgression.EMPTY,
    val sheet: BodyWeightSheetState? = null,
    val pendingDelete: BodyWeightLog? = null,
    val exercisePickerOpen: Boolean = false,
) {
    val hasWorkouts: Boolean get() = attendance.any { it.workoutDays > 0 }

    val totalWorkoutDays: Int get() = attendance.sumOf { it.workoutDays }
}
