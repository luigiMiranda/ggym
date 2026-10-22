package com.ggym.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import com.ggym.app.ui.exercises.digitsOnly
import com.ggym.app.ui.theme.GgymTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The one data-entry field, held to its two promises: the cursor stays where the user left it, and
 * every box is the same size.
 *
 * SDK 36 and native graphics follow the rest of the Compose suites; see `DayDetailScreenTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class GgymFieldTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The cursor bug, pinned. The host never writes the edit back, which is the worst case of the
     * screen's state arriving a frame late: the old field replayed that stale value over the
     * character just typed, so the text flickered and the cursor jumped. The field must keep what
     * was typed and leave the cursor at the end.
     */
    @Test
    fun `the typed text and the cursor survive a host that lags a frame behind`() {
        compose.setContent {
            GgymTheme {
                GgymField(value = "10", onValueChange = {}, label = "Reps")
            }
        }

        val field = compose.onNode(hasSetTextAction())
        field.performTextInput("12")
        compose.waitForIdle()

        field.assertTextEquals("1012")
        assertEquals(
            TextRange(4, 4),
            field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange],
        )
    }

    /** A number that changes outside the field (a stepper, another item) is still adopted. */
    @Test
    fun `a screen value that changes outside the field is adopted`() {
        var weight by mutableStateOf("80")
        compose.setContent {
            GgymTheme {
                GgymField(value = weight, onValueChange = { weight = it }, label = "Weight")
            }
        }

        compose.onNode(hasSetTextAction()).assertTextEquals("80")
        compose.runOnIdle { weight = "82.5" }
        compose.onNode(hasSetTextAction()).assertTextEquals("82.5")
    }

    /** A character the screen would refuse never reaches the box, so the box never keeps it. */
    @Test
    fun `the input filter keeps a refused character out of the box`() {
        var sets by mutableStateOf("3")
        compose.setContent {
            GgymTheme {
                GgymField(
                    value = sets,
                    onValueChange = { sets = it },
                    label = "Sets",
                    inputFilter = { it.digitsOnly() },
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextReplacement("1a2")
        compose.waitForIdle()

        compose.onNode(hasSetTextAction()).assertTextEquals("12")
        assertEquals("12", sets)
    }

    /**
     * The user's consistency rule: a stepped field, a typed-only field and a field with a hint are
     * the same rectangle. The supporting line lives below the box, so it cannot make one taller.
     */
    @Test
    fun `every box is the same height whatever it carries`() {
        compose.setContent {
            GgymTheme {
                Column {
                    GgymField(value = "Bench", onValueChange = {}, label = "Name")
                    GgymField(
                        value = "3",
                        onValueChange = {},
                        label = "Sets",
                        onDecrease = {},
                        onIncrease = {},
                    )
                    GgymField(value = "10", onValueChange = {}, label = "Reps")
                    GgymField(
                        value = "90",
                        onValueChange = {},
                        label = "Rest",
                        suffix = "s",
                        onDecrease = {},
                        onIncrease = {},
                    )
                    GgymField(
                        value = "",
                        onValueChange = {},
                        label = "Weight",
                        suffix = "kg",
                        supportingText = "A hint that must not live inside the box",
                    )
                }
            }
        }

        val boxes = compose.onAllNodesWithTag(GgymFieldBoxTag).fetchSemanticsNodes()
        assertEquals(5, boxes.size)
        val heights = boxes.map { it.boundsInRoot.height }
        // One height for every box, whatever it carries. The value line inside is fixed at 36dp
        // (the step buttons' size) and the supporting line is outside the box, so nothing can make
        // one rectangle taller than another.
        assertTrue("box heights were $heights", heights.distinct().size == 1)
    }
}
