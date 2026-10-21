package com.ggym.app.ui.workouts

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.workout.ActiveWorkoutUseCases
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.domain.workout.StartWorkoutResult
import com.ggym.app.domain.workout.WorkingWeightUseCases
import com.ggym.app.testing.FakeRestTimerCommands
import com.ggym.app.testing.FakeRestTimerStore
import com.ggym.app.testing.FakeWorkoutPreferences
import com.ggym.app.testing.PlannedSlot
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.TestData
import com.ggym.app.testing.givenExerciseNamed
import com.ggym.app.testing.givenPlannedDay
import com.ggym.app.ui.exercises.SlotEditorSheet
import com.ggym.app.ui.theme.GgymTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * "Start workout" on the training day, driven as a user drives it: the button, the resume-or-discard
 * question when a workout already runs, and the disabled state of a day with nothing planned.
 *
 * SDK 36 for the same reason as the other Compose suites: the framework's Espresso reflection.
 *
 * **One limitation, found here**: a text field inside a `Dialog` window never reports idle under
 * Robolectric — `NameInputDialog` (an `AlertDialog` with an `OutlinedTextField`) fails every
 * `waitForIdle` with `AppNotIdleException`, while the same field inside a `ModalBottomSheet`
 * (`SlotEditorSheetTest`) and an `AlertDialog` with no field (`ConfirmDeleteDialog`) both idle. So
 * the renamed-exercise *form* is exercised by `DayDetailViewModelTest` and
 * `RoomExerciseRepositoryTest`, and by the device gate; this class pins the entry point that opens
 * it.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class DayDetailScreenTest : RepositoryTest() {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val restStore = FakeRestTimerStore()
    private val restCommands = FakeRestTimerCommands()

    private val weights by lazy { WorkingWeightUseCases(routineRepository, exerciseRepository, historyRepository) }

    private val active by lazy {
        ActiveWorkoutUseCases(
            routines = routineRepository,
            exercises = exerciseRepository,
            history = historyRepository,
            weights = weights,
            now = { clock },
            zoneId = TestData.zone,
        )
    }

    private val timer by lazy {
        RestTimerUseCases(store = restStore, commands = restCommands, preferences = FakeWorkoutPreferences(), now = { clock })
    }

    private val opened = mutableListOf<Long>()
    private var routineId = 0L
    private var dayId = 0L

    private fun show() {
        val factory = viewModelFactory {
            initializer {
                DayDetailViewModel(
                    routineId = routineId,
                    routineDayId = dayId,
                    routines = routineRepository,
                    exercises = exerciseRepository,
                    weights = weights,
                    history = historyRepository,
                    workout = active,
                    restTimer = timer,
                )
            }
        }

        compose.setContent {
            GgymTheme {
                DayDetailScreen(
                    viewModelFactory = factory,
                    slotEditorViewModelFactory = { _ -> error("the slot editor is not part of this flow") },
                    onBack = {},
                    onAddExercise = {},
                    onOpenWorkout = { opened += it },
                )
            }
        }
    }

    private fun givenPushDay() = runBlocking {
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 80.0)
        val fly = exerciseRepository.givenExerciseNamed("Cable Fly")
        routineId = routineRepository.createRoutine("Push Day")
        dayId = routineRepository.addDay(routineId, "Push A")
        routineRepository.addPlannedExercise(dayId, bench, 4, "8-10", 120)
        routineRepository.addPlannedExercise(dayId, fly, 3, "12", 60)
    }

    @Test
    fun `start workout opens a new workout for the day`() {
        givenPushDay()
        show()
        waitForText("Start workout")
        compose.onNodeWithText("Start workout").performClick()

        awaitTrue("the workout was never opened") { opened.isNotEmpty() }
        val draft = runBlocking { historyRepository.observeDraft().first() }
        assertEquals("the screen opens the draft it started", listOf(draft!!.id), opened)
        assertEquals("Push A", draft.dayNameSnapshot)
    }

    @Test
    fun `with a workout already running the question names it, and resume opens that one`() {
        givenPushDay()
        val running = runBlocking { (active.start(dayId) as StartWorkoutResult.Started).sessionId }
        show()
        waitForText("Start workout")

        compose.onNodeWithText("Start workout").performClick()
        waitForText(context.getString(R.string.day_conflict_title))
        compose.onNodeWithText(
            context.getString(R.string.day_conflict_message, "Push A", "Push Day"),
        ).assertExists()

        compose.onNodeWithText(context.getString(R.string.day_conflict_resume)).performClick()

        awaitTrue("resume never opened the workout") { opened.isNotEmpty() }
        assertEquals(listOf(running), opened)
    }

    @Test
    fun `dragging the first handle moves that exercise below the next one`() {
        givenPushDay()
        show()
        waitForText("Bench Press")

        val before = runBlocking { slotIds() }
        compose.onAllNodesWithContentDescription(context.getString(R.string.action_reorder))[0]
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, 250f))
                up()
            }

        // The drop is a view-model write: idle the looper until the database has the new order.
        awaitTrue("the dragged order was never written") {
            runBlocking { slotIds() } == before.reversed()
        }
        assertEquals(before.reversed(), runBlocking { slotIds() })
    }

    @Test
    fun `a slot's row menu offers renaming the exercise beside editing the slot`() {
        givenPushDay()
        show()
        waitForText("Bench Press")

        compose.onAllNodesWithContentDescription(context.getString(R.string.action_more))[0].performClick()

        // The name on the row has its own verb, and it is not the same verb as editing the slot's
        // sets: one moves the dictionary entry, the other this day's numbers.
        compose.onNodeWithText(context.getString(R.string.exercise_rename_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_edit)).assertExists()
        compose.onNodeWithText(context.getString(R.string.slot_remove)).assertExists()

        // Opening and dismissing the rename form is deliberately *not* driven here: a text field
        // inside a dialog window never reports idle under Robolectric (see the class KDoc), so the
        // form's own flow — prefill, the `NOCASE` refusal, the rename landing — is
        // `DayDetailViewModelTest`'s and `RoomExerciseRepositoryTest`'s, and the device gate's.
    }

    @Test
    fun `a day with nothing planned shows the start action, disabled`() {
        routineId = runBlocking { routineRepository.createRoutine("Push Day") }
        dayId = runBlocking { routineRepository.addDay(routineId, "Rest Day") }
        show()

        waitForText("Start workout")
        compose.onNodeWithText("Start workout").assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.day_empty_title)).assertExists()
        assertTrue(opened.isEmpty())
    }


    /** The day's slots in stored order, as exercise ids. */
    private suspend fun slotIds(): List<Long> =
        routineRepository.observeRoutine(routineId).first()!!
            .days
            .single { it.id == dayId }
            .plannedExercises
            .map { it.exerciseId }

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Looper-idling poll: the start is a view-model coroutine, which only moves while the looper does. */
    private fun awaitTrue(message: String, predicate: () -> Boolean) {
        repeat(WAIT_ATTEMPTS) {
            compose.waitForIdle()
            if (predicate()) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(message)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val WAIT_ATTEMPTS = 100
        const val POLL_MILLIS = 50L
    }
}
