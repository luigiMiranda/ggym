package com.ggym.app.ui.workouts

import com.ggym.app.domain.model.Routine

/** One routine card: the counts are computed on the way in, so a card never loads the tree. */
data class RoutineCard(
    val id: Long,
    val name: String,
    val dayCount: Int,
    val exerciseCount: Int,
)

/** The routine list, as the screen renders it. */
data class WorkoutsUiState(
    /** True until the first database emission, so "no routines" is never shown before the read. */
    val isLoading: Boolean = true,
    val routines: List<RoutineCard> = emptyList(),
    val nameDialog: RoutineNameDialog? = null,
    val pendingDelete: RoutineCard? = null,
)

/**
 * The name dialog, shared by "new routine" and "rename routine": [routineId] is `null` while
 * creating, which is the only difference between the two verbs. The typed text lives in the dialog
 * (a text field owns its own state) and arrives with the save call.
 */
data class RoutineNameDialog(
    val routineId: Long? = null,
    val initialName: String = "",
) {
    val isCreating: Boolean get() = routineId == null
}

internal fun Routine.toCard(): RoutineCard = RoutineCard(
    id = id,
    name = name,
    dayCount = days.size,
    exerciseCount = days.sumOf { day -> day.plannedExercises.size },
)
