package com.ggym.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.history.CalendarGrid
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * The calendar: one month's finished workouts, one selected day, and the deletion of a workout
 * logged by mistake.
 *
 * The month's sessions are a database flow, so a workout finished elsewhere, a corrected set or a
 * deletion shows up without a refresh — the dot and the card are two views of the same rows.
 * Only the shown month, the selected day and the open confirmation are screen state.
 *
 * Selection follows three rules with an obvious priority (today when it is in the shown month, the
 * first day with a workout, the 1st), and they live here rather than in the screen so paging
 * through months cannot invent a different answer on each composable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val history: WorkoutHistoryRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(today()))

    /** `null` means "apply the selection rules"; a date means the user tapped it. */
    private val selection = MutableStateFlow<LocalDate?>(null)

    private val pendingDelete = MutableStateFlow<CalendarSessionCard?>(null)

    /**
     * The month's read, tagged with the month it belongs to.
     *
     * The tag is what stops a new month from being drawn with the previous month's sessions while
     * the new query warms up: a mismatched emission is a read in progress, not an answer.
     */
    private val monthSessions: Flow<MonthSessions> = month.flatMapLatest { shown ->
        history.observeSessionsInRange(shown.atDay(1), shown.atEndOfMonth())
            .map { sessions -> MonthSessions(shown, sessions.map { it.toCalendarCard() }) }
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        month,
        monthSessions,
        selection,
        pendingDelete,
    ) { shown, loaded, explicitSelection, pendingDelete ->
        val currentToday = today()
        val fresh = loaded.month == shown
        val cards = if (fresh) loaded.cards else emptyList()
        val byDay = cards.groupBy { it.date }

        // An explicit selection belongs to the month it was made in; paging away clears it, and a
        // date the user tapped in an adjacent month's row is handled by selectDate (it changes the
        // month first, so the date is in the shown one by the time this runs).
        val selected = explicitSelection
            ?.takeIf { YearMonth.from(it) == shown }
            ?: defaultSelection(shown, byDay.keys, currentToday)

        CalendarUiState(
            isLoading = !fresh,
            month = shown,
            today = currentToday,
            selectedDate = selected,
            grid = CalendarGrid.of(shown),
            sessionsByDay = byDay,
            selectedSessions = byDay[selected].orEmpty(),
            pendingDelete = pendingDelete,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = initialState(),
    )

    /** Tapping a day. A day from an adjacent month turns the page to it first. */
    fun selectDate(date: LocalDate) {
        val target = YearMonth.from(date)
        if (target != month.value) month.value = target
        selection.value = date
    }

    fun showPreviousMonth() {
        month.value = month.value.minusMonths(1)
        selection.value = null
    }

    fun showNextMonth() {
        month.value = month.value.plusMonths(1)
        selection.value = null
    }

    /** Back to the current month, with today selected — the one jump a calendar needs. */
    fun showToday() {
        month.value = YearMonth.from(today())
        selection.value = today()
    }

    fun askDelete(card: CalendarSessionCard) {
        pendingDelete.value = card
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    /** Deletes exactly one workout; the cascade is the schema's job. The flow re-emits the dot. */
    fun confirmDelete() {
        val card = pendingDelete.value ?: return

        viewModelScope.launch {
            history.deleteSession(card.sessionId)
            pendingDelete.value = null
        }
    }

    private fun initialState(): CalendarUiState {
        val shown = month.value
        val currentToday = today()
        val selected = defaultSelection(shown, emptySet(), currentToday)
        return CalendarUiState(
            isLoading = true,
            month = shown,
            today = currentToday,
            selectedDate = selected,
            grid = CalendarGrid.of(shown),
        )
    }

    private data class MonthSessions(
        val month: YearMonth,
        val cards: List<CalendarSessionCard>,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * The three selection rules, in priority order: today when the shown month contains it,
         * otherwise the month's first trained day, otherwise its first day.
         *
         * A month with no workouts still needs a selected day (the grid needs a focus and the line
         * below it needs a date), and a user who pages back should not land on the 14th by accident.
         */
        fun defaultSelection(
            month: YearMonth,
            trainedDays: Set<LocalDate>,
            today: LocalDate,
        ): LocalDate = when {
            YearMonth.from(today) == month -> today
            trainedDays.isNotEmpty() -> trainedDays.min()
            else -> month.atDay(1)
        }
    }
}
