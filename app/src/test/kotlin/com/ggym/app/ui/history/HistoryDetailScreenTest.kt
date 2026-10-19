package com.ggym.app.ui.history

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One past workout, corrected through the real screen.
 *
 * The two things a JVM ViewModel test cannot see are here: the sheet's prefill and its text field,
 * and the menu and confirmation that a mis-tap has to get through.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HistoryDetailScreenTest : RepositoryTest() {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private var sessionId = 0L
    private var deleted = false

    private fun show(id: Long = sessionId) {
        val factory = viewModelFactory {
            initializer { HistoryDetailViewModel(id, historyRepository) }
        }

        compose.setContent {
            GgymTheme {
                HistoryDetailScreen(
                    viewModelFactory = factory,
                    onBack = {},
                    onWorkoutDeleted = { deleted = true },
                )
            }
        }
    }

    @Test
    fun `a set's weight and reps are corrected through the sheet`() {
        givenWorkout()
        show()
        waitForText("Bench Press")

        compose.onAllNodesWithContentDescription(context.getString(R.string.history_edit_set, 2))[0]
            .performClick()
        waitForText(context.getString(R.string.history_editor_title, 2))

        // The sheet's two fields, prefilled from the row: weight first, reps second.
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextClearance()
        fields[0].performTextInput("85")
        fields[1].performTextClearance()
        fields[1].performTextInput("6")

        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()

        awaitTrue("the corrected set never reached the database") {
            runBlocking { storedWeights() } == listOf(80.0, 85.0, 82.5) &&
                runBlocking { storedReps() } == listOf(10, 6, 8)
        }
        waitForText(context.getString(R.string.history_set_values, "85 kg", 6))
    }

    @Test
    fun `removing a set asks and deletes only that set`() {
        givenWorkout()
        show()
        waitForText("Bench Press")

        compose.onAllNodesWithContentDescription(context.getString(R.string.history_edit_set, 3))[0]
            .performClick()
        waitForText(context.getString(R.string.history_editor_title, 3))

        compose.onNodeWithText(context.getString(R.string.history_remove_set)).performClick()
        waitForText(context.getString(R.string.history_remove_set_title))
        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()

        awaitTrue("the set was never removed") {
            runBlocking { storedSetCount() } == 3
        }
        waitForText(context.getString(R.string.history_set_values, "80 kg", 10))
    }

    @Test
    fun `deleting the workout asks first and leaves the screen`() {
        givenWorkout()
        show()
        waitForText("Bench Press")

        compose.onNodeWithContentDescription(context.getString(R.string.action_more)).performClick()
        compose.onNodeWithText(context.getString(R.string.history_delete_workout)).performClick()

        waitForText(context.getString(R.string.history_delete_title, "Push A", "14 October"))
        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()

        awaitTrue("the workout was never deleted") {
            deleted && runBlocking { historyRepository.observeSession(sessionId).first() } == null
        }
        assertTrue(deleted)
    }

    @Test
    fun `a workout that is already gone shows the gone state`() {
        show(id = 9_999)

        waitForText(context.getString(R.string.history_missing_title))
        compose.onNodeWithText(context.getString(R.string.history_missing_message)).assertExists()
    }

    @Test
    fun `there is no way to add a set`() {
        givenWorkout()
        show()
        waitForText("Bench Press")

        // The only two verbs are Edit set N and the menu's Delete workout.
        assertEquals(0, count(context.getString(R.string.workout_add_set)))
        compose.onNodeWithContentDescription(context.getString(R.string.action_more)).assertExists()
    }

    private fun givenWorkout() = runBlocking {
        sessionId = historyRepository.saveSession(
            TestData.session(
                date = TestData.date(2026, 10, 14),
                dayNameSnapshot = "Push A",
                durationSeconds = 3120,
                exercises = listOf(
                    TestData.exerciseLog(
                        exerciseId = null,
                        exerciseNameSnapshot = "Bench Press",
                        orderIndex = 0,
                        sets = listOf(
                            TestData.setLog(setNumber = 1, weight = 80.0, reps = 10),
                            TestData.setLog(setNumber = 2, weight = 82.5, reps = 9),
                            TestData.setLog(setNumber = 3, weight = 82.5, reps = 8),
                        ),
                    ),
                    TestData.exerciseLog(
                        exerciseId = null,
                        exerciseNameSnapshot = "Cable Fly",
                        orderIndex = 1,
                        sets = listOf(TestData.setLog(setNumber = 1, weight = 20.0, reps = 12)),
                    ),
                ),
            ),
        )
    }

    private suspend fun storedWeights(): List<Double> =
        historyRepository.observeSession(sessionId).first()!!
            .exercises.first { it.exerciseNameSnapshot == "Bench Press" }
            .sets
            .map { it.weight }

    private suspend fun storedReps(): List<Int> =
        historyRepository.observeSession(sessionId).first()!!
            .exercises.first { it.exerciseNameSnapshot == "Bench Press" }
            .sets
            .map { it.reps }

    private suspend fun storedSetCount(): Int =
        historyRepository.observeSession(sessionId).first()!!.exercises.sumOf { it.sets.size }

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
