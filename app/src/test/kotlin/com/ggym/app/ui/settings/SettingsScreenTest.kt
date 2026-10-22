package com.ggym.app.ui.settings

import android.Manifest
import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
import com.ggym.app.domain.repository.BackupImportResult
import com.ggym.app.domain.repository.BackupSummary
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.workout.AlertSound
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.domain.workout.RingPolicy
import com.ggym.app.data.prefs.AppLanguage
import com.ggym.app.testing.FakeBackupRepository
import com.ggym.app.testing.FakeLanguagePreferences
import com.ggym.app.testing.FakeRestTimerCommands
import com.ggym.app.testing.FakeRestTimerStore
import com.ggym.app.testing.FakeThemePreferences
import com.ggym.app.testing.FakeWorkoutPreferences
import com.ggym.app.testing.RecordedCommand
import com.ggym.app.ui.theme.GgymTheme
import kotlinx.coroutines.flow.first
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
    private val languagePreferences = FakeLanguagePreferences()
    private val backups = FakeBackupRepository()

    /**
     * The screen's state holder, built by the test rather than by the factory so a test can set up
     * a situation the UI alone cannot reach — a file already picked, waiting on the confirm dialog.
     */
    private val viewModel by lazy {
        SettingsViewModel(
            themePreferences = FakeThemePreferences(),
            languagePreferences = languagePreferences,
            restTimer = RestTimerUseCases(
                store = restStore,
                commands = restCommands,
                preferences = workoutPreferences,
            ),
            workoutPreferences = workoutPreferences,
            backups = backups,
        )
    }

    private fun show() {
        val factory = viewModelFactory {
            initializer { viewModel }
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
    fun `the language pills show the choice and write it`() {
        show()

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(LANGUAGE_SYSTEM_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        // The three pills are there, and the default is to follow the device.
        compose.onNodeWithTag(LANGUAGE_SYSTEM_TAG).assertExists()
        compose.onNodeWithTag(LANGUAGE_ENGLISH_TAG).assertExists()
        // The Language section is the last one on the page, so the pills are scrolled to first.
        compose.onNodeWithTag(LANGUAGE_ITALIAN_TAG).performScrollTo().performClick()

        compose.waitUntil(TIMEOUT_MILLIS) {
            runBlocking { languagePreferences.language.first() } == AppLanguage.Italian
        }
        assertEquals(AppLanguage.Italian, runBlocking { languagePreferences.language.first() })
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

    @Test
    fun `choosing None for the alert sound persists it`() {
        show()
        waitForTheScreen()

        compose.onNodeWithTag(ALERT_SOUND_NONE_TAG).performClick()

        compose.waitUntil(TIMEOUT_MILLIS) { workoutPreferences.sound.value == AlertSound.None }
        assertEquals(AlertSound.None, workoutPreferences.sound.value)
    }

    @Test
    fun `choosing manual ringing persists it and takes the seconds away`() {
        show()
        waitForTheScreen()

        compose.onNodeWithTag(RINGING_MANUAL_TAG).performClick()

        compose.waitUntil(TIMEOUT_MILLIS) { workoutPreferences.policy.value == RingPolicy.Manual }
        // Manual has no number to choose, so the stepper goes with it.
        compose.onNodeWithTag(RINGING_SECONDS_TAG).assertDoesNotExist()
    }

    @Test
    fun `the seconds stepper moves by five and stops at the top of the range`() {
        workoutPreferences.policy.value = RingPolicy.Automatic(10)
        show()
        waitForTheScreen()

        compose.onNodeWithContentDescription(context.getString(R.string.settings_ringing_more)).performClick()

        compose.waitUntil(TIMEOUT_MILLIS) { workoutPreferences.policy.value == RingPolicy.Automatic(15) }

        workoutPreferences.policy.value = RingPolicy.Automatic(RingPolicy.MAX_SECONDS)
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(
                context.getString(R.string.rest_seconds_short, RingPolicy.MAX_SECONDS),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        // At the end of the range the button is gone rather than silently doing nothing.
        compose.onNodeWithContentDescription(context.getString(R.string.settings_ringing_more))
            .assertIsNotEnabled()
    }

    /**
     * The page is grouped the way a phone's own settings are: appearance, the rest timer with its
     * notifications, and language kept apart. The labels are the contract a user navigates by.
     */
    @Test
    fun `the settings are grouped into appearance, rest timer, language and data sections`() {
        show()
        waitForTheScreen()

        listOf(
            R.string.settings_section_appearance,
            R.string.settings_section_rest_timer,
            R.string.settings_section_language,
            R.string.settings_section_data,
        ).forEach { label ->
            compose.onNodeWithText(context.getString(label).uppercase()).assertExists()
        }
    }

    /** The two cards stay on screen but keep their hands off the preference while nothing rings. */
    @Test
    fun `the alert settings do nothing while the rest timer is off`() {
        workoutPreferences.enabled.value = false
        show()
        waitForTheScreen()

        compose.onNodeWithTag(ALERT_SOUND_NONE_TAG).assertHasNoClickAction()
        compose.onNodeWithTag(RINGING_MANUAL_TAG).assertHasNoClickAction()
    }

    /** The Data section offers both directions, and each is a live button. */
    @Test
    fun `the data section offers export and import`() {
        show()
        waitForTheScreen()

        compose.onNodeWithTag(BACKUP_EXPORT_TAG).performScrollTo().assertHasClickAction()
        compose.onNodeWithTag(BACKUP_IMPORT_TAG).performScrollTo().assertHasClickAction()
    }

    /**
     * The whole reason import is two steps: a picked file is not a decision until the question has
     * been answered.
     */
    @Test
    fun `a picked file is confirmed before anything is restored`() {
        show()
        waitForTheScreen()

        viewModel.requestImport(SAF_DOCUMENT)
        waitForConfirmDialog()
        assertEquals("nothing may have been read yet", 0, backups.imported.size)

        compose.onNodeWithText(context.getString(R.string.settings_backup_import_confirm_action)).performClick()

        compose.waitUntil(TIMEOUT_MILLIS) { backups.imported.isNotEmpty() }
        assertEquals(SAF_DOCUMENT, backups.imported.single())
    }

    @Test
    fun `cancelling the confirm dialog restores nothing`() {
        show()
        waitForTheScreen()

        viewModel.requestImport(SAF_DOCUMENT)
        waitForConfirmDialog()

        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(context.getString(R.string.settings_backup_import_confirm_title))
                .fetchSemanticsNodes().isEmpty()
        }
        assertEquals(0, backups.imported.size)
    }

    /** A finished restore says how much came back; the sentence is the acknowledgment. */
    @Test
    fun `a finished restore reports what came back`() {
        backups.importResult = BackupImportResult.Restored(
            BackupSummary(routines = 2, workouts = 7, exercises = 5, weighIns = 1),
        )
        show()
        waitForTheScreen()

        viewModel.requestImport(SAF_DOCUMENT)
        waitForConfirmDialog()
        compose.onNodeWithText(context.getString(R.string.settings_backup_import_confirm_action)).performClick()

        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(BACKUP_OUTCOME_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        // A plural and a singular in one sentence, which is why the counts go through plurals and
        // not a format string. The numbers prove the summary reached the screen intact.
        compose.onNodeWithText(
            context.getString(
                R.string.settings_backup_imported,
                "2 routines",
                "7 workouts",
                "5 exercises",
                "1 weigh-in",
            ),
        ).assertExists()
    }

    private fun waitForConfirmDialog() {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(context.getString(R.string.settings_backup_import_confirm_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTheScreen() {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(SWITCH).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag(ALERT_SOUND_SYSTEM_TAG).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val SWITCH = "rest_timer_switch"
        const val TIMEOUT_MILLIS = 10_000L

        /** A document URI of the shape the system picker hands back. */
        val SAF_DOCUMENT: Uri = Uri.parse("content://com.ggym.test/document/backup.json")
    }
}
