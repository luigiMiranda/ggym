package com.ggym.app.data.backup

import com.ggym.app.testing.version1BackupJson
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
 * nulls, refusing anything that does not match, and still reading a format 1 file.
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
    fun `a slot weight of null is written explicitly`() {
        val file = sampleFile().withFirstSlotWeight(null)

        val json = BackupJsonFormat.encodeToString(BackupFile.serializer(), file)

        assertTrue("expected an explicit null, got\n$json", json.contains("\"weightKg\": null"))
        assertNull(
            BackupJsonFormat.decodeFromString(BackupFile.serializer(), json)
                .routines.single().days.single().exercises.single().weightKg,
        )
    }

    @Test
    fun `each slot keeps its own weight`() {
        val file = sampleFile().withTwoDaysAt(80.0, 70.0)

        val json = BackupJsonFormat.encodeToString(BackupFile.serializer(), file)
        val decoded = BackupJsonFormat.decodeFromString(BackupFile.serializer(), json)

        assertEquals(
            listOf(80.0, 70.0),
            decoded.routines.single().days.map { it.exercises.single().weightKg },
        )
    }

    @Test
    fun `a format 1 document is read with no slot weights - that field did not exist`() {
        val file = BackupJsonFormat.decodeFromString(BackupFile.serializer(), version1BackupJson())

        assertEquals(1, file.schemaVersion)
        assertNull("v1 slots inherit, they do not carry a weight", file.routines.single().days.single().exercises.single().weightKg)
        assertEquals("the dictionary is untouched by the format change", 87.5, file.exercises.single().currentWorkingWeight!!, 0.0)
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

    /** The sample's one slot, replanned at [weight] — the shape a format 2 file adds. */
    private fun BackupFile.withFirstSlotWeight(weight: Double?): BackupFile {
        val routine = routines.single()
        val day = routine.days.single()
        return copy(
            routines = listOf(
                routine.copy(days = listOf(day.copy(exercises = listOf(day.exercises.single().copy(weightKg = weight))))),
            ),
        )
    }

    /** The sample's routine with the same slot planned twice, at two different weights. */
    private fun BackupFile.withTwoDaysAt(firstWeight: Double?, secondWeight: Double?): BackupFile {
        val routine = routines.single()
        val day = routine.days.single()
        val slot = day.exercises.single()
        return copy(
            routines = listOf(
                routine.copy(
                    days = listOf(
                        day.copy(exercises = listOf(slot.copy(weightKg = firstWeight))),
                        day.copy(
                            id = 2,
                            dayName = "Push B",
                            orderIndex = 1,
                            exercises = listOf(slot.copy(id = 2, weightKg = secondWeight)),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun sampleFile() = BackupFile(
        schemaVersion = BACKUP_FORMAT_VERSION,
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
                                weightKg = 80.0,
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
