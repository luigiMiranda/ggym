package com.ggym.app.data.backup

import com.ggym.app.testing.version1BackupJson
import com.ggym.app.testing.version2BackupJson
import com.ggym.app.testing.version3BackupJson
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
        listOf("\"dayName\"", "\"exerciseId\"", "\"targetReps\"", "\"setNumber\"", "\"weightKg\"", "\"isDraft\"", "\"restSeconds\"")
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
        assertOldSessionsAreReadAsFinished(file)
    }

    @Test
    fun `a format 2 document is read with its slot weight - workouts in progress did not exist yet`() {
        val file = BackupJsonFormat.decodeFromString(BackupFile.serializer(), version2BackupJson())

        assertEquals(2, file.schemaVersion)
        assertEquals("the slot weight is what format 2 was for", 80.0, file.routines.single().days.single().exercises.single().weightKg!!, 0.0)
        assertOldSessionsAreReadAsFinished(file)
    }

    @Test
    fun `a workout in progress is written with its flag and its start time`() {
        val draft = sampleFile().sessions.single().copy(
            id = 2,
            isDraft = true,
            startedAtMillis = 1_759_500_000_000,
        )

        val json = BackupJsonFormat.encodeToString(BackupFile.serializer(), sampleFile().copy(sessions = listOf(draft)))
        val decoded = BackupJsonFormat.decodeFromString(BackupFile.serializer(), json).sessions.single()

        assertTrue("the draft flag is written, not implied\n$json", json.contains("\"isDraft\": true"))
        assertEquals(true, decoded.isDraft)
        assertEquals(1_759_500_000_000, decoded.startedAtMillis)
    }

    @Test
    fun `a format 3 document is read with its draft fields and a blank target`() {
        val file = BackupJsonFormat.decodeFromString(BackupFile.serializer(), version3BackupJson())

        assertEquals(3, file.schemaVersion)
        assertEquals("format 3 could describe a workout in progress", false, file.sessions.single().isDraft)
        assertNull("and finished workouts carried no start time", file.sessions.single().startedAtMillis)
        assertEquals("the rest time was snapshotted by then", 120, file.sessions.single().exercises.single().restSeconds)
        assertEquals(
            "but the target was not: there is nothing to show, and the blank says so",
            "",
            file.sessions.single().exercises.single().targetReps,
        )
    }

    /**
     * A file written before format 3 describes finished workouts that never recorded a start time,
     * and exercises whose rest time was not snapshotted yet.
     */
    private fun assertOldSessionsAreReadAsFinished(file: BackupFile) {
        val session = file.sessions.single()
        assertEquals("nothing was in progress before format 3", false, session.isDraft)
        assertNull("and nothing recorded a start time", session.startedAtMillis)
        assertEquals(
            "the rest time those rows meant is the dictionary's default",
            90,
            session.exercises.single().restSeconds,
        )
        assertEquals(
            "no workout had a target to show before format 4",
            "",
            session.exercises.single().targetReps,
        )
    }

    @Test
    fun `a workout's target reps are written and read back`() {
        val draft = sampleFile().sessions.single().copy(
            id = 2,
            isDraft = true,
            startedAtMillis = 1_759_500_000_000,
            exercises = listOf(
                sampleFile().sessions.single().exercises.single().copy(targetReps = "AMRAP"),
            ),
        )

        val json = BackupJsonFormat.encodeToString(BackupFile.serializer(), sampleFile().copy(sessions = listOf(draft)))
        val decoded = BackupJsonFormat.decodeFromString(BackupFile.serializer(), json).sessions.single()

        assertTrue("the target is written, not implied\n$json", json.contains("\"targetReps\": \"AMRAP\""))
        assertEquals("the free text is kept as typed", "AMRAP", decoded.exercises.single().targetReps)
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
