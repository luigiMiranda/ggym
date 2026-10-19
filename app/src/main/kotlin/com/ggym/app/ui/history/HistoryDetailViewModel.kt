package com.ggym.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.model.WorkoutSetLog
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One past workout: read it, correct a set, remove a set, or delete the workout.
 *
 * Everything that can be written here is a correction of something that already happened. There is
 * deliberately no "add a set", no "add an exercise" and no date field: history is corrected, never
 * extended. `wasCompleted` is not recomputed either — it records the workout's shape when it
 * finished, and a typo in one set's weight does not change which exercises were performed.
 *
 * The session is a database flow, so a set written back (or a workout deleted from the calendar in
 * another tab) arrives here as the next state rather than as a stale copy the screen has to patch.
 */
class HistoryDetailViewModel(
    private val sessionId: Long,
    private val history: WorkoutHistoryRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<SetEditorState?>(null)

    private val pendingDelete = MutableStateFlow<HistoryDeleteTarget?>(null)

    private val events = MutableSharedFlow<HistoryDetailEffect>(extraBufferCapacity = 1)

    /** The one-shot "this workout no longer exists, leave" — the screen cannot render that. */
    val effects: SharedFlow<HistoryDetailEffect> = events.asSharedFlow()

    val uiState: StateFlow<HistoryDetailUiState> = combine(
        history.observeSession(sessionId),
        editor,
        pendingDelete,
    ) { session, editor, pendingDelete ->
        session?.toUiState(editor, pendingDelete)
            ?: HistoryDetailUiState(isLoading = false, isMissing = true, sessionId = sessionId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HistoryDetailUiState(sessionId = sessionId),
    )

    fun editSet(set: HistorySetRow) {
        editor.value = SetEditorState(set)
    }

    fun dismissEditor() {
        editor.value = null
    }

    /**
     * Writes the corrected weight and reps of the set the editor was opened on.
     *
     * The dialog parses its own text (what is on screen is what the user is judging), and this is
     * the second check: the values that reach the database are always a real weight and a positive
     * rep count, whatever a future caller passes.
     */
    fun saveSet(weightKg: Double?, reps: Int?) {
        val open = editor.value ?: return
        val validWeight = weightKg?.takeIf { it.isFinite() && it >= 0 }
        val validReps = reps?.takeIf { it >= 1 }

        if (validWeight == null || validReps == null) {
            editor.value = open.copy(
                error = if (validWeight == null) SetEditError.WeightInvalid else SetEditError.RepsInvalid,
            )
            return
        }

        viewModelScope.launch {
            history.updateSet(
                WorkoutSetLog(
                    id = open.set.id,
                    workoutExerciseLogId = open.set.exerciseLogId,
                    setNumber = open.set.setNumber,
                    weight = validWeight,
                    reps = validReps,
                    isCompleted = open.set.isCompleted,
                ),
            )
            // The editor closed while the write was in flight: do not reopen the state it was in.
            if (editor.value?.set?.id == open.set.id) editor.value = null
        }
    }

    fun askDeleteSet(set: HistorySetRow) {
        pendingDelete.value = HistoryDeleteTarget.Set(set)
    }

    fun askDeleteWorkout() {
        pendingDelete.value = HistoryDeleteTarget.Workout
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        when (val target = pendingDelete.value) {
            null -> Unit

            is HistoryDeleteTarget.Set -> viewModelScope.launch {
                history.deleteSet(target.set.id)
                pendingDelete.value = null
                if (editor.value?.set?.id == target.set.id) editor.value = null
            }

            HistoryDeleteTarget.Workout -> viewModelScope.launch {
                history.deleteSession(sessionId)
                pendingDelete.value = null
                events.emit(HistoryDetailEffect.WorkoutDeleted)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * The display model of one session.
 *
 * Sets are sorted by their number and exercises by their stored order, so the screen shows the
 * workout in the order it was performed rather than in whatever order the relation query returned.
 */
private fun WorkoutSession.toUiState(
    editor: SetEditorState?,
    pendingDelete: HistoryDeleteTarget?,
): HistoryDetailUiState = HistoryDetailUiState(
    isLoading = false,
    sessionId = id,
    date = date,
    dayName = dayNameSnapshot,
    routineName = routineNameSnapshot,
    durationSeconds = durationSeconds,
    exercises = exercises
        .sortedBy { it.orderIndex }
        .map { log ->
            HistoryExerciseBlock(
                id = log.id,
                name = log.exerciseNameSnapshot,
                wasCompleted = log.wasCompleted,
                sets = log.sets
                    .sortedBy { it.setNumber }
                    .map { set ->
                        HistorySetRow(
                            id = set.id,
                            exerciseLogId = set.workoutExerciseLogId,
                            setNumber = set.setNumber,
                            weightKg = set.weight,
                            reps = set.reps,
                            isCompleted = set.isCompleted,
                        )
                    },
            )
        },
    editor = editor,
    pendingDelete = pendingDelete,
)
