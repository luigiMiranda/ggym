package com.ggym.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The routine list.
 *
 * The routines themselves are a database flow, so a rename or a delete anywhere shows up here
 * without a refresh; only the open dialog is screen state.
 *
 * The resume card is the running workout, if there is one. Its elapsed time is the one thing here
 * that moves without a write, so [ticks] drives it; a test steps it by hand.
 */
class WorkoutsViewModel(
    private val routines: RoutineRepository,
    private val history: WorkoutHistoryRepository,
    private val now: () -> Long = System::currentTimeMillis,
    ticks: Flow<Unit> = minuteTicks(),
) : ViewModel() {

    private val dialogs = MutableStateFlow(Dialogs())

    val uiState: StateFlow<WorkoutsUiState> = combine(
        routines.observeRoutines(),
        dialogs,
        history.observeDraft(),
        ticks,
    ) { routines, dialogs, draft, _ ->
        WorkoutsUiState(
            isLoading = false,
            routines = routines.map { it.toCard() },
            nameDialog = dialogs.nameDialog,
            pendingDelete = dialogs.pendingDelete,
            resume = draft?.let { it.toResumeCard(now()) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = WorkoutsUiState(),
    )

    fun startCreateRoutine() {
        dialogs.update { it.copy(nameDialog = RoutineNameDialog()) }
    }

    fun startRenameRoutine(routine: RoutineCard) {
        dialogs.update { it.copy(nameDialog = RoutineNameDialog(routineId = routine.id, initialName = routine.name)) }
    }

    fun dismissNameDialog() {
        dialogs.update { it.copy(nameDialog = null) }
    }

    /**
     * Writes [name] as a new routine, or as the new name of the one being edited. The name is a
     * parameter rather than dialog state because the text field owns what has been typed so far.
     */
    fun saveName(name: String) {
        val dialog = dialogs.value.nameDialog ?: return
        if (name.isBlank()) return

        viewModelScope.launch {
            val routineId = dialog.routineId
            if (routineId == null) {
                routines.createRoutine(name)
            } else {
                routines.renameRoutine(routineId, name)
            }
            dismissNameDialog()
        }
    }

    fun askDeleteRoutine(routine: RoutineCard) {
        dialogs.update { it.copy(pendingDelete = routine) }
    }

    fun cancelDelete() {
        dialogs.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val routine = dialogs.value.pendingDelete ?: return

        viewModelScope.launch {
            routines.deleteRoutine(routine.id)
            cancelDelete()
        }
    }

    private data class Dialogs(
        val nameDialog: RoutineNameDialog? = null,
        val pendingDelete: RoutineCard? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Once a minute is as fine as the card's "23 min" needs to be. */
private fun minuteTicks(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(TICK_MILLIS)
    }
}

private const val TICK_MILLIS = 30_000L
