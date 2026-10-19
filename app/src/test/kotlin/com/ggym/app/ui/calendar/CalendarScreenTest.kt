package com.ggym.app.ui.calendar

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
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
 * The calendar as a user drives it: the month, the dots, the day's card, the way into history, and
 * a deletion.
 *
 * The date is pinned (14 October 2026), so "today", the grid and the seeded workouts agree no
 * matter when the suite runs. SDK 36 for the same Espresso reason as the other Compose suites.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CalendarScreenTest : RepositoryTest() {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val today: LocalDate = TestData.date(2026, 10, 14)
    private val opened = mutableListOf<Long>()

    private fun show() {
        val factory = viewModelFactory {
            initializer { CalendarViewModel(historyRepository, today = { today }) }
        }

        compose.setContent {
            GgymTheme {
                CalendarScreen(
                    viewModelFactory = factory,
                    onOpenHistory = { opened += it },
                )
            }
        }
    }

    @Test
    fun `the month opens on today with its workouts, and a dot names its day`() {
        givenWorkout(TestData.date(2026, 10, 3), "Pull A")
        givenWorkout(TestData.date(2026, 10, 14), "Push A")

        show()

        waitForText("October 2026")
        waitForText("Push A")

        compose.onNodeWithContentDescription("14 October, 1 workout").assertExists()
        compose.onNodeWithContentDescription("3 October, 1 workout").assertExists()
        compose.onNodeWithContentDescription("4 October, 0 workouts").assertExists()

        // Today's card is the one on screen: the day the calendar selected is the one it read.
        compose.onNodeWithText("Bench Press").assertExists()

        // The weekday initials are Monday first.
        compose.onNodeWithText("M").assertExists()
    }

    @Test
    fun `tapping a day shows that day's workout and opens its log`() {
        givenWorkout(TestData.date(2026, 10, 3), "Pull A")
        givenWorkout(TestData.date(2026, 10, 14), "Push A")

        show()
        waitForText("Push A")

        compose.onNodeWithContentDescription("3 October, 1 workout").performClick()
        waitForText("Pull A")
        assertEquals(0, count("Push A"))

        compose.onNodeWithText(context.getString(R.string.history_edit_log)).performClick()
        awaitTrue("the edit-log pill never opened the workout") { opened.isNotEmpty() }
        assertEquals(1, opened.size)
    }

    @Test
    fun `a day with nothing logged says so`() {
        givenWorkout(TestData.date(2026, 10, 3), "Pull A")

        show()
        waitForContentDescription("3 October, 1 workout")

        compose.onNodeWithContentDescription("5 October, 0 workouts").performClick()
        waitForText(context.getString(R.string.calendar_day_empty, "5 October"))
    }

    @Test
    fun `deleting a workout asks first and takes its dot with it`() {
        givenWorkout(TestData.date(2026, 10, 14), "Push A")

        show()
        waitForText("Push A")

        compose.onNodeWithText(context.getString(R.string.history_delete_workout)).performClick()
        waitForText(context.getString(R.string.history_delete_title, "Push A", "14 October"))

        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()

        awaitTrue("the workout was never deleted") {
            runBlocking {
                historyRepository.observeSessionsInRange(today.withDayOfMonth(1), today).first()
            }.isEmpty()
        }
        // The only workout in the month is gone, so the month says it is empty rather than the day.
        waitForContentDescription("14 October, 0 workouts")
        waitForText(context.getString(R.string.calendar_month_empty, "October 2026"))
    }

    @Test
    fun `a month with no workouts still draws the grid and says it is empty`() {
        givenWorkout(TestData.date(2026, 9, 30), "Push A")

        show()
        waitForText("October 2026")

        compose.onNodeWithContentDescription(context.getString(R.string.calendar_previous_month)).performClick()

        waitForText("September 2026")
        waitForContentDescription("30 September, 1 workout")
        waitForText("Push A")
    }

    @Test
    fun `paging to an empty month keeps the grid and names the month`() {
        show()
        waitForText("October 2026")

        compose.onNodeWithContentDescription(context.getString(R.string.calendar_previous_month)).performClick()

        waitForText("September 2026")
        waitForText(context.getString(R.string.calendar_month_empty, "September 2026"))
        compose.onNodeWithContentDescription("1 September, 0 workouts").assertExists()
    }

    @Test
    fun `the today action comes back from a past month`() {
        show()
        waitForText("October 2026")

        compose.onNodeWithContentDescription(context.getString(R.string.calendar_previous_month)).performClick()
        waitForText("September 2026")

        compose.onNodeWithText(context.getString(R.string.calendar_today)).performClick()

        waitForText("October 2026")
        compose.onNodeWithContentDescription("14 October, 0 workouts").assertExists()
    }

    private fun givenWorkout(date: LocalDate, dayName: String) = runBlocking {
        historyRepository.saveSession(
            TestData.session(
                date = date,
                dayNameSnapshot = dayName,
                durationSeconds = 3120,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = null,
                        exerciseNameSnapshot = "Bench Press",
                        orderIndex = 0,
                        sets = listOf(
                            TestData.setLog(setNumber = 1, weight = 80.0, reps = 10),
                            TestData.setLog(setNumber = 2, weight = 82.5, reps = 8),
                        ),
                    ),
                    TestData.exerciseLog(
                        exerciseId = null,
                        exerciseNameSnapshot = "Overhead Press",
                        wasCompleted = false,
                        orderIndex = 1,
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

    private fun waitForContentDescription(description: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Looper-idling poll for a view-model write, which only progresses while the looper is idled. */
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
