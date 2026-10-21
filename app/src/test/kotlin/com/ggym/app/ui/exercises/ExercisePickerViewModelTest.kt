package com.ggym.app.ui.exercises

import app.cash.turbine.test
import com.ggym.app.domain.exercise.ExerciseFormUseCases
import com.ggym.app.domain.model.Exercise
import com.ggym.app.testing.ViewModelTest
import com.ggym.app.testing.awaitState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker: search, reuse, create, and the one delete the dictionary refuses.
 *
 * This is where the dictionary grows, so the create-vs-reuse path is the important one: a second
 * "bench press" would mean a second history.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExercisePickerViewModelTest : ViewModelTest() {

    private var routineId: Long = 0
    private var dayId: Long = 0

    private fun viewModel() = ExercisePickerViewModel(
        routineDayId = dayId,
        routines = routineRepository,
        exercises = exerciseRepository,
        formUseCases = ExerciseFormUseCases(routineRepository, exerciseRepository),
    )

    private suspend fun givenDayWithBench() {
        val bench = exerciseRepository.findOrCreate("Bench Press", defaultRestSeconds = 150)
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        // The bench is planned already: "it is in the dictionary" and "it is in this day" differ.
        routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 150)
    }

    @Test
    fun `the full dictionary is offered before anything is typed`() = runTest {
        exerciseRepository.findOrCreate("Bench Press")
        exerciseRepository.findOrCreate("Cable Fly")
        givenDayWithBench()

        viewModel().uiState.test {
            val state = awaitState { it.results.size == 2 }

            assertEquals(listOf("Bench Press", "Cable Fly"), state.results.map { it.name })
            assertFalse("nothing typed, nothing to create", state.canCreate)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing filters the dictionary`() = runTest {
        givenDayWithBench()
        exerciseRepository.findOrCreate("Cable Fly")
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.results.size == 2 }
            vm.updateQuery("cable")

            val state = awaitState { it.results.map { exercise -> exercise.name } == listOf("Cable Fly") }
            assertTrue("nothing is called 'cable' yet, so it can be created", state.canCreate)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a name that only differs in case is an existing exercise, not a new one`() = runTest {
        givenDayWithBench()
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.updateQuery("bench press")

            val state = awaitState { it.results.isNotEmpty() }
            assertFalse("the entry is already here, in another case", state.canCreate)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a new name is configured in the form, and saving it plans it in this day`() = runTest {
        givenDayWithBench()
        val vm = viewModel()
        val added = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.effects.first() }

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.updateQuery("Cable Fly")
            val state = awaitState { it.canCreate }
            assertEquals("the typed text is what the create row shows", "Cable Fly", state.query)

            vm.openCreateForm()

            val form = awaitState { it.form != null }.form!!
            assertEquals("the name carries into the form so a typo can be fixed", "Cable Fly", form.name)
            assertEquals(ExerciseFormDefaults.SETS, form.sets)
            assertEquals(ExerciseFormDefaults.REPS, form.reps)
            assertEquals(ExerciseFormMode.Create, form.mode)
            assertEquals("a new exercise may have no weight at all", "", form.weight)

            // The user sets the numbers the form exists for, then saves.
            vm.stepFormSets(1)
            vm.setFormReps("12")
            vm.stepFormWeight(2.5)
            vm.saveForm()

            awaitState { it.results.any { exercise -> exercise.name == "Cable Fly" } }
            assertEquals(ExercisePickerEffect.Added, added.await())

            val dictionary = exerciseRepository.observeAll().first()
            assertEquals(listOf("Bench Press", "Cable Fly"), dictionary.map { it.name })
            assertEquals(
                "the starting weight is the exercise's base weight, not the day's",
                2.5,
                dictionary.single { it.name == "Cable Fly" }.currentWorkingWeight,
            )

            val planned = routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises
            assertEquals("the existing slot is untouched", 4, planned.first().targetSets)
            assertEquals("and the new one is planned at what the form said", 4, planned.last().targetSets)
            assertEquals("12", planned.last().targetReps)
            assertEquals(
                "each kind of exercise keeps its own rest time",
                listOf(150, Exercise.DEFAULT_REST_SECONDS),
                planned.map { it.restSeconds },
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a create form that is dismissed writes nothing`() = runTest {
        givenDayWithBench()
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.updateQuery("Cable Fly")
            awaitState { it.canCreate }
            vm.openCreateForm()
            awaitState { it.form != null }

            vm.dismissForm()
            awaitState { it.form == null }

            assertEquals(
                "closing the form leaves the dictionary exactly as it was",
                listOf("Bench Press"),
                exerciseRepository.observeAll().first().map { it.name },
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank form is refused and nothing is written`() = runTest {
        givenDayWithBench()
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.updateQuery("Cable Fly")
            awaitState { it.canCreate }
            vm.openCreateForm()
            awaitState { it.form != null }

            vm.setFormName("   ")
            vm.saveForm()

            val state = awaitState { it.form?.error != null }
            assertEquals(ExerciseFormError.NameBlank, state.form!!.error)
            assertEquals(
                "a refused form does not reach the dictionary",
                listOf("Bench Press"),
                exerciseRepository.observeAll().first().map { it.name },
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding an existing exercise reuses the entry and takes its rest time`() = runTest {
        givenDayWithBench()
        val fly = exerciseRepository.findOrCreate("Cable Fly", defaultRestSeconds = 45)
        val vm = viewModel()
        val added = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.effects.first() }

        vm.uiState.test {
            val state = awaitState { it.results.size == 2 }
            vm.addExisting(state.results.single { it.name == "Cable Fly" })

            assertEquals(ExercisePickerEffect.Added, added.await())

            assertEquals("the dictionary was not duplicated", 2, exerciseRepository.observeAll().first().size)
            val addedSlot = routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises.last()
            assertEquals(fly.id, addedSlot.exerciseId)
            assertEquals(45, addedSlot.restSeconds)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting an exercise no routine plans removes it from the dictionary`() = runTest {
        givenDayWithBench()
        exerciseRepository.findOrCreate("Cable Fly")
        val vm = viewModel()

        vm.uiState.test {
            val state = awaitState { it.results.size == 2 }
            vm.askDelete(state.results.single { it.name == "Cable Fly" })

            assertEquals("asking deletes nothing", 2, exerciseRepository.observeAll().first().size)

            vm.confirmDelete()
            awaitState { it.results.size == 1 }

            assertEquals(listOf("Bench Press"), exerciseRepository.observeAll().first().map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting an exercise a routine still plans names the routine and deletes nothing`() = runTest {
        givenDayWithBench()
        val vm = viewModel()

        vm.uiState.test {
            val state = awaitState { it.results.isNotEmpty() }
            vm.askDelete(state.results.single { it.name == "Bench Press" })
            vm.confirmDelete()

            val blocked = awaitState { it.blockedDeleteRoutines.isNotEmpty() }
            assertEquals(listOf("Push Day"), blocked.blockedDeleteRoutines)
            assertEquals("the dictionary is untouched", listOf("Bench Press"), exerciseRepository.observeAll().first().map { it.name })
            assertTrue("and the question about deleting it is gone", blocked.pendingDelete == null)

            vm.dismissBlockedDelete()
            val dismissed = awaitState { it.blockedDeleteRoutines.isEmpty() }
            assertTrue(dismissed.blockedDeleteRoutines.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
