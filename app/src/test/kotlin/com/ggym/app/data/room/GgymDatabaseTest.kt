package com.ggym.app.data.room

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.testing.SchemaSnapshots
import com.ggym.app.testing.TestData
import com.ggym.app.testing.givenExercise
import com.ggym.app.testing.givenSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The database the app actually builds — through `Room.databaseBuilder`, on disk, with the
 * migration list — and the exported schema that is committed next to the source.
 *
 * The point of opening the real builder rather than an in-memory one is that this is the only test
 * that can check the promise the schema snapshot makes: that `app/schemas/…/<version>.json`
 * describes the schema a shipped build will create.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class GgymDatabaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the app's database opens through the real builder and round trips a row`() {
        val database = createGgymDatabase(context, name = "ggym-builder-${System.nanoTime()}.db")

        runTest {
            database.givenExercise(name = "Bench Press", currentWorkingWeight = 82.5)

            val stored = database.exerciseDao().getAll().single()
            assertEquals("Bench Press", stored.name)
            assertEquals(82.5, stored.currentWorkingWeight!!, 0.0)
        }

        database.close()
    }

    @Test
    fun `the exported schema describes the database this build creates`() {
        val schemaFile = SchemaSnapshots.file(SCHEMA_VERSION)

        assertTrue(
            "the schema snapshot must be committed at app/schemas/${SCHEMA_VERSION}.json: ${schemaFile.absolutePath}",
            schemaFile.isFile,
        )

        val schema = Json.parseToJsonElement(schemaFile.readText())
            .jsonObject.getValue("database")
            .jsonObject

        assertEquals(SCHEMA_VERSION.toLong(), schema.getValue("version").jsonPrimitive.long)
        val exportedHash = schema.getValue("identityHash").jsonPrimitive.content
        val exportedTables = schema.getValue("entities").jsonArray
            .map { it.jsonObject.getValue("tableName").jsonPrimitive.content }
            .sorted()

        assertEquals(
            listOf(
                "body_weight_log",
                "exercise",
                "planned_exercise",
                "routine",
                "routine_day",
                "workout_exercise_log",
                "workout_session",
                "workout_set_log",
            ),
            exportedTables,
        )

        val database = createGgymDatabase(context, name = "ggym-schema-${System.nanoTime()}.db")
        val installedHash = database.openHelper.readableDatabase
            .query("SELECT identity_hash FROM room_master_table")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        database.close()

        assertEquals("the committed snapshot must match the schema this build installs", exportedHash, installedHash)
    }

    @Test
    fun `the migration list is the explicit one the database builder was given`() {
        assertEquals(
            "one hand-written step per schema version",
            listOf(1 to 2, 2 to 3, 3 to 4),
            GgymMigrations.ALL.map { it.startVersion to it.endVersion },
        )
        assertEquals(
            "the list must reach the version this build installs",
            SCHEMA_VERSION,
            GgymMigrations.ALL.last().endVersion,
        )
    }

    @Test
    fun `dates are stored as local midnight, so a session belongs to its day`() {
        val database = createGgymDatabase(context, name = "ggym-dates-${System.nanoTime()}.db")

        runTest {
            database.givenSession(date = TestData.date(day = 5))

            val stored = database.workoutHistoryDao().getAllSessions().single().session
            assertEquals(TestData.timestamp(TestData.date(day = 5)), stored.dateTimestamp)
        }

        database.close()
    }
}
