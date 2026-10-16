package com.ggym.app.ui.workout

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
import com.ggym.app.testing.RecordedCommand
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.TestData
import com.ggym.app.testing.givenExerciseNamed
import com.ggym.app.testing.givenPlannedDay
import com.ggym.app.ui.theme.GgymTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The active workout, driven as a user drives it: the overview, the focused pager, the hero, the
 * chips, the rest banner and the way out.
 *
 * These are the flows a ViewModel test cannot see — which control is where, whether a tap reaches
 * it, whether the pager and the state stay the same exercise — and they run on the JVM under
 * Robolectric against the real repositories on an in-memory database.
 *
 * **Why SDK 36 and not the app's 37**: as in `SlotEditorSheetTest`, the Compose test framework
 * idles through Espresso under Robolectric, and Espresso 3.5 initializes its input injector by
 * reflecting on the hidden `InputManager.getInstance()` that AOSP removed in Android 17. The
 * framework jar a UI test draws its widgets from is not what this milestone verifies; the database
 * and repository tests keep the app's API 37.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ActiveWorkoutScreenTest : RepositoryTest() {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val restStore = FakeRestTimerStore()
    private val restCommands = FakeRestTimerCommands()

    /** The screen's clock is the test's, and its ticker is driven by hand. */
    private val ticks = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1).also { it.tryEmit(Unit) }

    private val ended = AtomicBoolean(false)
    private var sessionId = 0L
    private val exerciseIds = mutableMapOf<String, Long>()

    private val active by lazy {
        ActiveWorkoutUseCases(
            routines = routineRepository,
            exercises = exerciseRepository,
            history = historyRepository,
            weights = WorkingWeightUseCases(routineRepository, exerciseRepository, historyRepository),
            now = { clock },
            zoneId = TestData.zone,
        )
    }

    private val timer by lazy {
        RestTimerUseCases(
            store = restStore,
            commands = restCommands,
            preferences = FakeWorkoutPreferences(),
            now = { clock },
        )
    }

    @Test
    fun `a set is completed from the focused view and lands in the database`() {
        runBlocking { seed() }
        show()

        // The overview: the exercise, what is planned, and the weight to load.
        waitForText("Bench Press")
        compose.onNodeWithText("0 / 4 sets").assertExists()
        compose.onNodeWithText("80 kg").assertExists()

        // Tapping the card opens the focused page on that exercise.
        compose.onNodeWithText("Bench Press").performClick()
        waitForText("Exercise 1 of 2")
        compose.onNodeWithText("80").assertExists()

        // The hero is the field: 80 goes to 85 (a 2.5 step twice, the plate pair the buttons are for).
        compose.onNode(hasSetTextAction()).performTextReplacement("85")
        compose.onNodeWithText("85").assertExists()

        compose.onNodeWithText(context.getString(R.string.workout_complete_set)).performClick()

        // The write is a view-model coroutine: poll the looper (not `waitUntil`, which would never
        // let it run) until the set — and then the dictionary behind it — is in the database.
        awaitStored { row(0, 0)?.isCompleted == true }
        val bench = exerciseIds.getValue("Bench Press")
        awaitStored { runBlocking { exerciseRepository.findById(bench)?.currentWorkingWeight } == 85.0 }
        awaitTrue("the rest starts for the exercise") { restCommands.received.isNotEmpty() }

        val first = runBlocking { row(0, 0)!! }
        assertEquals(85.0, first.weight, 0.0)
        assertTrue(first.isCompleted)
        assertEquals(
            "the sets behind the completed one follow it",
            listOf(85.0, 85.0, 85.0),
            runBlocking { (1..3).map { row(0, it)!!.weight } },
        )
        assertEquals(
            "and the exercise's working weight follows too",
            85.0,
            runBlocking { exerciseRepository.findById(bench)!!.currentWorkingWeight!! },
            0.0,
        )
        val rest = restCommands.received.single() as RecordedCommand.Start
        assertEquals("Bench Press", rest.state.exerciseName)
        assertEquals(clock + 120_000, rest.state.endsAtMillis)
    }

    @Test
    fun `a set can be added and removed from the strip`() {
        // One planned set, so the whole strip fits on screen and every chip is tappable.
        runBlocking { seed(benchSets = 1) }
        show()

        waitForText("Bench Press")
        compose.onNodeWithText("Bench Press").performClick()
        waitForText("Exercise 1 of 2")

        val before = runBlocking { sets(0).map { it.id } }

        compose.onNodeWithText(context.getString(R.string.workout_add_set)).performScrollTo().performClick()

        // Two waits, and both are needed: the write is a `viewModelScope` coroutine (the database
        // polls with `waitForIdle`), and the strip grows when the new state reaches the composition
        // (`waitUntil` drives the frames).
        awaitStored { sets(0).size == 2 }
        awaitChip(2)
        val added = runBlocking { sets(0).last() }
        assertEquals("the new set inherits the last one's numbers", 80.0, added.weight, 0.0)
        assertEquals(8, added.reps)

        // The new set is unperformed, so it is the one the strip lets the user take back.
        // Every remove button names its own set, so the tap cannot land on the wrong chip.
        compose.onNodeWithContentDescription(context.getString(R.string.workout_remove_set, 2)).performClick()
        awaitStored { sets(0).size == 1 }
        awaitChipGone(2)
        assertEquals("the planned set is the one that was there", before, runBlocking { sets(0).map { it.id } })
    }

    @Test
    fun `the rest banner shows what the stored deadline says`() {
        runBlocking { seed() }
        show()
        waitForText("Bench Press")

        // What the service writes when a rest starts: a deadline, not a remaining count.
        restStore.current.value = RestTimerState(
            endsAtMillis = clock + 90_000,
            totalSeconds = 90,
            exerciseLogId = 1,
            exerciseName = "Bench Press",
        )

        waitForText("01:30")

        // Half a minute later, the banner is recomputed from the deadline on the next tick.
        clock += 30_000
        ticks.tryEmit(Unit)
        waitForText("01:00")
    }

    @Test
    fun `back from the overview asks what to do with the workout instead of dropping it`() {
        runBlocking { seed() }
        var back: OnBackPressedDispatcher? = null
        show(captureBack = { back = it })

        waitForText("Bench Press")
        compose.onNodeWithText("Bench Press").performClick()
        waitForText("Exercise 1 of 2")

        // Focused → overview, and the workout is still running.
        compose.runOnIdle { back!!.onBackPressed() }
        waitForText("0 / 4 sets")
        compose.onNodeWithText(context.getString(R.string.workout_complete_set)).assertDoesNotExist()

        // Overview → the finish sheet: leaving is a decision, not a back gesture.
        compose.runOnIdle { back!!.onBackPressed() }
        waitForText(context.getString(R.string.workout_keep_training))
        compose.onNodeWithText(context.getString(R.string.workout_finish_confirm)).assertExists()
    }

    @Test
    fun `finishing saves the workout and leaves the screen`() {
        runBlocking { seed() }
        show()
        waitForText("Bench Press")

        compose.onNodeWithText(context.getString(R.string.workout_finish)).performClick()
        waitForText(context.getString(R.string.workout_finish_title))
        compose.onNodeWithText(context.getString(R.string.workout_finish_confirm)).performClick()

        awaitStored { runBlocking { historyRepository.observeDraft().first() } == null }
        awaitTrue("the screen is told to leave") { ended.get() }
        assertEquals(1, runBlocking { historyRepository.observeSessionsInRange(TestData.date(month = 6, day = 15), TestData.date(month = 6, day = 15)).first() }.size)
    }

    private fun show(captureBack: (OnBackPressedDispatcher) -> Unit = {}) {
        val factory = viewModelFactory {
            initializer {
                ActiveWorkoutViewModel(
                    sessionId = sessionId,
                    history = historyRepository,
                    workout = active,
                    restTimer = timer,
                    now = { clock },
                    ticks = ticks,
                    locale = Locale.ENGLISH,
                )
            }
        }

        compose.setContent {
            captureBack(LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher)
            GgymTheme {
                ActiveWorkoutScreen(
                    viewModelFactory = factory,
                    onWorkoutEnded = { ended.set(true) },
                )
            }
        }
    }

    /** A push day whose first exercise carries a weight, so the hero has a real number to show. */
    private suspend fun seed(benchSets: Int = 4) {
        clock = TestData.timestamp(TestData.date(month = 6, day = 15)) + 8 * 60 * 60 * 1000
        val bench = exerciseRepository.givenExerciseNamed("Bench Press", workingWeight = 87.5)
        val fly = exerciseRepository.givenExerciseNamed("Cable Fly")
        exerciseIds["Bench Press"] = bench
        exerciseIds["Cable Fly"] = fly

        val dayId = routineRepository.givenPlannedDay(
            PlannedSlot(exerciseId = bench, sets = benchSets, reps = "8-10", restSeconds = 120, weightKg = 80.0),
            PlannedSlot(exerciseId = fly, sets = 3, reps = "12", restSeconds = 60),
        )
        sessionId = (active.start(dayId) as StartWorkoutResult.Started).sessionId
    }


    /** One set of the draft, read fresh from the database. */
    private suspend fun row(logIndex: Int, setIndex: Int) = sets(logIndex).getOrNull(setIndex)

    private suspend fun sets(logIndex: Int) = requireNotNull(historyRepository.observeDraft(sessionId).first()) {
        "the workout is still running"
    }.exercises[logIndex].sets.sortedBy { it.setNumber }

    /**
     * Idles until the database says so.
     *
     * A plain `waitUntil` is not enough: the write is a coroutine on `viewModelScope`, which under
     * Robolectric only progresses while the main looper is idled — what `waitForIdle` does and what
     * polling the condition alone does not.
     */
    private fun awaitStored(predicate: suspend () -> Boolean) {
        awaitTrue("the database never reached the expected state") { runBlocking { predicate() } }
    }

    /** Idles the looper until a condition that is not a database read holds. */
    private fun awaitTrue(message: String, predicate: () -> Boolean) {
        repeat(WAIT_ATTEMPTS) {
            compose.waitForIdle()
            if (predicate()) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(message)
    }

    /** Waits for one set chip to be on screen, by the position it shows. */
    private fun awaitChip(position: Int) = waitForText(position.toString())

    private fun awaitChipGone(position: Int) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(position.toString()).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val WAIT_ATTEMPTS = 100
        const val POLL_MILLIS = 50L
    }
}
