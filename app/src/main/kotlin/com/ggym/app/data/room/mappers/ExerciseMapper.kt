package com.ggym.app.data.room.mappers

import com.ggym.app.data.room.entity.ExerciseEntity
import com.ggym.app.domain.model.Exercise

/**
 * Entities never leave the data layer; this is the boundary that makes that true (see
 * docs/ARCHITECTURE.md, "Module and package layout").
 */
internal object ExerciseMapper {

    fun toDomain(entity: ExerciseEntity): Exercise = Exercise(
        id = entity.id,
        name = entity.name,
        currentWorkingWeight = entity.currentWorkingWeight,
        defaultRestSeconds = entity.defaultRestSeconds,
    )

    fun toEntity(exercise: Exercise): ExerciseEntity = ExerciseEntity(
        id = exercise.id,
        name = exercise.name,
        currentWorkingWeight = exercise.currentWorkingWeight,
        defaultRestSeconds = exercise.defaultRestSeconds,
    )
}
