package com.ggym.app.ui.exercises

import app.cash.turbine.test
import com.ggym.app.testing.ViewModelTest
import com.ggym.app.testing.awaitState
import com.ggym.app.testing.givenExercise
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The slot editor: what it opens with, what it refuses, and what a save writes.
 *
 * The weights are deliberately unlike each other, so "the field shows the slot's own weight when
 * it has one, the exercise's otherwise" cannot pass by accident.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SlotEditorViewModelTest : ViewModelTest() {

    private var routineId: Long = 0
    private var dayId: Long = 0

    private fun viewModel(plannedExerciseId: Long, locale: Locale = Locale.US) = SlotEditorViewModel(
        routineId = routineId,
        plannedExerciseId = plannedExerciseId,
        routines = routineRepository,
        exercises = exerciseRepository,
        weights = workingWeights,
        locale = locale,
    )

    /** A day with one bench slot, carrying [slotWeightKg] (null = it inherits). */
    private suspend fun givenBenchSlot(slotWeightKg: Double? = null): Long {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        return routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 120, weightKg = slotWeightKg)
    }

    private suspend fun benchId(): Long = exerciseRepository.findByName("Bench Press")!!.id

    @Test
    fun `the form opens with the slot's own weight and its other fields`() = runTest {
        val slotId = givenBenchSlot(slotWeightKg = 80.0)
        exerciseRepository.updateCurrentWorkingWeight(benchId(), 87.5)

        viewModel(slotId).uiState.test {
            val state = awaitState { !it.isLoading }

            assertEquals("Bench Press", state.exerciseName)
            assertEquals("4", state.sets)
            assertEquals("8-10", state.reps)
            assertEquals("120", state.restSeconds)
            assertEquals("80", state.weight)
            assertTrue("the number is the slot's own", state.isOwnWeight)
            assertNull(state.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a slot with no weight opens with the exercise's, and says it is inherited`() = runTest {
        val slotId = givenBenchSlot(slotWeightKg = null)
        exerciseRepository.updateCurrentWorkingWeight(benchId(), 87.5)

        viewModel(slotId).uiState.test {
            val state = awaitState { !it.isLoading }

            assertEquals("87.5", state.weight)
            assertTrue("nothing has been chosen for this slot yet", !state.isOwnWeight)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the weight is formatted for the locale the user reads`() = runTest {
        val slotId = givenBenchSlot(slotWeightKg = 82.5)

        viewModel(slotId, locale = Locale.ITALY).uiState.test {
            assertEquals("82,5", awaitState { !it.isLoading }.weight)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving writes the fields and the slot's own weight`() = runTest {
        val slotId = givenBenchSlot(slotWeightKg = null)
        val vm = viewModel(slotId)
        val saved = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.effects.first() }

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.setSets("5")
            vm.setReps("5")
            vm.setRestSeconds("180")
            vm.setWeight("82.5")

            vm.save()
            assertEquals(SlotEditorEffect.Saved, saved.await())

            val stored = routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises.single()
            assertEquals(5, stored.targetSets)
            assertEquals("5", stored.targetReps)
            assertEquals(180, stored.restSeconds)
            assertEquals("the save promoted the shown default into the slot's own weight", 82.5, stored.weightKg!!, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving a blank weight hands the slot back to the exercise's default`() = runTest {
        val slotId = givenBenchSlot(slotWeightKg = 80.0)
        exerciseRepository.updateCurrentWorkingWeight(benchId(), 87.5)
        val vm = viewModel(slotId)

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.setWeight("")

            vm.save()

            // The write happens off the UI thread: wait for the database to say it landed rather
            // than for a state change that a save does not produce.
            val stored = routineRepository.observeRoutine(routineId).first { routine ->
                routine!!.days.single().plannedExercises.single().weightKg == null
            }!!.days.single().plannedExercises.single()
            assertNull("the slot carries no weight of its own again", stored.weightKg)
            assertEquals("and the dictionary is what answers now", 87.5, workingWeights.resolve(stored)!!, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each invalid field is refused with its own error, and nothing is written`() = runTest {
        val slotId = givenBenchSlot(slotWeightKg = 80.0)
        val vm = viewModel(slotId)

        vm.uiState.test {
            awaitState { !it.isLoading }

            vm.setSets("0")
            vm.save()
            assertEquals(SlotEditorError.SetsTooLow, awaitState { it.error != null }.error)

            vm.setSets("4")
            vm.setReps("   ")
            vm.save()
            assertEquals(SlotEditorError.RepsBlank, awaitState { it.error == SlotEditorError.RepsBlank }.error)

            vm.setReps("8-10")
            vm.setRestSeconds("-1")
            vm.save()
            assertEquals(SlotEditorError.RestInvalid, awaitState { it.error == SlotEditorError.RestInvalid }.error)

            vm.setRestSeconds("120")
            vm.setWeight("82.555")
            vm.save()
            assertEquals(SlotEditorError.WeightInvalid, awaitState { it.error == SlotEditorError.WeightInvalid }.error)

            assertEquals(
                "not one of the four attempts wrote anything",
                80.0,
                routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises.single().weightKg!!,
                0.0,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing a field clears the error the last attempt produced`() = runTest {
        val slotId = givenBenchSlot()
        val vm = viewModel(slotId)

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.setSets("0")
            vm.save()
            assertEquals(SlotEditorError.SetsTooLow, awaitState { it.error != null }.error)

            vm.setSets("3")
            assertNull(awaitState { it.error == null }.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the steppers move by a plate pair, format the result, and stop at zero`() = runTest {
        val slotId = givenBenchSlot(slotWeightKg = 80.0)
        val vm = viewModel(slotId)

        vm.uiState.test {
            awaitState { !it.isLoading }

            vm.stepWeight(2.5)
            assertEquals("82.5", awaitState { it.weight == "82.5" }.weight)

            vm.stepWeight(-2.5)
            assertEquals("80", awaitState { it.weight == "80" }.weight)

            vm.setWeight("1")
            vm.stepWeight(-2.5)
            assertEquals("0", awaitState { it.weight == "0" }.weight)

            vm.setWeight("")
            vm.stepWeight(2.5)
            assertEquals("2.5", awaitState { it.weight == "2.5" }.weight)

            vm.setWeight("nonsense")
            vm.stepWeight(2.5)
            assertEquals("a field that holds no number is not stepped", "nonsense", vm.uiState.first().weight)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing the slot takes it out of the day`() = runTest {
        val bench = database.givenExercise(name = "Bench Press")
        val fly = database.givenExercise(name = "Cable Fly")
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        val doomed = routineRepository.addPlannedExercise(dayId, bench, 4, "8-10", 120)
        routineRepository.addPlannedExercise(dayId, fly, 3, "12", 60)
        val vm = viewModel(doomed)
        val removed = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.effects.first() }

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.remove()

            assertEquals(SlotEditorEffect.Removed, removed.await())
            val remaining = routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises
            assertEquals(listOf(fly), remaining.map { it.exerciseId })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a slot that is already gone reports itself missing instead of opening an empty form`() = runTest {
        val slotId = givenBenchSlot()
        routineRepository.deletePlannedExercise(slotId)

        viewModel(slotId).uiState.test {
            assertTrue(awaitState { it.isMissing }.isMissing)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
