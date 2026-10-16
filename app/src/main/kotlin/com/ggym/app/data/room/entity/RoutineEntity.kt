package com.ggym.app.data.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A workout routine ("scheda"). Days and planned exercises are children and cascade with it. */
@Entity(tableName = "routine")
internal data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)
