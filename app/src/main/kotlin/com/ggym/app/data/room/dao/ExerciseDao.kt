package com.ggym.app.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ggym.app.data.room.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ExerciseDao {

    /** Ordered by name under the same `NOCASE` collation the uniqueness rule uses. */
    @Query("SELECT * FROM exercise ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    /** One-shot read for the backup exporter, which needs a consistent snapshot. */
    @Query("SELECT * FROM exercise ORDER BY id ASC")
    suspend fun getAll(): List<ExerciseEntity>

    /**
     * Autocomplete lookup. [namePattern] is a *LIKE pattern* owned by the repository (it escapes
     * the user's text and appends `%`), so that `%` and `_` typed by the user match themselves.
     */
    @Query("SELECT * FROM exercise WHERE name LIKE :namePattern ESCAPE '\\' ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun searchByName(namePattern: String): Flow<List<ExerciseEntity>>

    /** Exact match ignoring case, mirroring the unique index. */
    @Query("SELECT * FROM exercise WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ExerciseEntity?

    /** Single lookup by id, for the places that hold an id rather than a name. */
    @Query("SELECT * FROM exercise WHERE id = :exerciseId")
    suspend fun getById(exerciseId: Long): ExerciseEntity?

    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long

    /**
     * Insert that steps aside when the name already exists. Returns the new row id, or `-1` when
     * the unique index rejected it — which is how [com.ggym.app.domain.repository.ExerciseRepository.findOrCreate]
     * reuses an existing entry without depending on a read-then-write race.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringNameConflicts(exercise: ExerciseEntity): Long

    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Query("UPDATE exercise SET currentWorkingWeight = :weightKg WHERE id = :exerciseId")
    suspend fun updateCurrentWorkingWeight(exerciseId: Long, weightKg: Double)

    /**
     * Renames one dictionary entry.
     *
     * The unique `NOCASE` index is the authority: it throws `SQLiteConstraintException` when
     * another entry already holds the name, and accepts a row renamed to a different case of its
     * own name. The repository turns the refusal into a typed result.
     */
    @Query("UPDATE exercise SET name = :name WHERE id = :exerciseId")
    suspend fun rename(exerciseId: Long, name: String)

    @Query("DELETE FROM exercise WHERE id = :exerciseId")
    suspend fun deleteById(exerciseId: Long)

    /** Every routine that still plans this exercise, so a refused delete can name them. */
    @Query(
        """
        SELECT DISTINCT routine.name
        FROM routine
        JOIN routine_day ON routine_day.routineId = routine.id
        JOIN planned_exercise ON planned_exercise.routineDayId = routine_day.id
        WHERE planned_exercise.exerciseId = :exerciseId
        ORDER BY routine.name COLLATE NOCASE ASC
        """,
    )
    suspend fun routineNamesUsing(exerciseId: Long): List<String>

    @Query("DELETE FROM exercise")
    suspend fun deleteAll()
}
