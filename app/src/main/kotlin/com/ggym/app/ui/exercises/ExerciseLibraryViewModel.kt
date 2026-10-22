package com.ggym.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.exercise.ExerciseFormUseCases
import com.ggym.app.domain.exercise.UpdateDictionaryResult
import com.ggym.app.domain.model.Exercise
import com.ggym.app.domain.model.ExercisePlan
import com.ggym.app.domain.model.RoutineRef
import com.ggym.app.domain.repository.DeleteExerciseResult
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.ui.common.KgFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The exercise library: find, rename, re-weight, give a rest default, or delete a dictionary entry
 * without opening a workout day.
 *
 * It is the dictionary's own screen, so it edits what is *global* — name, base weight, default rest
 * — and shows where the exercise is used read-only. Sets and reps are the day's plan and are edited
 * on the day, which is why the form's dictionary mode hides them.
 *
 * One read of the routine tree's placements ([RoutineRepository.observeExercisePlans]) answers
 * "where is this used?" for the whole list; a query per row would be the same data read N times.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLibraryViewModel(
    private val exercises: ExerciseRepository,
    private val routines: RoutineRepository,
    private val formUseCases: ExerciseFormUseCases,
    /** The locale the weight field is shown and re-parsed in; injected so tests are stable. */
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val form = MutableStateFlow<ExerciseFormState?>(null)
    private val formPlans = MutableStateFlow<List<ExercisePlan>>(emptyList())
    private val dialogs = MutableStateFlow(Dialogs())

    /** The dictionary filtered by the search: the same literal-text query the picker uses. */
    private val results: Flow<List<Exercise>> = query.flatMapLatest { exercises.searchByName(it) }

    private val rows: Flow<List<LibraryExercise>> = combine(
        results,
        routines.observeExercisePlans(),
    ) { dictionary, plans ->
        val byExercise = plans.groupBy { it.exerciseId }
        dictionary.map { exercise ->
            LibraryExercise(
                id = exercise.id,
                name = exercise.name,
                weightKg = exercise.currentWorkingWeight,
                restSeconds = exercise.defaultRestSeconds,
                plans = byExercise[exercise.id].orEmpty(),
            )
        }
    }

    /** Counted over the whole dictionary, not the search results: the card is not a filter. */
    private val counts: Flow<LibraryCounts> = exercises.observeAll().map { all ->
        LibraryCounts(
            total = all.size,
            withoutWeight = all.count { it.currentWorkingWeight == null },
        )
    }

    private val formSection: Flow<FormSection> = combine(form, formPlans) { form, plans ->
        FormSection(form, plans)
    }

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        rows,
        counts,
        query,
        formSection,
        dialogs,
    ) { rows, counts, query, formSection, dialogs ->
        ExerciseLibraryUiState(
            isLoading = false,
            query = query,
            exercises = rows,
            totalCount = counts.total,
            withoutWeightCount = counts.withoutWeight,
            form = formSection.form,
            formPlans = formSection.plans,
            pendingDelete = dialogs.pendingDelete,
            blockedDelete = dialogs.blockedDelete,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ExerciseLibraryUiState(),
    )

    fun updateQuery(text: String) {
        query.value = text
    }

    /** The FAB: a dictionary entry with no day behind it, so no sets or reps to ask for. */
    fun addExercise() {
        formPlans.value = emptyList()
        form.value = ExerciseFormState(
            mode = ExerciseFormMode.CreateDictionary,
            name = "",
            restSeconds = Exercise.DEFAULT_REST_SECONDS.toString(),
            weight = "",
        )
    }

    /** The row's Edit (and the row body): the same form, in its dictionary mode. */
    fun openEditForm(exercise: LibraryExercise) {
        formPlans.value = exercise.plans
        form.value = ExerciseFormState(
            mode = ExerciseFormMode.EditDictionary,
            exerciseId = exercise.id,
            name = exercise.name,
            restSeconds = exercise.restSeconds.toString(),
            weight = exercise.weightKg?.let { KgFormat.formatNumber(it, locale) }.orEmpty(),
            isOwnWeight = true,
        )
    }

    fun dismissForm() {
        form.value = null
        formPlans.value = emptyList()
    }

    fun setFormName(text: String) = editForm { it.withName(text) }

    fun setFormSets(text: String) = editForm { it.withSets(text) }

    fun stepFormSets(delta: Int) = editForm { it.withSetsStep(delta) }

    fun setFormReps(text: String) = editForm { it.withReps(text) }

    fun setFormRest(text: String) = editForm { it.withRest(text) }

    fun stepFormRest(deltaSeconds: Int) = editForm { it.withRestStep(deltaSeconds) }

    fun setFormWeight(text: String) = editForm { it.withWeight(text) }

    fun stepFormWeight(deltaKg: Double) = editForm { it.withWeightStep(deltaKg, locale) }

    /**
     * Writes the form: a new entry, or the name, base weight and default rest of an existing one.
     *
     * A name another entry holds is the one refusal a dictionary edit has to explain — it stays open
     * with the typed text and nothing is written, not even the weight. An entry deleted while the
     * form was open simply closes the form: there is nothing to write to.
     */
    fun saveForm() {
        val current = form.value ?: return

        when (val validation = current.validate()) {
            is ExerciseFormValidation.Invalid ->
                form.value = current.copy(error = validation.error)

            is ExerciseFormValidation.Valid -> viewModelScope.launch {
                when (current.mode) {
                    ExerciseFormMode.CreateDictionary -> {
                        formUseCases.createDictionary(
                            name = validation.name,
                            restSeconds = validation.restSeconds,
                            startingWeightKg = validation.weightKg,
                        )
                        dismissForm()
                    }

                    ExerciseFormMode.EditDictionary -> {
                        val exerciseId = current.exerciseId
                        val exercise = exerciseId?.let { exercises.findById(it) }
                        if (exercise == null) {
                            dismissForm()
                            return@launch
                        }
                        when (
                            formUseCases.updateDictionary(
                                exercise = exercise,
                                name = validation.name,
                                restSeconds = validation.restSeconds,
                                weightKg = validation.weightKg,
                            )
                        ) {
                            UpdateDictionaryResult.Saved -> dismissForm()
                            UpdateDictionaryResult.NameTaken ->
                                form.update { it?.copy(error = ExerciseFormError.NameTaken) }
                            UpdateDictionaryResult.NotFound -> dismissForm()
                        }
                    }

                    // The library hosts only the dictionary modes; a stray state is closed rather
                    // than written, so a slot can never be created from here.
                    ExerciseFormMode.Create, ExerciseFormMode.EditSlot -> dismissForm()
                }
            }
        }
    }

    /** The sheet's "Delete exercise", aimed at the entry the form is on. */
    fun askDeleteFromForm() {
        val current = form.value ?: return
        val exerciseId = current.exerciseId ?: return
        dialogs.update {
            it.copy(
                pendingDelete = LibraryExercise(
                    id = exerciseId,
                    name = current.name.trim(),
                    weightKg = null,
                    restSeconds = current.restSeconds.toIntOrNull() ?: Exercise.DEFAULT_REST_SECONDS,
                ),
            )
        }
        form.value = null
        formPlans.value = emptyList()
    }

    fun askDelete(exercise: LibraryExercise) {
        dialogs.update { it.copy(pendingDelete = exercise) }
    }

    fun cancelDelete() {
        dialogs.update { it.copy(pendingDelete = null) }
    }

    /**
     * Deletes a dictionary entry, unless a routine still plans it — in which case the routines are
     * named (with their ids), and the dialog can offer a way into the first one.
     */
    fun confirmDelete() {
        val exercise = dialogs.value.pendingDelete ?: return

        viewModelScope.launch {
            when (val result = exercises.deleteExercise(exercise.id)) {
                DeleteExerciseResult.Deleted -> dialogs.update { it.copy(pendingDelete = null) }
                is DeleteExerciseResult.Blocked ->
                    dialogs.update { it.copy(pendingDelete = null, blockedDelete = result.routines) }
            }
        }
    }

    fun dismissBlockedDelete() {
        dialogs.update { it.copy(blockedDelete = emptyList()) }
    }

    /** One field edit, applied only while the form is open. */
    private fun editForm(transform: (ExerciseFormState) -> ExerciseFormState) {
        form.value = form.value?.let(transform)
    }

    private data class Dialogs(
        val pendingDelete: LibraryExercise? = null,
        val blockedDelete: List<RoutineRef> = emptyList(),
    )

    private data class LibraryCounts(
        val total: Int = 0,
        val withoutWeight: Int = 0,
    )

    private data class FormSection(
        val form: ExerciseFormState? = null,
        val plans: List<ExercisePlan> = emptyList(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
