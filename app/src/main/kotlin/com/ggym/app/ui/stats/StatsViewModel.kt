package com.ggym.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.model.BodyWeightLimits
import com.ggym.app.domain.model.BodyWeightLog
import com.ggym.app.domain.repository.BodyWeightWriteResult
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import com.ggym.app.domain.stats.AttendanceCounts
import com.ggym.app.domain.stats.BodyWeightRange
import com.ggym.app.domain.stats.BodyWeightStats
import com.ggym.app.domain.stats.BodyWeightUseCases
import com.ggym.app.domain.stats.CalendarMonths
import com.ggym.app.domain.stats.ExerciseProgression
import com.ggym.app.domain.stats.MonthAttendance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The Stats tab: attendance, body weight, one exercise's progression, and the body-weight log.
 *
 * One ViewModel and one immutable state because the four sections share a clock and a locale, and
 * because the log and the chart behind the sheet must never disagree: both are reads of the same
 * database, and a saved measurement arrives in both as the next emission.
 *
 * The range and the chosen exercise are screen state. The range re-subscribes the body-weight read
 * (a window is a query, not a filter of everything), and the selection re-subscribes the
 * progression read — the dictionary is the only place exercise names come from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val history: WorkoutHistoryRepository,
    private val exercises: ExerciseRepository,
    private val bodyWeights: BodyWeightUseCases,
    todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val today = todayProvider()
    private val months = CalendarMonths.last(CalendarMonths.ATTENDANCE_MONTHS, today)

    private val range = MutableStateFlow(BodyWeightRange.DEFAULT)
    private val selection = MutableStateFlow<Long?>(null)
    private val dialogs = MutableStateFlow(Dialogs())

    /** The attendance window: finished sessions' dates, counted by distinct day and zero-filled. */
    private val attendance: Flow<List<MonthAttendance>> =
        history.observeSessionDates(months.first().atDay(1), today)
            .map { dates -> AttendanceCounts.from(dates, months) }

    private val dictionary = exercises.observeAll()

    /**
     * The dictionary and the chosen exercise.
     *
     * A selection that is no longer in the dictionary (the exercise was deleted) falls back to the
     * first entry, so the chart can never plot a name the picker does not offer.
     */
    private val dictionaryState: StateFlow<DictionaryState> = combine(
        dictionary,
        selection,
    ) { all, selected ->
        val options = all.map { ExerciseOption(it.id, it.name) }
        DictionaryState(
            options = options,
            selectedId = selected?.takeIf { id -> options.any { it.id == id } }
                ?: options.firstOrNull()?.id,
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DictionaryState(),
    )

    /** The selected exercise's completed sets, grouped into one maximum per date. */
    private val progression: Flow<ExerciseProgression> = dictionaryState
        .map { it.selectedId }
        .distinctUntilChanged()
        .flatMapLatest { exerciseId ->
            if (exerciseId == null) {
                flowOf(ExerciseProgression.EMPTY)
            } else {
                history.observeCompletedSetsForExercise(exerciseId)
                    .map(ExerciseProgression::from)
            }
        }

    /** The body-weight read for the chosen window, tagged so the range and the points agree. */
    private val bodyEntries: Flow<RangeEntries> = range.flatMapLatest { chosen ->
        bodyWeights.observe(chosen, today).map { entries -> RangeEntries(chosen, entries) }
    }

    private val bodyLog: Flow<List<BodyWeightLog>> = bodyWeights.observeAll(today)

    private val bodySection: Flow<BodySection> = combine(
        bodyEntries,
        bodyLog,
        dialogs,
    ) { entries, log, dialogs ->
        BodySection(
            range = entries.range,
            stats = BodyWeightStats.from(entries.entries, today, entries.range),
            log = log,
            dialogs = dialogs,
        )
    }

    val uiState: StateFlow<StatsUiState> = combine(
        attendance,
        bodySection,
        dictionaryState,
        progression,
    ) { attendance, body, dictionary, progression ->
        StatsUiState(
            // The dictionary's first emission is the signal that the reads have landed: showing
            // the empty cards before it would flash "no workouts" at a user who has some.
            isLoading = !dictionary.loaded,
            today = today,
            attendance = attendance,
            bodyRange = body.range,
            bodyStats = body.stats,
            bodyLog = body.log,
            exercises = dictionary.options,
            selectedExerciseId = dictionary.selectedId,
            selectedExerciseName = dictionary.options
                .firstOrNull { it.id == dictionary.selectedId }
                ?.name
                .orEmpty(),
            progression = progression,
            sheet = body.dialogs.sheet,
            pendingDelete = body.dialogs.pendingDelete,
            exercisePickerOpen = body.dialogs.exercisePickerOpen,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StatsUiState(today = today),
    )

    fun selectRange(value: BodyWeightRange) {
        range.value = value
    }

    fun selectExercise(exerciseId: Long) {
        selection.value = exerciseId
        dialogs.update { it.copy(exercisePickerOpen = false) }
    }

    fun openExercisePicker() {
        dialogs.update { it.copy(exercisePickerOpen = true) }
    }

    fun dismissExercisePicker() {
        dialogs.update { it.copy(exercisePickerOpen = false) }
    }

    /**
     * The "+ Log" action: a new measurement for a day, defaulting to today and blank.
     *
     * It deliberately does not open today's existing row for editing: the one-per-day rule means
     * saving today again updates it, but the form is a *log* form, and changing its date must
     * insert on that date rather than move today's measurement away. Moving a measurement is what
     * the log row's Edit does (decision 11), through [openEditSheet].
     */
    fun openLogSheet() {
        updateSheet(
            BodyWeightSheetState(
                entryId = null,
                date = today,
                initialWeightKg = null,
            ),
        )
    }

    fun openEditSheet(entry: BodyWeightLog) {
        updateSheet(
            BodyWeightSheetState(
                entryId = entry.id,
                date = entry.date,
                initialWeightKg = entry.weightKg,
            ),
        )
    }

    fun setSheetDate(date: LocalDate) {
        val sheet = dialogs.value.sheet ?: return
        updateSheet(sheet.copy(date = date, invalid = false))
    }

    fun dismissSheet() {
        updateSheet(null)
    }

    /**
     * Writes what the sheet holds. The range rule is checked here as well as in the form and the
     * repository: a rejected value is an ordinary outcome that the sheet explains under its field.
     */
    fun saveSheet(weightKg: Double?) {
        val sheet = dialogs.value.sheet ?: return
        if (weightKg == null || !BodyWeightLimits.isValid(weightKg)) {
            updateSheet(sheet.copy(invalid = true))
            return
        }

        viewModelScope.launch {
            val result = if (sheet.entryId == null) {
                bodyWeights.log(sheet.date, weightKg)
            } else {
                bodyWeights.update(BodyWeightLog(id = sheet.entryId, date = sheet.date, weightKg = weightKg))
            }

            when (result) {
                is BodyWeightWriteResult.Saved -> updateSheet(null)
                is BodyWeightWriteResult.InvalidWeight -> updateSheet(sheet.copy(invalid = true))
            }
        }
    }

    fun askDeleteEntry(entry: BodyWeightLog) {
        dialogs.update { it.copy(pendingDelete = entry, sheet = null) }
    }

    /** "Delete" inside the edit sheet: it is the same confirmation, aimed at the sheet's entry. */
    fun askDeleteSheetEntry() {
        val sheet = dialogs.value.sheet ?: return
        val entryId = sheet.entryId ?: return
        dialogs.update {
            it.copy(
                pendingDelete = BodyWeightLog(
                    id = entryId,
                    date = sheet.date,
                    weightKg = sheet.initialWeightKg ?: 0.0,
                ),
                sheet = null,
            )
        }
    }

    fun cancelDelete() {
        dialogs.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val entry = dialogs.value.pendingDelete ?: return

        viewModelScope.launch {
            bodyWeights.delete(entry.id)
            dialogs.update { it.copy(pendingDelete = null) }
        }
    }

    private fun updateSheet(sheet: BodyWeightSheetState?) {
        dialogs.update { it.copy(sheet = sheet) }
    }

    private data class Dialogs(
        val sheet: BodyWeightSheetState? = null,
        val pendingDelete: BodyWeightLog? = null,
        val exercisePickerOpen: Boolean = false,
    )

    private data class DictionaryState(
        val options: List<ExerciseOption> = emptyList(),
        val selectedId: Long? = null,
        val loaded: Boolean = false,
    )

    private data class RangeEntries(
        val range: BodyWeightRange,
        val entries: List<BodyWeightLog>,
    )

    private data class BodySection(
        val range: BodyWeightRange,
        val stats: BodyWeightStats,
        val log: List<BodyWeightLog>,
        val dialogs: Dialogs,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
