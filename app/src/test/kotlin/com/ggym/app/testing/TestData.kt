package com.ggym.app.testing

import com.ggym.app.data.room.GgymDatabase
import com.ggym.app.data.room.entity.BodyWeightLogEntity
import com.ggym.app.data.room.entity.ExerciseEntity
import com.ggym.app.data.room.entity.PlannedExerciseEntity
import com.ggym.app.data.room.entity.RoutineDayEntity
import com.ggym.app.data.room.entity.RoutineEntity
import com.ggym.app.data.room.entity.WorkoutExerciseLogEntity
import com.ggym.app.data.room.entity.WorkoutSessionEntity
import com.ggym.app.data.room.entity.WorkoutSetLogEntity
import com.ggym.app.domain.model.BodyWeightLog
import com.ggym.app.domain.model.Exercise
import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.domain.model.Routine
import com.ggym.app.domain.model.RoutineDay
import com.ggym.app.domain.model.WorkoutExerciseLog
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.model.WorkoutSetLog
import java.time.LocalDate

/**
 * Fixtures, so a test reads like training data instead of like SQL.
 *
 * Everything is pinned: [zone] is UTC and every date is explicit, because a suite that depends on
 * today's date or on the machine's timezone fails once a year in a different timezone. Callers
 * that need a difference of a few days ask for two explicit dates.
 */
internal object TestData {

    val zone: java.time.ZoneId = java.time.ZoneId.of("UTC")

    /** A Thursday, so a test that happens to care about weekdays is at least predictable. */
    fun date(year: Int = 2026, month: Int = 1, day: Int = 1): LocalDate = LocalDate.of(year, month, day)

    /** The stored form of a date: local midnight. */
    fun timestamp(date: LocalDate = date(), zoneId: java.time.ZoneId = zone): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun exercise(
        id: Long = 0,
        name: String = "Bench Press",
        currentWorkingWeight: Double? = 82.5,
        defaultRestSeconds: Int = 90,
    ): Exercise = Exercise(
        id = id,
        name = name,
        currentWorkingWeight = currentWorkingWeight,
        defaultRestSeconds = defaultRestSeconds,
    )

    fun routine(
        id: Long = 0,
        name: String = "Push Day",
        createdAt: Long = 1_750_000_000_000,
        days: List<RoutineDay> = emptyList(),
    ): Routine = Routine(id = id, name = name, createdAt = createdAt, days = days)

    fun routineDay(
        id: Long = 0,
        routineId: Long = 1,
        dayName: String = "Push",
        orderIndex: Int = 0,
        plannedExercises: List<PlannedExercise> = emptyList(),
    ): RoutineDay = RoutineDay(
        id = id,
        routineId = routineId,
        dayName = dayName,
        orderIndex = orderIndex,
        plannedExercises = plannedExercises,
    )

    fun plannedExercise(
        id: Long = 0,
        routineDayId: Long = 1,
        exerciseId: Long = 1,
        targetSets: Int = 4,
        targetReps: String = "8-10",
        restSeconds: Int = 90,
        orderIndex: Int = 0,
        weightKg: Double? = null,
    ): PlannedExercise = PlannedExercise(
        id = id,
        routineDayId = routineDayId,
        exerciseId = exerciseId,
        targetSets = targetSets,
        targetReps = targetReps,
        restSeconds = restSeconds,
        orderIndex = orderIndex,
        weightKg = weightKg,
    )

    fun session(
        id: Long = 0,
        date: LocalDate = date(),
        routineNameSnapshot: String = "Push Day",
        dayNameSnapshot: String = "Push",
        durationSeconds: Long = 3120,
        isDraft: Boolean = false,
        startedAtMillis: Long? = null,
        exercises: List<WorkoutExerciseLog> = emptyList(),
    ): WorkoutSession = WorkoutSession(
        id = id,
        date = date,
        routineNameSnapshot = routineNameSnapshot,
        dayNameSnapshot = dayNameSnapshot,
        durationSeconds = durationSeconds,
        isDraft = isDraft,
        startedAtMillis = startedAtMillis,
        exercises = exercises,
    )

    fun exerciseLog(
        id: Long = 0,
        sessionId: Long = 1,
        exerciseId: Long? = 1,
        exerciseNameSnapshot: String = "Bench Press",
        wasCompleted: Boolean = true,
        orderIndex: Int = 0,
        restSeconds: Int = 90,
        targetReps: String = "",
        plannedExerciseId: Long? = null,
        sets: List<WorkoutSetLog> = emptyList(),
    ): WorkoutExerciseLog = WorkoutExerciseLog(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        exerciseNameSnapshot = exerciseNameSnapshot,
        wasCompleted = wasCompleted,
        orderIndex = orderIndex,
        restSeconds = restSeconds,
        targetReps = targetReps,
        plannedExerciseId = plannedExerciseId,
        sets = sets,
    )

    fun setLog(
        id: Long = 0,
        workoutExerciseLogId: Long = 1,
        setNumber: Int = 1,
        weight: Double = 82.5,
        reps: Int = 10,
        isCompleted: Boolean = true,
    ): WorkoutSetLog = WorkoutSetLog(
        id = id,
        workoutExerciseLogId = workoutExerciseLogId,
        setNumber = setNumber,
        weight = weight,
        reps = reps,
        isCompleted = isCompleted,
    )

    fun bodyWeight(
        id: Long = 0,
        date: LocalDate = date(),
        weightKg: Double = 82.4,
    ): BodyWeightLog = BodyWeightLog(id = id, date = date, weightKg = weightKg)
}

/*
 * Row-level inserts. DAO tests use these to state exactly what is in the database, without going
 * through the repositories: when the query is the thing under test, the SQL and the rows should be
 * the only ingredients.
 */

internal suspend fun GgymDatabase.givenExercise(
    name: String = "Bench Press",
    currentWorkingWeight: Double? = 82.5,
    defaultRestSeconds: Int = 90,
): Long = exerciseDao().insert(
    ExerciseEntity(
        name = name,
        currentWorkingWeight = currentWorkingWeight,
        defaultRestSeconds = defaultRestSeconds,
    ),
)

internal suspend fun GgymDatabase.givenRoutine(
    name: String = "Push Day",
    createdAt: Long = 1_750_000_000_000,
): Long = routineDao().insert(RoutineEntity(name = name, createdAt = createdAt))

internal suspend fun GgymDatabase.givenRoutineDay(
    routineId: Long,
    dayName: String = "Push",
    orderIndex: Int = 0,
): Long = routineDayDao().insert(
    RoutineDayEntity(routineId = routineId, dayName = dayName, orderIndex = orderIndex),
)

internal suspend fun GgymDatabase.givenPlannedExercise(
    routineDayId: Long,
    exerciseId: Long,
    targetSets: Int = 4,
    targetReps: String = "8-10",
    restSeconds: Int = 90,
    orderIndex: Int = 0,
    weightKg: Double? = null,
): Long = plannedExerciseDao().insert(
    PlannedExerciseEntity(
        routineDayId = routineDayId,
        exerciseId = exerciseId,
        targetSets = targetSets,
        targetReps = targetReps,
        restSeconds = restSeconds,
        orderIndex = orderIndex,
        weightKg = weightKg,
    ),
)

internal suspend fun GgymDatabase.givenSession(
    date: LocalDate = TestData.date(),
    routineNameSnapshot: String = "Push Day",
    dayNameSnapshot: String = "Push",
    durationSeconds: Long = 3120,
    isDraft: Boolean = false,
    startedAtMillis: Long? = null,
): Long = workoutHistoryDao().insertSession(
    WorkoutSessionEntity(
        dateTimestamp = TestData.timestamp(date),
        routineNameSnapshot = routineNameSnapshot,
        dayNameSnapshot = dayNameSnapshot,
        durationSeconds = durationSeconds,
        isDraft = isDraft,
        startedAtMillis = startedAtMillis,
    ),
)

internal suspend fun GgymDatabase.givenExerciseLog(
    sessionId: Long,
    exerciseId: Long? = null,
    exerciseNameSnapshot: String = "Bench Press",
    wasCompleted: Boolean = true,
    orderIndex: Int = 0,
    restSeconds: Int = 90,
    targetReps: String = "",
    plannedExerciseId: Long? = null,
): Long = workoutHistoryDao().insertExerciseLog(
    WorkoutExerciseLogEntity(
        sessionId = sessionId,
        exerciseId = exerciseId,
        exerciseNameSnapshot = exerciseNameSnapshot,
        wasCompleted = wasCompleted,
        orderIndex = orderIndex,
        restSeconds = restSeconds,
        targetReps = targetReps,
        plannedExerciseId = plannedExerciseId,
    ),
)

internal suspend fun GgymDatabase.givenSet(
    workoutExerciseLogId: Long,
    setNumber: Int = 1,
    weight: Double = 82.5,
    reps: Int = 10,
    isCompleted: Boolean = true,
): Long = workoutHistoryDao().insertSet(
    WorkoutSetLogEntity(
        workoutExerciseLogId = workoutExerciseLogId,
        setNumber = setNumber,
        weight = weight,
        reps = reps,
        isCompleted = isCompleted,
    ),
)

internal suspend fun GgymDatabase.givenBodyWeight(
    date: LocalDate = TestData.date(),
    weightKg: Double = 82.4,
): Long = bodyWeightDao().insert(
    BodyWeightLogEntity(dateTimestamp = TestData.timestamp(date), weightKg = weightKg),
)
