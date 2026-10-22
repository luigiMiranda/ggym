package com.ggym.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.data.repository.RoomBodyWeightRepository
import com.ggym.app.data.repository.RoomExerciseRepository
import com.ggym.app.data.repository.RoomHistoryRepository
import com.ggym.app.data.repository.RoomRoutineRepository
import com.ggym.app.data.room.GgymDatabase
import com.ggym.app.domain.model.BodyWeightLog
import com.ggym.app.domain.model.Exercise
import com.ggym.app.domain.model.LastCompletedSet
import com.ggym.app.domain.model.Routine
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.testing.TestData
import com.ggym.app.testing.countRows
import com.ggym.app.testing.createTestDatabase
import com.ggym.app.testing.version1BackupJson
import com.ggym.app.testing.version2BackupJson
import com.ggym.app.testing.version3BackupJson
import com.ggym.app.testing.version4BackupJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * Export, wipe, import, compare — the whole point of M1's backup layer.
 *
 * The comparison is a deep equality of what the repositories hand out, not of the JSON: the file
 * is an implementation of a promise ("restoring your phone gives you your data back"), and the
 * promise is about the data. Everything else here is about the ways a restore can be asked to do
 * something it should refuse, and the guarantee that a refusal changes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class BackupRoundTripTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var database: GgymDatabase
    private lateinit var backupManager: BackupManager

    private val exerciseTime = 1_750_000_000_000L
    private val exportTime = 1_760_000_000_000L

    private val exerciseRepository by lazy { RoomExerciseRepository(database) }
    private val routineRepository by lazy { RoomRoutineRepository(database, now = { exerciseTime }) }
    private val historyRepository by lazy { RoomHistoryRepository(database, TestData.zone) }
    private val bodyWeightRepository by lazy { RoomBodyWeightRepository(database, TestData.zone) }

    @Before
    fun setUp() {
        database = createTestDatabase()
        backupManager = BackupManager(
            database = database,
            contentResolver = context.contentResolver,
            appVersion = "0.1.0",
            now = { exportTime },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an exported database comes back identical after being emptied and restored`() = runTest {
        populate()
        val before = snapshot()
        assertEquals("the fixture must actually contain something", 3, before.exercises.size)
        assertEquals(2, before.routines.size)
        assertEquals(2, before.sessions.size)

        val document = backupManager.exportJson()
        emptyEverything()
        assertEquals("the wipe is a restore of an empty file", GymSnapshot(), snapshot())

        val result = backupManager.importJson(document)

        assertEquals(ImportResult.Restored(BackupCounts(exercises = 3, routines = 2, sessions = 3, bodyWeights = 2)), result)
        assertEquals(before, snapshot())
    }

    @Test
    fun `a restored database exports the same document it was restored from`() = runTest {
        populate()

        val firstExport = backupManager.exportJson()
        emptyEverything()
        backupManager.importJson(firstExport)
        val secondExport = backupManager.exportJson()

        assertEquals(
            "only the moment of export may differ",
            readDocument(firstExport).copy(exportedAt = 0),
            readDocument(secondExport).copy(exportedAt = 0),
        )
    }

    @Test
    fun `the file says which app wrote it, and when`() = runTest {
        populate()

        val document = readDocument(backupManager.exportJson())

        assertEquals(BACKUP_FORMAT_VERSION, document.schemaVersion)
        assertEquals("0.1.0", document.appVersion)
        assertEquals(exportTime, document.exportedAt)
        assertEquals(3, document.exercises.size)
        assertEquals(2, document.routines.size)
        assertEquals(3, document.sessions.size)
        assertEquals(2, document.bodyWeights.size)
    }

    @Test
    fun `the dictionary and the routines keep their identity, ids included`() = runTest {
        populate()
        val before = snapshot()

        val document = backupManager.exportJson()
        emptyEverything()
        backupManager.importJson(document)
        val after = snapshot()

        assertEquals(before.exercises.map { it.id }, after.exercises.map { it.id })
        assertEquals(before.routines.map { it.id }, after.routines.map { it.id })
        assertEquals(before.sessions.map { it.id }, after.sessions.map { it.id })
        assertEquals(
            "a routine's whole tree keeps its shape",
            before.routines.map { routine -> routine.days.map { it.id } },
            after.routines.map { routine -> routine.days.map { it.id } },
        )
    }

    @Test
    fun `a workout whose dictionary entry is gone is restored as a detached log`() = runTest {
        populate()

        val document = backupManager.exportJson()
        emptyEverything()
        backupManager.importJson(document)

        val detached = snapshot().sessions
            .flatMap { it.exercises }
            .single { it.exerciseNameSnapshot == "Machine Chest Press" }

        assertNull("the id stays detached", detached.exerciseId)
        assertEquals("the name is still there to read", "Machine Chest Press", detached.exerciseNameSnapshot)
        assertEquals(false, detached.wasCompleted)
    }

    @Test
    fun `an empty backup empties the database - the user's way to start over`() = runTest {
        populate()

        val result = backupManager.importJson(emptyBackupJson())

        assertEquals(ImportResult.Restored(BackupCounts(0, 0, 0, 0)), result)
        assertEquals(GymSnapshot(), snapshot())
        listOf(
            "exercise",
            "routine",
            "routine_day",
            "planned_exercise",
            "workout_session",
            "workout_exercise_log",
            "workout_set_log",
            "body_weight_log",
        ).forEach { table -> assertEquals("$table should be empty", 0, database.countRows(table)) }
    }

    @Test
    fun `a document that is not a backup is refused and changes nothing`() = runTest {
        populate()
        val before = snapshot()

        assertTrue(backupManager.importJson("this is not JSON") is ImportResult.Failed)
        assertTrue(backupManager.importJson("{ }") is ImportResult.Failed)
        assertTrue(backupManager.importJson("""{"schemaVersion": 1}""") is ImportResult.Failed)

        assertEquals(before, snapshot())
    }

    @Test
    fun `two slots of one exercise keep their own weight, and the dictionary and history stay global`() = runTest {
        populate()

        val before = snapshot()
        val bench = before.exercises.single { it.name == "Bench Press" }
        assertEquals(
            "the fixture is only meaningful if the three weights differ",
            listOf(80.0, 70.0),
            plannedWeights(before, bench.id),
        )
        assertEquals(87.5, bench.currentWorkingWeight!!, 0.0)
        assertEquals(85.0, before.lastSets.getValue(bench.id)!!.weightKg, 0.0)

        backupManager.importJson(backupManager.exportJson())

        val after = snapshot()
        assertEquals("each slot is restored at its own weight", listOf(80.0, 70.0), plannedWeights(after, bench.id))
        assertEquals(
            "a restore does not collapse them into one number",
            87.5,
            after.exercises.single { it.name == "Bench Press" }.currentWorkingWeight!!,
            0.0,
        )
        assertEquals(
            "the trained set is still the last thing performed",
            85.0,
            after.lastSets.getValue(bench.id)!!.weightKg,
            0.0,
        )
    }

    @Test
    fun `a format 1 document is restored, with its slots inheriting instead of carrying a weight`() = runTest {
        populate()

        val result = backupManager.importJson(version1BackupJson())

        assertEquals(ImportResult.Restored(BackupCounts(exercises = 1, routines = 1, sessions = 1, bodyWeights = 1)), result)
        val restored = snapshot()
        val slot = restored.routines.single().days.single().plannedExercises.single()
        assertNull("format 1 had no per-slot weight", slot.weightKg)
        assertEquals("the dictionary weight is the global one the file carried", 87.5, restored.exercises.single().currentWorkingWeight!!, 0.0)
        assertEquals("the history came back with its set", 82.5, restored.lastSets.getValue(restored.exercises.single().id)!!.weightKg, 0.0)
    }

    @Test
    fun `a workout in progress is exported, restored, and still not history`() = runTest {
        populate()
        val before = snapshot()
        assertNotNull("the fixture must contain a workout in progress", before.draft)
        assertTrue(before.draft!!.isDraft)

        val document = readDocument(backupManager.exportJson())
        assertEquals("a draft is in the file — restoring on a new phone resumes the workout", 1, document.sessions.count { it.isDraft })

        emptyEverything()
        assertNull(snapshot().draft)

        backupManager.importJson(jsonOf(document))

        val after = snapshot()
        assertEquals("the workout comes back set by set, ids included", before.draft, after.draft)
        assertEquals("and it is still not on the calendar", before.sessions, after.sessions)
        assertEquals("nor is a set inside it what I lifted last", before.lastSets, after.lastSets)
    }

    @Test
    fun `a format 2 document is restored with its slot weight and its history arriving finished`() = runTest {
        populate()

        val result = backupManager.importJson(version2BackupJson())

        assertEquals(ImportResult.Restored(BackupCounts(exercises = 1, routines = 1, sessions = 1, bodyWeights = 1)), result)
        val restored = snapshot()
        assertEquals(
            "the per-slot weight is what format 2 was for",
            80.0,
            restored.routines.single().days.single().plannedExercises.single().weightKg!!,
            0.0,
        )
        assertEquals("nothing was in progress in format 2", false, restored.sessions.single().isDraft)
        assertNull("and nothing recorded a start time that could be invented", restored.sessions.single().startedAtMillis)
        assertEquals("the rest time arrives as the default those rows meant", 90, restored.sessions.single().exercises.single().restSeconds)
        assertEquals(
            "and no target, because format 2 never wrote one",
            "",
            restored.sessions.single().exercises.single().targetReps,
        )
        assertNull("and no workout is resumed by a file that never had one", restored.draft)
    }

    @Test
    fun `a format 3 document is restored with its snapshotted rest time but a blank target`() = runTest {
        populate()

        val result = backupManager.importJson(version3BackupJson())

        assertEquals(ImportResult.Restored(BackupCounts(exercises = 1, routines = 1, sessions = 1, bodyWeights = 1)), result)
        val restored = snapshot()
        val log = restored.sessions.single().exercises.single()
        assertEquals("the rest time was snapshotted by format 3", 120, log.restSeconds)
        assertEquals("the target was not: the column arrives blank, not invented", "", log.targetReps)
    }

    @Test
    fun `a format 4 document is restored with its snapshotted target but no slot pointer`() = runTest {
        populate()

        val result = backupManager.importJson(version4BackupJson())

        assertEquals(ImportResult.Restored(BackupCounts(exercises = 1, routines = 1, sessions = 1, bodyWeights = 1)), result)
        val log = snapshot().sessions.single().exercises.single()
        assertEquals("format 4 snapshotted the target", "8-10", log.targetReps)
        assertNull(
            "and knew nothing about the slot the workout came from",
            log.plannedExerciseId,
        )
    }

    @Test
    fun `the slot a workout in progress came from survives the round trip`() = runTest {
        populate()
        val before = snapshot()
        val pointer = before.draft!!.exercises.single().plannedExerciseId
        assertNotNull("the fixture must carry a slot pointer — that is what the test is about", pointer)

        val document = readDocument(backupManager.exportJson())
        assertEquals(
            "the pointer is in the file, so a restored running workout can still move its day",
            pointer,
            document.sessions.single { it.isDraft }.exercises.single().plannedExerciseId,
        )

        emptyEverything()
        assertNull("the wipe removes the workout", snapshot().draft)
        backupManager.importJson(jsonOf(document))

        assertEquals(
            "and it comes back on the same slot",
            pointer,
            snapshot().draft!!.exercises.single().plannedExerciseId,
        )
    }

    @Test
    fun `a document from a newer version of the app is refused and changes nothing`() = runTest {
        populate()
        val before = snapshot()

        val result = backupManager.importJson(
            jsonOf(readDocument(backupManager.exportJson()).copy(schemaVersion = BACKUP_FORMAT_VERSION + 1)),
        )

        assertEquals(
            ImportResult.Failed(
                ImportError.UnsupportedVersion(found = BACKUP_FORMAT_VERSION + 1, supported = BACKUP_FORMAT_VERSION),
            ),
            result,
        )
        assertEquals(before, snapshot())
    }

    @Test
    fun `a document that points at an exercise it does not contain is refused and changes nothing`() = runTest {
        populate()
        val before = snapshot()

        val document = readDocument(backupManager.exportJson())
        val orphaned = document.copy(
            routines = document.routines.map { routine ->
                routine.copy(
                    days = routine.days.map { day ->
                        day.copy(exercises = day.exercises.map { planned -> planned.copy(exerciseId = 4242) })
                    },
                )
            },
        )

        val result = backupManager.importJson(jsonOf(orphaned))

        assertTrue("expected an inconsistency, got $result", result is ImportResult.Failed && result.error is ImportError.Inconsistent)
        assertEquals(before, snapshot())
    }

    @Test
    fun `two dictionary entries sharing a name are refused by the database, and nothing changes`() = runTest {
        // The name rule is SQLite's `NOCASE`, which folds ASCII only. Re-implementing it in the
        // validator could reject a file GGym itself wrote (two accented names), so the database
        // has the last word here — and the rollback is what keeps a refusal harmless.
        populate()
        val before = snapshot()

        val document = readDocument(backupManager.exportJson())
        val forked = document.copy(
            exercises = document.exercises.map { exercise ->
                if (exercise.id == document.exercises.last().id) exercise.copy(name = "bench press") else exercise
            },
        )

        val result = backupManager.importJson(jsonOf(forked))

        assertTrue("expected an inconsistency, got $result", result is ImportResult.Failed && result.error is ImportError.Inconsistent)
        assertEquals(before, snapshot())
    }

    @Test
    fun `a document with an impossible body weight is refused and changes nothing`() = runTest {
        populate()
        val before = snapshot()

        val document = readDocument(backupManager.exportJson())
        val impossible = document.copy(bodyWeights = document.bodyWeights.map { it.copy(weightKg = 500.0) })

        assertTrue(backupManager.importJson(jsonOf(impossible)) is ImportResult.Failed)
        assertEquals(before, snapshot())
    }

    @Test
    fun `a backup is written to a document and can be read back from it`() = runTest {
        populate()
        val before = snapshot()
        val file = File(tempFolder.root, "ggym-backup.json")
        val uri = Uri.fromFile(file)

        assertEquals(ExportResult.Written, backupManager.exportTo(uri))
        assertTrue("the file is human readable", file.readText().contains("\"schemaVersion\""))

        emptyEverything()
        assertEquals(GymSnapshot(), snapshot())

        val result = backupManager.importFrom(uri)

        assertTrue("expected a restore, got $result", result is ImportResult.Restored)
        assertEquals(before, snapshot())
    }

    @Test
    fun `a document that cannot be written reports a failure instead of crashing`() = runTest {
        populate()

        val uri = Uri.fromFile(File(tempFolder.root, "missing-directory/backup.json"))

        assertTrue(backupManager.exportTo(uri) is ExportResult.Failed)
    }

    @Test
    fun `a document that is not there reports a failure instead of crashing`() = runTest {
        populate()
        val before = snapshot()

        val result = backupManager.importFrom(Uri.fromFile(File(tempFolder.root, "not-there.json")))

        assertTrue("expected an unreadable file, got $result", result is ImportResult.Failed && result.error is ImportError.Unreadable)
        assertEquals(before, snapshot())
    }

    @Test
    fun `importing twice is not additive - the second import replaces the first`() = runTest {
        populate()
        val first = backupManager.exportJson()
        val before = snapshot()

        backupManager.importJson(first)
        backupManager.importJson(first)

        assertEquals("no duplicated rows", before, snapshot())
        assertEquals(3, database.countRows("exercise"))
    }

    /** A complete, realistic database: two routines, saved history, body weights, and a detached log. */
    private suspend fun populate() {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        exerciseRepository.updateCurrentWorkingWeight(bench.id, 87.5)
        val fly = exerciseRepository.findOrCreate("Cable Fly")
        exerciseRepository.findOrCreate("Squat", defaultRestSeconds = 150)

        val pushId = routineRepository.createRoutine("Push Day")
        val pushA = routineRepository.addDay(pushId, "Push A")
        val pushB = routineRepository.addDay(pushId, "Push B")
        val benchSlot = routineRepository.addPlannedExercise(pushA, bench.id, targetSets = 4, targetReps = "8-10", restSeconds = 120, weightKg = 80.0)
        routineRepository.addPlannedExercise(pushA, fly.id, targetSets = 3, targetReps = "12", restSeconds = 60, weightKg = 20.0)
        routineRepository.addPlannedExercise(pushB, bench.id, targetSets = 5, targetReps = "5", restSeconds = 180, weightKg = 70.0)
        routineRepository.createRoutine("Pull Day")

        historyRepository.saveSession(
            TestData.session(
                date = TestData.date(month = 1, day = 5),
                routineNameSnapshot = "Push Day",
                dayNameSnapshot = "Push A",
                durationSeconds = 3120,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = bench.id,
                        exerciseNameSnapshot = "Bench Press",
                        orderIndex = 0,
                        targetReps = "8-10",
                        plannedExerciseId = benchSlot,
                        sets = listOf(
                            TestData.setLog(setNumber = 1, weight = 82.5, reps = 8),
                            TestData.setLog(setNumber = 2, weight = 85.0, reps = 6),
                        ),
                    ),
                    TestData.exerciseLog(
                        exerciseId = null,
                        exerciseNameSnapshot = "Machine Chest Press",
                        wasCompleted = false,
                        orderIndex = 1,
                        sets = listOf(TestData.setLog(setNumber = 1, weight = 60.0, reps = 10, isCompleted = false)),
                    ),
                ),
            ),
        )
        historyRepository.saveSession(
            TestData.session(
                date = TestData.date(month = 2, day = 17),
                routineNameSnapshot = "Push Day",
                dayNameSnapshot = "Push B",
                durationSeconds = 2400,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = fly.id,
                        exerciseNameSnapshot = "Cable Fly",
                        orderIndex = 0,
                        sets = listOf(TestData.setLog(setNumber = 1, weight = 20.0, reps = 15)),
                    ),
                ),
            ),
        )

        bodyWeightRepository.add(TestData.date(month = 1, day = 5), 82.4)
        bodyWeightRepository.add(TestData.date(month = 2, day = 17), 81.9)

        historyRepository.startDraft(
            TestData.session(
                date = TestData.date(month = 3, day = 2),
                startedAtMillis = 1_759_500_000_000,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = bench.id,
                        exerciseNameSnapshot = "Bench Press",
                        restSeconds = 120,
                        targetReps = "AMRAP",
                        plannedExerciseId = benchSlot,
                        sets = listOf(
                            TestData.setLog(setNumber = 1, weight = 80.0, reps = 8),
                            TestData.setLog(setNumber = 2, weight = 80.0, reps = 8, isCompleted = false),
                        ),
                    ),
                ),
            ),
        )
    }

    /** Reads everything the app can show, through the repositories, as one comparable value. */
    private suspend fun snapshot(): GymSnapshot {
        val exercises = exerciseRepository.observeAll().first()
        return GymSnapshot(
            exercises = exercises,
            routines = routineRepository.observeRoutines().first(),
            sessions = historyRepository.observeSessionsInRange(FIRST_DAY, LAST_DAY).first(),
            bodyWeights = bodyWeightRepository.observeRange(FIRST_DAY, LAST_DAY).first(),
            draft = historyRepository.observeDraft().first(),
            lastSets = exercises.associate { exercise -> exercise.id to historyRepository.latestCompletedSet(exercise.id) },
        )
    }

    /** The "reinstalled app" case: an empty file is a legitimate backup, and a restore replaces. */
    private suspend fun emptyEverything() {
        assertEquals(ImportResult.Restored(BackupCounts(0, 0, 0, 0)), backupManager.importJson(emptyBackupJson()))
    }

    private fun emptyBackupJson(): String = jsonOf(
        BackupFile(
            schemaVersion = BACKUP_FORMAT_VERSION,
            exportedAt = exportTime,
            appVersion = "0.1.0",
            exercises = emptyList(),
            routines = emptyList(),
            sessions = emptyList(),
            bodyWeights = emptyList(),
        ),
    )

    /** Each planned weight of one exercise's slots, in day order. */
    private fun plannedWeights(state: GymSnapshot, exerciseId: Long): List<Double?> = state.routines
        .flatMap { it.days }
        .flatMap { it.plannedExercises }
        .filter { it.exerciseId == exerciseId }
        .map { it.weightKg }

    private fun jsonOf(document: BackupFile): String = BackupJsonFormat.encodeToString(BackupFile.serializer(), document)

    private fun readDocument(json: String): BackupFile = BackupJsonFormat.decodeFromString(BackupFile.serializer(), json)

    private companion object {
        val FIRST_DAY: LocalDate = LocalDate.of(2000, 1, 1)
        val LAST_DAY: LocalDate = LocalDate.of(2100, 1, 1)
    }
}

/** Everything the screens can show, in one value: the state a restore has to reproduce. */
private data class GymSnapshot(
    val exercises: List<Exercise> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val sessions: List<WorkoutSession> = emptyList(),
    val bodyWeights: List<BodyWeightLog> = emptyList(),
    val draft: WorkoutSession? = null,
    val lastSets: Map<Long, LastCompletedSet?> = emptyMap(),
)
