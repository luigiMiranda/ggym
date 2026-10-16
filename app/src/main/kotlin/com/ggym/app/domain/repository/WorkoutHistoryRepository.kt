package com.ggym.app.domain.repository

import com.ggym.app.domain.model.LastCompletedSet
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.model.WorkoutSetLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Reads and writes training history: saved sessions, their exercises and their sets. */
interface WorkoutHistoryRepository {

    /**
     * Sessions whose date falls in the range, both ends included — a calendar month asks for its
     * first and last day and expects the whole month.
     */
    fun observeSessionsInRange(from: LocalDate, to: LocalDate): Flow<List<WorkoutSession>>

    /** One session with its exercises and sets, for inspecting or correcting a past workout. */
    fun observeSession(sessionId: Long): Flow<WorkoutSession?>

    /** Saves a whole session, sets included, and returns its new id. */
    suspend fun saveSession(session: WorkoutSession): Long

    suspend fun deleteSession(sessionId: Long)

    /** Corrects one logged set — a typo in a past workout, not a new training event. */
    suspend fun updateSet(set: WorkoutSetLog)

    suspend fun deleteSet(setId: Long)

    /**
     * The latest set marked as completed for an exercise, or `null` if it has never been done.
     *
     * "Latest" is by session date first and set number second, so a backdated session does not
     * masquerade as the most recent one.
     */
    suspend fun latestCompletedSet(exerciseId: Long): LastCompletedSet?
}
