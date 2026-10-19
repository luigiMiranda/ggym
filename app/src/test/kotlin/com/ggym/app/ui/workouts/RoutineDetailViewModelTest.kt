package com.ggym.app.ui.workouts

import app.cash.turbine.test
import com.ggym.app.testing.ViewModelTest
import com.ggym.app.testing.awaitState
import com.ggym.app.testing.givenExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The days of one routine: ordering at both ends, and deletes that take what they say they take.
 */
class RoutineDetailViewModelTest : ViewModelTest() {

    private var routineId: Long = 0

    private fun viewModel() = RoutineDetailViewModel(routineId, routineRepository)

    @Test
    fun `the days come back in order, each with its exercise count`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        val pushA = routineRepository.addDay(routineId, "Push A")
        val pushB = routineRepository.addDay(routineId, "Push B")
        val bench = database.givenExercise(name = "Bench Press")
        val fly = database.givenExercise(name = "Cable Fly")
        routineRepository.addPlannedExercise(pushA, bench, 4, "8-10", 120)
        routineRepository.addPlannedExercise(pushA, fly, 3, "12", 60)
        routineRepository.addPlannedExercise(pushB, bench, 5, "5", 180)

        viewModel().uiState.test {
            val state = awaitState { it.days.size == 2 }

            assertEquals("Push Day", state.routineName)
            assertEquals(listOf("Push A", "Push B"), state.days.map { it.name })
            assertEquals(listOf(2, 1), state.days.map { it.exerciseCount })
            assertEquals(listOf(pushA, pushB), state.days.map { it.id })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding a day appends it at the end`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        routineRepository.addDay(routineId, "Push A")
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.days.size == 1 }
            vm.startAddDay()
            vm.saveDayName("Push B")
            val state = awaitState { it.days.size == 2 }

            assertEquals(listOf("Push A", "Push B"), state.days.map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renaming a day changes only that day`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        routineRepository.addDay(routineId, "Push A")
        routineRepository.addDay(routineId, "Push B")
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { it.days.size == 2 }
            vm.startRenameDay(loaded.days.first().name, loaded.days.first())
            vm.saveDayName("Chest & Back")

            val state = awaitState { it.days.first().name == "Chest & Back" }
            assertEquals(listOf("Chest & Back", "Push B"), state.days.map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving a day down swaps it with the next one`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        val first = routineRepository.addDay(routineId, "Push A")
        routineRepository.addDay(routineId, "Push B")
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.days.size == 2 }
            vm.moveDay(first, up = false)

            val state = awaitState { it.days.first().name == "Push B" }
            assertEquals(listOf("Push B", "Push A"), state.days.map { it.name })

            val stored = routineRepository.observeRoutine(routineId).first()!!.days
            assertEquals("the stored order is the one the screen shows", listOf("Push B", "Push A"), stored.map { it.dayName })
            assertEquals("and the indices were rewritten, not just the list", listOf(0, 1), stored.map { it.orderIndex })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving the first day up and the last day down are no-ops`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        val first = routineRepository.addDay(routineId, "Push A")
        val last = routineRepository.addDay(routineId, "Push B")
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.days.size == 2 }

            vm.moveDay(first, up = true)
            vm.moveDay(last, up = false)

            assertEquals(
                "nothing moved, and nothing was written",
                listOf("Push A", "Push B"),
                routineRepository.observeRoutine(routineId).first()!!.days.map { it.dayName },
            )
            assertEquals(listOf(0, 1), routineRepository.observeRoutine(routineId).first()!!.days.map { it.orderIndex })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a dragged order writes the whole list and ignores ids the routine does not have`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        val first = routineRepository.addDay(routineId, "Push A")
        val second = routineRepository.addDay(routineId, "Push B")
        val third = routineRepository.addDay(routineId, "Push C")
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.days.size == 3 }

            // What a drag hands over: the new order, plus an id that was deleted while the finger
            // was down (the re-read inside the write is what catches it).
            vm.reorderDays(listOf(third, first, 9_999L))

            val state = awaitState { it.days.first().id == third }
            assertEquals("the missing day keeps its place at the end", listOf(third, first, second), state.days.map { it.id })

            val stored = routineRepository.observeRoutine(routineId).first()!!.days
            assertEquals(listOf(third, first, second), stored.map { it.id })
            assertEquals("the indices are rewritten, not just the list", listOf(0, 1, 2), stored.map { it.orderIndex })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a day asks first, then takes only that day`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        routineRepository.addDay(routineId, "Push A")
        routineRepository.addDay(routineId, "Push B")
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { it.days.size == 2 }
            vm.askDeleteDay(loaded.days.first())
            awaitState { it.pendingDelete != null }
            assertEquals("asking deletes nothing", 2, routineRepository.observeRoutine(routineId).first()!!.days.size)

            vm.confirmDelete()
            val state = awaitState { it.days.size == 1 && it.pendingDelete == null }

            assertEquals(listOf("Push B"), state.days.map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a routine deleted elsewhere reports itself missing instead of showing an empty list`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        routineRepository.addDay(routineId, "Push A")
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.days.size == 1 }

            routineRepository.deleteRoutine(routineId)

            val state = awaitState { it.isMissing }
            assertTrue(state.isMissing)
            assertEquals(emptyList<DayRow>(), state.days)
            assertEquals("", state.routineName)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the screen can rename the routine itself`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.startRenameRoutine("Push Day")
            vm.saveRoutineName("Push / Pull")

            // The rename and the dialog closing are two updates, so the state that carries the new
            // name can still carry the open dialog. Waiting for both is what "the rename landed"
            // means; asserting on the first state with the new name is a race.
            val state = awaitState { it.routineName == "Push / Pull" && it.nameDialog == null }
            assertEquals("Push / Pull", routineRepository.observeRoutines().first().single().name)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
