package com.ggym.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import com.ggym.app.domain.workout.ActiveWorkoutUseCases
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.domain.workout.StartWorkoutResult
import com.ggym.app.domain.workout.WorkingWeightUseCases
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val history: WorkoutHistoryRepository,
    private val workout: ActiveWorkoutUseCases,
    private val restTimer: RestTimerUseCases,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<SlotRow?>(null)

    /** The running workout that blocks a start, while the resume-or-discard question is open. */
    private val startConflict = MutableStateFlow<StartConflict?>(null)

    private val opened = MutableSharedFlow<DayDetailEffect>(extraBufferCapacity = 1)

    /** One-shots for the screen: the workout that was started or resumed, to open. */
    val effects: SharedFlow<DayDetailEffect> = opened.asSharedFlow()

    val uiState: StateFlow<DayDetailUiState> = combine(
        routines.observeRoutine(routineId),
        exercises.observeAll(),
        pendingDelete,
        startConflict,
    ) { routine, dictionary, pendingDelete, startConflict ->
        val day = routine?.days?.firstOrNull { it.id == routineDayId }
        if (day == null) {
            return@combine DayDetailUiState(isLoading = false, isMissing = true)
        }

        val names = dictionary.associateBy { it.id }
        val resolved = weights.resolveAll(day.plannedExercises)

        DayDetailUiState(
            isLoading = false,
            dayName = day.dayName,
            routineName = routine.name,
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
            startConflict = startConflict,
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

    /**
     * "Start workout". A day can be started only when no workout is running; when one is, the
     * screen asks whether to resume it or discard it, and nothing is written until it does.
     */
    fun startWorkout() {
        viewModelScope.launch { handleStart(workout.start(routineDayId)) }
    }

    /** The answer "resume": open the workout that is already running. */
    fun resumeRunningWorkout() {
        val conflict = startConflict.value ?: return
        startConflict.value = null
        opened.tryEmit(DayDetailEffect.OpenWorkout(conflict.sessionId))
    }

    /**
     * The answer "discard and start": the running workout goes, its rest with it (as it does when
     * the user discards from the workout itself), and this day's workout starts in its place.
     */
    fun discardRunningAndStart() {
        val conflict = startConflict.value ?: return

        viewModelScope.launch {
            workout.discard(conflict.sessionId)
            restTimer.stop()
            startConflict.value = null
            handleStart(workout.start(routineDayId))
        }
    }

    fun dismissStartConflict() {
        startConflict.value = null
    }

    private suspend fun handleStart(result: StartWorkoutResult) {
        when (result) {
            is StartWorkoutResult.Started -> opened.emit(DayDetailEffect.OpenWorkout(result.sessionId))
            is StartWorkoutResult.AlreadyRunning -> {
                // Named in the question, so the user knows which workout they are about to throw away.
                val running = history.observeDraft(result.sessionId).first() ?: return
                startConflict.value = StartConflict(
                    sessionId = running.id,
                    runningDayName = running.dayNameSnapshot,
                    runningRoutineName = running.routineNameSnapshot,
                )
            }
            // A day with nothing planned has nothing to start; the button is disabled for it, and a
            // day deleted in the meantime shows the missing state on its own.
            StartWorkoutResult.NothingToStart -> Unit
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
