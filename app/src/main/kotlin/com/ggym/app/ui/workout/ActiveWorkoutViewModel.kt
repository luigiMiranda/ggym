package com.ggym.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.model.WorkoutExerciseLog
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import com.ggym.app.domain.workout.ActiveWorkoutUseCases
import com.ggym.app.domain.workout.RemoveSetResult
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.ui.common.DurationFormat
import com.ggym.app.ui.common.KgFormat
import com.ggym.app.ui.common.WeightInput
import com.ggym.app.ui.common.WeightParse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The workout that is running, held as the screen shows it.
 *
 * The state is a function of three things: the **draft in the database** (every set, every
 * completion), the **rest countdown's stored deadline**, and the few pieces of screen-only state
 * that no other layer should know about — which exercise is focused, which set the hero points at,
 * and what has been typed into it. Nothing here caches a workout: the draft is re-read on every
 * emission, so a completion that happened in another process is simply the next state.
 *
 * The hero's numbers are the one thing this screen holds *before* the database does. Typing and
 * stepping change the field and the selected chip, and nothing else: a set's numbers become real
 * when it is completed (decision 5's "committed on save, not per keystroke"), which is also what
 * lets a completion still see which sets are carrying the plan's weight rather than the user's.
 *
 * [now] and [ticks] are injected: the elapsed clock and the rest banner are the only things here
 * that change without a write, and a test drives both by hand.
 */
class ActiveWorkoutViewModel(
    private val sessionId: Long,
    private val history: WorkoutHistoryRepository,
    private val workout: ActiveWorkoutUseCases,
    private val restTimer: RestTimerUseCases,
    private val now: () -> Long = System::currentTimeMillis,
    ticks: Flow<Unit> = secondTicks(),
    private val locale: Locale = Locale.getDefault(),
) : ViewModel() {

    /** The state that belongs to the screen and to nothing else. */
    private data class Screen(
        /** Which exercise the focused view is on; `null` is the overview. */
        val focusedIndex: Int? = null,
        /** The set the user last tapped a chip on; `null` follows the first unperformed one. */
        val selectedSetId: Long? = null,
        /** What the user has typed into the hero, kept *with* the set it was typed for. */
        val weightEdit: WeightEdit? = null,
        /** Where the reps stepper has been taken, likewise. */
        val repsEdit: RepsEdit? = null,
        val isFinishSheetVisible: Boolean = false,
        val isDiscardDialogVisible: Boolean = false,
    )

    private data class WeightEdit(val setId: Long, val text: String)

    private data class RepsEdit(val setId: Long, val reps: Int)

    private val screen = MutableStateFlow(Screen())

    private val ended = MutableSharedFlow<ActiveWorkoutEffect>(extraBufferCapacity = 1)

    /** The one-shot the screen waits for: the workout is over and it should leave. */
    val effects: SharedFlow<ActiveWorkoutEffect> = ended.asSharedFlow()

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        history.observeDraft(sessionId),
        restTimer.state,
        ticks,
        screen,
    ) { draft, rest, _, screen -> buildState(draft, rest, screen) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ActiveWorkoutUiState(),
        )

    /** Opens one exercise's focused page. Leaving and re-entering resets the chip selection. */
    fun focusExercise(index: Int) {
        screen.update { current ->
            if (current.focusedIndex == index) {
                current
            } else {
                current.withoutEdits().copy(focusedIndex = index)
            }
        }
    }

    /** Back to the exercise list. */
    fun showOverview() {
        screen.update { it.withoutEdits().copy(focusedIndex = null) }
    }

    /** The hero edits this set from now on, until another is selected. */
    fun selectSet(setId: Long) {
        screen.update { it.withoutEdits().copy(selectedSetId = setId) }
    }

    /**
     * What the user typed into the hero. Nothing is written: the numbers on the hero belong to the
     * set the moment it is *completed* (decision 5's "committed on save, not per keystroke"), which
     * is also what keeps the completion able to see which sets still carry the plan's weight.
     */
    fun editWeight(text: String) {
        val set = selectedSetOf(uiState.value) ?: return
        screen.update { it.copy(weightEdit = WeightEdit(set.id, text)) }
    }

    /** One tap of `−2.5` / `+2.5` on the hero: the plate pair the step exists for. */
    fun stepWeight(deltaKg: Double) {
        val focused = uiState.value.focused ?: return
        val set = selectedSetOf(uiState.value) ?: return
        val stepped = WeightInput.stepped(focused.hero.weightText, deltaKg) ?: return

        screen.update { it.copy(weightEdit = WeightEdit(set.id, KgFormat.formatNumber(stepped, locale))) }
    }

    /** One tap of the reps stepper. Reps never go below zero. */
    fun stepReps(delta: Int) {
        val focused = uiState.value.focused ?: return
        val set = selectedSetOf(uiState.value) ?: return
        val reps = (focused.hero.reps + delta).coerceAtLeast(0)
        if (reps == focused.hero.reps) return

        screen.update { it.copy(repsEdit = RepsEdit(set.id, reps)) }
    }

    /**
     * Marks the selected set as performed, at the numbers the hero shows, and starts the rest.
     *
     * A set that was **already completed** is a correction rather than a new completion: it is
     * rewritten (the domain carries the working-weight rule with it) but it does not restart a rest
     * the user has already finished.
     */
    fun completeSelectedSet() {
        val state = uiState.value
        val focused = state.focused ?: return
        val set = selectedSetOf(state) ?: return
        if (!focused.hero.canComplete) return

        val card = state.exercises.getOrNull(focused.index) ?: return
        val weightKg = (WeightInput.parse(focused.hero.weightText) as? WeightParse.Weight)?.weightKg ?: 0.0
        val startsRest = !set.isCompleted

        viewModelScope.launch {
            workout.completeSet(set.id, weightKg, focused.hero.reps)
            if (startsRest) restTimer.startForSet(card.exerciseLogId, card.exerciseName, card.restSeconds)
            // The selection follows the workout: the next unperformed set is the one the hero
            // should point at, which is what a user who just performed one is looking for.
            screen.update { it.withoutEdits().copy(selectedSetId = null) }
        }
    }

    /** `+ Add set`: the new set inherits the last one's numbers, and the hero moves to it. */
    fun addSet() {
        val focused = uiState.value.focused ?: return

        viewModelScope.launch {
            val newSetId = workout.addSet(focused.exerciseLogId) ?: return@launch
            screen.update { it.withoutEdits().copy(selectedSetId = newSetId) }
        }
    }

    /** Removes an unperformed set. A completed one is refused by the domain, and nothing moves. */
    fun removeSet(setId: Long) {
        viewModelScope.launch {
            if (workout.removeSet(setId) == RemoveSetResult.Removed) {
                screen.update { it.withoutEdits().copy(selectedSetId = null) }
            }
        }
    }

    /** `+15 s` on the banner, or the notification's button. */
    fun addRestTime() {
        viewModelScope.launch { restTimer.extend(RestTimerUseCases.STEP_SECONDS) }
    }

    /** `−15 s` on the banner. Reaching zero ends the rest. */
    fun subtractRestTime() {
        viewModelScope.launch { restTimer.extend(-RestTimerUseCases.STEP_SECONDS) }
    }

    /** Ends the rest early, silently. */
    fun skipRest() {
        viewModelScope.launch { restTimer.skip() }
    }

    /** The summary of what the workout holds, with its two endings. */
    fun askFinish() {
        screen.update { it.copy(isFinishSheetVisible = true) }
    }

    fun dismissFinishSheet() {
        screen.update { it.copy(isFinishSheetVisible = false) }
    }

    /** Saves what happened and leaves. The draft becomes a finished session and is deleted. */
    fun confirmFinish() {
        viewModelScope.launch {
            val finishedId = workout.finish(sessionId)
            restTimer.stop()
            ended.emit(ActiveWorkoutEffect.Ended(finishedId))
        }
    }

    /** Throwing the workout away is destructive, so it goes through the one confirmation dialog. */
    fun askDiscard() {
        screen.update { it.copy(isFinishSheetVisible = false, isDiscardDialogVisible = true) }
    }

    fun cancelDiscard() {
        screen.update { it.copy(isDiscardDialogVisible = false) }
    }

    /** Ends the workout by cancelling it: nothing reaches the history. */
    fun confirmDiscard() {
        viewModelScope.launch {
            workout.discard(sessionId)
            restTimer.stop()
            ended.emit(ActiveWorkoutEffect.Ended(savedSessionId = null))
        }
    }

    private fun selectedSetOf(state: ActiveWorkoutUiState): WorkoutSetRow? {
        val focused = state.focused ?: return null
        return focused.sets.firstOrNull { it.id == focused.hero.selectedSetId }
    }

    /** Drop what the hero was holding: an edit belongs to the selection it was made on. */
    private fun Screen.withoutEdits(): Screen = copy(selectedSetId = null, weightEdit = null, repsEdit = null)

    private fun buildState(
        draft: WorkoutSession?,
        rest: RestTimerState?,
        screen: Screen,
    ): ActiveWorkoutUiState {
        if (draft == null) return ActiveWorkoutUiState(isLoading = false, isMissing = true)

        val cards = draft.exercises.sortedBy { it.orderIndex }.map { log -> card(log) }
        val startedAt = draft.startedAtMillis ?: now()

        return ActiveWorkoutUiState(
            isLoading = false,
            routineName = draft.routineNameSnapshot,
            dayName = draft.dayNameSnapshot,
            elapsedSeconds = DurationFormat.seconds(now() - startedAt),
            exercises = cards,
            focused = screen.focusedIndex?.let { index -> focused(cards, index, screen) },
            rest = rest?.let { running -> banner(running) },
            isFinishSheetVisible = screen.isFinishSheetVisible,
            isDiscardDialogVisible = screen.isDiscardDialogVisible,
        )
    }

    private fun card(log: WorkoutExerciseLog): WorkoutExerciseCard {
        val sets = log.sets.sortedBy { it.setNumber }.mapIndexed { index, set ->
            WorkoutSetRow(
                id = set.id,
                position = index + 1,
                weightKg = set.weight,
                reps = set.reps,
                isCompleted = set.isCompleted,
            )
        }
        val upcoming = sets.firstOrNull { !it.isCompleted } ?: sets.lastOrNull()

        return WorkoutExerciseCard(
            exerciseLogId = log.id,
            exerciseName = log.exerciseNameSnapshot,
            completedSets = sets.count { it.isCompleted },
            totalSets = sets.size,
            workingWeightKg = upcoming?.weightKg?.takeIf { it > 0.0 },
            restSeconds = log.restSeconds,
            sets = sets,
        )
    }

    private fun focused(
        cards: List<WorkoutExerciseCard>,
        index: Int,
        screen: Screen,
    ): FocusedExercise? {
        val card = cards.getOrNull(index) ?: return null

        val selected = card.sets.firstOrNull { it.id == screen.selectedSetId }
            ?: card.sets.firstOrNull { !it.isCompleted }
            ?: card.sets.lastOrNull()

        val weightText = when {
            selected == null -> ""
            screen.weightEdit?.setId == selected.id -> screen.weightEdit.text
            // The domain's "nothing entered yet": an unperformed set at zero is an empty field,
            // while a completed zero is a real bodyweight entry.
            selected.weightKg == 0.0 && !selected.isCompleted -> ""
            else -> KgFormat.formatNumber(selected.weightKg, locale)
        }
        val shownWeight = (WeightInput.parse(weightText) as? WeightParse.Weight)?.weightKg ?: selected?.weightKg ?: 0.0
        val shownReps = screen.repsEdit?.takeIf { it.setId == selected?.id }?.reps ?: selected?.reps ?: 0

        // The selected chip shows what the hero shows, not what the database holds: the numbers on
        // screen are one edit, and the completion is what writes them.
        val displayedSets = if (selected == null) {
            card.sets
        } else {
            card.sets.map { set ->
                if (set.id == selected.id) set.copy(weightKg = shownWeight, reps = shownReps) else set
            }
        }

        val action = when {
            card.sets.isEmpty() -> FocusedAction.AddSet
            card.isFinished && index < cards.lastIndex -> FocusedAction.NextExercise
            card.isFinished -> FocusedAction.FinishWorkout
            else -> FocusedAction.CompleteSet
        }

        return FocusedExercise(
            index = index,
            exerciseLogId = card.exerciseLogId,
            exerciseName = card.exerciseName,
            restSeconds = card.restSeconds,
            sets = displayedSets,
            hero = WeightHeroState(
                selectedSetId = selected?.id,
                weightText = weightText,
                reps = shownReps,
                canComplete = selected != null && WeightInput.parse(weightText) is WeightParse.Weight,
                isSelectedSetCompleted = selected?.isCompleted == true,
            ),
            action = action,
        )
    }

    private fun banner(rest: RestTimerState): RestBanner = RestBanner(
        exerciseName = rest.exerciseName,
        remainingSeconds = DurationFormat.ceilingSeconds(restTimer.remainingMillis(rest, now())),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The screen's one-shot: the workout ended, so there is nothing left for it to show. */
sealed interface ActiveWorkoutEffect {

    /**
     * The workout is over. [savedSessionId] is the session that was written, or `null` when the
     * workout was discarded (or had already ended elsewhere).
     */
    data class Ended(val savedSessionId: Long?) : ActiveWorkoutEffect
}

/** One tick a second: the elapsed clock and the rest banner are all that need it. */
private fun secondTicks(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(TICK_MILLIS)
    }
}

private const val TICK_MILLIS = 1_000L
