package com.ggym.app.data.backup

import com.ggym.app.domain.model.Exercise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The backup format this build writes.
 *
 * The format has its own version, separate from the database schema version, because the two
 * questions are different: a file must stay readable across app updates (1, 2, 3, 4 and 5 each
 * added only fields whose absence meant what the older build did), while the database is migrated
 * in place.
 */
internal const val BACKUP_FORMAT_VERSION = 5

/**
 * The oldest format this build still reads, so that a file written before the per-slot weight
 * existed (or before workouts in progress were written down) is a backup, not a paperweight.
 */
internal const val OLDEST_READABLE_BACKUP_FORMAT_VERSION = 1

/**
 * The backup file, as it appears on disk.
 *
 * It is plain, pretty-printed JSON that a human can read and diff: the app is offline and the
 * file is the only way data leaves the device, so "the user can open it and see their workouts"
 * is a feature, not a side effect. One array per table, and every row carries its id, so a
 * restore reproduces the database exactly — including the identity of the rows — instead of
 * renumbering them.
 *
 * Parent ids are not repeated in children: the nesting already says "this day belongs to this
 * routine", and repeating it would allow a file to contradict itself.
 *
 * Because the file is only ever written by GGym, every field is required. A missing one is a
 * broken file, and [BackupManager] reports it as malformed rather than filling in a default. The
 * exceptions are the fields that arrived in a later format, where the old behaviour and the
 * default are the same statement, so accepting the absence keeps the older files readable instead
 * of duplicating the whole DTO tree per version:
 *
 * - [BackupPlannedExercise.weightKg] (format 2): absent and `null` both mean "this slot has no
 *   weight of its own".
 * - [BackupSession.isDraft] and [BackupSession.startedAtMillis] (format 3): a file written before
 *   drafts existed describes finished workouts that never recorded a start time.
 * - [BackupWorkoutExerciseLog.restSeconds] (format 3): the rest time was not snapshotted yet, so
 *   the dictionary's own default is what those rows meant.
 * - [BackupWorkoutExerciseLog.targetReps] (format 4): the target was not snapshotted yet, and a
 *   blank target is exactly "this workout has no target to show".
 * - [BackupWorkoutExerciseLog.plannedExerciseId] (format 5): the pointer back to the slot was not
 *   recorded yet, and `null` means exactly "this log has no slot behind it" — the dictionary-only
 *   behaviour of every format before it.
 */
@Serializable
internal data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val exercises: List<BackupExercise>,
    val routines: List<BackupRoutine>,
    val sessions: List<BackupSession>,
    val bodyWeights: List<BackupBodyWeight>,
)

@Serializable
internal data class BackupExercise(
    val id: Long,
    val name: String,
    val currentWorkingWeight: Double?,
    val defaultRestSeconds: Int,
)

@Serializable
internal data class BackupRoutine(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val days: List<BackupRoutineDay>,
)

@Serializable
internal data class BackupRoutineDay(
    val id: Long,
    val dayName: String,
    val orderIndex: Int,
    val exercises: List<BackupPlannedExercise>,
)

@Serializable
internal data class BackupPlannedExercise(
    val id: Long,
    val exerciseId: Long,
    val targetSets: Int,
    val targetReps: String,
    val restSeconds: Int,
    val orderIndex: Int,
    /** The slot's own weight in kg; `null` means it inherits the exercise's resolved default. */
    val weightKg: Double? = null,
)

@Serializable
internal data class BackupSession(
    val id: Long,
    val dateTimestamp: Long,
    val routineNameSnapshot: String,
    val dayNameSnapshot: String,
    val durationSeconds: Long,
    /** `true` while the workout is still running. A file may hold at most one such session. */
    val isDraft: Boolean = false,
    /** When the workout began, epoch millis; required for a draft, `null` on older history. */
    val startedAtMillis: Long? = null,
    val exercises: List<BackupWorkoutExerciseLog>,
)

@Serializable
internal data class BackupWorkoutExerciseLog(
    val id: Long,
    /** `null` once the dictionary entry it came from has been deleted; the snapshot keeps it readable. */
    val exerciseId: Long?,
    val exerciseNameSnapshot: String,
    val wasCompleted: Boolean,
    val orderIndex: Int,
    /** The rest time between this exercise's sets, snapshotted when the workout started. */
    val restSeconds: Int = Exercise.DEFAULT_REST_SECONDS,
    /**
     * The written target reps snapshotted with it ("8-10", "AMRAP"); blank on a workout that has
     * no target to show — everything written before format 4.
     */
    val targetReps: String = "",
    /**
     * The routine slot this log was built from, so a restored workout in progress can still move
     * that day's weight when a set is completed. `null` on formats 1–4 and on a log with no slot.
     */
    val plannedExerciseId: Long? = null,
    val sets: List<BackupWorkoutSetLog>,
)

@Serializable
internal data class BackupWorkoutSetLog(
    val id: Long,
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val isCompleted: Boolean,
)

@Serializable
internal data class BackupBodyWeight(
    val id: Long,
    val dateTimestamp: Long,
    val weightKg: Double,
)

/**
 * The one JSON configuration the format is read and written with.
 *
 * `explicitNulls` is left on so that "no working weight yet" is written as an explicit `null`
 * rather than an absent key: a reader can then tell "never set" from "field missing". Unknown
 * keys are rejected, which is what makes `schemaVersion` meaningful — a file from a future
 * version cannot be silently read as if it were this one. The fields with a default are the
 * documented exceptions listed on [BackupFile], and they exist so older formats stay readable.
 */
internal val BackupJsonFormat: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    explicitNulls = true
    ignoreUnknownKeys = false
}
