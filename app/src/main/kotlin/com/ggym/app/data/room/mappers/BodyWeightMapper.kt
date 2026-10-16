package com.ggym.app.data.room.mappers

import com.ggym.app.data.room.entity.BodyWeightLogEntity
import com.ggym.app.domain.model.BodyWeightLog
import java.time.ZoneId

internal object BodyWeightMapper {

    fun toDomain(entity: BodyWeightLogEntity, zone: ZoneId): BodyWeightLog = BodyWeightLog(
        id = entity.id,
        date = entity.dateTimestamp.toLocalDate(zone),
        weightKg = entity.weightKg,
    )

    fun toEntity(entry: BodyWeightLog, zone: ZoneId): BodyWeightLogEntity = BodyWeightLogEntity(
        id = entry.id,
        dateTimestamp = entry.date.toLocalMidnightMillis(zone),
        weightKg = entry.weightKg,
    )
}
