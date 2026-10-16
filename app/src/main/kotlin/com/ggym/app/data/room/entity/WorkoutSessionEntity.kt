package com.ggym.app.data.room.entity

import androidx.room.ColumnInfo
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
 *
 * `isDraft` marks a workout that is still running. A draft is the same tree as a saved session,
 * written at start and updated set by set, so a process death cannot lose it; it is excluded from
 * every history read and becomes history only when the workout finishes (as a fresh row — see
 * `WorkoutHistoryRepository`). The old rows are `0` by definition: nothing was a draft before
 * drafts existed.
 *
 * `startedAtMillis` is when the workout began, needed to show and save the elapsed time. It is
 * null on history written before drafts existed, which is why it is the one nullable column here.
 *
 * The two defaults exist so the schema a migration produces is character-for-character the schema
 * a fresh install creates.
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
    @ColumnInfo(defaultValue = "0") val isDraft: Boolean = false,
    val startedAtMillis: Long? = null,
)
