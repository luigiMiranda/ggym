package com.ggym.app.ui.exercises

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
import com.ggym.app.domain.exercise.ExerciseFormUseCases
import com.ggym.app.testing.PlannedSlot
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.givenPlannedDay
import com.ggym.app.ui.theme.GgymTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The exercise library driven as a user drives it: the rows' facts, the row menu, the add form and
 * both halves of the delete — the one that removes and the one a routine refuses.
 *
 * The screen runs over a real database and the real ViewModel, so what is asserted is what a user
 * would see after the write lands, not a mocked state.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExerciseLibraryScreenTest : RepositoryTest() {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val openedRoutines = mutableListOf<Long>()
    private val openedDays = mutableListOf<Pair<Long, Long>>()
    private val openedProgressions = mutableListOf<Long>()

    private fun show() {
        val factory = viewModelFactory {
            initializer {
                ExerciseLibraryViewModel(
                    exercises = exerciseRepository,
                    routines = routineRepository,
                    formUseCases = ExerciseFormUseCases(routineRepository, exerciseRepository),
                    locale = Locale.US,
                )
            }
        }

        compose.setContent {
            GgymTheme {
                ExerciseLibraryScreen(
                    viewModelFactory = factory,
                    onBack = {},
                    onOpenRoutine = { openedRoutines += it },
                    onOpenDay = { routineId, dayId -> openedDays += routineId to dayId },
                    onOpenProgression = { openedProgressions += it },
                )
            }
        }
    }

    @Test
    fun `each row carries the weight, the rest and where the exercise is used`() {
        val bench = runBlocking { exerciseRepository.findOrCreate("Bench Press", defaultRestSeconds = 120).id }
        runBlocking {
            exerciseRepository.updateCurrentWorkingWeight(bench, 82.5)
            routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench, sets = 4, reps = "8-10", restSeconds = 120))
            exerciseRepository.findOrCreate("Cable Fly")
        }

        show()

        val used = context.getString(
            R.string.library_row_detail,
            context.getString(R.string.weight_kg, "82.5"),
            context.getString(R.string.library_rest, 120),
            context.getString(R.string.library_used_in, "Push A"),
        )
        val unused = context.getString(
            R.string.library_row_detail,
            context.getString(R.string.library_no_weight),
            context.getString(R.string.library_rest, 90),
            context.getString(R.string.library_unused),
        )
        waitForText(used)
        compose.onNodeWithText(used).assertExists()
        compose.onNodeWithText(unused).assertExists()
        compose.onNodeWithText("Bench Press").assertExists()
        compose.onNodeWithText("Cable Fly").assertExists()
    }

    @Test
    fun `the row menu offers edit, progression and delete`() {
        runBlocking { exerciseRepository.findOrCreate("Bench Press") }
        show()
        waitForText("Bench Press")

        compose.onNodeWithContentDescription(context.getString(R.string.action_more)).performClick()

        compose.onNodeWithText(context.getString(R.string.action_edit)).assertExists()
        compose.onNodeWithText(context.getString(R.string.library_progression)).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_delete)).assertExists()
    }

    @Test
    fun `the add action opens the form with no sets or reps to fill`() {
        runBlocking { exerciseRepository.findOrCreate("Bench Press") }
        show()
        waitForText("Bench Press")

        compose.onNodeWithText(context.getString(R.string.library_add), useUnmergedTree = true).performClick()

        waitForText(context.getString(R.string.form_title_create))
        compose.onNodeWithText(context.getString(R.string.exercise_rename_label).uppercase()).assertExists()
        compose.onNodeWithText(context.getString(R.string.slot_field_sets).uppercase()).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.slot_field_reps).uppercase()).assertDoesNotExist()
    }

    @Test
    fun `the edit action opens the row's numbers and its plans`() {
        val bench = runBlocking { exerciseRepository.findOrCreate("Bench Press", defaultRestSeconds = 120).id }
        runBlocking {
            exerciseRepository.updateCurrentWorkingWeight(bench, 82.5)
            routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench, sets = 4, reps = "8-10"))
        }
        show()
        waitForText("Bench Press")

        compose.onNodeWithContentDescription(context.getString(R.string.action_more)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_edit)).performClick()

        waitForText(context.getString(R.string.form_title_edit))
        compose.onNodeWithText("82.5").assertExists()
        compose.onNodeWithText("120").assertExists()
        compose.onNodeWithText("Push A").assertExists()
        compose.onNodeWithText(context.getString(R.string.library_plan_detail, 4, "8-10")).assertExists()
    }

    @Test
    fun `deleting an unused exercise asks and then leaves the list`() {
        runBlocking { exerciseRepository.findOrCreate("Bench Press") }
        show()
        waitForText("Bench Press")

        compose.onNodeWithContentDescription(context.getString(R.string.action_more)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()
        waitForText(context.getString(R.string.exercise_delete_title))
        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()

        waitForText(context.getString(R.string.library_empty))
        compose.onNodeWithText("Bench Press").assertDoesNotExist()
    }

    @Test
    fun `deleting a planned exercise names the routine and offers a way in`() {
        val bench = runBlocking { exerciseRepository.findOrCreate("Bench Press").id }
        val routineId = runBlocking {
            routineRepository.givenPlannedDay(PlannedSlot(exerciseId = bench))
            routineRepository.observeRoutines().first().single().id
        }
        show()
        waitForText("Bench Press")

        compose.onNodeWithContentDescription(context.getString(R.string.action_more)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()
        waitForText(context.getString(R.string.exercise_delete_title))
        compose.onNodeWithText(context.getString(R.string.action_delete)).performClick()

        waitForText(context.getString(R.string.exercise_delete_blocked_title))
        compose.onNodeWithText(
            context.getString(R.string.exercise_delete_blocked_message, "Push Day"),
        ).assertExists()
        compose.onNodeWithText(context.getString(R.string.exercise_delete_blocked_open, "Push Day")).performClick()

        assertEquals(listOf(routineId), openedRoutines)
        compose.onNodeWithText("Bench Press").assertExists()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
