package com.ggym.app.domain.repository

import com.ggym.app.domain.model.Exercise
import kotlinx.coroutines.flow.Flow

/** Reads and writes the global exercise dictionary. */
interface ExerciseRepository {

    /** The whole dictionary, ordered by name, case-insensitively (so "Bench Press" sorts as "bench press"). */
    fun observeAll(): Flow<List<Exercise>>

    /**
     * Dictionary entries whose name starts with [query], for the autocomplete.
     *
     * [query] is treated as literal text: `%` and `_` typed by the user match themselves instead
     * of acting as wildcards. A blank query returns the full dictionary, which is what an empty
     * autocomplete field should show.
     */
    fun searchByName(query: String): Flow<List<Exercise>>

    /** Exact lookup, ignoring case — the same rule the unique index enforces. */
    suspend fun findByName(name: String): Exercise?

    /**
     * The exercise called [name], creating it if the dictionary does not have one yet.
     *
     * This is the write path behind "type a name, reuse it forever": an existing entry wins over
     * the arguments, so reusing "Bench Press" can never reset the weight it carries. The name is
     * trimmed, and matching ignores case.
     */
    suspend fun findOrCreate(name: String, defaultRestSeconds: Int = Exercise.DEFAULT_REST_SECONDS): Exercise

    /** Inserts [exercise] or updates it by id; returns the stored copy, with its id filled in. */
    suspend fun save(exercise: Exercise): Exercise

    /** Persists the weight to load next time, and remembers when it was set. */
    suspend fun updateCurrentWorkingWeight(exerciseId: Long, weightKg: Double)

    /** Deletes a dictionary entry, unless a routine still plans it. */
    suspend fun deleteExercise(exerciseId: Long): DeleteExerciseResult
}

/** Outcome of [ExerciseRepository.deleteExercise]. */
sealed interface DeleteExerciseResult {

    /** The dictionary entry is gone. Training history that referenced it kept its snapshot name. */
    data object Deleted : DeleteExerciseResult

    /**
     * The entry is still planned in at least one routine, so deleting it would leave a routine
     * slot with nothing to perform. [routineNames] names them, so the UI can say which.
     */
    data class Blocked(val routineNames: List<String>) : DeleteExerciseResult
}
