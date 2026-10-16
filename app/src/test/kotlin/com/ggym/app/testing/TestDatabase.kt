package com.ggym.app.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.data.room.GgymDatabase

/**
 * An in-memory database with the real schema, for tests.
 *
 * Built through [Room.inMemoryDatabaseBuilder] rather than [com.ggym.app.data.room.createGgymDatabase]
 * so each test starts empty and nothing is written to the Robolectric sandbox's disk. The schema
 * is the same one the app ships — the DAOs, entities and foreign keys are the ones under test.
 */
internal fun createTestDatabase(): GgymDatabase = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext<Context>(),
    GgymDatabase::class.java,
).build()

/**
 * How many rows a table actually holds.
 *
 * Cascade deletes are a property of the schema, not of a query, and a tree loaded through
 * `@Relation` would hide an orphan row whose parent is gone. Counting the table is the only
 * honest way to assert that a delete took its children with it.
 */
internal fun GgymDatabase.countRows(table: String): Int =
    openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
