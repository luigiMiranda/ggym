package com.ggym.app.ui.stats

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
import com.ggym.app.domain.stats.BodyWeightUseCases
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.TestData
import com.ggym.app.ui.theme.GgymTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The Stats tab driven as a user drives it: the four sections, a body weight logged through the
 * sheet, the range filters, the exercise selector, and a measurement deleted behind its
 * confirmation.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class StatsScreenTest : RepositoryTest() {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val today: LocalDate = TestData.date(2026, 10, 19)
    private val bodyWeights by lazy { BodyWeightUseCases(bodyWeightRepository) }

    private fun show() {
        val factory = viewModelFactory {
            initializer {
                StatsViewModel(
                    history = historyRepository,
                    exercises = exerciseRepository,
                    bodyWeights = bodyWeights,
                    todayProvider = { today },
                )
            }
        }

        compose.setContent {
            GgymTheme {
                StatsScreen(viewModelFactory = factory)
            }
        }
    }

    @Test
    fun `the sections show the workouts and measurements that exist`() {
        val bench = runBlocking { exerciseRepository.findOrCreate("Bench Press").id }
        givenWorkout(TestData.date(2026, 9, 2), bench, 80.0)
        givenWorkout(today, bench, 82.5)
        runBlocking {
            bodyWeights.log(TestData.date(2026, 10, 5), 82.4)
            bodyWeights.log(today, 84.0)
        }

        val days = context.resources.getQuantityString(R.plurals.stats_workout_days, 2, 2)
        val months = context.resources.getQuantityString(R.plurals.stats_months, 12, 12)
        val attendanceSummary = context.getString(
            R.string.stats_attendance_summary,
            days,
            months,
            "September",
            1,
        )
        val bodySummary = context.getString(R.string.stats_body_summary, "82.4", "83.2", "84.0")

        show()

        waitForText(attendanceSummary)
        waitForText(bodySummary)
        waitForText(context.getString(R.string.weight_kg, "82.4"))

        val sessions = context.resources.getQuantityString(R.plurals.stats_progression_sessions, 2, 2)
        val progressionSummary = context.getString(
            R.string.stats_progression_summary,
            sessions,
            context.getString(R.string.weight_kg, "82.5"),
        )
        waitForText(progressionSummary)

        compose.onNodeWithText(attendanceSummary).assertExists()
        compose.onNodeWithText(bodySummary).assertExists()
        compose.onNodeWithText(context.getString(R.string.stats_log_today)).assertExists()
        compose.onNodeWithText(context.getString(R.string.weight_kg, "84")).assertExists()
    }

    @Test
    fun `logging a weight from the sheet updates the log and the chart`() {
        show()
        waitForText(context.getString(R.string.stats_body_empty))

        compose.onNodeWithText(context.getString(R.string.stats_body_empty_action)).performClick()
        waitForText(context.getString(R.string.stats_sheet_new_title))

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("82.4")
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()

        awaitTrue("the measurement never reached the database") {
            runBlocking { bodyWeights.observeAll(today).first() }.size == 1
        }
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(context.getString(R.string.weight_kg, "82.4")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.stats_body_summary, "82.4", "82.4", "82.4")).assertExists()
        compose.onNodeWithText(context.getString(R.string.stats_body_one)).assertExists()
    }

    @Test
    fun `the range filter changes the window's numbers`() {
        runBlocking {
            bodyWeights.log(TestData.date(2026, 8, 19), 80.0)
            bodyWeights.log(today, 82.4)
        }

        show()
        waitForText(context.getString(R.string.stats_body_summary, "80.0", "81.2", "82.4"))

        compose.onNodeWithText(context.getString(R.string.stats_range_1m)).performClick()

        waitForText(context.getString(R.string.stats_body_summary, "82.4", "82.4", "82.4"))
        compose.onNodeWithText(context.getString(R.string.stats_body_summary, "80.0", "81.2", "82.4")).assertDoesNotExist()
    }

    @Test
    fun `the exercise selector changes the chart's title`() {
        val bench = runBlocking { exerciseRepository.findOrCreate("Bench Press").id }
        val fly = runBlocking { exerciseRepository.findOrCreate("Cable Fly").id }
        givenWorkout(TestData.date(2026, 9, 1), bench, 80.0)
        givenWorkout(TestData.date(2026, 9, 15), bench, 85.0)
        givenWorkout(today, bench, 90.0)
        givenWorkout(today, fly, 20.0)

        show()
        waitForText(context.getString(R.string.stats_exercise_selector, "Bench Press"))
        waitForText(context.getString(R.string.stats_progression_legend_trend))

        compose.onNodeWithText(context.getString(R.string.stats_exercise_selector, "Bench Press")).performClick()
        waitForText(context.getString(R.string.stats_select_exercise))
        compose.onNodeWithText("Cable Fly").performClick()

        waitForText(context.getString(R.string.stats_exercise_selector, "Cable Fly"))
        assertEquals(0, count(context.getString(R.string.stats_progression_legend_trend)))
    }

    @Test
    fun `the trend appears only once there are three points`() {
        val bench = runBlocking { exerciseRepository.findOrCreate("Bench Press").id }
        givenWorkout(TestData.date(2026, 9, 1), bench, 80.0)
        givenWorkout(today, bench, 85.0)

        show()
        waitForText(context.getString(R.string.stats_progression_legend_max))
        assertEquals(0, count(context.getString(R.string.stats_progression_legend_trend)))

        givenWorkout(TestData.date(2026, 10, 10), bench, 90.0)

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(context.getString(R.string.stats_progression_legend_trend)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `deleting a measurement asks first and empties the card`() {
        runBlocking { bodyWeights.log(today, 82.4) }

        show()
        waitForText(context.getString(R.string.weight_kg, "82.4"))

        compose.onNodeWithContentDescription(context.getString(R.string.action_more)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()
        waitForText(context.getString(R.string.stats_delete_title))
        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()

        awaitTrue("the measurement was never deleted") {
            runBlocking { bodyWeights.observeAll(today).first() }.isEmpty()
        }
        waitForText(context.getString(R.string.stats_body_empty))
        waitForText(context.getString(R.string.stats_log_empty))
    }

    @Test
    fun `a new install sees literal empty states, not a sample chart`() {
        show()

        waitForText(context.getString(R.string.stats_attendance_empty))
        waitForText(context.getString(R.string.stats_body_empty))
        waitForText(context.getString(R.string.stats_log_empty))
        waitForText(context.getString(R.string.stats_progression_no_exercises))
    }

    private fun givenWorkout(date: LocalDate, exerciseId: Long, weight: Double) = runBlocking {
        historyRepository.saveSession(
            TestData.session(
                date = date,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = exerciseId,
                        sets = listOf(TestData.setLog(weight = weight, reps = 8)),
                    ),
                ),
            ),
        )
    }

    private fun count(text: String): Int = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

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
