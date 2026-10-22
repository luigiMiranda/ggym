package com.ggym.app.ui.workouts

import app.cash.turbine.test
import com.ggym.app.domain.workout.ActiveWorkoutUseCases
import com.ggym.app.domain.workout.StartWorkoutResult
import com.ggym.app.testing.TestData
import com.ggym.app.testing.ViewModelTest
import com.ggym.app.testing.awaitState
import com.ggym.app.testing.givenExerciseNamed
import com.ggym.app.testing.givenPlannedDay
import com.ggym.app.testing.PlannedSlot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The routine list: what a card says, and what each verb in the menu does to the database.
 *
 * The assertions are mostly made against the repository rather than the state, because the promise
 * is about what was stored — the state is only how the user finds out.
 */
class WorkoutsViewModelTest : ViewModelTest() {

    /** The elapsed time moves with the ticker, which a test steps by hand. */
    private val ticks = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1).also { it.tryEmit(Unit) }

    private val viewModel by lazy {
        WorkoutsViewModel(routineRepository, historyRepository, exerciseRepository, now = { clock }, ticks = ticks)
    }

    private val active by lazy {
        ActiveWorkoutUseCases(
            routines = routineRepository,
            exercises = exerciseRepository,
            history = historyRepository,
            weights = workingWeights,
            now = { clock },
            zoneId = TestData.zone,
        )
    }

    @Test
    fun `the list loads before it claims to be empty`() = runTest {
        viewModel.uiState.test {
            assertTrue("the first state is a read in progress", awaitItem().isLoading)

            val loaded = awaitState { !it.isLoading }
            assertEquals(emptyList<RoutineCard>(), loaded.routines)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a saved routine appears with its day and exercise counts`() = runTest {
        val routineId = routineRepository.createRoutine("Push Day")
        val dayId = routineRepository.addDay(routineId, "Push A")
        val bench = exerciseRepository.findOrCreate("Bench Press")
        routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 120)

        viewModel.uiState.test {
            val state = awaitState { it.routines.isNotEmpty() }

            assertEquals(
                listOf(RoutineCard(routineId, "Push Day", dayCount = 1, exerciseCount = 1, setCount = 4)),
                state.routines,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the new-routine dialog saves a routine and closes`() = runTest {
        viewModel.uiState.test {
            awaitState { !it.isLoading }

            viewModel.startCreateRoutine()
            awaitState { it.nameDialog != null }

            viewModel.saveName("Pull Day")
            val closed = awaitState { it.nameDialog == null && it.routines.isNotEmpty() }

            assertEquals(listOf("Pull Day"), closed.routines.map { it.name })
            assertEquals(listOf("Pull Day"), routineRepository.observeRoutines().first().map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank name is not saved and the dialog stays open`() = runTest {
        viewModel.uiState.test {
            awaitState { !it.isLoading }
            viewModel.startCreateRoutine()
            awaitState { it.nameDialog != null }

            viewModel.saveName("   ")

            assertEquals("the dialog is still there", true, viewModel.uiState.first().nameDialog != null)
            assertEquals(emptyList<String>(), routineRepository.observeRoutines().first().map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renaming offers the current name and writes the new one`() = runTest {
        routineRepository.createRoutine("Push Day")

        viewModel.uiState.test {
            val loaded = awaitState { it.routines.isNotEmpty() }
            viewModel.startRenameRoutine(loaded.routines.single())

            assertEquals("Push Day", viewModel.uiState.first().nameDialog?.initialName)

            viewModel.saveName("Push / Pull")
            awaitState { it.routines.singleOrNull()?.name == "Push / Pull" }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting asks first, and only the confirmation removes the routine`() = runTest {
        routineRepository.createRoutine("Push Day")

        viewModel.uiState.test {
            val card = awaitState { it.routines.isNotEmpty() }.routines.single()
            viewModel.askDeleteRoutine(card)
            awaitState { it.pendingDelete != null }

            assertEquals("asking deletes nothing", listOf("Push Day"), routineRepository.observeRoutines().first().map { it.name })

            viewModel.confirmDelete()
            val emptied = awaitState { it.pendingDelete == null && it.routines.isEmpty() }

            assertTrue("the list is empty again", emptied.routines.isEmpty())
            assertEquals(emptyList<String>(), routineRepository.observeRoutines().first().map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancelling a delete keeps the routine and clears the question`() = runTest {
        routineRepository.createRoutine("Push Day")

        viewModel.uiState.test {
            val card = awaitState { it.routines.isNotEmpty() }.routines.single()
            viewModel.askDeleteRoutine(card)
            awaitState { it.pendingDelete != null }

            viewModel.cancelDelete()
            val cancelled = awaitState { it.pendingDelete == null }

            assertNull(cancelled.pendingDelete)
            assertEquals(listOf("Push Day"), cancelled.routines.map { it.name })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a running workout is offered first, with its progress and how long it has run`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 80.0)
        val dayId = routineRepository.givenPlannedDay(PlannedSlot(bench, sets = 3))
        val started = active.start(dayId) as StartWorkoutResult.Started
        clock += 23 * 60 * 1_000L

        viewModel.uiState.test {
            val state = awaitState { it.resume != null }

            assertEquals(
                ResumeCard(
                    started.sessionId,
                    dayName = "Push A",
                    completedSets = 0,
                    totalSets = 3,
                    elapsedMillis = 23 * 60 * 1_000L,
                    exerciseName = "Bench Press",
                ),
                state.resume,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the elapsed time follows the clock on the next tick`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 80.0)
        val dayId = routineRepository.givenPlannedDay(PlannedSlot(bench, sets = 3))
        active.start(dayId)

        viewModel.uiState.test {
            awaitState { it.resume != null }

            clock += 60_000L
            ticks.tryEmit(Unit)

            assertEquals(60_000L, awaitState { it.resume?.elapsedMillis == 60_000L }.resume?.elapsedMillis)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the card goes away when the workout is discarded`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 80.0)
        val dayId = routineRepository.givenPlannedDay(PlannedSlot(bench, sets = 3))
        val started = active.start(dayId) as StartWorkoutResult.Started

        viewModel.uiState.test {
            awaitState { it.resume != null }

            active.discard(started.sessionId)

            assertNull(awaitState { it.resume == null }.resume)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a workout whose routine was deleted can still be resumed`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 80.0)
        val dayId = routineRepository.givenPlannedDay(PlannedSlot(bench, sets = 3))
        val started = active.start(dayId) as StartWorkoutResult.Started
        routineRepository.deleteRoutine(routineRepository.observeRoutines().first().single().id)

        viewModel.uiState.test {
            val state = awaitState { it.resume != null }

            assertEquals(started.sessionId, state.resume?.sessionId)
            assertEquals(emptyList<RoutineCard>(), state.routines)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the library card counts the dictionary and the entries with no weight`() = runTest {
        exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 82.5)
        exerciseRepository.givenExerciseNamed("Cable Fly")

        viewModel.uiState.test {
            val state = awaitState { !it.isLoading && it.library.exerciseCount == 2 }

            assertEquals("one has a base weight, one does not", 1, state.library.withoutWeightCount)
            assertTrue("a dictionary with entries is a reason to show the card", !state.library.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a fresh install has an empty library card`() = runTest {
        viewModel.uiState.test {
            val state = awaitState { !it.isLoading }

            assertEquals(0, state.library.exerciseCount)
            assertEquals(0, state.library.withoutWeightCount)
            assertTrue(state.library.isEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
