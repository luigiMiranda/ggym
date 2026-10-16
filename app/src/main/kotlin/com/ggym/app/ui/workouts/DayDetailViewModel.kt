package com.ggym.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.domain.workout.WorkingWeightUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The exercises of one training day.
 *
 * The day arrives as a database flow, so adding an exercise in the picker shows up the moment the
 * write lands, and every row's weight is resolved through [WorkingWeightUseCases] — the same rule
 * the editor sheet uses, so a row and the sheet it opens can never disagree.
 */
class DayDetailViewModel(
    private val routineId: Long,
    private val routineDayId: Long,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val weights: WorkingWeightUseCases,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<SlotRow?>(null)

    val uiState: StateFlow<DayDetailUiState> = combine(
        routines.observeRoutine(routineId),
        exercises.observeAll(),
        pendingDelete,
    ) { routine, dictionary, pendingDelete ->
        val day = routine?.days?.firstOrNull { it.id == routineDayId }
        if (day == null) {
            return@combine DayDetailUiState(isLoading = false, isMissing = true)
        }

        val names = dictionary.associateBy { it.id }
        val resolved = weights.resolveAll(day.plannedExercises)

        DayDetailUiState(
            isLoading = false,
            dayName = day.dayName,
            slots = day.plannedExercises.map { slot ->
                SlotRow(
                    id = slot.id,
                    exerciseId = slot.exerciseId,
                    exerciseName = names[slot.exerciseId]?.name.orEmpty(),
                    targetSets = slot.targetSets,
                    targetReps = slot.targetReps,
                    restSeconds = slot.restSeconds,
                    weightKg = resolved[slot.id],
                    hasOwnWeight = slot.weightKg != null,
                )
            },
            pendingDelete = pendingDelete,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DayDetailUiState(),
    )

    /** Same contract as the day list: the order is rewritten from the database's own list. */
    fun moveSlot(slotId: Long, up: Boolean) {
        viewModelScope.launch {
            val slots = routines.observeRoutine(routineId).first()
                ?.days
                ?.firstOrNull { it.id == routineDayId }
                ?.plannedExercises
                ?: return@launch
            val from = slots.indexOfFirst { it.id == slotId }
            if (from < 0) return@launch

            val to = if (up) from - 1 else from + 1
            if (to !in slots.indices) return@launch

            val reordered = slots.toMutableList().apply { add(to, removeAt(from)) }
            routines.reorderPlannedExercises(routineDayId, reordered.map { it.id })
        }
    }

    fun askDeleteSlot(slot: SlotRow) {
        pendingDelete.value = slot
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val slot = pendingDelete.value ?: return

        viewModelScope.launch {
            routines.deletePlannedExercise(slot.id)
            pendingDelete.update { current -> current?.takeUnless { it.id == slot.id } }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
