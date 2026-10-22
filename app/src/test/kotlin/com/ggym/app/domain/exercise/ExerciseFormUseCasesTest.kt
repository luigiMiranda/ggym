package com.ggym.app.domain.exercise

import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.testing.RepositoryTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The form's two writes, against a real database.
 *
 * These are the rules the screens only *show*: a name that is already taken must leave both rows
 * untouched, an existing entry must keep the weight the user has been progressing, and a new
 * exercise's starting weight must land on the exercise rather than on the day — the last one being
 * the difference between a number that carries over and one that never moves again.
 */
class ExerciseFormUseCasesTest : RepositoryTest() {

    private val form by lazy { ExerciseFormUseCases(routineRepository, exerciseRepository) }

    private var routineId: Long = 0
    private var dayId: Long = 0

    private suspend fun givenDay(): Long {
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        return dayId
    }

    private suspend fun slots(): List<PlannedExercise> =
        routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises

    @Test
    fun `create writes the exercise with its rest and weight, and the day follows that weight`() = runTest {
        givenDay()

        val slotId = form.create(
            routineDayId = dayId,
            name = "  Cable Fly ",
            sets = 4,
            reps = "12",
            restSeconds = 60,
            startingWeightKg = 17.5,
        )

        val exercise = exerciseRepository.findByName("Cable Fly")!!
        assertEquals("the name is trimmed", "Cable Fly", exercise.name)
        assertEquals("the starting weight is the exercise's base", 17.5, exercise.currentWorkingWeight)
        assertEquals("and its rest default too", 60, exercise.defaultRestSeconds)

        val slot = slots().single()
        assertEquals(slotId, slot.id)
        assertEquals(exercise.id, slot.exerciseId)
        assertEquals(4, slot.targetSets)
        assertEquals("12", slot.targetReps)
        assertEquals(60, slot.restSeconds)
        assertNull(
            "the slot carries no weight of its own, so a logged set can move it",
            slot.weightKg,
        )
    }

    @Test
    fun `create with no starting weight leaves the exercise with none`() = runTest {
        givenDay()

        form.create(dayId, "Front Squat", sets = 3, reps = "5", restSeconds = 120, startingWeightKg = null)

        assertNull(exerciseRepository.findByName("Front Squat")!!.currentWorkingWeight)
        assertNull(slots().single().weightKg)
    }

    /**
     * Reusing a name is the whole point of the dictionary: “bench press” typed again must not reset
     * a number the user has been progressing for months.
     */
    @Test
    fun `create reuses an existing name and does not touch what it has learned`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press", defaultRestSeconds = 150)
        exerciseRepository.updateCurrentWorkingWeight(bench.id, 82.5)
        givenDay()

        form.create(dayId, "bench press", sets = 3, reps = "5", restSeconds = 45, startingWeightKg = 20.0)

        assertEquals("the dictionary was not duplicated", 1, exerciseRepository.observeAll().first().size)
        assertEquals("the base weight is the one the user progressed to", 82.5, exerciseRepository.findByName("Bench Press")!!.currentWorkingWeight)
        assertEquals("and so is its rest default", 150, exerciseRepository.findByName("Bench Press")!!.defaultRestSeconds)

        val slot = slots().single()
        assertEquals("the day still gets the numbers the form showed", 3, slot.targetSets)
        assertEquals("5", slot.targetReps)
        assertEquals(45, slot.restSeconds)
        assertEquals(bench.id, slot.exerciseId)
    }

    @Test
    fun `updateSlot writes the dictionary name and the day's numbers in one call`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        givenDay()
        routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 120, weightKg = 80.0)

        val slot = slots().single()
        val result = form.updateSlot(
            plannedExercise = slot,
            name = "Barbell Bench Press",
            sets = 5,
            reps = "5",
            restSeconds = 180,
            weightKg = 85.0,
        )

        assertEquals(UpdateSlotResult.Saved, result)
        assertNotNull("the dictionary moved", exerciseRepository.findByName("Barbell Bench Press"))
        assertNull("and the old name is gone", exerciseRepository.findByName("Bench Press"))

        val stored = slots().single()
        assertEquals(5, stored.targetSets)
        assertEquals("5", stored.targetReps)
        assertEquals(180, stored.restSeconds)
        assertEquals(85.0, stored.weightKg)
    }

    @Test
    fun `a null weight hands the slot back to the exercise`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        givenDay()
        routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 120, weightKg = 80.0)

        form.updateSlot(
            plannedExercise = slots().single(),
            name = "Bench Press",
            sets = 4,
            reps = "8-10",
            restSeconds = 120,
            weightKg = null,
        )

        assertNull("the day no longer pins a number of its own", slots().single().weightKg)
    }

    /**
     * The refusal has to be total. A name that collides writes *nothing* — not the name, and not
     * the sets and reps that were sitting next to it in the same form.
     */
    @Test
    fun `a colliding name writes neither the name nor the numbers`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        exerciseRepository.findOrCreate("Cable Fly")
        givenDay()
        routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 120, weightKg = 80.0)

        val result = form.updateSlot(
            plannedExercise = slots().single(),
            name = "cable fly",
            sets = 5,
            reps = "5",
            restSeconds = 180,
            weightKg = 85.0,
        )

        assertEquals(UpdateSlotResult.NameTaken, result)
        assertNotNull("the entry it collided with is untouched", exerciseRepository.findByName("Cable Fly"))
        assertNotNull("and so is the one being edited", exerciseRepository.findByName("Bench Press"))

        val stored = slots().single()
        assertEquals("the sets were not written either", 4, stored.targetSets)
        assertEquals("8-10", stored.targetReps)
        assertEquals(120, stored.restSeconds)
        assertEquals(80.0, stored.weightKg)
    }

    /** A case-only fix is a legitimate rename, so it must not be read as a collision with itself. */
    @Test
    fun `renaming to the same name still saves the numbers`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        givenDay()
        routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 120)

        val result = form.updateSlot(
            plannedExercise = slots().single(),
            name = "Bench Press",
            sets = 6,
            reps = "6",
            restSeconds = 150,
            weightKg = null,
        )

        assertEquals(UpdateSlotResult.Saved, result)
        assertEquals(6, slots().single().targetSets)
    }

    @Test
    fun `an unknown exercise cannot be written to`() = runTest {
        givenDay()

        val result = form.updateSlot(
            plannedExercise = PlannedExercise(
                id = 99,
                routineDayId = dayId,
                exerciseId = 4_242,
                targetSets = 3,
                targetReps = "10",
                restSeconds = 90,
                orderIndex = 0,
            ),
            name = "Ghost",
            sets = 3,
            reps = "10",
            restSeconds = 90,
            weightKg = null,
        )

        assertEquals(UpdateSlotResult.NotFound, result)
        assertEquals("the day is untouched", emptyList<PlannedExercise>(), slots())
    }

    @Test
    fun `createDictionary makes an entry with a base weight and no plan`() = runTest {
        givenDay()

        val exercise = form.createDictionary(
            name = "  Bench Press ",
            restSeconds = 150,
            startingWeightKg = 82.5,
        )

        assertEquals("the name is trimmed", "Bench Press", exercise.name)
        assertEquals(82.5, exerciseRepository.findById(exercise.id)!!.currentWorkingWeight!!, 0.0)
        assertEquals(150, exerciseRepository.findById(exercise.id)!!.defaultRestSeconds)
        assertEquals("no day was touched: the library has none to write to", emptyList<PlannedExercise>(), slots())
    }

    @Test
    fun `createDictionary with no weight leaves the entry without one, and reuses an existing name`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press", currentWorkingWeight = 82.5, defaultRestSeconds = 90)

        val created = form.createDictionary(name = "bench press", restSeconds = 150, startingWeightKg = 100.0)

        assertEquals("the existing entry wins, id and all", bench.id, created.id)
        assertEquals("and keeps the number it has been progressing", 82.5, created.currentWorkingWeight!!, 0.0)
        assertEquals("as well as the rest it chose", 90, created.defaultRestSeconds)
    }

    @Test
    fun `updateDictionary writes the name, the base weight and the default rest`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press", currentWorkingWeight = 82.5, defaultRestSeconds = 90)

        val result = form.updateDictionary(bench, name = "Bench Press Heavy", restSeconds = 150, weightKg = 85.0)

        assertEquals(UpdateDictionaryResult.Saved, result)
        val stored = exerciseRepository.findById(bench.id)!!
        assertEquals("Bench Press Heavy", stored.name)
        assertEquals(85.0, stored.currentWorkingWeight!!, 0.0)
        assertEquals(150, stored.defaultRestSeconds)
    }

    @Test
    fun `updateDictionary can clear the base weight`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press", currentWorkingWeight = 82.5)

        assertEquals(UpdateDictionaryResult.Saved, form.updateDictionary(bench, name = "Bench Press", restSeconds = 90, weightKg = null))

        assertNull("a blank field means no weight of its own", exerciseRepository.findById(bench.id)!!.currentWorkingWeight)
    }

    @Test
    fun `updateDictionary refuses a colliding name and writes nothing`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press", currentWorkingWeight = 82.5, defaultRestSeconds = 90)
        exerciseRepository.findOrCreate("Cable Fly")

        val result = form.updateDictionary(bench, name = "Cable Fly", restSeconds = 200, weightKg = 100.0)

        assertEquals(UpdateDictionaryResult.NameTaken, result)
        val stored = exerciseRepository.findById(bench.id)!!
        assertEquals("the name did not move", "Bench Press", stored.name)
        assertEquals("nor did the weight in the same form", 82.5, stored.currentWorkingWeight!!, 0.0)
        assertEquals("nor the rest time", 90, stored.defaultRestSeconds)
    }

    @Test
    fun `updateDictionary on a deleted entry reports not found`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        exerciseRepository.deleteExercise(bench.id)

        assertEquals(
            UpdateDictionaryResult.NotFound,
            form.updateDictionary(bench, name = "Bench Press", restSeconds = 90, weightKg = null),
        )
    }
}
