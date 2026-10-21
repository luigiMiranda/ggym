package com.ggym.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.exercise.ExerciseFormUseCases
import com.ggym.app.domain.exercise.UpdateSlotResult
import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import com.ggym.app.domain.workout.ActiveWorkoutUseCases
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.domain.workout.StartWorkoutResult
import com.ggym.app.domain.workout.WorkingWeightUseCases
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.exercises.ExerciseFormError
import com.ggym.app.ui.exercises.ExerciseFormMode
import com.ggym.app.ui.exercises.ExerciseFormState
import com.ggym.app.ui.exercises.ExerciseFormValidation
import com.ggym.app.ui.exercises.validate
import com.ggym.app.ui.exercises.withName
import com.ggym.app.ui.exercises.withReps
import com.ggym.app.ui.exercises.withRestStep
import com.ggym.app.ui.exercises.withSetsStep
import com.ggym.app.ui.exercises.withWeight
import com.ggym.app.ui.exercises.withWeightStep
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
import java.util.Locale

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
    private val formUseCases: ExerciseFormUseCases,
    /** The locale the weight field is shown and re-parsed in; injected so tests are stable. */
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<SlotRow?>(null)

    /** The running workout that blocks a start, while the resume-or-discard question is open. */
    private val startConflict = MutableStateFlow<StartConflict?>(null)

    /** The exercise form, while it is open: the name and the day's numbers, edited together. */
    private val form = MutableStateFlow<ExerciseFormState?>(null)

    private val opened = MutableSharedFlow<DayDetailEffect>(extraBufferCapacity = 1)

    /** One-shots for the screen: the workout that was started or resumed, to open. */
    val effects: SharedFlow<DayDetailEffect> = opened.asSharedFlow()

    val uiState: StateFlow<DayDetailUiState> = combine(
        routines.observeRoutine(routineId),
        exercises.observeAll(),
        pendingDelete,
        startConflict,
        form,
    ) { routine, dictionary, pendingDelete, startConflict, form ->
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
            form = form,
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

    /** Counterpart of [RoutineDetailViewModel.reorderDays], for the exercises of this day. */
    fun reorderSlots(orderedSlotIds: List<Long>) {
        viewModelScope.launch {
            val day = routines.observeRoutine(routineId).first()
                ?.days
                ?.firstOrNull { it.id == routineDayId }
                ?: return@launch
            val current = day.plannedExercises.map { it.id }
            val wanted = orderedSlotIds.distinct().filter { it in current }
            val order = wanted + current.filter { it !in wanted }
            if (order == current) return@launch
            routines.reorderPlannedExercises(routineDayId, order)
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
     * Opens the form for one slot.
     *
     * The row has already resolved the weight ([WorkingWeightUseCases]), so the form opens with the
     * same number the user was just looking at rather than reading the database a second time — the
     * two can therefore never disagree. `weightTouched` starts false: an inherited number shown in
     * the field is there to be seen, not to be written back unless the user changes it.
     */
    fun openForm(slot: SlotRow) {
        form.value = ExerciseFormState(
            mode = ExerciseFormMode.EditSlot,
            plannedExerciseId = slot.id,
            name = slot.exerciseName,
            sets = slot.targetSets,
            reps = slot.targetReps,
            restSeconds = slot.restSeconds,
            weight = slot.weightKg?.let { KgFormat.formatNumber(it, locale) }.orEmpty(),
            isOwnWeight = slot.hasOwnWeight,
        )
    }

    fun dismissForm() {
        form.value = null
    }

    fun setFormName(text: String) = editForm { it.withName(text) }

    fun stepFormSets(delta: Int) = editForm { it.withSetsStep(delta) }

    fun setFormReps(text: String) = editForm { it.withReps(text) }

    fun stepFormRest(deltaSeconds: Int) = editForm { it.withRestStep(deltaSeconds) }

    fun setFormWeight(text: String) = editForm { it.withWeight(text) }

    fun stepFormWeight(deltaKg: Double) = editForm { it.withWeightStep(deltaKg, locale) }

    /**
     * Writes the exercise's name and the slot's numbers in one action.
     *
     * The slot is re-read here rather than trusted from the form: the form only owns the five fields
     * it shows, and the rest of the slot (its order, its exercise) belongs to the day. A name another
     * entry holds is the one refusal the form has to explain — it stays open with the typed text and
     * nothing is written, not even the numbers.
     */
    fun saveForm() {
        val current = form.value ?: return
        val plannedExerciseId = current.plannedExerciseId ?: return

        when (val validation = current.validate()) {
            is ExerciseFormValidation.Invalid ->
                form.value = current.copy(error = validation.error)

            is ExerciseFormValidation.Valid -> viewModelScope.launch {
                val slot = findSlot(plannedExerciseId)
                if (slot == null) {
                    // The slot is gone (the day was deleted, or another screen removed it): there is
                    // nothing to write, and pretending otherwise would re-create it as an orphan.
                    form.value = null
                    return@launch
                }

                // An inherited weight the user never touched is left inherited, so the day keeps
                // following the exercise instead of pinning the number it happened to show.
                val weightKg = if (current.weightTouched || current.isOwnWeight) validation.weightKg else null

                when (
                    formUseCases.updateSlot(
                        plannedExercise = slot,
                        name = validation.name,
                        sets = validation.sets,
                        reps = validation.reps,
                        restSeconds = validation.restSeconds,
                        weightKg = weightKg,
                    )
                ) {
                    UpdateSlotResult.Saved -> form.value = null
                    UpdateSlotResult.NameTaken ->
                        form.update { it?.copy(error = ExerciseFormError.NameTaken) }
                    UpdateSlotResult.NotFound -> form.value = null
                }
            }
        }
    }

    /** The sheet's "Remove from day": the same write as the row menu, from inside the form. */
    fun removeFormSlot() {
        val plannedExerciseId = form.value?.plannedExerciseId ?: return

        viewModelScope.launch {
            routines.deletePlannedExercise(plannedExerciseId)
            form.value = null
        }
    }

    /** The slot as the database has it right now, or `null` once it is gone. */
    private suspend fun findSlot(plannedExerciseId: Long): PlannedExercise? =
        routines.observeRoutine(routineId).first()
            ?.days
            ?.flatMap { day -> day.plannedExercises }
            ?.firstOrNull { slot -> slot.id == plannedExerciseId }

    /** One field edit, applied only while the form is open. */
    private fun editForm(transform: (ExerciseFormState) -> ExerciseFormState) {
        form.value = form.value?.let(transform)
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
