package com.ggym.app.data.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One performed set.
 *
 * `weight` and `reps` are what actually happened, not what was planned — the charts read these
 * rows and nothing else. A `weight` of `0.0` is meaningful (bodyweight work) and `isCompleted`
 * distinguishes a set that was performed from one that was only pre-filled.
 */
@Entity(
    tableName = "workout_set_log",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseLogId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workoutExerciseLogId"])],
)
internal data class WorkoutSetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseLogId: Long,
    val setNumber: Int,
    val weight: Double,
    val reps: Int,
    val isCompleted: Boolean,
)
