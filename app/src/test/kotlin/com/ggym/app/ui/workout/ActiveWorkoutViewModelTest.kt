package com.ggym.app.ui.workout

import app.cash.turbine.test
import com.ggym.app.domain.model.WorkoutSession
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.workout.ActiveWorkoutUseCases
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.domain.workout.StartWorkoutResult
import com.ggym.app.testing.FakeRestTimerCommands
import com.ggym.app.testing.FakeRestTimerStore
import com.ggym.app.testing.FakeWorkoutPreferences
import com.ggym.app.testing.PlannedSlot
import com.ggym.app.testing.RecordedCommand
import com.ggym.app.testing.TestData
import com.ggym.app.testing.ViewModelTest
import com.ggym.app.testing.awaitState
import com.ggym.app.testing.countRows
import com.ggym.app.testing.givenExerciseNamed
import com.ggym.app.testing.givenPlannedDay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The active workout as a screen: what the overview says, what the hero edits, what a completion
 * does, and what the two endings mean.
 *
 * Everything is driven through the real repositories on a real in-memory database and the real
 * workout use cases; the timer is the one thing faked, because a foreground service has no business
 * being started by a unit test. The numbers are the M2/M3 fixture numbers on purpose: the
 * dictionary holds 87.5, the bench slot plans 80.0, and the user completes at 82.5 — a rule that
 * read the wrong source would still look right whenever two of them agreed.
 */
class ActiveWorkoutViewModelTest : ViewModelTest() {

    private val restStore = FakeRestTimerStore()
    private val restCommands = FakeRestTimerCommands()
    private val restPreferences = FakeWorkoutPreferences()

    /** The screen's ticker, driven by the test: one emission is one frame of the clock. */
    private val ticks = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1).also {
        // The state is a `combine`, so it needs one emission from every source to exist at all;
        // the real ticker emits as soon as it is collected.
        it.tryEmit(Unit)
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

    private val timer by lazy {
        RestTimerUseCases(
            store = restStore,
            commands = restCommands,
            preferences = restPreferences,
            now = { clock },
        )
    }

    private var sessionId = 0L

    private fun viewModel() = ActiveWorkoutViewModel(
        sessionId = sessionId,
        history = historyRepository,
        workout = active,
        restTimer = timer,
        preferences = restPreferences,
        now = { clock },
        ticks = ticks,
        locale = Locale.ENGLISH,
    )

    @Test
    fun `the overview shows the day, what is done and how long it has been running`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 87.5)
        val fly = exerciseRepository.givenExerciseNamed("Cable Fly")
        clock = TestData.timestamp(TestData.date(month = 6, day = 15)) + 8 * 60 * 60 * 1000
        val dayId = routineRepository.givenPlannedDay(
            PlannedSlot(exerciseId = bench, sets = 4, reps = "8-10", restSeconds = 120, weightKg = 80.0),
            PlannedSlot(exerciseId = fly, sets = 3, reps = "12", restSeconds = 60),
        )
        sessionId = started(dayId)
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading && it.exercises.size == 2 }
            assertEquals("the day, not the routine", "Push A", loaded.dayName)
            assertEquals("Push Day", loaded.routineName)
            assertEquals(0, loaded.completedSets)
            assertEquals(7, loaded.totalSets)
            assertEquals(2, loaded.skippedExercises)
            assertEquals(0, loaded.elapsedSeconds)
            assertEquals(
                "the slot's 80 kg, and nothing at all for the exercise the day does not weight",
                listOf(80.0, null),
                loaded.exercises.map { it.workingWeightKg },
            )
            assertFalse("nothing has happened yet", loaded.exercises.any { it.isStarted })

            // Eight minutes of training later the tick moves the clock; the workout is still there.
            clock += 8 * 60 * 1000
            ticks.emit(Unit)

            val later = awaitState { it.elapsedSeconds == 480L }
            assertEquals(480, later.elapsedSeconds)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the focused view points the hero at the first set that has not been performed`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 87.5)
        clock = TestData.timestamp(TestData.date(month = 6, day = 15))
        sessionId = started(
            routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench, sets = 3, reps = "8-10", weightKg = 80.0)),
        )
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading }
            val firstSet = loaded.exercises[0].sets.first()
            val secondSet = loaded.exercises[0].sets[1]

            vm.focusExercise(0)
            val focusedState = awaitState { it.focused != null }
            val focused = focusedState.focused!!

            assertEquals("Exercise 1 of 1", 0, focused.index)
            assertEquals("Bench Press", focused.exerciseName)
            assertEquals(3, focused.sets.size)
            assertEquals("the hero starts on the first set", firstSet.id, focused.hero.selectedSetId)
            assertEquals("80", focused.hero.weightText)
            assertEquals(8, focused.hero.reps)
            assertTrue(focused.hero.canComplete)
            assertEquals(FocusedAction.CompleteSet, focused.action)
            assertFalse("the exercise is not finished", focusedState.exercises[0].isFinished)

            // Tapping another chip moves the hero to it, with that set's own numbers.
            vm.selectSet(secondSet.id)
            val moved = awaitState { it.focused?.hero?.selectedSetId == secondSet.id }.focused!!
            assertEquals("80", moved.hero.weightText)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the hero holds what the user types until the set is completed`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 87.5)
        sessionId = started(
            routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench, sets = 3, reps = "8-10", weightKg = 80.0)),
        )
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading }
            val firstSet = loaded.exercises[0].sets.first()
            vm.focusExercise(0)
            awaitState { it.focused != null }

            // A half-typed number is not a weight, and nothing is written either way: the numbers
            // on the hero become the set's when it is *completed* (decision 5).
            vm.editWeight("82.")
            awaitState { it.focused?.hero?.weightText == "82." }
            assertEquals(80.0, row(firstSet.id).weight, 0.0)

            vm.editWeight("82.5")
            val typed = awaitState { it.focused?.hero?.weightText == "82.5" }
            assertEquals("the selected chip shows what the hero shows", 82.5, typed.focused!!.sets.first().weightKg, 0.0)
            assertEquals("but the row still holds the plan's number", 80.0, row(firstSet.id).weight, 0.0)
            assertEquals(
                "nothing has been lifted yet, so the dictionary does not move",
                87.5,
                exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
                0.0,
            )

            // One tap of +2.5: the plate pair the step exists for.
            vm.stepWeight(2.5)
            val stepped = awaitState { it.focused?.hero?.weightText == "85" }
            assertEquals(85.0, stepped.focused!!.sets.first().weightKg, 0.0)

            vm.stepReps(1)
            val reps = awaitState { it.focused?.hero?.reps == 9 }
            assertEquals("the weight is still the typed one", "85", reps.focused!!.hero.weightText)

            // Completing is what commits the numbers — to the set, to the sets behind it, and to
            // the exercise's working weight.
            vm.completeSelectedSet()
            val done = awaitState { it.exercises[0].completedSets == 1 }
            assertEquals(85.0, row(firstSet.id).weight, 0.0)
            assertEquals(9, row(firstSet.id).reps)
            assertTrue(row(firstSet.id).isCompleted)
            assertEquals(
                "the sets the user is about to do follow the number that was lifted",
                listOf(85.0, 85.0),
                done.exercises[0].sets.drop(1).map { it.weightKg },
            )
            assertEquals(85.0, exerciseRepository.findById(bench)!!.currentWorkingWeight!!, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing a set writes it, moves the working weight and starts the rest`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 87.5)
        clock = TestData.timestamp(TestData.date(month = 6, day = 15))
        sessionId = started(
            routineRepository.givenPlannedDay(
                PlannedSlot(exerciseId = bench, sets = 3, reps = "8-10", restSeconds = 120, weightKg = 80.0),
            ),
        )
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading }
            val firstSet = loaded.exercises[0].sets.first()
            vm.focusExercise(0)
            awaitState { it.focused != null }

            vm.editWeight("82.5")
            awaitState { it.focused?.hero?.weightText == "82.5" }
            vm.stepReps(1)
            awaitState { it.focused?.hero?.reps == 9 }

            vm.completeSelectedSet()

            val done = awaitState { it.exercises[0].completedSets == 1 && it.focused?.hero?.selectedSetId != firstSet.id }
            assertTrue(row(firstSet.id).isCompleted)
            assertEquals(82.5, row(firstSet.id).weight, 0.0)
            assertEquals(9, row(firstSet.id).reps)
            assertEquals(
                "the sets the user is about to do follow the number that was lifted",
                listOf(82.5, 82.5),
                done.exercises[0].sets.drop(1).map { it.weightKg },
            )
            assertEquals("the hero moves on to the next set", 82.5, done.focused!!.hero.weightText.toDouble(), 0.0)
            assertTrue("the exercise now counts as started", done.exercises[0].isStarted)
            assertEquals(1, done.completedSets)

            assertEquals(
                "the dictionary now says what was actually lifted",
                82.5,
                exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
                0.0,
            )

            val rest = restCommands.received.single() as RecordedCommand.Start
            assertEquals("Bench Press", rest.state.exerciseName)
            assertEquals(clock + 120_000, rest.state.endsAtMillis)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completing the last set turns the action into the next exercise`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press")
        val fly = exerciseRepository.givenExerciseNamed("Cable Fly")
        sessionId = started(
            routineRepository.givenPlannedDay(
                PlannedSlot(exerciseId = bench, sets = 2, reps = "8-10", restSeconds = 0, weightKg = 80.0),
                PlannedSlot(exerciseId = fly, sets = 2, reps = "12", restSeconds = 0, weightKg = 60.0),
            ),
        )
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading && it.exercises.size == 2 }
            vm.focusExercise(0)
            awaitState { it.focused != null }
            vm.completeSelectedSet()
            awaitState { it.exercises[0].completedSets == 1 }

            // The selection followed the first completion, so the second call performs set 2.
            vm.completeSelectedSet()
            val finished = awaitState { it.exercises[0].isFinished }
            assertEquals(FocusedAction.NextExercise, finished.focused!!.action)

            // With no rest time on the slot, nothing was started: a zero-length countdown is not one.
            assertTrue(restCommands.received.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a set can be added and removed`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press")
        sessionId = started(
            routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench, sets = 2, reps = "10", weightKg = 80.0)),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.focusExercise(0)
            awaitState { it.focused != null }

            vm.addSet()
            val added = awaitState { it.exercises[0].totalSets == 3 && it.focused?.sets?.size == 3 }.focused!!

            val newSet = added.sets.last()
            assertEquals("the new set is what the hero edits", newSet.id, added.hero.selectedSetId)
            assertEquals("it inherits the last set's numbers", 80.0, additionalSet().weight, 0.0)
            assertEquals(10, additionalSet().reps)

            vm.removeSet(newSet.id)
            val removed = awaitState { it.exercises[0].totalSets == 2 }.focused!!
            assertEquals("the hero falls back to the first unperformed set", 1, removed.sets.first().position)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un-completing a set writes it back to a plan and stops that exercise's rest`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 87.5)
        clock = TestData.timestamp(TestData.date(month = 6, day = 15))
        sessionId = started(
            routineRepository.givenPlannedDay(
                PlannedSlot(exerciseId = bench, sets = 3, reps = "8-10", restSeconds = 120, weightKg = 80.0),
            ),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.focusExercise(0)
            awaitState { it.focused != null }
            vm.editWeight("82.5")
            awaitState { it.focused?.hero?.weightText == "82.5" }
            vm.completeSelectedSet()
            val done = awaitState { it.exercises[0].completedSets == 1 }
            val setId = done.exercises[0].sets.first().id
            // The service is the timer's only writer and the fake commands only record, so the
            // countdown the completion asked for is stood up here.
            restStore.current.value = RestTimerState(
                endsAtMillis = clock + 120_000,
                totalSeconds = 120,
                exerciseLogId = draft().exercises[0].id,
                exerciseName = "Bench Press",
            )

            vm.uncompleteSet(setId)

            awaitState { it.exercises[0].completedSets == 0 }
            assertFalse("the row is a plan again", row(setId).isCompleted)
            assertEquals("the number stays on the set", 82.5, row(setId).weight, 0.0)
            assertTrue(
                "the rest that belonged to it is stopped",
                restCommands.received.contains(RecordedCommand.Stop),
            )
            assertEquals(
                "the dictionary is not reverted: nothing remembers the old value",
                82.5,
                exerciseRepository.findById(bench)!!.currentWorkingWeight!!,
                0.0,
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un-completing a set leaves another exercise's rest alone`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press")
        val fly = exerciseRepository.givenExerciseNamed("Cable Fly")
        clock = TestData.timestamp(TestData.date(month = 6, day = 15))
        sessionId = started(
            routineRepository.givenPlannedDay(
                PlannedSlot(exerciseId = bench, sets = 2, reps = "8-10", restSeconds = 120, weightKg = 80.0),
                PlannedSlot(exerciseId = fly, sets = 2, reps = "12", restSeconds = 120, weightKg = 60.0),
            ),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading && it.exercises.size == 2 }
            vm.focusExercise(0)
            awaitState { it.focused != null }
            vm.completeSelectedSet()
            val done = awaitState { it.exercises[0].completedSets == 1 }
            val setId = done.exercises[0].sets.first().id
            // A countdown for the *other* exercise: taking a bench set back must not silence it.
            restStore.current.value = RestTimerState(
                endsAtMillis = clock + 120_000,
                totalSeconds = 120,
                exerciseLogId = draft().exercises[1].id,
                exerciseName = "Cable Fly",
            )

            vm.uncompleteSet(setId)

            awaitState { it.exercises[0].completedSets == 0 }
            assertFalse(
                "a rest that is not this set's is left running",
                restCommands.received.contains(RecordedCommand.Stop),
            )

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the rest banner follows the stored deadline, not a local countdown`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press")
        clock = TestData.timestamp(TestData.date(month = 6, day = 15))
        sessionId = started(
            routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench, sets = 2, reps = "10", weightKg = 80.0)),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading }
            assertNull("no rest, no banner", vm.uiState.value.rest)

            // What the service would have written when the set was completed.
            restStore.current.value = RestTimerState(
                endsAtMillis = clock + 90_000,
                totalSeconds = 90,
                exerciseLogId = 1,
                exerciseName = "Bench Press",
            )
            val started = awaitState { it.rest != null }.rest!!
            assertEquals("Bench Press", started.exerciseName)
            assertEquals(90L, started.remainingSeconds)

            // Half a minute later the banner shows what the deadline says, not what was left when
            // the countdown started.
            clock += 30_000
            ticks.emit(Unit)
            assertEquals(60L, awaitState { it.rest?.remainingSeconds == 60L }.rest?.remainingSeconds)

            // The rest ended: the store is the truth about what is running.
            restStore.current.value = null
            assertNull(awaitState { it.rest == null }.rest)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `finishing saves the workout and tells the screen to leave`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press")
        val fly = exerciseRepository.givenExerciseNamed("Cable Fly")
        clock = TestData.timestamp(TestData.date(month = 6, day = 15)) + 18 * 60 * 60 * 1000
        sessionId = started(
            routineRepository.givenPlannedDay(
                PlannedSlot(exerciseId = bench, sets = 2, reps = "8-10", restSeconds = 120, weightKg = 80.0),
                PlannedSlot(exerciseId = fly, sets = 2, reps = "12", restSeconds = 60),
            ),
        )
        val vm = viewModel()
        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.focusExercise(0)
            awaitState { it.focused != null }
            vm.completeSelectedSet()
            awaitState { it.exercises[0].completedSets == 1 }
            cancelAndIgnoreRemainingEvents()
        }
        clock += 23 * 60 * 1000
        // What the service would have written when it took the start command: a rest is running.
        restStore.current.value = RestTimerState(
            endsAtMillis = clock + 90_000,
            totalSeconds = 90,
            exerciseLogId = 1,
            exerciseName = "Bench Press",
        )

        vm.effects.test {
            vm.askFinish()
            vm.confirmFinish()
            val ended = awaitItem()
            assertTrue("the screen is told to leave", ended is ActiveWorkoutEffect.Ended)
            val saved = historyRepository
                .observeSessionsInRange(TestData.date(month = 6, day = 15), TestData.date(month = 6, day = 15))
                .first()
                .single()
            assertEquals(
                "finishing writes a fresh session, so the effect names that one, not the draft",
                saved.id,
                (ended as ActiveWorkoutEffect.Ended).savedSessionId,
            )
        }

        assertNull("the draft is gone", historyRepository.observeDraft().first())
        val saved = historyRepository.observeSessionsInRange(TestData.date(month = 6, day = 15), TestData.date(month = 6, day = 15)).first().single()
        assertFalse("what was saved is history, not a draft", saved.isDraft)
        assertEquals("the workout belongs to the day it started on", TestData.date(month = 6, day = 15), saved.date)
        assertEquals(23 * 60L, saved.durationSeconds)
        assertEquals(listOf(1), saved.exercises.flatMap { it.sets }.map { it.setNumber })
        assertTrue(
            "the exercise nothing happened for is saved as skipped",
            saved.exercises.none { it.exerciseNameSnapshot == "Cable Fly" && it.wasCompleted },
        )
        assertTrue("a running rest does not outlive the workout", restCommands.received.last() is RecordedCommand.Stop)
    }

    @Test
    fun `discarding asks first, then throws the workout away`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press")
        sessionId = started(
            routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench, sets = 2, reps = "10", weightKg = 80.0)),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading }
            vm.askFinish()
            awaitState { it.isFinishSheetVisible }

            vm.askDiscard()
            val asking = awaitState { it.isDiscardDialogVisible }
            assertFalse("the summary sheet makes way for the dialog", asking.isFinishSheetVisible)

            vm.cancelDiscard()
            awaitState { !it.isDiscardDialogVisible }
            assertNotNull("cancelling keeps the workout", historyRepository.observeDraft().first())

            cancelAndIgnoreRemainingEvents()
        }

        // The service has a countdown running: discarding has to stop it.
        restStore.current.value = RestTimerState(
            endsAtMillis = clock + 60_000,
            totalSeconds = 60,
            exerciseLogId = 1,
            exerciseName = "Bench Press",
        )

        vm.effects.test {
            vm.confirmDiscard()
            assertEquals(ActiveWorkoutEffect.Ended(savedSessionId = null), awaitItem())
        }

        assertNull(historyRepository.observeDraft().first())
        assertEquals("nothing reached the history", 0, database.countRows("workout_session"))
        assertTrue("a running rest does not outlive the workout", restCommands.received.last() is RecordedCommand.Stop)
    }

    @Test
    fun `a workout that ended elsewhere reports itself missing`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press")
        sessionId = started(
            routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench, sets = 2, reps = "10", weightKg = 80.0)),
        )
        val vm = viewModel()

        vm.uiState.test {
            awaitState { !it.isLoading }

            // The workout is finished somewhere else — another process, or the notification.
            active.discard(sessionId)

            val missing = awaitState { it.isMissing }
            assertTrue(missing.isMissing)
            assertEquals("nothing is offered for a workout that is gone", 0, missing.totalSets)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an exercise whose sets were all removed offers to add one back`() = runTest {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press")
        sessionId = started(
            routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench, sets = 1, reps = "10", weightKg = 80.0)),
        )
        val vm = viewModel()

        vm.uiState.test {
            val loaded = awaitState { !it.isLoading }
            vm.focusExercise(0)
            awaitState { it.focused != null }

            vm.removeSet(loaded.exercises[0].sets.single().id)
            val empty = awaitState { it.focused?.sets?.isEmpty() == true }.focused!!
            assertEquals("there is nothing to complete, so the action offers a set", FocusedAction.AddSet, empty.action)
            assertFalse("and the hero has no weight to commit", empty.hero.canComplete)

            vm.addSet()
            val back = awaitState { it.focused?.sets?.size == 1 }.focused!!
            assertEquals(FocusedAction.CompleteSet, back.action)
            assertEquals(0.0, row(back.sets.single().id).weight, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Starts the workout and hands back its id, failing loudly when the start is refused. */
    private suspend fun started(routineDayId: Long): Long =
        (active.start(routineDayId) as StartWorkoutResult.Started).sessionId

    /** The draft as the database holds it, read fresh so an assertion cannot see a cached copy. */
    private suspend fun draft(): WorkoutSession = historyRepository.observeDraft(sessionId).first()!!

    private suspend fun row(setId: Long) = draft().exercises
        .flatMap { it.sets }
        .first { it.id == setId }

    private suspend fun additionalSet() = draft().exercises.first().sets.last()

    @Test
    fun `the notification prompt is due until the screen has asked once`() = runTest {
        // The answer is the user's: the screen asks, a denial is an ordinary outcome, and the
        // question is not asked again on the next workout.
        val vm = viewModel()

        vm.isNotificationPromptDue.test {
            assertTrue(awaitItem())

            vm.notificationPromptShown()

            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
