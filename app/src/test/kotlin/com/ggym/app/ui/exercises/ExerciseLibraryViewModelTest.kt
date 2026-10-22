package com.ggym.app.ui.exercises

import app.cash.turbine.test
import com.ggym.app.domain.exercise.ExerciseFormUseCases
import com.ggym.app.testing.PlannedSlot
import com.ggym.app.testing.ViewModelTest
import com.ggym.app.testing.awaitState
import com.ggym.app.testing.countRows
import com.ggym.app.testing.givenPlannedDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * The exercise library over a real database: the list, the search, and the three writes a dictionary
 * entry has — create, edit, delete — including the two refusals the database decides.
 *
 * The assertions are mostly made against the repositories rather than the state, because the promise
 * is about what was stored: the state is only how the user finds out.
 */
class ExerciseLibraryViewModelTest : ViewModelTest() {

    private val formUseCases by lazy { ExerciseFormUseCases(routineRepository, exerciseRepository) }

    private val viewModel by lazy {
        ExerciseLibraryViewModel(
            exercises = exerciseRepository,
            routines = routineRepository,
            formUseCases = formUseCases,
            locale = Locale.US,
        )
    }

    @Test
    fun `the list carries the base weight, the rest and where the exercise is used`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press", defaultRestSeconds = 120)
        exerciseRepository.updateCurrentWorkingWeight(bench.id, 82.5)
        routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench.id, sets = 4, reps = "8-10", restSeconds = 120))
        exerciseRepository.findOrCreate("Cable Fly")

        viewModel.uiState.test {
            val state = awaitState { !it.isLoading && it.exercises.size == 2 }

            assertEquals("ordered by name, case-insensitively", listOf("Bench Press", "Cable Fly"), state.exercises.map { it.name })
            val row = state.exercises.single { it.name == "Bench Press" }
            assertEquals(82.5, row.weightKg!!, 0.0)
            assertEquals(120, row.restSeconds)
            assertEquals(listOf("Push A"), row.dayNames)
            assertEquals(2, state.totalCount)
            assertEquals("the fly has no base weight yet", 1, state.withoutWeightCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the search filters the list but never the counts`() = runTest {
        exerciseRepository.findOrCreate("Bench Press")
        exerciseRepository.findOrCreate("Cable Fly")

        viewModel.uiState.test {
            awaitState { !it.isLoading && it.exercises.size == 2 }

            viewModel.updateQuery("bench")

            val filtered = awaitState { it.query == "bench" && it.exercises.size == 1 }
            assertEquals("Bench Press", filtered.exercises.single().name)
            assertEquals("the card is about the dictionary, not the search", 2, filtered.totalCount)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding an exercise writes the dictionary and creates no plan`() = runTest {
        viewModel.uiState.test {
            awaitState { !it.isLoading }

            viewModel.addExercise()
            val open = awaitState { it.form != null }.form!!
            assertEquals(ExerciseFormMode.CreateDictionary, open.mode)
            assertEquals("a brand-new entry starts with no name", "", open.name)

            viewModel.setFormName("Bench Press")
            viewModel.setFormRest("120")
            viewModel.setFormWeight("82.5")
            viewModel.saveForm()

            awaitState { it.form == null && it.exercises.size == 1 }

            cancelAndIgnoreRemainingEvents()
        }

        val stored = exerciseRepository.findByName("Bench Press")!!
        assertEquals(82.5, stored.currentWorkingWeight!!, 0.0)
        assertEquals(120, stored.defaultRestSeconds)
        assertEquals("the library has no day to plan into", 0, database.countRows("planned_exercise"))
    }

    @Test
    fun `adding a name the dictionary already holds reuses it and keeps its numbers`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press", currentWorkingWeight = 82.5, defaultRestSeconds = 90)

        viewModel.uiState.test {
            awaitState { !it.isLoading }

            viewModel.addExercise()
            awaitState { it.form != null }
            viewModel.setFormName("bench press")
            viewModel.setFormWeight("100")
            viewModel.saveForm()
            awaitState { it.form == null }

            cancelAndIgnoreRemainingEvents()
        }

        assertEquals("one entry, not two", 1, exerciseRepository.observeAll().first().size)
        assertEquals("and the number it has been progressing survives", 82.5, exerciseRepository.findById(bench.id)!!.currentWorkingWeight!!, 0.0)
    }

    @Test
    fun `editing writes the name, the base weight and the default rest`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press", defaultRestSeconds = 90)
        exerciseRepository.updateCurrentWorkingWeight(bench.id, 82.5)
        routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench.id, sets = 4, reps = "8-10"))

        viewModel.uiState.test {
            val row = awaitState { it.exercises.isNotEmpty() }.exercises.single()
            viewModel.openEditForm(row)

            val open = awaitState { it.form != null && it.formPlans.isNotEmpty() }
            val form = open.form!!
            assertEquals(ExerciseFormMode.EditDictionary, form.mode)
            assertEquals("the weight field shows the number the row shows", "82.5", form.weight)
            assertEquals("and the plans ride along for the read-only list", 1, open.formPlans.size)

            viewModel.setFormName("Bench Press Heavy")
            viewModel.setFormRest("150")
            viewModel.setFormWeight("85")
            viewModel.saveForm()

            awaitState { it.form == null }

            cancelAndIgnoreRemainingEvents()
        }

        val stored = exerciseRepository.findById(bench.id)!!
        assertEquals("Bench Press Heavy", stored.name)
        assertEquals(85.0, stored.currentWorkingWeight!!, 0.0)
        assertEquals(150, stored.defaultRestSeconds)
    }

    @Test
    fun `a name another entry holds is refused, and nothing is written`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press", defaultRestSeconds = 90)
        exerciseRepository.updateCurrentWorkingWeight(bench.id, 82.5)
        exerciseRepository.findOrCreate("Cable Fly")

        viewModel.uiState.test {
            val row = awaitState { it.exercises.size == 2 }.exercises.single { it.name == "Bench Press" }
            viewModel.openEditForm(row)
            awaitState { it.form != null }

            viewModel.setFormName("Cable Fly")
            viewModel.setFormWeight("100")
            viewModel.setFormRest("200")
            viewModel.saveForm()

            val refused = awaitState { it.form?.error == ExerciseFormError.NameTaken }
            assertNotNull("the form stays open with the typed name", refused.form)
            assertEquals("Cable Fly", refused.form!!.name)

            cancelAndIgnoreRemainingEvents()
        }

        val stored = exerciseRepository.findById(bench.id)!!
        assertEquals("the name did not move", "Bench Press", stored.name)
        assertEquals("nor did the weight, which was in the same form", 82.5, stored.currentWorkingWeight!!, 0.0)
        assertEquals("nor the rest time", 90, stored.defaultRestSeconds)
    }

    @Test
    fun `deleting an exercise no routine plans removes it`() = runTest {
        exerciseRepository.findOrCreate("Bench Press")

        viewModel.uiState.test {
            val row = awaitState { it.exercises.size == 1 }.exercises.single()
            viewModel.askDelete(row)
            awaitState { it.pendingDelete != null }

            viewModel.confirmDelete()

            val empty = awaitState { it.pendingDelete == null && it.exercises.isEmpty() }
            assertEquals(0, empty.totalCount)

            cancelAndIgnoreRemainingEvents()
        }

        assertNull("gone from the dictionary", exerciseRepository.findByName("Bench Press"))
    }

    @Test
    fun `deleting a planned exercise is refused with the routine named and openable`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench.id))
        val routineId = routineRepository.observeRoutines().first().single().id

        viewModel.uiState.test {
            val row = awaitState { it.exercises.size == 1 }.exercises.single()
            viewModel.askDelete(row)
            awaitState { it.pendingDelete != null }

            viewModel.confirmDelete()

            val blocked = awaitState { it.blockedDelete.isNotEmpty() }
            assertEquals(listOf("Push Day"), blocked.blockedDelete.map { it.name })
            assertEquals("with the id, so the dialog can offer a way in", routineId, blocked.blockedDelete.single().id)

            viewModel.dismissBlockedDelete()
            awaitState { it.blockedDelete.isEmpty() }

            cancelAndIgnoreRemainingEvents()
        }

        assertNotNull("nothing was deleted", exerciseRepository.findByName("Bench Press"))
    }

    @Test
    fun `the sheet's delete goes through the same refusal as the row menu`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench.id))

        viewModel.uiState.test {
            val row = awaitState { it.exercises.size == 1 }.exercises.single()
            viewModel.openEditForm(row)
            awaitState { it.form != null }

            viewModel.askDeleteFromForm()
            val asking = awaitState { it.form == null && it.pendingDelete != null }
            assertEquals("the sheet closes behind the confirmation", "Bench Press", asking.pendingDelete!!.name)

            viewModel.confirmDelete()
            awaitState { it.blockedDelete.isNotEmpty() }

            cancelAndIgnoreRemainingEvents()
        }

        assertNotNull("still there, and still named", exerciseRepository.findById(bench.id))
    }
}
