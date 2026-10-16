package com.ggym.app.ui.workout

/**
 * One exercise in the running workout, as both modes of the active-workout screen show it.
 *
 * [workingWeightKg] is the number the user is about to lift: the first set that has not been
 * performed yet, else the last one that has. It is `null` when the exercise has no numbers at all
 * (nothing known before the workout, or every set removed), which the card renders as "—" rather
 * than as a weight of zero.
 */
data class WorkoutExerciseCard(
    val exerciseLogId: Long,
    val exerciseName: String,
    val completedSets: Int,
    val totalSets: Int,
    val workingWeightKg: Double?,
    val restSeconds: Int,
    val sets: List<WorkoutSetRow>,
) {
    /** At least one set has been performed: the card earns its tonal treatment. */
    val isStarted: Boolean get() = completedSets > 0

    /** Every set is performed — the exercise is done, and the action moves on. */
    val isFinished: Boolean get() = totalSets > 0 && completedSets == totalSets

    /** How far through this exercise the workout is, for the card's progress bar. */
    val progress: Float get() = if (totalSets == 0) 0f else completedSets.toFloat() / totalSets
}

/**
 * One set of the running workout.
 *
 * [position] is the place in the exercise's list, 1-based: the stored `setNumber` is insertion
 * order and may have gaps after a removal, while the user counts positions.
 *
 * A `0.0` weight on a **[isCompleted] = false** row means "nothing entered yet" and renders as an
 * empty field; on a completed row it is a real bodyweight entry. That convention is the domain
 * one (`ActiveWorkoutUseCases`), restated here because this is where it becomes text.
 */
data class WorkoutSetRow(
    val id: Long,
    val position: Int,
    val weightKg: Double,
    val reps: Int,
    val isCompleted: Boolean,
)

/** What the focused view's hero shows and what its big action can do right now. */
data class WeightHeroState(
    val selectedSetId: Long?,
    /** The field's contents: the selected set's weight, or what the user has typed into it. */
    val weightText: String,
    /** The reps stepper's value for the selected set. */
    val reps: Int,
    /** The text parses as a weight (a blank field counts: it means bodyweight). */
    val canComplete: Boolean,
    val isSelectedSetCompleted: Boolean,
)

/** What the big action does, which is decided by how far the focused exercise has come. */
enum class FocusedAction {
    /** The selected set is about to be performed. */
    CompleteSet,

    /** Every set of this exercise is done and another exercise follows. */
    NextExercise,

    /** Every set of this exercise is done and it was the last one: there is nothing left to do. */
    FinishWorkout,

    /** Every set was removed from this exercise, so the only useful move is to add one back. */
    AddSet,
}

/** The focused view's one exercise: the hero, the chips, and where this page sits in the workout. */
data class FocusedExercise(
    val index: Int,
    val exerciseLogId: Long,
    val exerciseName: String,
    val restSeconds: Int,
    val sets: List<WorkoutSetRow>,
    val hero: WeightHeroState,
    val action: FocusedAction,
)

/** The rest banner, recomputed from the stored deadline on every tick. */
data class RestBanner(
    val exerciseName: String,
    val remainingSeconds: Long,
)

/**
 * The active workout, as one immutable state.
 *
 * Everything here is derived from the draft in the database plus [ScreenState]'s two pieces of
 * screen-only state (which exercise is focused, which set the hero points at), so a write anywhere
 * — here, in another process, on another screen — lands as a new state.
 */
data class ActiveWorkoutUiState(
    /** True until the first database emission. */
    val isLoading: Boolean = true,
    /**
     * The workout is gone: it was finished or discarded, possibly by another screen. The screen
     * says so instead of pretending to be an empty workout.
     */
    val isMissing: Boolean = false,
    val routineName: String = "",
    val dayName: String = "",
    /** How long the workout has been running; recomputed on every tick. */
    val elapsedSeconds: Long = 0,
    val exercises: List<WorkoutExerciseCard> = emptyList(),
    /** Non-null in focused mode; `null` means the overview. */
    val focused: FocusedExercise? = null,
    val rest: RestBanner? = null,
    /** The summary sheet, opened by "Finish workout" (and by system back from the overview). */
    val isFinishSheetVisible: Boolean = false,
    val isDiscardDialogVisible: Boolean = false,
) {

    val completedSets: Int get() = exercises.sumOf { it.completedSets }

    val totalSets: Int get() = exercises.sumOf { it.totalSets }

    /** Exercises nothing was performed for: finishing saves them as skipped. */
    val skippedExercises: Int get() = exercises.count { it.completedSets == 0 }

    val elapsedMillis: Long get() = elapsedSeconds * 1_000
}
