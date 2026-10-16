package com.ggym.app.data.room

import androidx.room.migration.Migration

/**
 * Every schema migration, in order, and the only place one may be registered.
 *
 * The list is empty because version [SCHEMA_VERSION] is the first version that shipped: there is
 * no older data to migrate from. It stays as an empty, referenced list rather than being added
 * later for two reasons — it pins down *where* a migration goes, and it makes the rule visible
 * that a new one must be written by hand. `fallbackToDestructiveMigration` is deliberately absent
 * from [createGgymDatabase]: recovering from a schema change by deleting the user's training
 * history is not a recovery.
 *
 * When adding the first migration:
 *
 * 1. write the `Migration` and append it here,
 * 2. bump [SCHEMA_VERSION],
 * 3. commit the new `app/schemas/…/<version>.json` that the build exports,
 * 4. extend the migration test to open the *previous* exported schema and migrate it forward.
 */
internal object GgymMigrations {
    val ALL: Array<Migration> = emptyArray()
}
