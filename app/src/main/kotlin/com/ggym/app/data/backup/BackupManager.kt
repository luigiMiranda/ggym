package com.ggym.app.data.backup

import android.content.ContentResolver
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.room.withTransaction
import com.ggym.app.data.room.GgymDatabase
import com.ggym.app.data.room.dao.RoutineDayWithPlannedExercises
import com.ggym.app.data.room.dao.RoutineWithDays
import com.ggym.app.data.room.dao.SessionWithExercises
import com.ggym.app.data.room.dao.WorkoutExerciseLogWithSets
import com.ggym.app.data.room.entity.BodyWeightLogEntity
import com.ggym.app.data.room.entity.ExerciseEntity
import com.ggym.app.data.room.entity.PlannedExerciseEntity
import com.ggym.app.data.room.entity.RoutineDayEntity
import com.ggym.app.data.room.entity.RoutineEntity
import com.ggym.app.data.room.entity.WorkoutExerciseLogEntity
import com.ggym.app.data.room.entity.WorkoutSessionEntity
import com.ggym.app.data.room.entity.WorkoutSetLogEntity
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Exports and restores the whole database as one JSON document, through the Storage Access
 * Framework so the user decides where the file lives.
 *
 * The two halves are deliberately asymmetric:
 *
 * - **Export** is a snapshot of every table, taken inside one transaction so a workout saved while
 *   the export runs cannot land half-way through the file. It reads *entities*, not domain models:
 *   the file is a dump of storage, so a value survives the round trip byte for byte (including a
 *   timestamp that is not exactly at midnight, should a file ever be edited by hand).
 * - **Import** validates first and replaces everything second, in one transaction. "Validate, then
 *   replace" is what makes restoring deterministic: the device ends up exactly in the state the
 *   file describes, never a merge of two divergent histories. If anything fails — including a
 *   value the validator could not see coming — the transaction rolls back and the existing data is
 *   untouched. A file written by an older format version is upgraded on the way in: format 1
 *   simply has no per-slot weights, so its slots arrive with none.
 *
 * Failure is always a value here, never an exception: a screen shows the case, and
 * [ImportError.detail] is for the log.
 */
internal class BackupManager(
    private val database: GgymDatabase,
    private val contentResolver: ContentResolver,
    /** Recorded in the file, and the only reason this is a parameter: tests pin it. */
    private val appVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** The document as it would be written, without writing it. */
    suspend fun exportJson(): String = database.withTransaction {
        BackupJsonFormat.encodeToString(BackupFile.serializer(), snapshot())
    }

    /** Writes the snapshot to a document the user picked. Existing content is replaced. */
    suspend fun exportTo(uri: Uri): ExportResult {
        val json = exportJson()
        return try {
            val stream = contentResolver.openOutputStream(uri, "wt")
                ?: return ExportResult.Failed("the document could not be opened for writing")
            stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ExportResult.Written
        } catch (e: IOException) {
            ExportResult.Failed(e.message ?: "the document could not be written")
        } catch (e: SecurityException) {
            ExportResult.Failed(e.message ?: "writing to the document was refused")
        }
    }

    /** Reads and restores a document the user picked. */
    suspend fun importFrom(uri: Uri): ImportResult {
        val json = try {
            val stream = contentResolver.openInputStream(uri)
                ?: return ImportResult.Failed(ImportError.Unreadable("the document could not be opened"))
            // A backup file is a few hundred kilobytes of text; reading it in one go keeps the
            // parse step (and therefore the error handling) in one place.
            stream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            return ImportResult.Failed(ImportError.Unreadable(e.message ?: "the document could not be read"))
        } catch (e: SecurityException) {
            return ImportResult.Failed(ImportError.Unreadable(e.message ?: "reading the document was refused"))
        }
        return importJson(json)
    }

    /**
     * Parses [json], validates it and replaces the database contents with it.
     *
     * Nothing is written until both the parse and the validation have passed, and everything that
     * is written happens in one transaction.
     */
    suspend fun importJson(json: String): ImportResult {
        val file = try {
            BackupJsonFormat.decodeFromString(BackupFile.serializer(), json)
        } catch (e: SerializationException) {
            return ImportResult.Failed(ImportError.Malformed(e.message ?: "not a GGym backup"))
        } catch (e: IllegalArgumentException) {
            return ImportResult.Failed(ImportError.Malformed(e.message ?: "not a GGym backup"))
        }

        BackupValidator.validate(file)?.let { problem -> return ImportResult.Failed(problem) }

        return try {
            database.withTransaction { replaceEverythingWith(file) }
            ImportResult.Restored(
                BackupCounts(
                    exercises = file.exercises.size,
                    routines = file.routines.size,
                    sessions = file.sessions.size,
                    bodyWeights = file.bodyWeights.size,
                ),
            )
        } catch (e: SQLiteConstraintException) {
            // The validator covers the contradictions it can see; the database has the last word.
            ImportResult.Failed(ImportError.Inconsistent(e.message ?: "the file contradicts the schema"))
        } catch (e: SQLiteException) {
            ImportResult.Failed(ImportError.RestoreFailed(e.message ?: "the database refused the restore"))
        }
    }

    private suspend fun snapshot(): BackupFile = BackupFile(
        schemaVersion = BACKUP_FORMAT_VERSION,
        exportedAt = now(),
        appVersion = appVersion,
        exercises = database.exerciseDao().getAll().map { it.toBackup() },
        routines = database.routineDao().getAllRoutinesWithDays().map { it.toBackup() },
        sessions = database.workoutHistoryDao().getAllSessions().map { it.toBackup() },
        bodyWeights = database.bodyWeightDao().getAll().map { it.toBackup() },
    )

    /**
     * Empties every table and fills it from [file].
     *
     * Deletes run child-first and inserts parent-first, so foreign keys hold at every moment —
     * the routine tree and the history tree are rebuilt bottom-up rather than relying on
     * cascades, which would also hide a mistake in the order.
     */
    private suspend fun replaceEverythingWith(file: BackupFile) {
        val exerciseDao = database.exerciseDao()
        val routineDao = database.routineDao()
        val dayDao = database.routineDayDao()
        val plannedExerciseDao = database.plannedExerciseDao()
        val historyDao = database.workoutHistoryDao()
        val bodyWeightDao = database.bodyWeightDao()

        historyDao.deleteAllSets()
        historyDao.deleteAllExerciseLogs()
        historyDao.deleteAllSessions()
        plannedExerciseDao.deleteAll()
        dayDao.deleteAll()
        routineDao.deleteAll()
        bodyWeightDao.deleteAll()
        exerciseDao.deleteAll()

        file.exercises.forEach { exercise ->
            exerciseDao.insert(
                ExerciseEntity(
                    id = exercise.id,
                    name = exercise.name,
                    currentWorkingWeight = exercise.currentWorkingWeight,
                    defaultRestSeconds = exercise.defaultRestSeconds,
                ),
            )
        }

        file.routines.forEach { routine ->
            routineDao.insert(
                RoutineEntity(id = routine.id, name = routine.name, createdAt = routine.createdAt),
            )
            routine.days.forEach { day ->
                dayDao.insert(
                    RoutineDayEntity(
                        id = day.id,
                        routineId = routine.id,
                        dayName = day.dayName,
                        orderIndex = day.orderIndex,
                    ),
                )
                day.exercises.forEach { planned ->
                    plannedExerciseDao.insert(
                        PlannedExerciseEntity(
                            id = planned.id,
                            routineDayId = day.id,
                            exerciseId = planned.exerciseId,
                            targetSets = planned.targetSets,
                            targetReps = planned.targetReps,
                            restSeconds = planned.restSeconds,
                            orderIndex = planned.orderIndex,
                            weightKg = planned.weightKg,
                        ),
                    )
                }
            }
        }

        file.sessions.forEach { session ->
            historyDao.insertSession(
                WorkoutSessionEntity(
                    id = session.id,
                    dateTimestamp = session.dateTimestamp,
                    routineNameSnapshot = session.routineNameSnapshot,
                    dayNameSnapshot = session.dayNameSnapshot,
                    durationSeconds = session.durationSeconds,
                    isDraft = session.isDraft,
                    startedAtMillis = session.startedAtMillis,
                ),
            )
            session.exercises.forEach { log ->
                historyDao.insertExerciseLog(
                    WorkoutExerciseLogEntity(
                        id = log.id,
                        sessionId = session.id,
                        exerciseId = log.exerciseId,
                        exerciseNameSnapshot = log.exerciseNameSnapshot,
                        wasCompleted = log.wasCompleted,
                        orderIndex = log.orderIndex,
                        restSeconds = log.restSeconds,
                        targetReps = log.targetReps,
                        plannedExerciseId = log.plannedExerciseId,
                    ),
                )
                log.sets.forEach { set ->
                    historyDao.insertSet(
                        WorkoutSetLogEntity(
                            id = set.id,
                            workoutExerciseLogId = log.id,
                            setNumber = set.setNumber,
                            weight = set.weight,
                            reps = set.reps,
                            isCompleted = set.isCompleted,
                        ),
                    )
                }
            }
        }

        file.bodyWeights.forEach { entry ->
            bodyWeightDao.insert(
                BodyWeightLogEntity(
                    id = entry.id,
                    dateTimestamp = entry.dateTimestamp,
                    weightKg = entry.weightKg,
                ),
            )
        }
    }
}

private fun ExerciseEntity.toBackup(): BackupExercise = BackupExercise(
    id = id,
    name = name,
    currentWorkingWeight = currentWorkingWeight,
    defaultRestSeconds = defaultRestSeconds,
)

private fun RoutineWithDays.toBackup(): BackupRoutine = BackupRoutine(
    id = routine.id,
    name = routine.name,
    createdAt = routine.createdAt,
    days = days.sortedWith(compareBy({ it.day.orderIndex }, { it.day.id })).map { it.toBackup() },
)

private fun RoutineDayWithPlannedExercises.toBackup(): BackupRoutineDay = BackupRoutineDay(
    id = day.id,
    dayName = day.dayName,
    orderIndex = day.orderIndex,
    exercises = plannedExercises.sortedWith(compareBy({ it.orderIndex }, { it.id })).map { it.toBackup() },
)

private fun PlannedExerciseEntity.toBackup(): BackupPlannedExercise = BackupPlannedExercise(
    id = id,
    exerciseId = exerciseId,
    targetSets = targetSets,
    targetReps = targetReps,
    restSeconds = restSeconds,
    orderIndex = orderIndex,
    weightKg = weightKg,
)

private fun SessionWithExercises.toBackup(): BackupSession = BackupSession(
    id = session.id,
    dateTimestamp = session.dateTimestamp,
    routineNameSnapshot = session.routineNameSnapshot,
    dayNameSnapshot = session.dayNameSnapshot,
    durationSeconds = session.durationSeconds,
    isDraft = session.isDraft,
    startedAtMillis = session.startedAtMillis,
    exercises = exercises.sortedWith(compareBy({ it.log.orderIndex }, { it.log.id })).map { it.toBackup() },
)

private fun WorkoutExerciseLogWithSets.toBackup(): BackupWorkoutExerciseLog = BackupWorkoutExerciseLog(
    id = log.id,
    exerciseId = log.exerciseId,
    exerciseNameSnapshot = log.exerciseNameSnapshot,
    wasCompleted = log.wasCompleted,
    orderIndex = log.orderIndex,
    restSeconds = log.restSeconds,
    targetReps = log.targetReps,
    plannedExerciseId = log.plannedExerciseId,
    sets = sets.sortedWith(compareBy({ it.setNumber }, { it.id })).map { it.toBackup() },
)

private fun WorkoutSetLogEntity.toBackup(): BackupWorkoutSetLog = BackupWorkoutSetLog(
    id = id,
    setNumber = setNumber,
    weight = weight,
    reps = reps,
    isCompleted = isCompleted,
)

private fun BodyWeightLogEntity.toBackup(): BackupBodyWeight = BackupBodyWeight(
    id = id,
    dateTimestamp = dateTimestamp,
    weightKg = weightKg,
)
