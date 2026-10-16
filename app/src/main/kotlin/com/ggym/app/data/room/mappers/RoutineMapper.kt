package com.ggym.app.data.room.mappers

import com.ggym.app.data.room.dao.RoutineDayWithPlannedExercises
import com.ggym.app.data.room.dao.RoutineWithDays
import com.ggym.app.data.room.entity.PlannedExerciseEntity
import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.domain.model.Routine
import com.ggym.app.domain.model.RoutineDay

/**
 * Turns the query rows into the routine tree.
 *
 * Ordering is applied here rather than in SQL because `@Relation` gives no guarantee about the
 * order of the children it loads: `orderIndex` is what the user arranged, and ties fall back to
 * the id so the result is stable even if two rows briefly share an index.
 */
internal object RoutineMapper {

    fun toDomain(row: RoutineWithDays): Routine = Routine(
        id = row.routine.id,
        name = row.routine.name,
        createdAt = row.routine.createdAt,
        days = row.days
            .sortedWith(compareBy({ it.day.orderIndex }, { it.day.id }))
            .map { toDomain(it) },
    )

    fun toDomain(row: RoutineDayWithPlannedExercises): RoutineDay = RoutineDay(
        id = row.day.id,
        routineId = row.day.routineId,
        dayName = row.day.dayName,
        orderIndex = row.day.orderIndex,
        plannedExercises = row.plannedExercises
            .sortedWith(compareBy({ it.orderIndex }, { it.id }))
            .map { toDomain(it) },
    )

    fun toDomain(entity: PlannedExerciseEntity): PlannedExercise = PlannedExercise(
        id = entity.id,
        routineDayId = entity.routineDayId,
        exerciseId = entity.exerciseId,
        targetSets = entity.targetSets,
        targetReps = entity.targetReps,
        restSeconds = entity.restSeconds,
        orderIndex = entity.orderIndex,
    )

    fun toEntity(plannedExercise: PlannedExercise): PlannedExerciseEntity = PlannedExerciseEntity(
        id = plannedExercise.id,
        routineDayId = plannedExercise.routineDayId,
        exerciseId = plannedExercise.exerciseId,
        targetSets = plannedExercise.targetSets,
        targetReps = plannedExercise.targetReps,
        restSeconds = plannedExercise.restSeconds,
        orderIndex = plannedExercise.orderIndex,
    )
}
