package com.ggym.app.ui.nav

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
import com.ggym.app.di.AppContainer
import com.ggym.app.ui.theme.GgymTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app shell, driven the way the bugs were found: a workout that ended somewhere else, the
 * button that should leave it, and the two tabs that must keep working afterwards.
 *
 * This is the real `GgymNavHost` over a real `AppContainer`, because the defect was in the
 * composition of navigation state — the one thing a screen test cannot see.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class GgymNavHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val container = AppContainer(context)

    private fun show() {
        compose.setContent {
            GgymTheme {
                GgymNavHost(container = container)
            }
        }
    }

    /** "Push Day" with one "Push A" day planning a bench press, seeded through the real repositories. */
    private fun seed() = runBlocking {
        val bench = container.exerciseRepository.findOrCreate("Bench Press")
        val routineId = container.routineRepository.createRoutine("Push Day")
        val dayId = container.routineRepository.addDay(routineId, "Push A")
        container.routineRepository.addPlannedExercise(dayId, bench.id, 4, "8-10", 120, weightKg = 80.0)
    }

    /** Workouts → routine → day → the running workout. */
    private fun openWorkout() {
        show()
        waitForText("Push Day")
        compose.onNodeWithText("Push Day").performClick()

        waitForText("Push A")
        compose.onNodeWithText("Push A").performClick()

        waitForText(context.getString(R.string.day_start_label))
        compose.onNodeWithText(context.getString(R.string.day_start_label)).performClick()

        awaitTrue("the workout never opened") {
            runBlocking { container.workoutHistoryRepository.observeDraft().first() } != null
        }
        compose.waitForIdle()
    }

    @Test
    fun `a workout that ended elsewhere can be left for the routine list`() {
        seed()
        openWorkout()
        val draft = runBlocking { container.workoutHistoryRepository.observeDraft().first() }!!

        // Finished from somewhere that is not this screen — what a second process, or a state left
        // behind after the user came back to a dead route, looks like.
        runBlocking { container.activeWorkoutUseCases.finish(draft.id) }

        waitForText(context.getString(R.string.workout_missing_title))
        compose.onNodeWithText(context.getString(R.string.workout_back_to_workouts)).performClick()

        // The list, not the same dead screen: the button must actually take the workout off the stack.
        waitForText("Push Day")
        assertEquals(0, count(context.getString(R.string.workout_missing_title)))
    }

    @Test
    fun `finishing from the workout lands on the routine list`() {
        seed()
        openWorkout()

        // The header's "Finish" opens the sheet; the sheet's own action is the one that saves.
        compose.onNodeWithText(context.getString(R.string.workout_finish_short)).performClick()
        waitForText(context.getString(R.string.workout_finish_title))
        compose.onNodeWithText(context.getString(R.string.workout_finish_confirm)).performClick()

        // The finish is a view-model write: idle the looper until the draft is actually gone, rather
        // than racing the assertion against a coroutine (the workout's own header says "Push Day"
        // too, so waiting for that text proves nothing).
        awaitTrue("the workout was never saved") {
            runBlocking { container.workoutHistoryRepository.observeDraft().first() } == null
        }

        // The workout ended: the screen must leave, not come back restored with its bottom bar gone.
        waitForText("Push Day")
        assertEquals(0, count(context.getString(R.string.workout_missing_title)))
        assertEquals(
            "the workout is saved and the draft is gone",
            null,
            runBlocking { container.workoutHistoryRepository.observeDraft().first() },
        )
    }

    @Test
    fun `the calendar tab opens`() {
        seed()
        show()

        waitForText("Push Day")
        compose.onNodeWithText(context.getString(R.string.nav_calendar)).performClick()

        waitForText(context.getString(R.string.calendar_empty_title))
    }

    @Test
    fun `the stats tab opens`() {
        seed()
        show()

        waitForText("Push Day")
        compose.onNodeWithText(context.getString(R.string.nav_stats)).performClick()

        waitForText(context.getString(R.string.stats_empty_title))
    }

    @Test
    fun `after leaving an ended workout the calendar still opens`() {
        seed()
        openWorkout()
        val draft = runBlocking { container.workoutHistoryRepository.observeDraft().first() }!!
        runBlocking { container.activeWorkoutUseCases.finish(draft.id) }

        waitForText(context.getString(R.string.workout_missing_title))
        compose.onNodeWithText(context.getString(R.string.workout_back_to_workouts)).performClick()
        waitForText("Push Day")

        compose.onNodeWithText(context.getString(R.string.nav_calendar)).performClick()
        waitForText(context.getString(R.string.calendar_empty_title))
    }

    private fun count(text: String): Int =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Looper-idling poll for a database write, which only progresses while the looper is idled. */
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
