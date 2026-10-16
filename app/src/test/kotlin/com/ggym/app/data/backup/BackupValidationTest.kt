package com.ggym.app.data.backup

import com.ggym.app.data.room.SCHEMA_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The validation step, on its own.
 *
 * It is a pure function over the parsed file, so every "what if the file is wrong" case can be
 * checked without a database — which is the point of validating before writing anything.
 */
class BackupValidationTest {

    @Test
    fun `a well-formed file passes`() {
        assertNull(BackupValidator.validate(validBackup()))
    }

    @Test
    fun `an empty file passes - restoring it is how a user starts over`() {
        val empty = validBackup().copy(exercises = emptyList(), routines = emptyList(), sessions = emptyList(), bodyWeights = emptyList())

        assertNull(BackupValidator.validate(empty))
    }

    @Test
    fun `a file from a newer version is refused before anything else is checked`() {
        val file = validBackup().copy(schemaVersion = SCHEMA_VERSION + 1, exercises = listOf(blankNamedExercise()))

        val error = BackupValidator.validate(file)

        assertTrue("expected an unsupported version, got $error", error is ImportError.UnsupportedVersion)
        assertEquals(SCHEMA_VERSION + 1, (error as ImportError.UnsupportedVersion).found)
        assertEquals(SCHEMA_VERSION, error.supported)
    }

    @Test
    fun `a planned exercise that no dictionary entry backs is refused`() {
        val file = validBackup().copy(
            routines = listOf(
                validRoutine().copy(
                    days = listOf(validDay().copy(exercises = listOf(validPlannedExercise().copy(exerciseId = 99)))),
                ),
            ),
        )

        assertTrue(BackupValidator.validate(file) is ImportError.Inconsistent)
    }

    @Test
    fun `a workout exercise that no dictionary entry backs is refused`() {
        val file = validBackup().copy(
            sessions = listOf(
                validSession().copy(exercises = listOf(validLog().copy(exerciseId = 42))),
            ),
        )

        assertTrue(BackupValidator.validate(file) is ImportError.Inconsistent)
    }

    @Test
    fun `a detached workout exercise - a deleted dictionary entry - is fine`() {
        val file = validBackup().copy(
            sessions = listOf(validSession().copy(exercises = listOf(validLog().copy(exerciseId = null)))),
        )

        assertNull(BackupValidator.validate(file))
    }

    @Test
    fun `two rows of the same table sharing an id are refused`() {
        val file = validBackup().copy(
            sessions = listOf(validSession().copy(id = 1), validSession().copy(id = 1)),
        )

        assertTrue(BackupValidator.validate(file) is ImportError.Inconsistent)
    }

    @Test
    fun `two rows in different tables may share an id - ids are per table`() {
        val file = validBackup().copy(
            exercises = listOf(validExercise().copy(id = 7)),
            routines = listOf(
                validRoutine().copy(
                    id = 7,
                    days = listOf(validDay().copy(id = 7, exercises = listOf(validPlannedExercise().copy(id = 7, exerciseId = 7)))),
                ),
            ),
            sessions = listOf(
                validSession().copy(
                    id = 7,
                    exercises = listOf(validLog().copy(id = 7, exerciseId = 7, sets = listOf(validSet().copy(id = 7)))),
                ),
            ),
            bodyWeights = listOf(validBodyWeight().copy(id = 7)),
        )

        assertNull(BackupValidator.validate(file))
    }

    @Test
    fun `two nested rows of the same kind sharing an id are refused`() {
        val file = validBackup().copy(
            routines = listOf(
                validRoutine().copy(
                    days = listOf(validDay().copy(id = 5, exercises = emptyList()), validDay().copy(id = 5, exercises = emptyList())),
                ),
            ),
        )

        assertTrue(BackupValidator.validate(file) is ImportError.Inconsistent)
    }

    @Test
    fun `body weights outside the human range are refused`() {
        val outOfRange = listOf(0.0, 24.9, 25.0, 300.0, 300.1, 500.0, Double.NaN)

        outOfRange.forEach { weight ->
            val file = validBackup().copy(bodyWeights = listOf(validBodyWeight().copy(weightKg = weight)))
            assertTrue("$weight kg should be refused", BackupValidator.validate(file) is ImportError.Inconsistent)
        }
    }

    @Test
    fun `a plausible body weight passes`() {
        listOf(25.1, 82.4, 299.9).forEach { weight ->
            val file = validBackup().copy(bodyWeights = listOf(validBodyWeight().copy(weightKg = weight)))
            assertNull("$weight kg should be accepted", BackupValidator.validate(file))
        }
    }

    @Test
    fun `a set may weigh nothing - bodyweight work - but not something negative or impossible`() {
        val bodyweight = validBackup().copy(
            sessions = listOf(validSession().copy(exercises = listOf(validLog().copy(sets = listOf(validSet().copy(weight = 0.0))))))
        )

        assertNull(BackupValidator.validate(bodyweight))

        listOf(-0.5, Double.POSITIVE_INFINITY, Double.NaN).forEach { weight ->
            val file = validBackup().copy(
                sessions = listOf(validSession().copy(exercises = listOf(validLog().copy(sets = listOf(validSet().copy(weight = weight))))))
            )
            assertTrue("$weight should be refused", BackupValidator.validate(file) is ImportError.Inconsistent)
        }
    }

    @Test
    fun `names that are missing are refused`() {
        assertTrue(BackupValidator.validate(validBackup().copy(exercises = listOf(blankNamedExercise()))) is ImportError.Inconsistent)
    }

    @Test
    fun `snapshots in the history cannot be blank - they are the only record of what was done`() {
        val file = validBackup().copy(
            sessions = listOf(validSession().copy(routineNameSnapshot = "  ")),
        )

        assertTrue(BackupValidator.validate(file) is ImportError.Inconsistent)
    }

    @Test
    fun `counters and ordering cannot be negative`() {
        val cases = listOf(
            validBackup().copy(exercises = listOf(validExercise().copy(defaultRestSeconds = -1))),
            validBackup().copy(routines = listOf(validRoutine().copy(createdAt = -1))),
            validBackup().copy(
                routines = listOf(
                    validRoutine().copy(days = listOf(validDay().copy(exercises = listOf(validPlannedExercise().copy(targetSets = -1))))),
                ),
            ),
            validBackup().copy(
                routines = listOf(
                    validRoutine().copy(days = listOf(validDay().copy(exercises = listOf(validPlannedExercise().copy(orderIndex = -1))))),
                ),
            ),
            validBackup().copy(
                sessions = listOf(validSession().copy(durationSeconds = -1)),
            ),
            validBackup().copy(
                sessions = listOf(validSession().copy(exercises = listOf(validLog().copy(sets = listOf(validSet().copy(setNumber = 0))))))
            ),
            validBackup().copy(
                sessions = listOf(validSession().copy(exercises = listOf(validLog().copy(sets = listOf(validSet().copy(reps = -1))))))
            ),
        )

        cases.forEach { file ->
            assertTrue("expected $file to be refused", BackupValidator.validate(file) is ImportError.Inconsistent)
        }
    }

    @Test
    fun `an incomplete set is valid - the user may not have performed it`() {
        val file = validBackup().copy(
            sessions = listOf(
                validSession().copy(
                    exercises = listOf(
                        validLog().copy(
                            wasCompleted = false,
                            sets = listOf(validSet().copy(isCompleted = false)),
                        ),
                    ),
                ),
            ),
        )

        assertNull(BackupValidator.validate(file))
    }
}

private fun validBackup() = BackupFile(
    schemaVersion = SCHEMA_VERSION,
    exportedAt = 1_760_000_000_000,
    appVersion = "0.1.0",
    exercises = listOf(validExercise()),
    routines = listOf(validRoutine()),
    sessions = listOf(validSession()),
    bodyWeights = listOf(validBodyWeight()),
)

private fun validExercise() = BackupExercise(
    id = 1,
    name = "Bench Press",
    currentWorkingWeight = 82.5,
    defaultRestSeconds = 90,
)

private fun blankNamedExercise() = validExercise().copy(name = "  ")

private fun validRoutine() = BackupRoutine(
    id = 1,
    name = "Push Day",
    createdAt = 1_750_000_000_000,
    days = listOf(validDay()),
)

private fun validDay() = BackupRoutineDay(
    id = 1,
    dayName = "Push",
    orderIndex = 0,
    exercises = listOf(validPlannedExercise()),
)

private fun validPlannedExercise() = BackupPlannedExercise(
    id = 1,
    exerciseId = 1,
    targetSets = 4,
    targetReps = "8-10",
    restSeconds = 90,
    orderIndex = 0,
)

private fun validSession() = BackupSession(
    id = 1,
    dateTimestamp = 1_759_000_000_000,
    routineNameSnapshot = "Push Day",
    dayNameSnapshot = "Push",
    durationSeconds = 3120,
    exercises = listOf(validLog()),
)

private fun validLog() = BackupWorkoutExerciseLog(
    id = 1,
    exerciseId = 1,
    exerciseNameSnapshot = "Bench Press",
    wasCompleted = true,
    orderIndex = 0,
    sets = listOf(validSet()),
)

private fun validSet() = BackupWorkoutSetLog(
    id = 1,
    setNumber = 1,
    weight = 82.5,
    reps = 10,
    isCompleted = true,
)

private fun validBodyWeight() = BackupBodyWeight(
    id = 1,
    dateTimestamp = 1_759_000_000_000,
    weightKg = 82.4,
)
