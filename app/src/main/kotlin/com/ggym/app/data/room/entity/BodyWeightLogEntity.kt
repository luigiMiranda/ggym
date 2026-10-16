package com.ggym.app.data.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A body-weight measurement.
 *
 * `dateTimestamp` is epoch millis at local midnight like a workout's, so the trend chart and the
 * "latest" readout agree with the calendar about which day a measurement belongs to. Several
 * rows may share a day (weighing in twice is not an error); display decides which one to show.
 */
@Entity(
    tableName = "body_weight_log",
    indices = [Index(value = ["dateTimestamp"])],
)
internal data class BodyWeightLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTimestamp: Long,
    val weightKg: Double,
)
