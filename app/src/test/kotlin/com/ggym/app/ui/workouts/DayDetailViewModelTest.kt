package com.ggym.app.ui.workouts

import app.cash.turbine.test
import com.ggym.app.domain.exercise.ExerciseFormUseCases
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
import com.ggym.app.ui.exercises.ExerciseFormError
import com.ggym.app.ui.exercises.ExerciseFormMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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
        formUseCases = ExerciseFormUseCases(routineRepository, exerciseRepository),
        locale = Locale.ENGLISH,
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
    fun `a dragged slot order is written as one list`() = runTest {
        val bench = database.givenExercise(name = "Bench Press")
        val fly = database.givenExercise(name = "Cable Fly")
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        val benchSlot = routineRepository.addPlannedExercise(dayId, bench, 4, "8-10", 120)
        val flySlot = routineRepository.addPlannedExercise(dayId, fly, 3, "12", 60)
        val vm = viewModel()

        vm.uiState.test {
            awaitState { it.slots.size == 2 }
            vm.reorderSlots(listOf(flySlot, benchSlot))

            val state = awaitState { it.slots.first().id == flySlot }
            assertEquals(listOf(flySlot, benchSlot), state.slots.map { it.id })

            val stored = routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises
            assertEquals(listOf(flySlot, benchSlot), stored.map { it.id })
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
    fun `opening a slot's form fills it from the row the user is looking at`() = runTest {
        givenStartableDay()
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading && it.slots.size == 1 }
            val row = loaded.slots.single()

            vm.openForm(row)

            val form = awaitState { it.form != null }.form!!
            assertEquals(ExerciseFormMode.EditSlot, form.mode)
            assertEquals("the form edits the row it was opened for", row.id, form.plannedExerciseId)
            assertEquals("and starts from the name on screen", "Bench Press", form.name)
            assertEquals("4", form.sets)
            assertEquals("8-10", form.reps)
            assertEquals("120", form.restSeconds)
            assertEquals("no weight of its own, so the field is blank", "", form.weight)
            assertEquals(false, form.isOwnWeight)
            assertEquals("and the user has not been in the field", false, form.weightTouched)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving the form writes the exercise's name and the day's numbers in one action`() = runTest {
        givenStartableDay()
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading && it.slots.size == 1 }
            vm.openForm(loaded.slots.single())
            awaitState { it.form != null }

            vm.setFormName("Barbell Bench Press")
            vm.stepFormSets(1)
            vm.setFormReps("5")
            vm.stepFormRest(-30)
            vm.setFormWeight("85")
            vm.saveForm()

            // The two writes land in two tables, so the state that satisfies "both are saved" is the
            // one that already carries the new numbers — waiting only for the name would race the
            // routine flow and read the slot as it was before the save.
            val saved = awaitState {
                it.form == null &&
                    it.slots.single().exerciseName == "Barbell Bench Press" &&
                    it.slots.single().targetSets == 5
            }
            assertNotNull("the dictionary moved", exerciseRepository.findByName("Barbell Bench Press"))
            assertNull("and the old name is gone", exerciseRepository.findByName("Bench Press"))

            val slot = saved.slots.single()
            assertEquals("the sets came from the stepper", 5, slot.targetSets)
            assertEquals("5", slot.targetReps)
            assertEquals("and the rest from its own", 90, slot.restSeconds)
            assertEquals(85.0, slot.weightKg)
            assertEquals("the day's own number is no longer inherited", true, slot.hasOwnWeight)

            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The bug this milestone exists for, in the form's own terms: the number an inherited slot shows
     * is there to be read, not to be written back. Pinning it would freeze the day at whatever it
     * happened to display, and the next logged set would never show up.
     */
    @Test
    fun `saving without touching an inherited weight leaves the slot inheriting it`() = runTest {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        exerciseRepository.updateCurrentWorkingWeight(bench.id, 87.5)
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 120, weightKg = null)
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading && it.slots.size == 1 }
            assertEquals("the row shows the exercise's number", 87.5, loaded.slots.single().weightKg)

            vm.openForm(loaded.slots.single())
            val form = awaitState { it.form != null }.form!!
            assertEquals("and the form opens with it in the field", "87.5", form.weight)

            vm.setFormReps("6")
            vm.saveForm()

            val saved = awaitState { it.form == null && it.slots.single().targetReps == "6" }
            assertEquals("the slot still has no weight of its own", false, saved.slots.single().hasOwnWeight)
            assertNull(
                "so a logged set is still free to move it",
                routineRepository.observeRoutine(routineId).first()!!.days.single().plannedExercises.single().weightKg,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a name another exercise holds keeps the form open and writes nothing`() = runTest {
        givenStartableDay()
        exerciseRepository.findOrCreate("Cable Fly")
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading && it.slots.size == 1 }
            vm.openForm(loaded.slots.single())
            awaitState { it.form != null }

            vm.setFormName("cable fly")
            vm.stepFormSets(2)
            vm.saveForm()

            val refused = awaitState { it.form?.error == ExerciseFormError.NameTaken }
            assertEquals("the row keeps the name it had", "Bench Press", refused.slots.single().exerciseName)
            assertNotNull("the entry it collided with is untouched", exerciseRepository.findByName("Cable Fly"))
            assertNotNull("and so is the one being edited", exerciseRepository.findByName("Bench Press"))
            assertEquals(
                "not even the sets were written",
                4,
                refused.slots.single().targetSets,
            )
            assertEquals("and the typed name is still there to fix", "cable fly", refused.form!!.name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the form's own controls move its numbers and clear the last error`() = runTest {
        givenStartableDay()
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading && it.slots.size == 1 }
            vm.openForm(loaded.slots.single())
            awaitState { it.form != null }

            // A refused save leaves the message on screen; the next edit is what takes it away.
            vm.setFormReps(" ")
            vm.saveForm()
            awaitState { it.form?.error == ExerciseFormError.RepsBlank }

            vm.stepFormSets(1)
            val stepped = awaitState { it.form?.sets == "5" }.form!!
            assertEquals("an edit clears the message about the old value", null, stepped.error)

            vm.stepFormRest(15)
            assertEquals("135", awaitState { it.form?.restSeconds == "135" }.form!!.restSeconds)

            // The two floors: one set, and no such thing as a negative rest.
            repeat(6) { vm.stepFormSets(-1) }
            assertEquals("1", awaitState { it.form?.sets == "1" }.form!!.sets)

            repeat(20) { vm.stepFormRest(-15) }
            assertEquals("0", awaitState { it.form?.restSeconds == "0" }.form!!.restSeconds)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing from inside the form deletes the slot and closes it`() = runTest {
        givenStartableDay()
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading && it.slots.size == 1 }
            vm.openForm(loaded.slots.single())
            awaitState { it.form != null }

            vm.removeFormSlot()

            val emptied = awaitState { it.form == null && it.slots.isEmpty() }
            assertTrue(emptied.slots.isEmpty())

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
