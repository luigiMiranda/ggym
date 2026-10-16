package com.ggym.app.data.repository

import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.countRows
import com.ggym.app.testing.givenExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Routines as the editor will use them: days and slots appended at the end, reordering that
 * rewrites indices in one go, and a delete that takes the whole tree with it.
 */
class RoomRoutineRepositoryTest : RepositoryTest() {

    @Test
    fun `a routine is created with the time the clock gives it`() = runTest {
        clock = 1_768_000_000_000

        val routineId = routineRepository.createRoutine("Push Day")

        val stored = routineRepository.observeRoutines().first().single()
        assertEquals(routineId, stored.id)
        assertEquals("Push Day", stored.name)
        assertEquals(1_768_000_000_000, stored.createdAt)
        assertTrue("a new routine has no days yet", stored.days.isEmpty())
    }

    @Test
    fun `routines are listed in the order they were created`() = runTest {
        routineRepository.createRoutine("Push Day")
        clock += 1_000
        routineRepository.createRoutine("Pull Day")
        clock += 1_000
        routineRepository.createRoutine("Leg Day")

        assertEquals(
            listOf("Push Day", "Pull Day", "Leg Day"),
            routineRepository.observeRoutines().first().map { it.name },
        )
    }

    @Test
    fun `a routine is renamed without touching its days`() = runTest {
        val routineId = routineRepository.createRoutine("Push Day")
        routineRepository.addDay(routineId, "Push")

        routineRepository.renameRoutine(routineId, "Push Day A")

        val stored = routineRepository.observeRoutine(routineId).first()
        assertEquals("Push Day A", stored?.name)
        assertEquals(listOf("Push"), stored?.days?.map { it.dayName })
    }

    @Test
    fun `deleting a routine removes its days and slots`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val doomedId = routineRepository.createRoutine("Push Day")
        val doomedDayId = routineRepository.addDay(doomedId, "Push")
        routineRepository.addPlannedExercise(doomedDayId, exerciseId, targetSets = 4, targetReps = "8-10", restSeconds = 90)

        val keptId = routineRepository.createRoutine("Pull Day")
        val keptDayId = routineRepository.addDay(keptId, "Pull")
        routineRepository.addPlannedExercise(keptDayId, exerciseId, targetSets = 3, targetReps = "10", restSeconds = 60)

        routineRepository.deleteRoutine(doomedId)

        assertNull(routineRepository.observeRoutine(doomedId).first())
        assertEquals(1, database.countRows("routine_day"))
        assertEquals(1, database.countRows("planned_exercise"))
        assertEquals(listOf("Pull Day"), routineRepository.observeRoutines().first().map { it.name })
    }

    @Test
    fun `days are appended in the order they are added`() = runTest {
        val routineId = routineRepository.createRoutine("Push Day")

        val first = routineRepository.addDay(routineId, "Push A")
        val second = routineRepository.addDay(routineId, "Push B")
        val third = routineRepository.addDay(routineId, "Push C")

        val stored = routineRepository.observeRoutine(routineId).first()!!
        assertEquals(listOf(first, second, third), stored.days.map { it.id })
        assertEquals(listOf(0, 1, 2), stored.days.map { it.orderIndex })
        assertEquals(listOf("Push A", "Push B", "Push C"), stored.days.map { it.dayName })
    }

    @Test
    fun `renaming and deleting a day leaves the other days alone`() = runTest {
        val routineId = routineRepository.createRoutine("Push Day")
        val first = routineRepository.addDay(routineId, "Push A")
        val second = routineRepository.addDay(routineId, "Push B")

        routineRepository.renameDay(first, "Push A - heavy")
        routineRepository.deleteDay(second)

        val stored = routineRepository.observeRoutine(routineId).first()!!
        assertEquals(listOf("Push A - heavy"), stored.days.map { it.dayName })
        assertEquals(0, stored.days.single().orderIndex)
    }

    @Test
    fun `reordering days rewrites every index`() = runTest {
        val routineId = routineRepository.createRoutine("Push Day")
        val first = routineRepository.addDay(routineId, "Push A")
        val second = routineRepository.addDay(routineId, "Push B")
        val third = routineRepository.addDay(routineId, "Push C")

        routineRepository.reorderDays(routineId, listOf(third, first, second))

        val stored = routineRepository.observeRoutine(routineId).first()!!
        assertEquals(listOf("Push C", "Push A", "Push B"), stored.days.map { it.dayName })
        assertEquals(listOf(0, 1, 2), stored.days.map { it.orderIndex })
    }

    @Test
    fun `reordering cannot move a day into another routine`() = runTest {
        val pushId = routineRepository.createRoutine("Push Day")
        val pullId = routineRepository.createRoutine("Pull Day")
        val pushDayId = routineRepository.addDay(pushId, "Push")
        val pullDayId = routineRepository.addDay(pullId, "Pull")

        routineRepository.reorderDays(pullId, listOf(pushDayId, pullDayId))

        val push = routineRepository.observeRoutine(pushId).first()!!
        val pull = routineRepository.observeRoutine(pullId).first()!!
        assertEquals("the day of another routine was not moved", 0, push.days.single().orderIndex)
        assertEquals("Pull Day still owns exactly its own day", pullDayId, pull.days.single().id)
        assertEquals("it took the position it was given in the list", 1, pull.days.single().orderIndex)
    }

    @Test
    fun `planned exercises are appended, ordered and reorderable`() = runTest {
        val benchId = database.givenExercise(name = "Bench Press")
        val flyId = database.givenExercise(name = "Cable Fly")
        val routineId = routineRepository.createRoutine("Push Day")
        val dayId = routineRepository.addDay(routineId, "Push")

        val bench = routineRepository.addPlannedExercise(dayId, benchId, targetSets = 4, targetReps = "8-10", restSeconds = 120)
        val fly = routineRepository.addPlannedExercise(dayId, flyId, targetSets = 3, targetReps = "12", restSeconds = 60)

        assertEquals(
            listOf(bench, fly),
            routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises.map { it.id },
        )

        routineRepository.reorderPlannedExercises(dayId, listOf(fly, bench))

        val stored = routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises
        assertEquals(listOf(flyId, benchId), stored.map { it.exerciseId })
        assertEquals(listOf(0, 1), stored.map { it.orderIndex })
        assertEquals("8-10", stored.last().targetReps)
        assertEquals(4, stored.last().targetSets)
        assertEquals(120, stored.last().restSeconds)
    }

    @Test
    fun `a planned exercise is updated in place`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val routineId = routineRepository.createRoutine("Push Day")
        val dayId = routineRepository.addDay(routineId, "Push")
        val plannedId = routineRepository.addPlannedExercise(dayId, exerciseId, targetSets = 4, targetReps = "8-10", restSeconds = 90)

        routineRepository.updatePlannedExercise(
            PlannedExercise(
                id = plannedId,
                routineDayId = dayId,
                exerciseId = exerciseId,
                targetSets = 5,
                targetReps = "5",
                restSeconds = 180,
                orderIndex = 3,
            ),
        )

        val updated = routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises.single()
        assertEquals(5, updated.targetSets)
        assertEquals("5", updated.targetReps)
        assertEquals(180, updated.restSeconds)
        assertEquals(3, updated.orderIndex)
        assertEquals("no other slot was touched", 1, database.countRows("planned_exercise"))
    }

    @Test
    fun `a planned exercise can be removed without touching the others`() = runTest {
        val benchId = database.givenExercise(name = "Bench Press")
        val flyId = database.givenExercise(name = "Cable Fly")
        val routineId = routineRepository.createRoutine("Push Day")
        val dayId = routineRepository.addDay(routineId, "Push")
        val bench = routineRepository.addPlannedExercise(dayId, benchId, targetSets = 4, targetReps = "8-10", restSeconds = 90)
        routineRepository.addPlannedExercise(dayId, flyId, targetSets = 3, targetReps = "12", restSeconds = 60)

        routineRepository.deletePlannedExercise(bench)

        val remaining = routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises
        assertEquals(listOf(flyId), remaining.map { it.exerciseId })
        assertEquals(1, database.countRows("planned_exercise"))
    }

    @Test
    fun `a whole routine loads as one tree, with the order the user arranged`() = runTest {
        val exerciseId = database.givenExercise(name = "Bench Press")
        val routineId = routineRepository.createRoutine("Push Day")
        val dayA = routineRepository.addDay(routineId, "Push A")
        val dayB = routineRepository.addDay(routineId, "Push B")
        routineRepository.addPlannedExercise(dayB, exerciseId, targetSets = 3, targetReps = "10", restSeconds = 60)
        routineRepository.addPlannedExercise(dayA, exerciseId, targetSets = 4, targetReps = "8-10", restSeconds = 90)
        routineRepository.reorderDays(routineId, listOf(dayB, dayA))

        val stored = routineRepository.observeRoutines().first().single()

        assertEquals(listOf("Push B", "Push A"), stored.days.map { it.dayName })
        assertTrue(stored.days.all { it.routineId == routineId })
        assertEquals(stored.days[0].id, stored.days[0].plannedExercises.single().routineDayId)
    }

    @Test
    fun `an unknown routine loads as null`() = runTest {
        assertNull(routineRepository.observeRoutine(4242).first())
    }
}
