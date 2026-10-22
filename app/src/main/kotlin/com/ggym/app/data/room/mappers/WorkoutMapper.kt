package com.ggym.app.data.room.mappers

import com.ggym.app.data.room.dao.SessionWithExercises
import com.ggym.app.data.room.dao.WorkoutExerciseLogWithSets
import com.ggym.app.data.room.entity.WorkoutExerciseLogEntity
import com.ggym.app.data.room.entity.WorkoutSessionEntity
import com.ggym.app.data.room.entity.WorkoutSetLogEntity
import com.ggym.app.domain.model.WorkoutExerciseLog
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.model.WorkoutSetLog
import java.time.ZoneId

/**
 * Turns stored history into domain models and back.
 *
 * As in [RoutineMapper], ordering is applied here: the exercises of a session follow
 * `orderIndex`, the sets of an exercise follow `setNumber`.
 */
internal object WorkoutMapper {

    fun toDomain(row: SessionWithExercises, zone: ZoneId): WorkoutSession = WorkoutSession(
        id = row.session.id,
        date = row.session.dateTimestamp.toLocalDate(zone),
        routineNameSnapshot = row.session.routineNameSnapshot,
        dayNameSnapshot = row.session.dayNameSnapshot,
        durationSeconds = row.session.durationSeconds,
        isDraft = row.session.isDraft,
        startedAtMillis = row.session.startedAtMillis,
        exercises = row.exercises
            .sortedWith(compareBy({ it.log.orderIndex }, { it.log.id }))
            .map { toDomain(it) },
    )

    fun toDomain(row: WorkoutExerciseLogWithSets): WorkoutExerciseLog = WorkoutExerciseLog(
        id = row.log.id,
        sessionId = row.log.sessionId,
        exerciseId = row.log.exerciseId,
        exerciseNameSnapshot = row.log.exerciseNameSnapshot,
        wasCompleted = row.log.wasCompleted,
        orderIndex = row.log.orderIndex,
        restSeconds = row.log.restSeconds,
        targetReps = row.log.targetReps,
        plannedExerciseId = row.log.plannedExerciseId,
        sets = row.sets
            .sortedWith(compareBy({ it.setNumber }, { it.id }))
            .map { toDomain(it) },
    )

    fun toDomain(entity: WorkoutSetLogEntity): WorkoutSetLog = WorkoutSetLog(
        id = entity.id,
        workoutExerciseLogId = entity.workoutExerciseLogId,
        setNumber = entity.setNumber,
        weight = entity.weight,
        reps = entity.reps,
        isCompleted = entity.isCompleted,
    )

    fun toEntity(session: WorkoutSession, zone: ZoneId): WorkoutSessionEntity = WorkoutSessionEntity(
        id = session.id,
        dateTimestamp = session.date.toLocalMidnightMillis(zone),
        routineNameSnapshot = session.routineNameSnapshot,
        dayNameSnapshot = session.dayNameSnapshot,
        durationSeconds = session.durationSeconds,
        isDraft = session.isDraft,
        startedAtMillis = session.startedAtMillis,
    )

    fun toEntity(log: WorkoutExerciseLog): WorkoutExerciseLogEntity = WorkoutExerciseLogEntity(
        id = log.id,
        sessionId = log.sessionId,
        exerciseId = log.exerciseId,
        exerciseNameSnapshot = log.exerciseNameSnapshot,
        wasCompleted = log.wasCompleted,
        orderIndex = log.orderIndex,
        restSeconds = log.restSeconds,
        targetReps = log.targetReps,
        plannedExerciseId = log.plannedExerciseId,
    )

    fun toEntity(set: WorkoutSetLog): WorkoutSetLogEntity = WorkoutSetLogEntity(
        id = set.id,
        workoutExerciseLogId = set.workoutExerciseLogId,
        setNumber = set.setNumber,
        weight = set.weight,
        reps = set.reps,
        isCompleted = set.isCompleted,
    )
}
