package com.ggym.app.domain.repository

import com.ggym.app.domain.model.Exercise
import com.ggym.app.domain.model.RoutineRef
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

    /** The dictionary entry with this id, or `null` once it has been deleted. */
    suspend fun findById(exerciseId: Long): Exercise?

    /**
     * The exercise called [name], creating it if the dictionary does not have one yet.
     *
     * This is the write path behind "type a name, reuse it forever": an existing entry wins over
     * the arguments, so reusing "Bench Press" can never reset the weight it carries. The name is
     * trimmed, and matching ignores case.
     *
     * [currentWorkingWeight] is written **only when the row is created** — it is the exercise's
     * starting weight, and an entry that already exists keeps the number its own workouts put
     * there. Passing it for an existing name is a silent no-op, exactly like
     * [defaultRestSeconds].
     */
    suspend fun findOrCreate(
        name: String,
        defaultRestSeconds: Int = Exercise.DEFAULT_REST_SECONDS,
        currentWorkingWeight: Double? = null,
    ): Exercise

    /** Inserts [exercise] or updates it by id; returns the stored copy, with its id filled in. */
    suspend fun save(exercise: Exercise): Exercise

    /** Persists the weight to load next time, and remembers when it was set. */
    suspend fun updateCurrentWorkingWeight(exerciseId: Long, weightKg: Double)

    /**
     * Renames a dictionary entry, and nothing else.
     *
     * History keeps the name it was written with: `workout_exercise_log.exerciseNameSnapshot` and
     * the session name snapshots are not rewritten, so a past workout still says what the exercise
     * was called when it happened, and a workout already in progress keeps the name it started
     * with. Only the dictionary row moves.
     *
     * The name is trimmed. A name another entry already holds is **refused**, under the same
     * case-insensitive rule the unique index enforces, rather than merged: merging two entries
     * would mean repointing every planned slot and every history row that referenced one of them.
     */
    suspend fun renameExercise(exerciseId: Long, newName: String): RenameExerciseResult

    /** Deletes a dictionary entry, unless a routine still plans it. */
    suspend fun deleteExercise(exerciseId: Long): DeleteExerciseResult
}

/** Outcome of [ExerciseRepository.deleteExercise]. */
sealed interface DeleteExerciseResult {

    /** The dictionary entry is gone. Training history that referenced it kept its snapshot name. */
    data object Deleted : DeleteExerciseResult

    /**
     * The entry is still planned in at least one routine, so deleting it would leave a routine
     * slot with nothing to perform. [routines] names them (and carries their ids), so the UI can
     * say which and then offer a way into the first one.
     */
    data class Blocked(val routines: List<RoutineRef>) : DeleteExerciseResult {
        /** The routines' names, for copy that only needs to list them. */
        val routineNames: List<String> get() = routines.map { it.name }
    }
}

/** Outcome of [ExerciseRepository.renameExercise]. */
sealed interface RenameExerciseResult {

    /** The dictionary entry now carries [exercise]. */
    data class Renamed(val exercise: Exercise) : RenameExerciseResult

    /** Another entry already holds that name, case-insensitively: nothing was written. */
    data object NameTaken : RenameExerciseResult

    /** No such entry — it was deleted while the dialog was open. */
    data object NotFound : RenameExerciseResult

    /** The entry already carries exactly this name: there is nothing to write. */
    data object Unchanged : RenameExerciseResult
}
