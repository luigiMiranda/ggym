package com.ggym.app.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ggym.app.data.room.dao.BodyWeightDao
import com.ggym.app.data.room.dao.ExerciseDao
import com.ggym.app.data.room.dao.ExerciseProgressDao
import com.ggym.app.data.room.dao.PlannedExerciseDao
import com.ggym.app.data.room.dao.RoutineDao
import com.ggym.app.data.room.dao.RoutineDayDao
import com.ggym.app.data.room.dao.WorkoutHistoryDao
import com.ggym.app.data.room.entity.BodyWeightLogEntity
import com.ggym.app.data.room.entity.ExerciseEntity
import com.ggym.app.data.room.entity.PlannedExerciseEntity
import com.ggym.app.data.room.entity.RoutineDayEntity
import com.ggym.app.data.room.entity.RoutineEntity
import com.ggym.app.data.room.entity.WorkoutExerciseLogEntity
import com.ggym.app.data.room.entity.WorkoutSessionEntity
import com.ggym.app.data.room.entity.WorkoutSetLogEntity

/**
 * The schema version of the database.
 *
 * This is Room's/SQLite's version, **not** the backup format version: the two move together only
 * by coincidence. `BACKUP_FORMAT_VERSION` in `data/backup` is the file format's own number, and a
 * file written by an older app version stays readable after the schema has moved on.
 *
 * Bumping this means writing a [GgymMigrations] entry and committing the newly exported
 * `app/schemas/…/<version>.json`.
 */
internal const val SCHEMA_VERSION = 2

/**
 * The app's single database: the dictionary, routines and all training history.
 *
 * Two rules hold here:
 *
 * 1. **Migrations are explicit.** [GgymMigrations] is where they are registered, and
 *    `fallbackToDestructiveMigration` is never called: the fallback would silently delete a
 *    user's training history, which is the one thing this app must not lose.
 * 2. **The schema is exported and committed** under `app/schemas/`, so a future migration can be
 *    verified against the schema version that actually shipped.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineDayEntity::class,
        PlannedExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutExerciseLogEntity::class,
        WorkoutSetLogEntity::class,
        BodyWeightLogEntity::class,
    ],
    version = SCHEMA_VERSION,
    exportSchema = true,
)
internal abstract class GgymDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao

    abstract fun routineDao(): RoutineDao

    abstract fun routineDayDao(): RoutineDayDao

    abstract fun plannedExerciseDao(): PlannedExerciseDao

    abstract fun workoutHistoryDao(): WorkoutHistoryDao

    abstract fun exerciseProgressDao(): ExerciseProgressDao

    abstract fun bodyWeightDao(): BodyWeightDao
}

/**
 * Builds the database the app runs on.
 *
 * This exists so the app and its tests construct the database the same way — including the
 * migration list — rather than one of them quietly testing a path the other never takes.
 */
internal fun createGgymDatabase(
    context: Context,
    name: String = GGYM_DATABASE_NAME,
): GgymDatabase = Room.databaseBuilder(context, GgymDatabase::class.java, name)
    .addMigrations(*GgymMigrations.ALL)
    .build()

internal const val GGYM_DATABASE_NAME = "ggym.db"
