package com.ggym.app.domain.workout

import com.ggym.app.domain.model.WorkoutExerciseLog
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.model.WorkoutSetLog
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * The workout that is running: starting it, working through it, ending it.
 *
 * A workout is not a screen's state — it is a *draft session* in the database. [start] materializes
 * a training day into one: the routine and day name snapshots, each exercise's name and rest time,
 * and one row per planned set, pre-filled with the day's resolved working weight and the reps its
 * target asks for. From then on the draft is the only thing the workout consults: the routine can
 * be renamed, edited or deleted and the workout in progress does not move, and a process death
 * costs nothing, because [WorkoutHistoryRepository.observeDraft] puts the user back where they were.
 *
 * The rules this type exists to keep true:
 *
 * - **One workout at a time.** A second [start] while one is running is refused with
 *   [StartWorkoutResult.AlreadyRunning] instead of quietly creating a second draft; the screen asks
 *   whether to resume the running workout or discard it.
 * - **Completion is the moment a number becomes real.** [completeSet] writes the set and hands its
 *   weight to [WorkingWeightUseCases.recordCompletedSet], which makes it the exercise's working
 *   weight for the next workout. The planned slot is not written: the plan is the plan.
 * - **A set with no number of its own follows the workout.** The weight a set carried before it was
 *   completed is the group default for the exercise that day; completing a lighter or heavier set
 *   replaces it in the sets the user has not given a number yet, so 80 → 85 kg is one edit instead
 *   of four. A set the user has already typed into keeps its own number.
 * - **Finishing keeps only what happened.** [finish] writes the completed sets as a *fresh* session,
 *   drops the prefills nobody performed, marks an exercise with no completed set as skipped, and
 *   deletes the draft — in one repository call, so a workout can never end up half-saved.
 *
 * [now] and [zoneId] are injected so tests can pin the wall clock and the day boundary; the app
 * passes the defaults.
 */
class ActiveWorkoutUseCases(
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val history: WorkoutHistoryRepository,
    private val weights: WorkingWeightUseCases,
    private val now: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    /**
     * Turns a training day into a workout in progress and returns its id.
     *
     * The day's slots decide the draft's shape once and for all: each becomes one exercise log with
     * the exercise's name, the slot's rest time and its target sets, each set pre-filled with the
     * weight the day screen shows for that slot ([WorkingWeightUseCases.resolveAll]: the slot's own,
     * the exercise's default, else what was last performed) and the first number in the written
     * target (`"8-10"` means eight). A slot with no weight at all starts at `0.0`, which the workout
     * screen renders as an empty field.
     *
     * A day that no longer exists, or has nothing planned, starts nothing: there is no workout to run.
     */
    suspend fun start(routineDayId: Long): StartWorkoutResult {
        history.observeDraft().first()?.let { running ->
            return StartWorkoutResult.AlreadyRunning(running.id)
        }

        val routine = routines.observeRoutines().first()
            .firstOrNull { candidate -> candidate.days.any { it.id == routineDayId } }
            ?: return StartWorkoutResult.NothingToStart
        val day = routine.days.first { it.id == routineDayId }
        if (day.plannedExercises.isEmpty()) return StartWorkoutResult.NothingToStart

        val dictionary = exercises.observeAll().first().associateBy { it.id }
        val resolved = weights.resolveAll(day.plannedExercises)
        val startedAt = now()

        val draft = WorkoutSession(
            date = Instant.ofEpochMilli(startedAt).atZone(zoneId).toLocalDate(),
            routineNameSnapshot = routine.name,
            dayNameSnapshot = day.dayName,
            durationSeconds = 0,
            isDraft = true,
            startedAtMillis = startedAt,
            exercises = day.plannedExercises.mapIndexed { index, slot ->
                val exercise = checkNotNull(dictionary[slot.exerciseId]) {
                    "routine '${routine.name}' plans exercise ${slot.exerciseId}, which the dictionary does not have"
                }
                WorkoutExerciseLog(
                    sessionId = 0,
                    exerciseId = slot.exerciseId,
                    exerciseNameSnapshot = exercise.name,
                    // Nothing has been performed yet; finishing marks the exercises that never got
                    // a completed set as skipped.
                    wasCompleted = false,
                    orderIndex = index,
                    restSeconds = slot.restSeconds,
                    sets = prefills(
                        count = slot.targetSets,
                        weightKg = resolved[slot.id] ?: 0.0,
                        reps = firstRepsIn(slot.targetReps),
                    ),
                )
            },
        )

        return StartWorkoutResult.Started(history.startDraft(draft))
    }

    /**
     * Marks one set of the running workout as performed, at [weightKg] and [reps], and does the
     * three things a completion means:
     *
     * 1. the set is written as completed;
     * 2. the exercise's working weight becomes [weightKg] when it is greater than `0.0`
     *    ([WorkingWeightUseCases.recordCompletedSet]);
     * 3. the exercise's *uncompleted* sets that still carry the weight this set had take the new one,
     *    so a change applies to the sets the user is about to do as well.
     *
     * Nothing else moves: not the planned slot, not another exercise, not a set the user gave a
     * number of its own. A set whose id belongs to no running workout is left alone, which is what
     * a tap that arrives after the workout ended has to be.
     *
     * Completing an already-completed set rewrites its numbers — the correction the history screen
     * will offer — and lets the sets behind it follow the new weight.
     */
    suspend fun completeSet(setId: Long, weightKg: Double, reps: Int) {
        val draft = history.observeDraft().first() ?: return
        val log = draft.exercises.firstOrNull { log -> log.sets.any { it.id == setId } } ?: return
        val set = log.sets.first { it.id == setId }

        val previousWeight = set.weight
        val completed = set.copy(weight = weightKg, reps = reps, isCompleted = true)
        val filled = if (weightKg > 0.0 && weightKg != previousWeight) {
            log.sets
                .filter { sibling ->
                    sibling.id != setId && !sibling.isCompleted && sibling.weight == previousWeight
                }
                .map { sibling -> sibling.copy(weight = weightKg) }
        } else {
            // A completion at 0.0 is bodyweight work: like the dictionary write, it must not erase a
            // number the user planned with. Equal values have nothing to replace either.
            emptyList()
        }

        history.updateSets(listOf(completed) + filled)
        weights.recordCompletedSet(log.exerciseId, weightKg)
    }

    /**
     * Appends a set to an exercise of the running workout, carrying the last set's weight and reps
     * so the common case — one more set at the same weight — asks the user for nothing but the tap.
     *
     * Returns the new set's id, or `null` when the exercise belongs to no running workout. An
     * exercise whose sets were all removed still gets a set: it starts empty, at `0.0` and no reps.
     */
    suspend fun addSet(workoutExerciseLogId: Long): Long? {
        val draft = history.observeDraft().first() ?: return null
        val log = draft.exercises.firstOrNull { it.id == workoutExerciseLogId } ?: return null
        val last = log.sets.lastOrNull()

        return history.appendSet(
            workoutExerciseLogId = log.id,
            set = WorkoutSetLog(
                workoutExerciseLogId = log.id,
                setNumber = (last?.setNumber ?: 0) + 1,
                weight = last?.weight ?: 0.0,
                reps = last?.reps ?: 0,
                isCompleted = false,
            ),
        )
    }

    /**
     * Removes an uncompleted set from the running workout — a set added by mistake, or one the user
     * decided against.
     *
     * A completed set is refused: it is what happened, not a plan, and correcting history is a
     * different act with a different screen (the workout's own set count only ever grows). Numbers
     * are not reshuffled on the way out: `setNumber` is insertion order, and the screen shows
     * positions.
     */
    suspend fun removeSet(setId: Long): RemoveSetResult {
        val draft = history.observeDraft().first() ?: return RemoveSetResult.NotFound
        val set = draft.exercises
            .asSequence()
            .flatMap { log -> log.sets.asSequence() }
            .firstOrNull { it.id == setId }
            ?: return RemoveSetResult.NotFound

        if (set.isCompleted) return RemoveSetResult.RefusedCompleted

        history.deleteSet(setId)
        return RemoveSetResult.Removed
    }

    /**
     * Ends the running workout: the completed sets become a finished session — the date it started
     * on, its duration measured from the start, its sets renumbered from position — and the draft
     * disappears in the same transaction.
     *
     * Prefills nobody performed are dropped, and an exercise with no completed set is saved as
     * skipped ([WorkoutExerciseLog.wasCompleted] is `false`) with no sets at all. Returns the new
     * session's id, or `null` when the id belongs to no running workout: a workout that was already
     * finished or discarded must not be saved twice.
     */
    suspend fun finish(draftId: Long): Long? {
        val draft = history.observeDraft(draftId).first() ?: return null
        val finishedAt = now()
        val startedAt = draft.startedAtMillis ?: finishedAt

        return history.finishDraft(
            draftId = draftId,
            session = WorkoutSession(
                date = draft.date,
                routineNameSnapshot = draft.routineNameSnapshot,
                dayNameSnapshot = draft.dayNameSnapshot,
                durationSeconds = ((finishedAt - startedAt) / 1_000).coerceAtLeast(0),
                isDraft = false,
                // The moment the workout began is kept: it is a fact the row can carry, and the
                // duration above is only its difference from the finish.
                startedAtMillis = draft.startedAtMillis,
                exercises = draft.exercises.map { log ->
                    val performed = log.sets.filter { it.isCompleted }
                    log.copy(
                        wasCompleted = performed.isNotEmpty(),
                        sets = performed.mapIndexed { position, set -> set.copy(setNumber = position + 1) },
                    )
                },
            ),
        )
    }

    /**
     * Throws the running workout away, prefills included. Nothing reaches the history: the user
     * ended the workout by cancelling it, not by performing it.
     */
    suspend fun discard(draftId: Long) {
        history.discardDraft(draftId)
    }

    /** [count] unperformed sets in insertion order, all carrying the same starting numbers. */
    private fun prefills(count: Int, weightKg: Double, reps: Int): List<WorkoutSetLog> =
        List(count) { index ->
            WorkoutSetLog(
                workoutExerciseLogId = 0,
                setNumber = index + 1,
                weight = weightKg,
                reps = reps,
                isCompleted = false,
            )
        }
}

/** What happened when the user asked to start a workout. */
sealed interface StartWorkoutResult {

    /** A workout is running now, as a draft with this id. */
    data class Started(val sessionId: Long) : StartWorkoutResult

    /**
     * A workout is already running: two at a time have no meaning for a single lifter, so the
     * screen offers to resume [sessionId] or discard it rather than starting a second one.
     */
    data class AlreadyRunning(val sessionId: Long) : StartWorkoutResult

    /** The day is gone, or has nothing planned left: there is no workout to start. */
    data object NothingToStart : StartWorkoutResult
}

/** What happened when the user removed a set from the running workout. */
sealed interface RemoveSetResult {

    /** The set is gone from the workout. */
    data object Removed : RemoveSetResult

    /**
     * The set is completed, so it is what happened rather than a plan: removing it would rewrite
     * the workout. Correcting a performed set is the history screen's job.
     */
    data object RefusedCompleted : RemoveSetResult

    /** No such set in the running workout. */
    data object NotFound : RemoveSetResult
}

/**
 * The first number a written target carries: `"8-10"` means eight reps to start, `"12"` means
 * twelve. A target with no number at all (`"AMRAP"`) pre-fills zero — the same "nothing entered
 * yet" the weight column uses, and a number the user replaces before completing the set.
 */
private fun firstRepsIn(targetReps: String): Int =
    REP_NUMBER.find(targetReps)?.value?.toIntOrNull() ?: 0

private val REP_NUMBER = Regex("\\d+")
