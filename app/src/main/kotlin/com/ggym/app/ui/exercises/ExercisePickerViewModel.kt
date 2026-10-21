package com.ggym.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.exercise.ExerciseFormUseCases
import com.ggym.app.domain.model.Exercise
import com.ggym.app.domain.repository.DeleteExerciseResult
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The exercise picker for one training day.
 *
 * Two ways in, both ending in a slot on this day: reuse an entry from the search results, or open
 * the [ExerciseFormState] and give a new exercise its numbers before it exists. The second is why
 * this screen owns a form: the typo, the sets and the starting weight are all decided before the
 * first write, so nothing half-made is ever in the dictionary.
 *
 * The default rest time comes from the exercise, so a heavy compound and a curl do not start from
 * the same number.
 */
class ExercisePickerViewModel(
    private val routineDayId: Long,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val formUseCases: ExerciseFormUseCases,
    /** The locale the weight field is shown and re-parsed in; injected so tests are stable. */
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val pendingDelete = MutableStateFlow<ExerciseRow?>(null)
    private val blockedDeleteRoutines = MutableStateFlow<List<String>>(emptyList())

    /** The create form, while it is open. `null` means the list is all there is. */
    private val form = MutableStateFlow<ExerciseFormState?>(null)

    private val _effects = MutableSharedFlow<ExercisePickerEffect>()
    val effects: SharedFlow<ExercisePickerEffect> = _effects.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val results = query.flatMapLatest { text -> exercises.searchByName(text) }

    val uiState: StateFlow<ExercisePickerUiState> = combine(
        query,
        results,
        pendingDelete,
        blockedDeleteRoutines,
        form,
    ) { text, results, pendingDelete, blocked, form ->
        val rows = results.map {
            ExerciseRow(
                id = it.id,
                name = it.name,
                weightKg = it.currentWorkingWeight,
                restSeconds = it.defaultRestSeconds,
            )
        }
        val typed = text.trim()

        ExercisePickerUiState(
            isLoading = false,
            query = text,
            results = rows,
            // The offer to create rides on the very list the user is looking at, so the row can
            // never say "Create X" above a row that is already called X. The database has the
            // last word when the slot is actually written (findOrCreate).
            canCreate = typed.isNotEmpty() && rows.none { row -> row.name.equals(typed, ignoreCase = true) },
            pendingDelete = pendingDelete,
            blockedDeleteRoutines = blocked,
            form = form,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ExercisePickerUiState(),
    )

    fun updateQuery(text: String) {
        query.value = text
    }

    /** Plans an existing dictionary entry in this day and closes the picker. */
    fun addExisting(exercise: ExerciseRow) {
        viewModelScope.launch { addToDay(exercise.id) }
    }

    /**
     * Opens the create form for the typed name.
     *
     * Nothing is written yet: the name is carried into the form so the typo can be fixed there, and
     * the exercise does not exist until Save. The rest time starts from the app's default and the
     * weight is blank — a new exercise with no number is a legitimate way to start.
     */
    fun openCreateForm() {
        val name = query.value.trim()
        if (name.isEmpty()) return

        form.value = ExerciseFormState(
            mode = ExerciseFormMode.Create,
            name = name,
            sets = ExerciseFormDefaults.SETS,
            reps = ExerciseFormDefaults.REPS,
            restSeconds = Exercise.DEFAULT_REST_SECONDS,
            weight = "",
            isOwnWeight = false,
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
     * Creates the exercise and plans it in this day, then leaves.
     *
     * Validation happens here and only here, so the form the user sees is always the one that was
     * saved. A name the dictionary already holds is not an error on this path: the entry wins, and
     * the day still gets its slot (see [ExerciseFormUseCases.create]).
     */
    fun saveForm() {
        val current = form.value ?: return

        when (val validation = current.validate()) {
            is ExerciseFormValidation.Invalid ->
                form.value = current.copy(error = validation.error)

            is ExerciseFormValidation.Valid -> viewModelScope.launch {
                formUseCases.create(
                    routineDayId = routineDayId,
                    name = validation.name,
                    sets = validation.sets,
                    reps = validation.reps,
                    restSeconds = validation.restSeconds,
                    startingWeightKg = validation.weightKg,
                )
                _effects.emit(ExercisePickerEffect.Added)
            }
        }
    }

    fun askDelete(exercise: ExerciseRow) {
        pendingDelete.value = exercise
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    /**
     * Deletes a dictionary entry, unless a routine still plans it — in which case the routines are
     * named, because "no" without a reason is not an answer.
     */
    fun confirmDelete() {
        val exercise = pendingDelete.value ?: return

        viewModelScope.launch {
            when (val result = exercises.deleteExercise(exercise.id)) {
                DeleteExerciseResult.Deleted -> pendingDelete.value = null
                is DeleteExerciseResult.Blocked -> {
                    pendingDelete.value = null
                    blockedDeleteRoutines.value = result.routineNames
                }
            }
        }
    }

    fun dismissBlockedDelete() {
        blockedDeleteRoutines.value = emptyList()
    }

    private suspend fun addToDay(exerciseId: Long) {
        val exercise = exercises.findById(exerciseId) ?: return
        routines.addPlannedExercise(
            routineDayId = routineDayId,
            exerciseId = exercise.id,
            targetSets = ExerciseFormDefaults.SETS,
            targetReps = ExerciseFormDefaults.REPS,
            restSeconds = exercise.defaultRestSeconds,
        )
        _effects.emit(ExercisePickerEffect.Added)
    }

    /** One field edit, applied only while the form is open. */
    private fun editForm(transform: (ExerciseFormState) -> ExerciseFormState) {
        form.value = form.value?.let(transform)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
