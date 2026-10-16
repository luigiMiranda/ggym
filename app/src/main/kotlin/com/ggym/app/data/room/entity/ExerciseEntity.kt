package com.ggym.app.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The global exercise dictionary: one row per exercise the user has ever named.
 *
 * `name` is unique case-insensitively (`NOCASE`), which is what stops "bench press" and
 * "Bench Press" from becoming two exercises with two histories. The unique index inherits the
 * column's collation, so `UNIQUE` means the same thing as the exact-match lookup.
 */
@Entity(
    tableName = "exercise",
    indices = [Index(value = ["name"], unique = true)],
)
internal data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** `null` until a workout or the routine editor writes the first value. */
    val currentWorkingWeight: Double?,
    val defaultRestSeconds: Int,
)
