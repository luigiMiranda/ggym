package com.ggym.app.ui.exercises

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
import com.ggym.app.ui.theme.GgymTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The form's controls, driven as a user drives them.
 *
 * The sheet is stateless, so there is no ViewModel and no database here: this class answers what
 * the form *offers* — which modes show which actions, which fields step, and that the reps field
 * has no stepper at all — while the writes are `ExerciseFormUseCasesTest`'s and the two hosts'
 * business.
 *
 * SDK 36 and `waitForIdle` polling follow the rest of the Compose suites; see
 * `DayDetailScreenTest` for why.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExerciseFormSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val steps = mutableListOf<Pair<String, Number>>()

    private fun show(
        state: ExerciseFormState,
        onSave: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onRemove: (() -> Unit)? = {},
    ) {
        compose.setContent {
            GgymTheme {
                ExerciseFormSheet(
                    form = state,
                    onNameChange = {},
                    onStepSets = { delta -> steps += "sets" to delta },
                    onRepsChange = {},
                    onStepRest = { delta -> steps += "rest" to delta },
                    onWeightChange = {},
                    onStepWeight = { delta -> steps += "weight" to delta },
                    onSave = onSave,
                    onDismiss = onDismiss,
                    onRemove = onRemove,
                )
            }
        }
    }

    private fun state(
        mode: ExerciseFormMode = ExerciseFormMode.EditSlot,
        name: String = "Bench Press",
        error: ExerciseFormError? = null,
    ) = ExerciseFormState(
        mode = mode,
        plannedExerciseId = if (mode == ExerciseFormMode.EditSlot) 7 else null,
        name = name,
        sets = 4,
        reps = "8-10",
        restSeconds = 90,
        weight = "82.5",
        isOwnWeight = true,
        error = error,
    )

    @Test
    fun `the edit form shows the slot's numbers and both actions`() {
        show(state())

        waitForText(string(R.string.form_title_edit))
        compose.onNodeWithText("Bench Press").assertExists()
        compose.onNodeWithText("4").assertExists()
        compose.onNodeWithText("8-10").assertExists()
        compose.onNodeWithText("90s").assertExists()
        compose.onNodeWithText("82.5").assertExists()
        compose.onNodeWithText(string(R.string.action_save)).assertExists()
        compose.onNodeWithText(string(R.string.slot_remove)).assertExists()
    }

    @Test
    fun `the create form offers a way out instead of a removal`() {
        show(state(mode = ExerciseFormMode.Create, name = "Cable Fly"), onRemove = null)

        waitForText(string(R.string.form_title_create))
        compose.onNodeWithText(string(R.string.form_add_to_day)).assertExists()
        compose.onNodeWithText(string(R.string.action_cancel)).assertExists()
        compose.onNodeWithText(string(R.string.slot_remove)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.action_save)).assertDoesNotExist()
    }

    @Test
    fun `the set stepper reports one step in each direction`() {
        show(state())

        waitForText(string(R.string.form_title_edit))
        compose.onNodeWithContentDescription(string(R.string.form_sets_increase)).performClick()
        compose.onNodeWithContentDescription(string(R.string.form_sets_decrease)).performClick()

        assertEquals(listOf("sets" to 1, "sets" to -1), steps)
    }

    @Test
    fun `the rest stepper reports the same fifteen seconds the rest banner uses`() {
        show(state())

        waitForText(string(R.string.form_title_edit))
        compose.onNodeWithContentDescription(string(R.string.form_rest_increase)).performClick()
        compose.onNodeWithContentDescription(string(R.string.form_rest_decrease)).performClick()

        assertEquals(listOf("rest" to 15, "rest" to -15), steps)
    }

    @Test
    fun `the weight stepper reports the plate pair`() {
        show(state())

        waitForText(string(R.string.form_title_edit))
        compose.onNodeWithContentDescription(string(R.string.weight_increase)).performClick()
        compose.onNodeWithContentDescription(string(R.string.weight_decrease)).performClick()

        assertEquals(listOf("weight" to 2.5, "weight" to -2.5), steps)
    }

    /**
     * The user's decision, pinned: a target reps field is free text, so it has no stepper. The ±
     * belongs to the *actual* reps on the workout screen, which is a different number.
     */
    @Test
    fun `the reps field has no stepper`() {
        show(state())

        waitForText(string(R.string.form_title_edit))
        compose.onNodeWithContentDescription(string(R.string.workout_reps_increase)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(R.string.workout_reps_decrease)).assertDoesNotExist()
    }

    @Test
    fun `a name another exercise holds is explained under the name field only`() {
        show(state(error = ExerciseFormError.NameTaken))

        waitForText(string(R.string.exercise_rename_taken))
        // The message belongs to the name, so it appears once — not also under the weight field,
        // where the same string would read as a complaint about the number.
        compose.onNodeWithText(string(R.string.exercise_rename_taken)).assertExists()
        compose.onNodeWithText(string(R.string.form_weight_slot_hint)).assertExists()
    }

    @Test
    fun `the save and the dismissal are reported`() {
        var saved = false
        var dismissed = false
        show(state(), onSave = { saved = true }, onDismiss = { dismissed = true })

        waitForText(string(R.string.form_title_edit))
        compose.onNodeWithText(string(R.string.action_save)).performClick()
        compose.runOnIdle { assertEquals(true, saved) }

        compose.onNodeWithText("Bench Press").assertExists()
        assertEquals(false, dismissed)
    }

    /**
     * The sheet animates in from `Hidden`, so waiting for its first words is what tells the test the
     * composition is up. A save is a view-model coroutine on the main dispatcher, which under
     * Robolectric only makes progress while the looper is idled — hence `compose.waitForIdle()`
     * rather than a bare poll (`DayDetailScreenTest` records why).
     */
    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(resId: Int): String = context.getString(resId)

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
