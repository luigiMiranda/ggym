package com.ggym.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The days of one routine: add, rename, reorder, delete.
 *
 * Reordering and deleting re-read the tree inside the write instead of trusting a snapshot the
 * screen handed back, so a day that moved under the user's finger cannot be moved twice or
 * deleted by id from a stale list.
 */
class RoutineDetailViewModel(
    private val routineId: Long,
    private val routines: RoutineRepository,
) : ViewModel() {

    private val dialogs = MutableStateFlow(Dialogs())

    val uiState: StateFlow<RoutineDetailUiState> =
        combine(routines.observeRoutine(routineId), dialogs) { routine, dialogs ->
            RoutineDetailUiState(
                isLoading = false,
                isMissing = routine == null,
                routineName = routine?.name.orEmpty(),
                days = routine?.days.orEmpty().map { day ->
                    DayRow(id = day.id, name = day.dayName, exerciseCount = day.plannedExercises.size)
                },
                nameDialog = dialogs.nameDialog,
                dayDialog = dialogs.dayDialog,
                pendingDelete = dialogs.pendingDelete,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = RoutineDetailUiState(),
        )

    fun startRenameRoutine(currentName: String) {
        dialogs.update { it.copy(nameDialog = RoutineNameDialog(routineId = routineId, initialName = currentName)) }
    }

    fun dismissRoutineNameDialog() {
        dialogs.update { it.copy(nameDialog = null) }
    }

    fun saveRoutineName(name: String) {
        if (dialogs.value.nameDialog == null || name.isBlank()) return

        viewModelScope.launch {
            routines.renameRoutine(routineId, name)
            dismissRoutineNameDialog()
        }
    }

    fun startAddDay() {
        dialogs.update { it.copy(dayDialog = DayNameDialog()) }
    }

    fun startRenameDay(currentName: String, day: DayRow) {
        dialogs.update { it.copy(dayDialog = DayNameDialog(dayId = day.id, initialName = currentName)) }
    }

    fun dismissDayDialog() {
        dialogs.update { it.copy(dayDialog = null) }
    }

    fun saveDayName(name: String) {
        val dialog = dialogs.value.dayDialog ?: return
        if (name.isBlank()) return

        viewModelScope.launch {
            val dayId = dialog.dayId
            if (dayId == null) {
                routines.addDay(routineId, name)
            } else {
                routines.renameDay(dayId, name)
            }
            dismissDayDialog()
        }
    }

    /**
     * Moves a day one position up or down by rewriting the whole order, which is exactly what the
     * repository's `reorderDays` takes. Moving the first day up is a no-op, not an error.
     */
    fun moveDay(dayId: Long, up: Boolean) {
        viewModelScope.launch {
            val days = routines.observeRoutine(routineId).first()?.days ?: return@launch
            val from = days.indexOfFirst { it.id == dayId }
            if (from < 0) return@launch

            val to = if (up) from - 1 else from + 1
            if (to !in days.indices) return@launch

            val reordered = days.toMutableList().apply { add(to, removeAt(from)) }
            routines.reorderDays(routineId, reordered.map { it.id })
        }
    }

    fun askDeleteDay(day: DayRow) {
        dialogs.update { it.copy(pendingDelete = day) }
    }

    fun cancelDelete() {
        dialogs.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val day = dialogs.value.pendingDelete ?: return

        viewModelScope.launch {
            routines.deleteDay(day.id)
            cancelDelete()
        }
    }

    private data class Dialogs(
        val nameDialog: RoutineNameDialog? = null,
        val dayDialog: DayNameDialog? = null,
        val pendingDelete: DayRow? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
