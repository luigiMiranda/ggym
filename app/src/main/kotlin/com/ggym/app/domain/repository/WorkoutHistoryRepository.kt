package com.ggym.app.domain.repository

import com.ggym.app.domain.model.LastCompletedSet
import com.ggym.app.domain.model.LoggedSetPoint
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.model.WorkoutSetLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Reads and writes training history: saved sessions, their exercises and their sets — and the one
 * workout that is still running.
 *
 * The two kinds of session are deliberately handled by different methods. History is written once,
 * when a workout ends ([saveSession]) and never changes afterwards except for correcting a single
 * set. A workout in progress is a *draft*: it is written when the workout starts ([startDraft]) and
 * updated as it happens, and it either turns into history ([finishDraft]) or disappears
 * ([discardDraft]).
 *
 * Every read on this interface means *finished* workouts. A draft is reachable only through
 * [observeDraft], which is what keeps the calendar, the charts and "what did I lift last" honest
 * without each caller remembering to filter.
 */
interface WorkoutHistoryRepository {

    /**
     * Sessions whose date falls in the range, both ends included — a calendar month asks for its
     * first and last day and expects the whole month. Drafts are not included.
     */
    fun observeSessionsInRange(from: LocalDate, to: LocalDate): Flow<List<WorkoutSession>>

    /**
     * The dates of the finished sessions in the range, both ends included, and nothing else.
     *
     * This is the attendance chart's read: it needs to know *which days* were trained, not what was
     * lifted on them, and loading a year of exercise trees to count days would be the expensive way
     * to answer a cheap question. A day with two workouts appears twice — "distinct days" is the
     * aggregation's rule, not the query's.
     */
    fun observeSessionDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>>

    /** One finished session with its exercises and sets, for inspecting or correcting a past workout. */
    fun observeSession(sessionId: Long): Flow<WorkoutSession?>

    /**
     * Every completed set of one exercise, oldest session first, with the day it happened.
     *
     * The progression chart's raw material. Drafts and pre-filled (unperformed) sets are not in
     * here: a point on that chart means "I lifted this", so the filters belong to the read rather
     * than to each caller — and a session deleted from history takes its points with it.
     */
    fun observeCompletedSetsForExercise(exerciseId: Long): Flow<List<LoggedSetPoint>>

    /** The one workout in progress, or `null` when there is none. */
    fun observeDraft(): Flow<WorkoutSession?>

    /**
     * One draft by its id, or `null` once it has been finished or discarded.
     *
     * The active-workout screen was opened on a specific workout, so it watches that one rather
     * than "whatever is running": if the workout ends elsewhere, the screen sees it end instead of
     * adopting the next one.
     */
    fun observeDraft(sessionId: Long): Flow<WorkoutSession?>

    /**
     * Writes a workout that is starting, with its exercises and pre-filled sets, and returns its
     * id. The draft flag comes from the method, not from the model: this is the running-workout
     * write path.
     */
    suspend fun startDraft(draft: WorkoutSession): Long

    /**
     * Ends a running workout: writes [session] as finished history and removes the draft, in one
     * transaction.
     *
     * The finished session is a *fresh* row — an id handed in on [session] is ignored, exactly as
     * in [saveSession] — so finishing can never overwrite an earlier workout, and the draft id
     * becomes meaningless. Doing both halves atomically is what makes the gate's "exactly once"
     * true: there is no moment where both exist (a duplicate workout) or neither does (a lost one).
     */
    suspend fun finishDraft(draftId: Long, session: WorkoutSession): Long

    /** Throws away the workout in progress, with its exercises and sets. History is untouched. */
    suspend fun discardDraft(draftId: Long)

    /** Adds one set to an exercise of a running workout, for `+ Add Set`. Returns the new set's id. */
    suspend fun appendSet(workoutExerciseLogId: Long, set: WorkoutSetLog): Long

    /**
     * Saves a whole finished session, sets included, and returns its new id.
     *
     * The call always inserts: an id on the incoming model is ignored, so a retried save cannot
     * silently overwrite an earlier workout.
     */
    suspend fun saveSession(session: WorkoutSession): Long

    suspend fun deleteSession(sessionId: Long)

    /** Corrects one logged set — a typo in a past workout, not a new training event. */
    suspend fun updateSet(set: WorkoutSetLog)

    /**
     * Writes several sets in one transaction. Completing a set in a running workout also pre-fills
     * the sets behind it, and half of that write would be a workout that disagrees with itself.
     */
    suspend fun updateSets(sets: List<WorkoutSetLog>)

    suspend fun deleteSet(setId: Long)

    /**
     * The latest set marked as completed for an exercise in a *finished* workout, or `null` if it
     * has never been done.
     *
     * "Latest" is by session date first and set number second, so a backdated session does not
     * masquerade as the most recent one.
     */
    suspend fun latestCompletedSet(exerciseId: Long): LastCompletedSet?
}
