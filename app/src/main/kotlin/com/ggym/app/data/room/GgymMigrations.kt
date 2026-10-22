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

    /**
     * v2 → v3: a workout can be written while it is still running.
     *
     * The active-workout milestone turns a workout from "something saved when it ends" into a tree
     * that exists from the moment it starts, so a process death cannot lose an hour of training.
     * That takes three columns:
     *
     * - `workout_session.isDraft` — `0` for every existing row, which is exactly what those rows
     *   are: finished workouts, written before a draft could exist.
     * - `workout_session.startedAtMillis` — nullable, because before v3 nothing recorded when a
     *   workout began, and inventing a start time for a finished one would be a lie. Only drafts
     *   require it.
     * - `workout_exercise_log.restSeconds` — the rest time snapshotted per exercise, so a resumed
     *   workout and the timer after a completed set do not have to ask the routine again. `90` is
     *   the dictionary's own default rest time, which is what those rows meant.
     *
     * `ALTER TABLE … ADD COLUMN` again, with the defaults the entity declares: no row is rewritten
     * and a fresh install creates byte-identical DDL.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE workout_session ADD COLUMN isDraft INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE workout_session ADD COLUMN startedAtMillis INTEGER")
            db.execSQL("ALTER TABLE workout_exercise_log ADD COLUMN restSeconds INTEGER NOT NULL DEFAULT 90")
        }
    }

    /**
     * v3 → v4: a workout that started from a plan keeps the plan's target reps, so the screen can
     * show the target next to the actual as the user enters it.
     *
     * The target is not derivable from what was already stored: each set snapshots the first number
     * in the target (`"8-10"` means eight) as an `Int`, so `Target 4 × 8-10` would come back as `4`
     * and `AMRAP` as `0`. It has to be snapshotted like `restSeconds` beside it, and for the same
     * reason — the target the workout started with must not move when the plan is edited.
     *
     * The blank default is what every existing row means: a workout with no target to show, which
     * the screen renders with the copy it used before. `ALTER TABLE … ADD COLUMN` again; no row is
     * rewritten and a fresh install creates the same DDL from the entity's own default.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE workout_exercise_log ADD COLUMN targetReps TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * v4 → v5: a workout records the routine slot it was materialized from, so a completion can
     * move the weight of the day that actually trained the exercise.
     *
     * The pointer is nullable and has no default in SQL beyond SQLite's own `NULL`: a row written
     * before the column existed has no slot to point at, which is exactly what `NULL` means here,
     * and a front-loaded, no-FK column means the migration rewrites no data. A draft restored from
     * a format-1–4 backup gets the same `null`, and its completions simply write the dictionary
     * only, as they did before this version.
     *
     * `ALTER TABLE … ADD COLUMN` again; no row is rewritten and a fresh install creates the same DDL
     * from the entity's own `null` default.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE workout_exercise_log ADD COLUMN plannedExerciseId INTEGER")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
