package com.ggym.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * The exercise picker for one training day.
 *
 * Adding to the day happens here rather than in the day screen, because the result of "create
 * «Cable Fly»" is a dictionary entry *and* a slot: doing both in one place is what makes the
 * create-then-reuse path testable. The default rest time comes from the exercise, so a heavy
 * compound and a curl do not start from the same number.
 */
class ExercisePickerViewModel(
    private val routineDayId: Long,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val pendingDelete = MutableStateFlow<ExerciseRow?>(null)
    private val blockedDeleteRoutines = MutableStateFlow<List<String>>(emptyList())

    private val _effects = MutableSharedFlow<ExercisePickerEffect>()
    val effects: SharedFlow<ExercisePickerEffect> = _effects.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val results = query.flatMapLatest { text -> exercises.searchByName(text) }

    val uiState: StateFlow<ExercisePickerUiState> = combine(
        query,
        results,
        pendingDelete,
        blockedDeleteRoutines,
    ) { text, results, pendingDelete, blocked ->
        val rows = results.map { ExerciseRow(id = it.id, name = it.name) }
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

    /** Creates the typed name in the dictionary (or reuses it, case-insensitively) and plans it. */
    fun createAndAdd() {
        val name = query.value.trim()
        if (name.isEmpty()) return

        viewModelScope.launch {
            val exercise = exercises.findOrCreate(name, Exercise.DEFAULT_REST_SECONDS)
            addToDay(exercise.id)
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
            targetSets = NewSlotDefaults.TARGET_SETS,
            targetReps = NewSlotDefaults.TARGET_REPS,
            restSeconds = exercise.defaultRestSeconds,
        )
        _effects.emit(ExercisePickerEffect.Added)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
