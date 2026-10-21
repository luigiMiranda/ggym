package com.ggym.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.ggym.app.data.room.GgymDatabase
import com.ggym.app.data.room.entity.ExerciseEntity
import com.ggym.app.data.room.mappers.ExerciseMapper
import com.ggym.app.domain.model.Exercise
import com.ggym.app.domain.repository.DeleteExerciseResult
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RenameExerciseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The dictionary, on top of Room.
 *
 * Two behaviours here are worth knowing about:
 *
 * - [findOrCreate] inserts with `INSERT OR IGNORE` and then reads the row back, so two callers
 *   racing on the same new name converge on one dictionary entry instead of one of them failing
 *   on the unique index.
 * - [deleteExercise] refuses a delete that a routine still plans, and names the routines, because
 *   the alternative the database would pick on its own is either an orphaned slot or a silently
 *   skipped exercise.
 */
internal class RoomExerciseRepository(
    private val database: GgymDatabase,
) : ExerciseRepository {

    private val dao = database.exerciseDao()

    override fun observeAll(): Flow<List<Exercise>> =
        dao.observeAll().map { entities -> entities.map(ExerciseMapper::toDomain) }

    override fun searchByName(query: String): Flow<List<Exercise>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return observeAll()
        return dao.searchByName(trimmed.toLikePrefixPattern())
            .map { entities -> entities.map(ExerciseMapper::toDomain) }
    }

    override suspend fun findByName(name: String): Exercise? =
        dao.findByName(name.trim())?.let(ExerciseMapper::toDomain)

    override suspend fun findById(exerciseId: Long): Exercise? =
        dao.getById(exerciseId)?.let(ExerciseMapper::toDomain)

    override suspend fun findOrCreate(
        name: String,
        defaultRestSeconds: Int,
        currentWorkingWeight: Double?,
    ): Exercise {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "an exercise needs a name" }

        dao.findByName(trimmed)?.let { return ExerciseMapper.toDomain(it) }
        dao.insertIgnoringNameConflicts(
            ExerciseEntity(
                name = trimmed,
                currentWorkingWeight = currentWorkingWeight,
                defaultRestSeconds = defaultRestSeconds,
            ),
        )
        // A created row and a row that won the race both live under this name, and the read
        // cannot come back empty: the name exists by the time the insert returns.
        val stored = checkNotNull(dao.findByName(trimmed)) { "exercise '$trimmed' was not stored" }
        return ExerciseMapper.toDomain(stored)
    }

    override suspend fun save(exercise: Exercise): Exercise {
        val entity = ExerciseMapper.toEntity(exercise)
        val id = if (entity.id == 0L) {
            dao.insert(entity)
        } else {
            dao.upsert(entity)
            entity.id
        }
        return ExerciseMapper.toDomain(entity.copy(id = id))
    }

    override suspend fun updateCurrentWorkingWeight(exerciseId: Long, weightKg: Double) {
        dao.updateCurrentWorkingWeight(exerciseId, weightKg)
    }

    override suspend fun renameExercise(exerciseId: Long, newName: String): RenameExerciseResult {
        val trimmed = newName.trim()
        val existing = dao.getById(exerciseId) ?: return RenameExerciseResult.NotFound
        // Exact equality only: a rename to a different *case* of the row's own name is a legitimate
        // fix ("bench press" → "Bench Press") and must reach the database.
        if (existing.name == trimmed) return RenameExerciseResult.Unchanged

        return try {
            dao.rename(exerciseId, trimmed)
            RenameExerciseResult.Renamed(ExerciseMapper.toDomain(existing.copy(name = trimmed)))
        } catch (_: SQLiteConstraintException) {
            // The unique `NOCASE` index refused: some other entry already answers to this name.
            RenameExerciseResult.NameTaken
        }
    }

    override suspend fun deleteExercise(exerciseId: Long): DeleteExerciseResult =
        database.withTransaction {
            val plannedIn = dao.routineNamesUsing(exerciseId)
            if (plannedIn.isNotEmpty()) return@withTransaction DeleteExerciseResult.Blocked(plannedIn)

            try {
                dao.deleteById(exerciseId)
                DeleteExerciseResult.Deleted
            } catch (_: SQLiteConstraintException) {
                // The check above normally catches this. If a routine started planning the
                // exercise in between, report it instead of crashing: the database refused for a
                // reason, and that reason is the answer.
                DeleteExerciseResult.Blocked(dao.routineNamesUsing(exerciseId))
            }
        }
}

/**
 * Escapes the user's text and turns it into a prefix pattern for the autocomplete, so that a
 * typed `%` or `_` looks for that character instead of matching everything.
 */
private fun String.toLikePrefixPattern(): String {
    val escaped = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "$escaped%"
}
