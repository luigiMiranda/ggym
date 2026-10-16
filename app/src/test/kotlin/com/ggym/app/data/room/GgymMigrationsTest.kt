package com.ggym.app.data.room

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.testing.SchemaSnapshots
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The v1 → v2 migration, run against a database built from the committed v1 schema.
 *
 * The interesting claim is not that `ALTER TABLE` works; it is that a database a user already has
 * opens in this build with nothing lost — dictionary, routines and training history all come back —
 * and that the new column starts out empty, which is exactly what "no weight chosen here" meant in
 * v1. Room validates the migrated schema against the entity definitions on the way in, so a column
 * of the wrong type or nullability would fail this test too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class GgymMigrationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a version 1 database migrates to version 2 keeping every row, with no slot weight yet`() = runTest {
        val name = "ggym-migration-${System.nanoTime()}.db"
        createVersion1Database(name)

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

            // The new column is writable, and writing it leaves the global records alone.
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

    /**
     * Writes a real database file at version 1: the shipped v1 DDL, a realistic set of rows, and a
     * `user_version` of 1, which is what Room reads before it decides to migrate.
     */
    private fun createVersion1Database(name: String) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version = 1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            SchemaSnapshots.statements(version = 1).forEach { statement -> db.execSQL(statement) }
                            seedVersion1Rows(db)
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
}
