package com.ggym.app.data.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An exercise slot inside a training day.
 *
 * The foreign keys encode the two different kinds of loss: the day cascades (a slot without its
 * day is meaningless), while the dictionary entry is `RESTRICT`ed, so deleting an exercise that a
 * routine still plans fails loudly instead of quietly emptying the slot. See
 * docs/ARCHITECTURE.md, "Foreign-key policy".
 */
@Entity(
    tableName = "planned_exercise",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineDayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["routineDayId", "orderIndex"]), Index(value = ["exerciseId"])],
)
internal data class PlannedExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineDayId: Long,
    val exerciseId: Long,
    val targetSets: Int,
    /** Written as a coach writes it: "8-10", "10", "AMRAP". */
    val targetReps: String,
    val restSeconds: Int,
    val orderIndex: Int,
)
