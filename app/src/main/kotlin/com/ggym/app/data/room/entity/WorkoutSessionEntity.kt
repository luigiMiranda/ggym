package com.ggym.app.data.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved workout.
 *
 * `routineNameSnapshot` and `dayNameSnapshot` are copies, not references: a routine is deleted or
 * renamed far more often than a workout is, and neither event may rewrite what happened. The
 * training history therefore has no foreign key pointing at the routine side of the schema at
 * all.
 *
 * `dateTimestamp` is epoch millis at local midnight: a workout belongs to a day, so calendar
 * bucketing stays exact and independent of the time it was saved.
 */
@Entity(
    tableName = "workout_session",
    indices = [Index(value = ["dateTimestamp"])],
)
internal data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTimestamp: Long,
    val routineNameSnapshot: String,
    val dayNameSnapshot: String,
    val durationSeconds: Long,
)
