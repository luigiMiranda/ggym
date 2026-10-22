package com.ggym.app.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ggym.app.domain.model.Exercise

/**
 * One exercise inside a saved workout, including the ones the user skipped.
 *
 * `exerciseId` is nullable and `SET_NULL` on delete: training history outlives the dictionary
 * entry it came from, and `exerciseNameSnapshot` keeps the line readable once the id is gone.
 * Compare `planned_exercise.exerciseId`, which is `RESTRICT` — a routine slot without its
 * exercise is broken, a logged workout without it is merely historical.
 *
 * `restSeconds` is the rest time to use between this exercise's sets, snapshotted when the workout
 * starts: a slot edited on another screen must not change the rest of a set completed ten minutes
 * ago, and a resumed draft needs no lookup into the routine. The default is the dictionary's own
 * default rest time, which is what the pre-draft rows meant.
 *
 * `targetReps` is the written target snapshotted with it, for the same reason: the workout must
 * keep showing the plan it started with even after the slot is edited, and the target is free text
 * ("8-10", "AMRAP") that the sets' integer reps cannot reconstruct. The blank default is what a
 * row written before the column existed means — a workout with no target to show.
 *
 * `plannedExerciseId` is the slot this log was materialized from, so a completion can move *that
 * day's* weight as well as the dictionary's. It is a **soft** pointer on purpose — no foreign key:
 * it is a breadcrumb read while a draft runs, and an FK would add an import-ordering constraint
 * (and a cascade decision) for a value that only ever needs to be read once. `null` is a workout
 * with no slot behind it: history written before the column existed, a restored draft from an
 * older backup, or a slot that was deleted while the workout ran. Declared last, so the v4 → v5
 * migration's `ALTER TABLE` and a fresh install put the column in the same place.
 */
@Entity(
    tableName = "workout_exercise_log",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["sessionId"]), Index(value = ["exerciseId"])],
)
internal data class WorkoutExerciseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long?,
    val exerciseNameSnapshot: String,
    val wasCompleted: Boolean,
    val orderIndex: Int,
    @ColumnInfo(defaultValue = "90") val restSeconds: Int = Exercise.DEFAULT_REST_SECONDS,
    @ColumnInfo(defaultValue = "") val targetReps: String = "",
    val plannedExerciseId: Long? = null,
)
