package com.ggym.app.ui.settings

import android.Manifest
import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.testing.FakeRestTimerCommands
import com.ggym.app.testing.FakeRestTimerStore
import com.ggym.app.testing.FakeThemePreferences
import com.ggym.app.testing.FakeWorkoutPreferences
import com.ggym.app.testing.RecordedCommand
import com.ggym.app.ui.theme.GgymTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Settings switch and the notification row, driven as a user drives them.
 *
 * The switch is checked for what it shows *and* for what it does to a rest that is running: "off"
 * above a live countdown would be a lie, and that is the rule this screen must not be able to skip.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val restStore = FakeRestTimerStore()
    private val restCommands = FakeRestTimerCommands()
    private val workoutPreferences = FakeWorkoutPreferences()

    private fun show() {
        val factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    themePreferences = FakeThemePreferences(),
                    restTimer = RestTimerUseCases(
                        store = restStore,
                        commands = restCommands,
                        preferences = workoutPreferences,
                    ),
                )
            }
        }
        compose.setContent {
            GgymTheme {
                SettingsScreen(viewModelFactory = factory)
            }
        }
    }

    @Test
    fun `the rest timer switch starts on and turning it off stops a running rest`() {
        runBlocking {
            restStore.set(RestTimerState(endsAtMillis = 2_000_000, totalSeconds = 90, exerciseLogId = 1, exerciseName = "Bench Press"))
        }
        show()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag(SWITCH).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(SWITCH).assertIsOn()

        compose.onNodeWithTag(SWITCH).performClick()

        compose.waitUntil(TIMEOUT_MILLIS) { restCommands.received.contains(RecordedCommand.Stop) }
        compose.onNodeWithTag(SWITCH).assertIsOff()
        assertFalse(workoutPreferences.enabled.value)
    }

    @Test
    fun `with notifications not granted the screen says so`() {
        // Robolectric starts with the permission denied, which is the state a user who said no is in.
        show()

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(context.getString(R.string.settings_notifications_title)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.settings_notifications_open)).assertExists()
    }

    @Test
    fun `with notifications granted and allowed the warning is not shown`() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show()

        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithTag(SWITCH).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, compose.onAllNodesWithText(context.getString(R.string.settings_notifications_title)).fetchSemanticsNodes().size)
    }

    private companion object {
        const val SWITCH = "rest_timer_switch"
        const val TIMEOUT_MILLIS = 10_000L
    }
}
