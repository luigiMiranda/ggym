package com.ggym.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.domain.workout.WorkingWeightUseCases
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.WeightInput
import com.ggym.app.ui.common.WeightParse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The form behind the slot editor sheet.
 *
 * Validation happens on save and only on save: a half-typed "8" is not an error while the user is
 * still typing "82.5", and a cancelled edit must not have written anything. The write goes through
 * [WorkingWeightUseCases.record] so that the one rule — a slot write never touches the dictionary
 * or the history — has one implementation.
 */
class SlotEditorViewModel(
    private val routineId: Long,
    private val plannedExerciseId: Long,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val weights: WorkingWeightUseCases,
    /** The locale the weight field is shown and re-parsed in; injected so tests are stable. */
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    private val form = MutableStateFlow(SlotEditorUiState())
    val uiState: StateFlow<SlotEditorUiState> = form.asStateFlow()

    private val _effects = MutableSharedFlow<SlotEditorEffect>()
    val effects: SharedFlow<SlotEditorEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            val slot = findSlot()
            if (slot == null) {
                form.update { it.copy(isLoading = false, isMissing = true) }
                return@launch
            }
            val shown = slot.weightKg ?: weights.resolve(slot)
            form.value = SlotEditorUiState(
                isLoading = false,
                exerciseName = exercises.findById(slot.exerciseId)?.name.orEmpty(),
                sets = slot.targetSets.toString(),
                reps = slot.targetReps,
                restSeconds = slot.restSeconds.toString(),
                weight = shown?.let { KgFormat.formatNumber(it, locale) }.orEmpty(),
                isOwnWeight = slot.weightKg != null,
            )
        }
    }

    fun setSets(text: String) = edit { it.copy(sets = text) }

    fun setReps(text: String) = edit { it.copy(reps = text) }

    fun setRestSeconds(text: String) = edit { it.copy(restSeconds = text) }

    fun setWeight(text: String) = edit { it.copy(weight = text) }

    /** The −/+ buttons: reformats the field, so the user sees the number the buttons produced. */
    fun stepWeight(deltaKg: Double) {
        val current = form.value
        val stepped = WeightInput.stepped(current.weight, deltaKg) ?: return
        form.update { it.copy(weight = KgFormat.formatNumber(stepped, locale), error = null) }
    }

    fun save() {
        when (val validation = validate(form.value)) {
            is Validation.Invalid -> form.update { it.copy(error = validation.error) }
            is Validation.Valid -> viewModelScope.launch {
                val slot = findSlot() ?: return@launch
                weights.record(
                    plannedExercise = slot.copy(
                        targetSets = validation.sets,
                        targetReps = validation.reps,
                        restSeconds = validation.restSeconds,
                    ),
                    weightKg = validation.weightKg,
                )
                _effects.emit(SlotEditorEffect.Saved)
            }
        }
    }

    /** Removes the slot from the day. The row's own menu asks first; this is the sheet's shortcut. */
    fun remove() {
        viewModelScope.launch {
            routines.deletePlannedExercise(plannedExerciseId)
            _effects.emit(SlotEditorEffect.Removed)
        }
    }

    private suspend fun findSlot(): PlannedExercise? = routines.observeRoutine(routineId).first()
        ?.days
        ?.flatMap { day -> day.plannedExercises }
        ?.firstOrNull { slot -> slot.id == plannedExerciseId }

    /** Reads the form once: either the values to store, or the first thing that is wrong with it. */
    private fun validate(current: SlotEditorUiState): Validation {
        val sets = current.sets.trim().toIntOrNull()
        val restSeconds = current.restSeconds.trim().toIntOrNull()
        val reps = current.reps.trim()
        val weight = WeightInput.parse(current.weight)

        return when {
            sets == null || sets < 1 -> Validation.Invalid(SlotEditorError.SetsTooLow)
            reps.isEmpty() -> Validation.Invalid(SlotEditorError.RepsBlank)
            restSeconds == null || restSeconds < 0 -> Validation.Invalid(SlotEditorError.RestInvalid)
            weight !is WeightParse.Weight -> Validation.Invalid(SlotEditorError.WeightInvalid)
            else -> Validation.Valid(
                sets = sets,
                reps = reps,
                restSeconds = restSeconds,
                weightKg = weight.weightKg,
            )
        }
    }

    /** The form, once read: either the values to store, or the first problem to show. */
    private sealed interface Validation {
        data class Valid(val sets: Int, val reps: String, val restSeconds: Int, val weightKg: Double?) : Validation
        data class Invalid(val error: SlotEditorError) : Validation
    }

    /** Every keystroke clears the last error: the message was about the text that just changed. */
    private fun edit(transform: (SlotEditorUiState) -> SlotEditorUiState) {
        form.update { current -> transform(current).copy(error = null) }
    }
}
