package com.ggym.app.data.room.mappers

import com.ggym.app.data.room.dao.RoutineDayWithPlannedExercises
import com.ggym.app.data.room.dao.RoutineWithDays
import com.ggym.app.data.room.dao.SessionWithExercises
import com.ggym.app.data.room.dao.WorkoutExerciseLogWithSets
import com.ggym.app.data.room.entity.BodyWeightLogEntity
import com.ggym.app.data.room.entity.ExerciseEntity
import com.ggym.app.data.room.entity.PlannedExerciseEntity
import com.ggym.app.data.room.entity.RoutineDayEntity
import com.ggym.app.data.room.entity.RoutineEntity
import com.ggym.app.data.room.entity.WorkoutExerciseLogEntity
import com.ggym.app.data.room.entity.WorkoutSessionEntity
import com.ggym.app.data.room.entity.WorkoutSetLogEntity
import com.ggym.app.domain.model.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Mappers are where two things happen that a SQL query cannot be trusted to do: turning a stored
 * timestamp back into a date, and imposing an order on the rows `@Relation` returns.
 *
 * They are plain functions over plain data classes, so this runs on the JVM with no Android
 * runtime at all.
 */
class MappersTest {

    private val utc = java.time.ZoneId.of("UTC")

    @Test
    fun `exercise mapping survives a round trip`() {
        val exercise = Exercise(
            id = 7,
            name = "Bench Press",
            currentWorkingWeight = 82.5,
            defaultRestSeconds = 120,
        )

        val entity = ExerciseMapper.toEntity(exercise)

        assertEquals(ExerciseEntity(id = 7, name = "Bench Press", currentWorkingWeight = 82.5, defaultRestSeconds = 120), entity)
        assertEquals(exercise, ExerciseMapper.toDomain(entity))
    }

    @Test
    fun `a routine without a working weight keeps its null`() {
        val entity = ExerciseEntity(id = 1, name = "Pull Up", currentWorkingWeight = null, defaultRestSeconds = 90)

        assertNull(ExerciseMapper.toDomain(entity).currentWorkingWeight)
    }

    @Test
    fun `a routine tree is ordered by the index the user arranged`() {
        val row = RoutineWithDays(
            routine = RoutineEntity(id = 1, name = "Push Day", createdAt = 1_750_000_000_000),
            days = listOf(
                routineDayRow(id = 30, orderIndex = 2, name = "Third"),
                routineDayRow(id = 10, orderIndex = 0, name = "First"),
                routineDayRow(id = 20, orderIndex = 1, name = "Second"),
            ),
        )

        val routine = RoutineMapper.toDomain(row)

        assertEquals(listOf("First", "Second", "Third"), routine.days.map { it.dayName })
        assertEquals(listOf(0, 1, 2), routine.days.map { it.orderIndex })
    }

    @Test
    fun `two days sharing an index still come back in a stable order`() {
        val row = RoutineWithDays(
            routine = RoutineEntity(id = 1, name = "Push Day", createdAt = 1),
            days = listOf(
                routineDayRow(id = 9, orderIndex = 0, name = "Later id"),
                routineDayRow(id = 4, orderIndex = 0, name = "Earlier id"),
            ),
        )

        assertEquals(listOf("Earlier id", "Later id"), RoutineMapper.toDomain(row).days.map { it.dayName })
    }

    @Test
    fun `the planned exercises of a day are ordered by their index`() {
        val row = RoutineDayWithPlannedExercises(
            day = RoutineDayEntity(id = 1, routineId = 1, dayName = "Push", orderIndex = 0),
            plannedExercises = listOf(
                plannedRow(id = 3, exerciseId = 30, orderIndex = 1),
                plannedRow(id = 2, exerciseId = 20, orderIndex = 0),
            ),
        )

        val day = RoutineMapper.toDomain(row)

        assertEquals(listOf(20L, 30L), day.plannedExercises.map { it.exerciseId })
        assertEquals(1L, day.id)
        assertEquals(1L, day.routineId)
    }

    @Test
    fun `a planned exercise round trips through its entity`() {
        val planned = RoutineMapper.toDomain(plannedRow(id = 5, exerciseId = 2, orderIndex = 1))

        assertEquals(planned, RoutineMapper.toDomain(RoutineMapper.toEntity(planned)))
    }

    @Test
    fun `a session's stored timestamp becomes the date it was trained on`() {
        val row = SessionWithExercises(
            session = WorkoutSessionEntity(
                id = 1,
                dateTimestamp = LocalDate.of(2026, 3, 8).toLocalMidnightMillis(utc),
                routineNameSnapshot = "Push Day",
                dayNameSnapshot = "Push",
                durationSeconds = 3120,
            ),
            exercises = emptyList(),
        )

        val session = WorkoutMapper.toDomain(row, utc)

        assertEquals(LocalDate.of(2026, 3, 8), session.date)
        assertEquals("Push Day", session.routineNameSnapshot)
        assertEquals(3120L, session.durationSeconds)
    }

    @Test
    fun `a session tree is ordered by exercise index and set number`() {
        val row = SessionWithExercises(
            session = WorkoutSessionEntity(
                id = 1,
                dateTimestamp = LocalDate.of(2026, 1, 5).toLocalMidnightMillis(utc),
                routineNameSnapshot = "Push Day",
                dayNameSnapshot = "Push",
                durationSeconds = 600,
            ),
            exercises = listOf(
                logRow(id = 11, orderIndex = 1, sets = listOf(setRow(id = 1, setNumber = 2, weight = 80.0))),
                logRow(id = 10, orderIndex = 0, sets = listOf(setRow(id = 2, setNumber = 1, weight = 82.5))),
            ),
        )

        val session = WorkoutMapper.toDomain(row, utc)

        assertEquals(listOf(0, 1), session.exercises.map { it.orderIndex })
        assertEquals("the first exercise is the one ordered first", listOf(1), session.exercises[0].sets.map { it.setNumber })
        assertEquals(listOf(2), session.exercises[1].sets.map { it.setNumber })
    }

    @Test
    fun `sets of one exercise are ordered by set number`() {
        val row = WorkoutExerciseLogWithSets(
            log = WorkoutExerciseLogEntity(
                id = 1,
                sessionId = 1,
                exerciseId = 1,
                exerciseNameSnapshot = "Bench Press",
                wasCompleted = true,
                orderIndex = 0,
            ),
            sets = listOf(
                setRow(id = 3, setNumber = 3, weight = 85.0),
                setRow(id = 1, setNumber = 1, weight = 80.0),
                setRow(id = 2, setNumber = 2, weight = 82.5),
            ),
        )

        assertEquals(listOf(1, 2, 3), WorkoutMapper.toDomain(row).sets.map { it.setNumber })
    }

    @Test
    fun `a log whose dictionary entry is gone keeps its snapshot and a null id`() {
        val row = WorkoutExerciseLogWithSets(
            log = WorkoutExerciseLogEntity(
                id = 1,
                sessionId = 1,
                exerciseId = null,
                exerciseNameSnapshot = "Deltoid Raise",
                wasCompleted = false,
                orderIndex = 0,
            ),
            sets = emptyList(),
        )

        val log = WorkoutMapper.toDomain(row)

        assertNull(log.exerciseId)
        assertEquals("Deltoid Raise", log.exerciseNameSnapshot)
        assertEquals(false, log.wasCompleted)
    }

    @Test
    fun `a set round trips through its entity`() {
        val set = WorkoutMapper.toDomain(setRow(id = 4, setNumber = 2, weight = 82.5))

        assertEquals(set, WorkoutMapper.toDomain(WorkoutMapper.toEntity(set)))
    }

    @Test
    fun `a session round trips through its entity`() {
        val session = WorkoutMapper.toDomain(
            SessionWithExercises(
                session = WorkoutSessionEntity(
                    id = 3,
                    dateTimestamp = LocalDate.of(2026, 6, 30).toLocalMidnightMillis(utc),
                    routineNameSnapshot = "Pull Day",
                    dayNameSnapshot = "Pull",
                    durationSeconds = 2700,
                ),
                exercises = emptyList(),
            ),
            utc,
        )

        val entity = WorkoutMapper.toEntity(session, utc)

        assertEquals(session, WorkoutMapper.toDomain(SessionWithExercises(session = entity, exercises = emptyList()), utc))
    }

    @Test
    fun `a body weight round trips through its entity`() {
        val entry = com.ggym.app.domain.model.BodyWeightLog(id = 2, date = LocalDate.of(2026, 2, 14), weightKg = 81.3)

        val entity = BodyWeightMapper.toEntity(entry, utc)

        assertEquals(BodyWeightLogEntity(id = 2, dateTimestamp = LocalDate.of(2026, 2, 14).toLocalMidnightMillis(utc), weightKg = 81.3), entity)
        assertEquals(entry, BodyWeightMapper.toDomain(entity, utc))
    }

    private fun routineDayRow(id: Long, orderIndex: Int, name: String) = RoutineDayWithPlannedExercises(
        day = RoutineDayEntity(id = id, routineId = 1, dayName = name, orderIndex = orderIndex),
        plannedExercises = emptyList(),
    )

    private fun plannedRow(id: Long, exerciseId: Long, orderIndex: Int) = PlannedExerciseEntity(
        id = id,
        routineDayId = 1,
        exerciseId = exerciseId,
        targetSets = 4,
        targetReps = "8-10",
        restSeconds = 90,
        orderIndex = orderIndex,
    )

    private fun logRow(
        id: Long,
        orderIndex: Int,
        sets: List<WorkoutSetLogEntity>,
    ) = WorkoutExerciseLogWithSets(
        log = WorkoutExerciseLogEntity(
            id = id,
            sessionId = 1,
            exerciseId = 1,
            exerciseNameSnapshot = "Bench Press",
            wasCompleted = true,
            orderIndex = orderIndex,
        ),
        sets = sets,
    )

    private fun setRow(id: Long, setNumber: Int, weight: Double) = WorkoutSetLogEntity(
        id = id,
        workoutExerciseLogId = 1,
        setNumber = setNumber,
        weight = weight,
        reps = 10,
        isCompleted = true,
    )
}
