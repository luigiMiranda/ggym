package com.ggym.app.ui.workouts

import com.ggym.app.domain.model.Routine
import com.ggym.app.domain.model.WorkoutSession

/** One routine card: the counts are computed on the way in, so a card never loads the tree. */
data class RoutineCard(
    val id: Long,
    val name: String,
    val dayCount: Int,
    val exerciseCount: Int,
    val setCount: Int = 0,
)

/** The routine list, as the screen renders it. */
data class WorkoutsUiState(
    /** True until the first database emission, so "no routines" is never shown before the read. */
    val isLoading: Boolean = true,
    val routines: List<RoutineCard> = emptyList(),
    val nameDialog: RoutineNameDialog? = null,
    val pendingDelete: RoutineCard? = null,
    /** The workout in progress, if one is: it can be resumed from here, whatever happened to its routine. */
    val resume: ResumeCard? = null,
)

/**
 * The running workout as the resume card shows it. The routine may have been edited or deleted
 * since it started: the workout keeps the names it was started with, so the card does too.
 */
data class ResumeCard(
    val sessionId: Long,
    val dayName: String,
    val completedSets: Int,
    val totalSets: Int,
    val elapsedMillis: Long,
    /** The exercise the next tap would land on, for the card's "Push Day · Bench Press" line. */
    val exerciseName: String? = null,
) {
    /** How much of the workout is behind the user, for the card's progress bar. */
    val progress: Float get() = if (totalSets == 0) 0f else completedSets.toFloat() / totalSets
}

/**
 * The name dialog, shared by "new routine" and "rename routine": [routineId] is `null` while
 * creating, which is the only difference between the two verbs. The typed text lives in the dialog
 * (a text field owns its own state) and arrives with the save call.
 */
data class RoutineNameDialog(
    val routineId: Long? = null,
    val initialName: String = "",
) {
    val isCreating: Boolean get() = routineId == null
}

internal fun WorkoutSession.toResumeCard(at: Long): ResumeCard {
    val sets = exercises.flatMap { it.sets }
    return ResumeCard(
        sessionId = id,
        dayName = dayNameSnapshot,
        completedSets = sets.count { it.isCompleted },
        totalSets = sets.size,
        // A draft always carries its start (the domain writes it); a missing one shows no elapsed time.
        elapsedMillis = startedAtMillis?.let { (at - it).coerceAtLeast(0L) } ?: 0L,
        exerciseName = exercises
            .firstOrNull { exercise -> exercise.sets.any { set -> !set.isCompleted } }
            ?.exerciseNameSnapshot
            ?: exercises.lastOrNull()?.exerciseNameSnapshot,
    )
}

internal fun Routine.toCard(): RoutineCard = RoutineCard(
    id = id,
    name = name,
    dayCount = days.size,
    exerciseCount = days.sumOf { day -> day.plannedExercises.size },
    setCount = days.sumOf { day -> day.plannedExercises.sumOf { slot -> slot.targetSets } },
)
