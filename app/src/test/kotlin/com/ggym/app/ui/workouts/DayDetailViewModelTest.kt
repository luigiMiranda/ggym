package com.ggym.app.ui.workouts

import app.cash.turbine.test
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.workout.ActiveWorkoutUseCases
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.domain.workout.StartWorkoutResult
import com.ggym.app.testing.FakeRestTimerCommands
import com.ggym.app.testing.FakeRestTimerStore
import com.ggym.app.testing.FakeWorkoutPreferences
import com.ggym.app.testing.RecordedCommand
import com.ggym.app.testing.TestData
import com.ggym.app.testing.ViewModelTest
import com.ggym.app.testing.awaitState
import com.ggym.app.testing.givenExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The day list: the weight each row shows, and the order the user arranged.
 *
 * The weights here are the three-way case the rule is about — a slot's own 80, the dictionary's
 * 87.5, the last performed set's 85 — because a row that picked the wrong source would still look
 * right if two of them agreed.
 */
class DayDetailViewModelTest : ViewModelTest() {

    private var routineId: Long = 0
    private var dayId: Long = 0

    private val restStore = FakeRestTimerStore()
    private val restCommands = FakeRestTimerCommands()

    private val timer by lazy {
        RestTimerUseCases(store = restStore, commands = restCommands, preferences = FakeWorkoutPreferences(), now = { clock })
    }

    private val activeWorkout by lazy {
        ActiveWorkoutUseCases(
            routines = routineRepository,
            exercises = exerciseRepository,
            history = historyRepository,
            weights = workingWeights,
            now = { clock },
            zoneId = TestData.zone,
        )
    }

    /** "Push Day" with one "Push A" day that plans a bench press: the smallest day that can start. */
    private suspend fun givenStartableDay() {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 120)
    }

    private fun viewModel() = DayDetailViewModel(
        routineId = routineId,
        routineDayId = dayId,
        routines = routineRepository,
        exercises = exerciseRepository,
        weights = workingWeights,
        history = historyRepository,
        workout = activeWorkout,
        restTimer = timer,
    )

    @Test
    fun `every row shows a weight, and only the slot's own number is marked as its own`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        exerciseRepository.updateCurrentWorkingWeight(bench.id, 87.5)
        historyRepository.saveSession(
            TestData.session(
                date = TestData.date(month = 1, day = 5),
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = bench.id,
                        sets = listOf(TestData.setLog(weight = 85.0, reps = 6)),
                    ),
                ),
            ),
        )
        val fly = exerciseRepository.findOrCreate("Cable Fly")
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 120, weightKg = 80.0)
        routineRepository.addPlannedExercise(dayId, bench.id, 3, "12", 60, weightKg = null)
        routineRepository.addPlannedExercise(dayId, fly.id, 3, "12", 60, weightKg = null)

        viewModel().uiState.test {
            val state = awaitState { it.slots.size == 3 }
            val rows = state.slots

            assertEquals(listOf("Bench Press", "Bench Press", "Cable Fly"), rows.map { it.exerciseName })
            assertEquals(
                "the slot's own 80, the dictionary's 87.5, and nothing at all for the unseen exercise",
                listOf(80.0, 87.5, null),
                rows.map { it.weightKg },
            )
            assertEquals("only the first carries its own number", listOf(true, false, false), rows.map { it.hasOwnWeight })
            assertEquals(listOf(4, 3, 3), rows.map { it.targetSets })
            assertEquals(listOf("8-10", "12", "12"), rows.map { it.targetReps })
            assertEquals(listOf(120, 60, 60), rows.map { it.restSeconds })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving a slot down swaps it with the next one`() = runTest {
        val bench = database.givenExercise(name = "Bench Press")
        val fly = database.givenExercise(name = "Cable Fly")
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        val benchSlot = routineRepository.addPlannedExercise(dayId, bench, 4, "8-10", 120)
        routineRepository.addPlannedExercise(dayId, fly, 3, "12", 60)
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.slots.size == 2 }
            vm.moveSlot(benchSlot, up = false)

            val state = awaitState { it.slots.first().exerciseName == "Cable Fly" }
            assertEquals(listOf("Cable Fly", "Bench Press"), state.slots.map { it.exerciseName })

            val stored = routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises
            assertEquals(listOf("Cable Fly", "Bench Press"), stored.map { if (it.exerciseId == fly) "Cable Fly" else "Bench Press" })
            assertEquals(listOf(0, 1), stored.map { it.orderIndex })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving the first slot up and the last one down are no-ops`() = runTest {
        val bench = database.givenExercise(name = "Bench Press")
        val fly = database.givenExercise(name = "Cable Fly")
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        val benchSlot = routineRepository.addPlannedExercise(dayId, bench, 4, "8-10", 120)
        val flySlot = routineRepository.addPlannedExercise(dayId, fly, 3, "12", 60)
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.slots.size == 2 }

            vm.moveSlot(benchSlot, up = true)
            vm.moveSlot(flySlot, up = false)

            val stored = routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises
            assertEquals(listOf(benchSlot, flySlot), stored.map { it.id })
            assertEquals(listOf(0, 1), stored.map { it.orderIndex })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing a slot asks first, then leaves the others alone`() = runTest {
        val bench = database.givenExercise(name = "Bench Press")
        val fly = database.givenExercise(name = "Cable Fly")
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        routineRepository.addPlannedExercise(dayId, bench, 4, "8-10", 120)
        routineRepository.addPlannedExercise(dayId, fly, 3, "12", 60)
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { it.slots.size == 2 }
            vm.askDeleteSlot(loaded.slots.first())
            awaitState { it.pendingDelete != null }
            assertEquals("asking removes nothing", 2, routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises.size)

            vm.confirmDelete()
            val state = awaitState { it.slots.size == 1 && it.pendingDelete == null }

            assertEquals(listOf("Cable Fly"), state.slots.map { it.exerciseName })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a deleted day reports itself missing`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading }

            routineRepository.deleteDay(dayId)

            val state = awaitState { it.isMissing }
            assertTrue(state.isMissing)
            assertEquals(emptyList<SlotRow>(), state.slots)
            assertNull(state.pendingDelete)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `starting a day opens the workout it started`() = runTest {
        givenStartableDay()
        val vm = viewModel()

        vm.effects.test {
            vm.startWorkout()

            val opened = awaitItem() as DayDetailEffect.OpenWorkout
            val draft = historyRepository.observeDraft().first()!!
            assertEquals("the effect names the one draft that exists", draft.id, opened.sessionId)
            assertEquals("Push A", draft.dayNameSnapshot)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a second start asks what to do with the running workout and writes nothing`() = runTest {
        givenStartableDay()
        val running = (activeWorkout.start(dayId) as StartWorkoutResult.Started).sessionId
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.slots.size == 1 }

            vm.startWorkout()
            val conflict = awaitState { it.startConflict != null }.startConflict!!

            assertEquals(StartConflict(running, runningDayName = "Push A", runningRoutineName = "Push Day"), conflict)
            assertEquals("still one draft", listOf(running), historyRepository.observeDraft().first()?.let { listOf(it.id) })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resume opens the running workout and changes nothing in it`() = runTest {
        givenStartableDay()
        val running = (activeWorkout.start(dayId) as StartWorkoutResult.Started).sessionId
        val vm = viewModel()

        vm.effects.test {
            vm.uiState.test {
                awaitState { it.slots.size == 1 }
                vm.startWorkout()
                awaitState { it.startConflict != null }
                cancelAndIgnoreRemainingEvents()
            }

            vm.resumeRunningWorkout()

            assertEquals(DayDetailEffect.OpenWorkout(running), awaitItem())
            assertEquals(running, historyRepository.observeDraft().first()?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discard and start replaces the running workout, stops its rest, and opens the new one`() = runTest {
        givenStartableDay()
        val old = (activeWorkout.start(dayId) as StartWorkoutResult.Started).sessionId
        restStore.set(RestTimerState(endsAtMillis = clock + 90_000, totalSeconds = 90, exerciseLogId = 1, exerciseName = "Bench Press"))
        val vm = viewModel()

        vm.effects.test {
            vm.uiState.test {
                awaitState { it.slots.size == 1 }
                vm.startWorkout()
                awaitState { it.startConflict != null }
                cancelAndIgnoreRemainingEvents()
            }

            vm.discardRunningAndStart()

            val opened = awaitItem() as DayDetailEffect.OpenWorkout
            val draft = historyRepository.observeDraft().first()!!
            assertTrue("a new workout, not the old one", draft.id != old && draft.id == opened.sessionId)
            assertTrue("the running rest belonged to the discarded workout", restCommands.received.contains(RecordedCommand.Stop))
            assertNull("the old draft is gone", historyRepository.observeDraft(old).first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the question changes nothing`() = runTest {
        givenStartableDay()
        val running = (activeWorkout.start(dayId) as StartWorkoutResult.Started).sessionId
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.slots.size == 1 }
            vm.startWorkout()
            awaitState { it.startConflict != null }

            vm.dismissStartConflict()
            assertNull(awaitState { it.startConflict == null }.startConflict)
            assertEquals(running, historyRepository.observeDraft().first()?.id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a day with nothing planned starts nothing`() = runTest {
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Rest Day")
        val vm = viewModel()

        vm.effects.test {
            vm.startWorkout()

            expectNoEvents()
            assertNull(historyRepository.observeDraft().first())
        }
    }
}
