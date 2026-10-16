package com.ggym.app.ui.workouts

/** One training day in the routine detail list: enough to draw the row, nothing more. */
data class DayRow(
    val id: Long,
    val name: String,
    val exerciseCount: Int,
)

/** The days of one routine. */
data class RoutineDetailUiState(
    /** True until the first database emission. */
    val isLoading: Boolean = true,
    /** The routine is gone — deleted from another screen — so the screen says so instead of lying. */
    val isMissing: Boolean = false,
    val routineName: String = "",
    val days: List<DayRow> = emptyList(),
    val nameDialog: RoutineNameDialog? = null,
    val dayDialog: DayNameDialog? = null,
    val pendingDelete: DayRow? = null,
)

/**
 * The day name dialog: `dayId` is `null` while adding a day, set while renaming one. The typed
 * text lives in the dialog and arrives with the save call.
 */
data class DayNameDialog(
    val dayId: Long? = null,
    val initialName: String = "",
) {
    val isCreating: Boolean get() = dayId == null
}
