package com.ggym.app.data.backup

import com.ggym.app.data.room.SCHEMA_VERSION
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file format itself.
 *
 * The format is a promise to the user — "you can open this in a text editor" — and a contract
 * with future versions of the app, which is what these checks are about: field names, explicit
 * nulls, and refusing anything that does not match.
 */
class BackupJsonTest {

    @Test
    fun `the document round trips through JSON`() {
        val file = sampleFile()

        val json = BackupJsonFormat.encodeToString(BackupFile.serializer(), file)

        assertEquals(file, BackupJsonFormat.decodeFromString(BackupFile.serializer(), json))
    }

    @Test
    fun `the document is readable, with one array per table`() {
        val json = BackupJsonFormat.encodeToString(BackupFile.serializer(), sampleFile())

        assertTrue("pretty printed", json.contains("\n"))
        listOf("schemaVersion", "exportedAt", "appVersion", "exercises", "routines", "sessions", "bodyWeights")
            .forEach { key -> assertTrue("missing $key in\n$json", json.contains("\"$key\"")) }
        listOf("\"dayName\"", "\"exerciseId\"", "\"targetReps\"", "\"setNumber\"", "\"weightKg\"")
            .forEach { key -> assertTrue("missing $key in\n$json", json.contains(key)) }
    }

    @Test
    fun `a working weight of null is written explicitly`() {
        val file = sampleFile().copy(
            exercises = listOf(BackupExercise(id = 1, name = "Pull Up", currentWorkingWeight = null, defaultRestSeconds = 90)),
        )

        val json = BackupJsonFormat.encodeToString(BackupFile.serializer(), file)

        assertTrue("expected an explicit null, got\n$json", json.contains("\"currentWorkingWeight\": null"))
        assertNull(BackupJsonFormat.decodeFromString(BackupFile.serializer(), json).exercises.single().currentWorkingWeight)
    }

    @Test
    fun `a missing field is not filled in with a default`() {
        val json = BackupJsonFormat.encodeToString(BackupFile.serializer(), sampleFile())
            .replace("\"appVersion\": \"0.1.0\",", "")

        assertFails(json)
    }

    @Test
    fun `a field this version does not know is refused`() {
        val json = BackupJsonFormat.encodeToString(BackupFile.serializer(), sampleFile())
            .replaceFirst("{", "{\n  \"somethingNew\": 1,")

        assertFails(json)
    }

    @Test
    fun `a body weight logged twice on the same day survives the round trip`() {
        val file = sampleFile().copy(
            bodyWeights = listOf(
                BackupBodyWeight(id = 1, dateTimestamp = 1_759_000_000_000, weightKg = 82.4),
                BackupBodyWeight(id = 2, dateTimestamp = 1_759_000_000_000, weightKg = 82.1),
            ),
        )

        val json = BackupJsonFormat.encodeToString(BackupFile.serializer(), file)

        assertEquals(2, BackupJsonFormat.decodeFromString(BackupFile.serializer(), json).bodyWeights.size)
    }

    private fun assertFails(json: String) {
        val result = try {
            BackupJsonFormat.decodeFromString(BackupFile.serializer(), json)
            null
        } catch (e: SerializationException) {
            e
        }

        assertNotNull("expected the document to be refused, but it was read as $result", result)
    }

    private fun sampleFile() = BackupFile(
        schemaVersion = SCHEMA_VERSION,
        exportedAt = 1_760_000_000_000,
        appVersion = "0.1.0",
        exercises = listOf(
            BackupExercise(id = 1, name = "Bench Press", currentWorkingWeight = 82.5, defaultRestSeconds = 90),
        ),
        routines = listOf(
            BackupRoutine(
                id = 1,
                name = "Push Day",
                createdAt = 1_750_000_000_000,
                days = listOf(
                    BackupRoutineDay(
                        id = 1,
                        dayName = "Push",
                        orderIndex = 0,
                        exercises = listOf(
                            BackupPlannedExercise(
                                id = 1,
                                exerciseId = 1,
                                targetSets = 4,
                                targetReps = "8-10",
                                restSeconds = 90,
                                orderIndex = 0,
                            ),
                        ),
                    ),
                ),
            ),
        ),
        sessions = listOf(
            BackupSession(
                id = 1,
                dateTimestamp = 1_759_000_000_000,
                routineNameSnapshot = "Push Day",
                dayNameSnapshot = "Push",
                durationSeconds = 3120,
                exercises = listOf(
                    BackupWorkoutExerciseLog(
                        id = 1,
                        exerciseId = 1,
                        exerciseNameSnapshot = "Bench Press",
                        wasCompleted = true,
                        orderIndex = 0,
                        sets = listOf(
                            BackupWorkoutSetLog(id = 1, setNumber = 1, weight = 82.5, reps = 10, isCompleted = true),
                        ),
                    ),
                ),
            ),
        ),
        bodyWeights = listOf(
            BackupBodyWeight(id = 1, dateTimestamp = 1_759_000_000_000, weightKg = 82.4),
        ),
    )
}
