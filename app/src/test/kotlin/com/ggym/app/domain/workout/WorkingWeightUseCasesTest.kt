package com.ggym.app.domain.workout

import com.ggym.app.data.repository.RoomRoutineRepository
import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The weight rule, which is M2's gate: what a slot shows, in which order, and what writing one
 * does *not* touch.
 *
 * The three numbers in the fixture are deliberately different — the dictionary at 87.5, the last
 * performed set at 85.0, a slot at 80.0 — because a rule that picks the wrong source still looks
 * right whenever two of them agree.
 */
class WorkingWeightUseCasesTest : RepositoryTest() {

    private val useCases by lazy {
        WorkingWeightUseCases(
            routines = routineRepository,
            exercises = exerciseRepository,
            history = historyRepository,
        )
    }

    @Test
    fun `an exercise with nothing set and nothing performed has no weight`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press")

        assertNull("there is no number to show", useCases.resolve(bench.id))
    }

    @Test
    fun `the dictionary answers when the user has set a weight`() = runTest {
        val bench = benchWith(workingWeight = 87.5)

        assertEquals(87.5, useCases.resolve(bench)!!, 0.0)
    }

    @Test
    fun `the last completed set answers when the dictionary carries no weight`() = runTest {
        val bench = benchWith(performed = 85.0)

        assertEquals("a performed set is the next best thing to a chosen weight", 85.0, useCases.resolve(bench)!!, 0.0)
    }

    @Test
    fun `a set the user never completed is not a weight to load`() = runTest {
        val bench = benchWith(performed = 85.0, completed = false)

        assertNull("an intention is not a lift", useCases.resolve(bench))
    }

    @Test
    fun `the dictionary wins over the history - a chosen weight beats the last one performed`() = runTest {
        val bench = benchWith(workingWeight = 87.5, performed = 85.0)

        assertEquals(87.5, useCases.resolve(bench)!!, 0.0)
    }

    @Test
    fun `a slot's own weight beats both the dictionary and the history`() = runTest {
        val bench = benchWith(workingWeight = 87.5, performed = 85.0)
        val routineId = routineRepository.createRoutine("Push Day")
        val slot = slotById(addSlot(bench, routineId, weightKg = 80.0))

        assertEquals(80.0, useCases.resolve(slot)!!, 0.0)
    }

    @Test
    fun `two slots of one exercise resolve independently while the dictionary stays global`() = runTest {
        val bench = benchWith(workingWeight = 87.5, performed = 85.0)
        val routineId = routineRepository.createRoutine("Push Day")
        val pushA = routineRepository.addDay(routineId, "Push A")
        val pushB = routineRepository.addDay(routineId, "Push B")
        routineRepository.addPlannedExercise(pushA, bench, 4, "8-10", 120, weightKg = 80.0)
        routineRepository.addPlannedExercise(pushA, bench, 3, "12", 60, weightKg = null)
        routineRepository.addPlannedExercise(pushB, bench, 5, "5", 180, weightKg = 70.0)

        val slots = slotsOf(routineId)
        val resolved = useCases.resolveAll(slots)

        assertEquals(
            "80 on Push A, 87.5 for the slot that has none of its own, 70 on Push B",
            listOf(80.0, 87.5, 70.0),
            slots.map { resolved.getValue(it.id) },
        )
    }

    @Test
    fun `recording a slot weight touches neither the dictionary nor the history`() = runTest {
        val bench = benchWith(workingWeight = 87.5, performed = 85.0)
        val routineId = routineRepository.createRoutine("Push Day")
        val slot = slotById(addSlot(bench, routineId, weightKg = null))

        val stored = useCases.record(slot, weightKg = 80.0)

        assertEquals(80.0, stored.weightKg!!, 0.0)
        assertEquals("the slot write reached the database", 80.0, slotById(slot.id).weightKg!!, 0.0)
        assertEquals(
            "the dictionary is still what the user set",
            87.5,
            exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
            0.0,
        )
        assertEquals(
            "the performed set is still the same set",
            85.0,
            historyRepository.latestCompletedSet(bench)!!.weightKg,
            0.0,
        )
    }

    @Test
    fun `clearing a slot weight hands it back to the exercise's default`() = runTest {
        val bench = benchWith(workingWeight = 87.5)
        val routineId = routineRepository.createRoutine("Push Day")
        val slot = slotById(addSlot(bench, routineId, weightKg = 80.0))

        useCases.record(slot, weightKg = null)

        val cleared = slotById(slot.id)
        assertNull("the slot carries no weight of its own again", cleared.weightKg)
        assertEquals("so it follows the dictionary", 87.5, useCases.resolve(cleared)!!, 0.0)
    }

    @Test
    fun `a completed set becomes the exercise's working weight`() = runTest {
        val bench = benchWith(workingWeight = 87.5, performed = 85.0)

        useCases.recordCompletedSetWeight(bench, plannedExerciseId = null, weightKg = 90.0)

        assertEquals(
            "the next workout starts from what was actually lifted",
            90.0,
            exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
            0.0,
        )
        assertEquals(
            "it is not a history write: the last performed set is still the set it was",
            85.0,
            historyRepository.latestCompletedSet(bench)!!.weightKg,
            0.0,
        )
    }

    @Test
    fun `a completed set moves the slot that produced it and leaves another day alone`() = runTest {
        val bench = benchWith(workingWeight = 87.5)
        val routineId = routineRepository.createRoutine("Push Day")
        val pushA = routineRepository.addDay(routineId, "Push A")
        val pushB = routineRepository.addDay(routineId, "Push B")
        val slotA = routineRepository.addPlannedExercise(pushA, bench, 4, "8-10", 120, weightKg = 80.0)
        val slotB = routineRepository.addPlannedExercise(pushB, bench, 5, "5", 180, weightKg = 70.0)

        useCases.recordCompletedSetWeight(bench, plannedExerciseId = slotA, weightKg = 90.0)

        assertEquals("the trained day follows the user", 90.0, slotById(slotA).weightKg!!, 0.0)
        assertEquals("the other day is a different plan and stays one", 70.0, slotById(slotB).weightKg!!, 0.0)
        assertEquals(
            "and the global answer moves with it",
            90.0,
            exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
            0.0,
        )
    }

    @Test
    fun `a completion with no slot pointer writes only the dictionary`() = runTest {
        val bench = benchWith(workingWeight = 87.5)
        val routineId = routineRepository.createRoutine("Push Day")
        val slot = slotById(addSlot(bench, routineId, weightKg = 80.0))

        useCases.recordCompletedSetWeight(bench, plannedExerciseId = null, weightKg = 90.0)

        assertEquals(90.0, exerciseRepository.findById(bench)!!.currentWorkingWeight!!, 0.0)
        assertEquals("without a pointer the slot is not guessed at", 80.0, slotById(slot.id).weightKg!!, 0.0)
    }

    @Test
    fun `a bodyweight set moves neither the working weight nor the slot`() = runTest {
        val bench = benchWith(workingWeight = 87.5)
        val routineId = routineRepository.createRoutine("Push Day")
        val slot = slotById(addSlot(bench, routineId, weightKg = 80.0))

        useCases.recordCompletedSetWeight(bench, plannedExerciseId = slot.id, weightKg = 0.0)

        assertEquals(
            "a bodyweight set is not a number to load next time",
            87.5,
            exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
            0.0,
        )
        assertEquals("and it does not overwrite the day's plan either", 80.0, slotById(slot.id).weightKg!!, 0.0)
    }

    @Test
    fun `a completed set of an exercise the dictionary no longer has writes nothing`() = runTest {
        val bench = benchWith(workingWeight = 87.5)

        useCases.recordCompletedSetWeight(exerciseId = null, plannedExerciseId = null, weightKg = 90.0)

        assertEquals("there is nothing to update, and no crash", 87.5, exerciseRepository.findById(bench)!!.currentWorkingWeight!!, 0.0)
    }

    @Test
    fun `a recorded weight survives a process restart - a fresh repository over the same database`() = runTest {
        val bench = benchWith(workingWeight = 87.5)
        val routineId = routineRepository.createRoutine("Push Day")
        val slotId = addSlot(bench, routineId, weightKg = null)

        useCases.record(slotById(slotId), weightKg = 80.0)

        // Nothing here is cached in the ViewModel layer: this is a new repository and a new
        // use-case instance reading the same database, which is what a relaunch means.
        val afterRestart = RoomRoutineRepository(database, now = { clock })
        val reread = WorkingWeightUseCases(afterRestart, exerciseRepository, historyRepository)
        val stored = afterRestart.observeRoutine(routineId).first()!!
            .days.flatMap { it.plannedExercises }
            .single()

        assertEquals(80.0, reread.resolve(stored)!!, 0.0)
    }

    @Test
    fun `resolveAll answers an empty day without touching the database`() = runTest {
        assertEquals(emptyMap<Long, Double?>(), useCases.resolveAll(emptyList()))
    }

    /** A dictionary entry, optionally with a chosen weight and/or a session already performed. */
    private suspend fun benchWith(
        workingWeight: Double? = null,
        performed: Double? = null,
        completed: Boolean = true,
    ): Long {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        workingWeight?.let { exerciseRepository.updateCurrentWorkingWeight(bench.id, it) }
        performed?.let { saveBenchSession(bench.id, it, completed) }
        return bench.id
    }

    private suspend fun addSlot(exerciseId: Long, routineId: Long, weightKg: Double?): Long {
        val dayId = routineRepository.addDay(routineId, "Push")
        return routineRepository.addPlannedExercise(dayId, exerciseId, 4, "8-10", 120, weightKg = weightKg)
    }

    private suspend fun slotById(plannedExerciseId: Long): PlannedExercise = routineRepository.observeRoutines().first()
        .flatMap { it.days }
        .flatMap { it.plannedExercises }
        .single { it.id == plannedExerciseId }

    private suspend fun slotsOf(routineId: Long): List<PlannedExercise> =
        routineRepository.observeRoutine(routineId).first()!!.days.flatMap { it.plannedExercises }

    /** One completed (or deliberately incomplete) bench session at [performed] kg. */
    private suspend fun saveBenchSession(exerciseId: Long, performed: Double, isCompleted: Boolean) {
        historyRepository.saveSession(
            TestData.session(
                date = TestData.date(month = 1, day = 5),
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = exerciseId,
                        sets = listOf(TestData.setLog(setNumber = 1, weight = performed, reps = 8, isCompleted = isCompleted)),
                    ),
                ),
            ),
        )
    }
}
