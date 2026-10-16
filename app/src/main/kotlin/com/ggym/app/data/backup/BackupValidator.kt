package com.ggym.app.data.backup

import com.ggym.app.domain.model.BodyWeightLimits

/**
 * Checks a parsed backup file before a single row is written.
 *
 * Validation is separate from parsing on purpose. Parsing answers "is this the shape I expect";
 * this answers "does the content make sense", which is the part that protects the database from a
 * file that is well-formed JSON but describes a world where a planned exercise points at an
 * exercise that does not exist.
 *
 * Only the first problem is reported: the user needs one sentence, and a listing of every
 * inconsistency in a corrupt file helps nobody.
 *
 * The checks are deliberately about *contradictions*, not about product rules. A weight of 0 kg on
 * a set or on a planned slot is legitimate (bodyweight work); a body weight outside the accepted
 * human range is not (see [BodyWeightLimits]); a missing target rep string is a routine-editor
 * question, not a corrupt file.
 *
 * The `schemaVersion` window is [BACKUP_FORMAT_VERSION] down to
 * [OLDEST_READABLE_BACKUP_FORMAT_VERSION]: the app reads the formats it has ever written, not just
 * its own, because a backup file outlives the build that made it.
 */
internal object BackupValidator {

    fun validate(
        file: BackupFile,
        newestReadableVersion: Int = BACKUP_FORMAT_VERSION,
        oldestReadableVersion: Int = OLDEST_READABLE_BACKUP_FORMAT_VERSION,
    ): ImportError? =
        versionProblem(file, oldestReadableVersion, newestReadableVersion)
            ?: valueProblem(file)
            ?: duplicateIdProblem(file)
            ?: referenceProblem(file)

    private fun versionProblem(file: BackupFile, oldestReadable: Int, newestReadable: Int): ImportError? =
        if (file.schemaVersion in oldestReadable..newestReadable) {
            null
        } else {
            // `supported` is the newest version that can be read; a file outside the window is
            // either from the future or too old to be trusted.
            ImportError.UnsupportedVersion(found = file.schemaVersion, supported = newestReadable)
        }

    private fun valueProblem(file: BackupFile): ImportError? {
        file.exercises.forEach { exercise ->
            if (exercise.name.isBlank()) return inconsistent("exercise ${exercise.id} has no name")
            if (exercise.defaultRestSeconds < 0) {
                return inconsistent("exercise ${exercise.id} has a negative rest time")
            }
            exercise.currentWorkingWeight?.let { weight ->
                if (!weight.isFinite() || weight < 0) {
                    return inconsistent("exercise ${exercise.id} has a working weight of $weight")
                }
            }
        }

        file.routines.forEach { routine ->
            if (routine.name.isBlank()) return inconsistent("routine ${routine.id} has no name")
            if (routine.createdAt < 0) return inconsistent("routine ${routine.id} was created before 1970")
            routine.days.forEach { day ->
                if (day.dayName.isBlank()) return inconsistent("routine day ${day.id} has no name")
                if (day.orderIndex < 0) return inconsistent("routine day ${day.id} has a negative order")
                day.exercises.forEach { planned ->
                    if (planned.targetSets < 0) {
                        return inconsistent("planned exercise ${planned.id} has a negative set count")
                    }
                    if (planned.restSeconds < 0) {
                        return inconsistent("planned exercise ${planned.id} has a negative rest time")
                    }
                    if (planned.orderIndex < 0) {
                        return inconsistent("planned exercise ${planned.id} has a negative order")
                    }
                    planned.weightKg?.let { weight ->
                        if (!weight.isFinite() || weight < 0) {
                            return inconsistent("planned exercise ${planned.id} has a weight of $weight")
                        }
                    }
                }
            }
        }

        file.sessions.forEach { session ->
            if (session.dateTimestamp < 0) return inconsistent("session ${session.id} is dated before 1970")
            if (session.routineNameSnapshot.isBlank()) {
                return inconsistent("session ${session.id} has no routine name")
            }
            if (session.dayNameSnapshot.isBlank()) return inconsistent("session ${session.id} has no day name")
            if (session.durationSeconds < 0) return inconsistent("session ${session.id} has a negative duration")
            session.exercises.forEach { log ->
                if (log.exerciseNameSnapshot.isBlank()) {
                    return inconsistent("workout exercise ${log.id} has no name")
                }
                if (log.orderIndex < 0) return inconsistent("workout exercise ${log.id} has a negative order")
                log.sets.forEach { set ->
                    if (set.setNumber < 1) return inconsistent("set ${set.id} has a number below 1")
                    if (!set.weight.isFinite() || set.weight < 0) {
                        return inconsistent("set ${set.id} has a weight of ${set.weight}")
                    }
                    if (set.reps < 0) return inconsistent("set ${set.id} has a negative rep count")
                }
            }
        }

        file.bodyWeights.forEach { entry ->
            if (entry.dateTimestamp < 0) return inconsistent("body weight ${entry.id} is dated before 1970")
            if (!BodyWeightLimits.isValid(entry.weightKg)) {
                return inconsistent("body weight ${entry.id} is outside the accepted human range")
            }
        }

        return null
    }

    /**
     * A row id only has to be unique inside its own table — that is what a primary key means —
     * so duplicates are looked for per table, not across the file. Two rows sharing an id inside
     * one table would silently overwrite each other on insert, which is why they are refused.
     */
    private fun duplicateIdProblem(file: BackupFile): ImportError? {
        fun check(ids: List<Long>, kind: String): ImportError? {
            val seen = mutableSetOf<Long>()
            ids.forEach { id ->
                if (!seen.add(id)) return inconsistent("two $kind rows share id $id")
            }
            return null
        }

        check(file.exercises.map { it.id }, "exercise")?.let { return it }
        check(file.routines.map { it.id }, "routine")?.let { return it }
        check(file.routines.flatMap { routine -> routine.days.map { it.id } }, "routine day")?.let { return it }
        check(
            file.routines.flatMap { routine -> routine.days.flatMap { day -> day.exercises.map { it.id } } },
            "planned exercise",
        )?.let { return it }
        check(file.sessions.map { it.id }, "session")?.let { return it }
        check(file.sessions.flatMap { session -> session.exercises.map { it.id } }, "workout exercise")?.let { return it }
        check(
            file.sessions.flatMap { session -> session.exercises.flatMap { log -> log.sets.map { it.id } } },
            "set",
        )?.let { return it }
        check(file.bodyWeights.map { it.id }, "body weight")?.let { return it }

        return null
    }

    private fun referenceProblem(file: BackupFile): ImportError? {
        val exerciseIds = file.exercises.mapTo(mutableSetOf()) { it.id }

        file.routines.forEach { routine ->
            routine.days.forEach { day ->
                day.exercises.forEach { planned ->
                    if (planned.exerciseId !in exerciseIds) {
                        return inconsistent(
                            "planned exercise ${planned.id} refers to exercise ${planned.exerciseId}, " +
                                "which is not in the file",
                        )
                    }
                }
            }
        }

        file.sessions.forEach { session ->
            session.exercises.forEach { log ->
                val exerciseId = log.exerciseId
                if (exerciseId != null && exerciseId !in exerciseIds) {
                    return inconsistent(
                        "workout exercise ${log.id} refers to exercise $exerciseId, which is not in the file",
                    )
                }
            }
        }

        return null
    }

    private fun inconsistent(detail: String) = ImportError.Inconsistent(detail)
}
