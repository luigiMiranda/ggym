package com.ggym.app.data.room

import app.cash.turbine.test
import com.ggym.app.testing.RoomTest
import com.ggym.app.testing.countRows
import com.ggym.app.testing.givenExercise
import com.ggym.app.testing.givenPlannedExercise
import com.ggym.app.testing.givenRoutine
import com.ggym.app.testing.givenRoutineDay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The routine tree: one query loads a routine with its days and their planned exercises, and the
 * two cascades mean a delete cannot leave a day or a slot behind.
 */
class RoutineDaoTest : RoomTest() {

    @Test
    fun `a routine comes back with its days and their planned exercises`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val routineId = database.givenRoutine(name = "Push Day")
        val dayId = database.givenRoutineDay(routineId = routineId, dayName = "Push")
        database.givenPlannedExercise(routineDayId = dayId, exerciseId = exerciseId, targetSets = 4, targetReps = "8-10")

        val row = database.routineDao().getAllRoutinesWithDays().single()

        assertEquals("Push Day", row.routine.name)
        assertEquals("Push", row.days.single().day.dayName)
        assertEquals(exerciseId, row.days.single().plannedExercises.single().exerciseId)
        assertEquals("8-10", row.days.single().plannedExercises.single().targetReps)
    }

    @Test
    fun `days of one routine are not mixed into another`() = runTest {
        val pushId = database.givenRoutine(name = "Push Day")
        val pullId = database.givenRoutine(name = "Pull Day", createdAt = 1_750_000_000_001)
        database.givenRoutineDay(routineId = pushId, dayName = "Push")
        database.givenRoutineDay(routineId = pullId, dayName = "Pull")

        val routines = database.routineDao().getAllRoutinesWithDays()

        assertEquals(listOf("Push Day", "Pull Day"), routines.map { it.routine.name })
        assertEquals(listOf("Push"), routines[0].days.map { it.day.dayName })
        assertEquals(listOf("Pull"), routines[1].days.map { it.day.dayName })
    }

    @Test
    fun `deleting a routine takes its days and their planned exercises with it`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val doomedId = database.givenRoutine(name = "Push Day")
        val doomedDayId = database.givenRoutineDay(routineId = doomedId, dayName = "Push")
        database.givenPlannedExercise(routineDayId = doomedDayId, exerciseId = exerciseId)

        val keptId = database.givenRoutine(name = "Pull Day", createdAt = 1_750_000_000_001)
        val keptDayId = database.givenRoutineDay(routineId = keptId, dayName = "Pull")
        database.givenPlannedExercise(routineDayId = keptDayId, exerciseId = exerciseId)

        database.routineDao().deleteById(doomedId)

        assertEquals(1, database.countRows("routine"))
        assertEquals(1, database.countRows("routine_day"))
        assertEquals(1, database.countRows("planned_exercise"))
        assertEquals(keptId, database.routineDao().getAllRoutinesWithDays().single().routine.id)
    }

    @Test
    fun `deleting a day takes its planned exercises with it`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val routineId = database.givenRoutine(name = "Push Day")
        val doomedDayId = database.givenRoutineDay(routineId = routineId, dayName = "Push")
        database.givenPlannedExercise(routineDayId = doomedDayId, exerciseId = exerciseId)
        val keptDayId = database.givenRoutineDay(routineId = routineId, dayName = "Push B", orderIndex = 1)
        database.givenPlannedExercise(routineDayId = keptDayId, exerciseId = exerciseId)

        database.routineDayDao().deleteById(doomedDayId)

        assertEquals(1, database.countRows("routine_day"))
        assertEquals(1, database.countRows("planned_exercise"))
        assertEquals(keptDayId, database.routineDao().getAllRoutinesWithDays().single().days.single().day.id)
    }

    @Test
    fun `a new day lands after the existing ones, and the first one lands at zero`() = runTest {
        val routineId = database.givenRoutine(name = "Push Day")

        assertEquals(0, database.routineDayDao().nextOrderIndex(routineId))
        database.givenRoutineDay(routineId = routineId, orderIndex = 0)
        database.givenRoutineDay(routineId = routineId, orderIndex = 1)

        assertEquals(2, database.routineDayDao().nextOrderIndex(routineId))
    }

    @Test
    fun `a new planned exercise lands after the existing ones`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val routineId = database.givenRoutine(name = "Push Day")
        val dayId = database.givenRoutineDay(routineId = routineId, dayName = "Push")

        assertEquals(0, database.plannedExerciseDao().nextOrderIndex(dayId))
        database.givenPlannedExercise(routineDayId = dayId, exerciseId = exerciseId, orderIndex = 0)

        assertEquals(1, database.plannedExerciseDao().nextOrderIndex(dayId))
    }

    @Test
    fun `reordering only touches the routine it was asked about`() = runTest {
        val pushId = database.givenRoutine(name = "Push Day")
        val pullId = database.givenRoutine(name = "Pull Day", createdAt = 1_750_000_000_001)
        val pushDayId = database.givenRoutineDay(routineId = pushId, dayName = "Push", orderIndex = 0)
        val pullDayId = database.givenRoutineDay(routineId = pullId, dayName = "Pull", orderIndex = 0)

        // A stale drag hands over the wrong routine's day id.
        database.routineDayDao().updateOrderIndex(routineId = pullId, routineDayId = pushDayId, orderIndex = 9)

        val routines = database.routineDao().getAllRoutinesWithDays()
        assertEquals(0, routines.first { it.routine.id == pushId }.days.single().day.orderIndex)
        assertEquals(0, routines.first { it.routine.id == pullId }.days.single().day.orderIndex)

        database.routineDayDao().updateOrderIndex(routineId = pullId, routineDayId = pullDayId, orderIndex = 3)
        assertEquals(
            3,
            database.routineDao().getAllRoutinesWithDays().first { it.routine.id == pullId }.days.single().day.orderIndex,
        )
    }

    @Test
    fun `a change to a routine tree reaches a collector that is already watching`() = runTest {
        val routineId = database.givenRoutine(name = "Push Day")

        database.routineDao().observeRoutinesWithDays().test {
            assertEquals("the routine arrives without days", 0, awaitItem().single().days.size)

            database.givenRoutineDay(routineId = routineId, dayName = "Push")
            assertEquals("the new day arrives in the same emission as its routine", 1, awaitItem().single().days.size)

            database.givenRoutineDay(routineId = routineId, dayName = "Push B", orderIndex = 1)
            assertEquals(2, awaitItem().single().days.size)

            database.routineDao().deleteById(routineId)
            assertEquals(emptyList<Any>(), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a routine that does not exist loads as null`() = runTest {
        assertNull(database.routineDao().getAllRoutinesWithDays().firstOrNull { it.routine.id == 42L })
    }
}
