package com.ggym.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.data.repository.RoomBodyWeightRepository
import com.ggym.app.data.repository.RoomExerciseRepository
import com.ggym.app.data.repository.RoomHistoryRepository
import com.ggym.app.data.repository.RoomRoutineRepository
import com.ggym.app.data.room.GgymDatabase
import com.ggym.app.data.room.SCHEMA_VERSION
import com.ggym.app.domain.model.BodyWeightLog
import com.ggym.app.domain.model.Exercise
import com.ggym.app.domain.model.LastCompletedSet
import com.ggym.app.domain.model.Routine
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.testing.TestData
import com.ggym.app.testing.countRows
import com.ggym.app.testing.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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

        assertEquals(ImportResult.Restored(BackupCounts(exercises = 3, routines = 2, sessions = 2, bodyWeights = 2)), result)
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

        assertEquals(SCHEMA_VERSION, document.schemaVersion)
        assertEquals("0.1.0", document.appVersion)
        assertEquals(exportTime, document.exportedAt)
        assertEquals(3, document.exercises.size)
        assertEquals(2, document.routines.size)
        assertEquals(2, document.sessions.size)
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
    fun `a document from a newer version of the app is refused and changes nothing`() = runTest {
        populate()
        val before = snapshot()

        val result = backupManager.importJson(jsonOf(readDocument(backupManager.exportJson()).copy(schemaVersion = SCHEMA_VERSION + 1)))

        assertEquals(
            ImportResult.Failed(ImportError.UnsupportedVersion(found = SCHEMA_VERSION + 1, supported = SCHEMA_VERSION)),
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
        routineRepository.addPlannedExercise(pushA, bench.id, targetSets = 4, targetReps = "8-10", restSeconds = 120)
        routineRepository.addPlannedExercise(pushA, fly.id, targetSets = 3, targetReps = "12", restSeconds = 60)
        routineRepository.addPlannedExercise(pushB, bench.id, targetSets = 5, targetReps = "5", restSeconds = 180)
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
    }

    /** Reads everything the app can show, through the repositories, as one comparable value. */
    private suspend fun snapshot(): GymSnapshot {
        val exercises = exerciseRepository.observeAll().first()
        return GymSnapshot(
            exercises = exercises,
            routines = routineRepository.observeRoutines().first(),
            sessions = historyRepository.observeSessionsInRange(FIRST_DAY, LAST_DAY).first(),
            bodyWeights = bodyWeightRepository.observeRange(FIRST_DAY, LAST_DAY).first(),
            lastSets = exercises.associate { exercise -> exercise.id to historyRepository.latestCompletedSet(exercise.id) },
        )
    }

    /** The "reinstalled app" case: an empty file is a legitimate backup, and a restore replaces. */
    private suspend fun emptyEverything() {
        assertEquals(ImportResult.Restored(BackupCounts(0, 0, 0, 0)), backupManager.importJson(emptyBackupJson()))
    }

    private fun emptyBackupJson(): String = jsonOf(
        BackupFile(
            schemaVersion = SCHEMA_VERSION,
            exportedAt = exportTime,
            appVersion = "0.1.0",
            exercises = emptyList(),
            routines = emptyList(),
            sessions = emptyList(),
            bodyWeights = emptyList(),
        ),
    )

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
    val lastSets: Map<Long, LastCompletedSet?> = emptyMap(),
)
