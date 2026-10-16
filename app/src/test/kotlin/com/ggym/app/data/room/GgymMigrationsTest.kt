package com.ggym.app.data.room

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.testing.SchemaSnapshots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The migrations, run against databases built from the committed schema snapshots.
 *
 * The interesting claim is not that `ALTER TABLE` works; it is that a database a user already has
 * opens in this build with nothing lost — dictionary, routines and training history all come back —
 * and that a new column starts out meaning exactly what it meant before it existed. Room validates
 * the migrated schema against the entity definitions on the way in, so a column of the wrong type,
 * nullability or default fails these tests too.
 *
 * Every version a user could be on gets its own test: v1 is someone who installed the first build
 * and never updated, v2 someone who has been training since the routine editor shipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class GgymMigrationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a version 1 database migrates to version 3 keeping every row, with no slot weight yet`() = runTest {
        val name = "ggym-migration-v1-${System.nanoTime()}.db"
        createDatabase(name, version = 1, seed = ::seedVersion1Rows)

        val database = createGgymDatabase(context, name)
        try {
            assertEquals(
                "the database now reports the version this build installs",
                SCHEMA_VERSION,
                database.openHelper.readableDatabase.version,
            )

            val bench = database.exerciseDao().getAll().single()
            assertEquals("the dictionary survived", 87.5, bench.currentWorkingWeight!!, 0.0)

            val slot = database.routineDao().getAllRoutinesWithDays()
                .single().days.single().plannedExercises.single()
            assertEquals("the slot is the one that was there", "8-10", slot.targetReps)
            assertEquals(4, slot.targetSets)
            assertEquals(120, slot.restSeconds)
            assertNull("a v1 slot has no weight of its own", slot.weightKg)

            val session = database.workoutHistoryDao().getAllSessions().single()
            assertEquals("the session survived", "Push A", session.session.dayNameSnapshot)
            assertEquals("its sets survived", 82.5, session.exercises.single().sets.single().weight, 0.0)
            assertEquals(82.4, database.bodyWeightDao().getAll().single().weightKg, 0.0)

            assertDefaultsOfTheNewColumns(session.session.isDraft, session.session.startedAtMillis, session.exercises.single().log.restSeconds)

            // The new columns are writable, and writing them leaves the global records alone.
            database.plannedExerciseDao().update(slot.copy(weightKg = 80.0))

            val migrated = database.routineDao().getAllRoutinesWithDays()
                .single().days.single().plannedExercises.single()
            assertEquals(80.0, migrated.weightKg!!, 0.0)
            assertEquals(
                "a slot weight is not a dictionary write",
                87.5,
                database.exerciseDao().getAll().single().currentWorkingWeight!!,
                0.0,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `a version 2 database migrates to version 3 with its slot weight and history intact`() = runTest {
        val name = "ggym-migration-v2-${System.nanoTime()}.db"
        createDatabase(name, version = 2, seed = ::seedVersion2Rows)

        val database = createGgymDatabase(context, name)
        try {
            assertEquals(SCHEMA_VERSION, database.openHelper.readableDatabase.version)

            val slot = database.routineDao().getAllRoutinesWithDays()
                .single().days.single().plannedExercises.single()
            assertEquals("the per-slot weight is a v2 fact and must survive v3", 80.0, slot.weightKg!!, 0.0)
            assertEquals(120, slot.restSeconds)
            assertEquals(87.5, database.exerciseDao().getAll().single().currentWorkingWeight!!, 0.0)

            val stored = database.workoutHistoryDao().getAllSessions().single()
            assertEquals("the session survived", "Push A", stored.session.dayNameSnapshot)
            assertEquals("its sets survived", 82.5, stored.exercises.single().sets.single().weight, 0.0)
            assertEquals(82.4, database.bodyWeightDao().getAll().single().weightKg, 0.0)

            assertDefaultsOfTheNewColumns(
                isDraft = stored.session.isDraft,
                startedAtMillis = stored.session.startedAtMillis,
                restSeconds = stored.exercises.single().log.restSeconds,
            )

            // The new columns are writable: a workout in progress can be written to a migrated
            // database, which is the whole point of v3.
            val draftId = database.workoutHistoryDao().insertSession(
                stored.session.copy(id = 0, isDraft = true, startedAtMillis = 1_759_000_000_000),
            )
            val logId = database.workoutHistoryDao().insertExerciseLog(
                stored.exercises.single().log.copy(id = 0, sessionId = draftId, restSeconds = 150),
            )
            database.workoutHistoryDao().insertSet(
                stored.exercises.single().sets.single().copy(id = 0, workoutExerciseLogId = logId, isCompleted = false),
            )

            val draft = database.workoutHistoryDao().observeDraft().first()
            assertNotNull("the draft is readable through the v3 query", draft)
            assertEquals(1_759_000_000_000, draft!!.session.startedAtMillis)
            assertEquals(150, draft.exercises.single().log.restSeconds)

            // A draft is not history: the v2 rows stay the only workout the calendar sees.
            assertEquals(
                listOf(stored.session.id),
                database.workoutHistoryDao()
                    .observeSessionsInRange(0, Long.MAX_VALUE)
                    .first()
                    .map { it.session.id },
            )
        } finally {
            database.close()
        }
    }

    /** What an existing row must mean after v3: not a draft, no start time, the default rest time. */
    private fun assertDefaultsOfTheNewColumns(isDraft: Boolean, startedAtMillis: Long?, restSeconds: Int) {
        assertEquals("nothing was a draft before drafts existed", false, isDraft)
        assertNull("no old session recorded when it started", startedAtMillis)
        assertEquals("the rest time those rows meant", 90, restSeconds)
    }

    /**
     * Writes a real database file at [version]: the committed DDL of that version, a realistic set
     * of rows, and that `user_version`, which is what Room reads before it decides to migrate.
     */
    private fun createDatabase(name: String, version: Int, seed: (SupportSQLiteDatabase) -> Unit) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version = version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            SchemaSnapshots.statements(version).forEach { statement -> db.execSQL(statement) }
                            seed(db)
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        // Touching the database runs `onCreate`; closing it leaves the file behind for Room to open.
        helper.writableDatabase
        helper.close()
    }

    private fun seedVersion1Rows(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO exercise (name, currentWorkingWeight, defaultRestSeconds) VALUES ('Bench Press', 87.5, 90)")
        db.execSQL("INSERT INTO routine (name, createdAt) VALUES ('Push Day', 1750000000000)")
        db.execSQL("INSERT INTO routine_day (routineId, dayName, orderIndex) VALUES (1, 'Push A', 0)")
        db.execSQL(
            "INSERT INTO planned_exercise (routineDayId, exerciseId, targetSets, targetReps, restSeconds, orderIndex) " +
                "VALUES (1, 1, 4, '8-10', 120, 0)",
        )
        db.execSQL(
            "INSERT INTO workout_session (dateTimestamp, routineNameSnapshot, dayNameSnapshot, durationSeconds) " +
                "VALUES (1759000000000, 'Push Day', 'Push A', 3120)",
        )
        db.execSQL(
            "INSERT INTO workout_exercise_log (sessionId, exerciseId, exerciseNameSnapshot, wasCompleted, orderIndex) " +
                "VALUES (1, 1, 'Bench Press', 1, 0)",
        )
        db.execSQL(
            "INSERT INTO workout_set_log (workoutExerciseLogId, setNumber, weight, reps, isCompleted) " +
                "VALUES (1, 1, 82.5, 10, 1)",
        )
        db.execSQL("INSERT INTO body_weight_log (dateTimestamp, weightKg) VALUES (1759000000000, 82.4)")
    }

    /** The same database as a v2 user has it: the slot carries the weight the routine editor wrote. */
    private fun seedVersion2Rows(db: SupportSQLiteDatabase) {
        seedVersion1Rows(db)
        db.execSQL("UPDATE planned_exercise SET weightKg = 80.0 WHERE id = 1")
    }
}
