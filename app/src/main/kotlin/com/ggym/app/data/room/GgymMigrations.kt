package com.ggym.app.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema migration, in order, and the only place one may be registered.
 *
 * The rule this file exists to enforce: `fallbackToDestructiveMigration` is deliberately absent
 * from [createGgymDatabase], because recovering from a schema change by deleting the user's
 * training history is not a recovery.
 *
 * When adding a migration:
 *
 * 1. write the `Migration` and append it here,
 * 2. bump [SCHEMA_VERSION],
 * 3. commit the new `app/schemas/…/<version>.json` that the build exports,
 * 4. extend the migration test to open the *previous* exported schema and migrate it forward.
 */
internal object GgymMigrations {

    /**
     * v1 → v2: a routine slot can carry its own working weight.
     *
     * v1 made the weight a property of the exercise alone, so two slots of the same exercise were
     * forced to show the same number. The new column is nullable and has no default on purpose:
     * every existing slot keeps meaning "no weight chosen here", which is exactly what it meant in
     * v1, and the dictionary's `currentWorkingWeight` remains the global fallback. The migration
     * therefore adds a column and rewrites no data.
     *
     * `ALTER TABLE … ADD COLUMN` is the honest way to say that in SQLite: rebuilding the table
     * would risk the rows for a change SQLite performs natively.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE planned_exercise ADD COLUMN weightKg REAL")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
