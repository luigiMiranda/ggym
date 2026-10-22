package com.ggym.app.ui.settings

import android.net.Uri
import app.cash.turbine.test
import com.ggym.app.domain.repository.BackupExportResult
import com.ggym.app.domain.repository.BackupFailure
import com.ggym.app.domain.repository.BackupImportResult
import com.ggym.app.domain.repository.BackupSummary
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.testing.FakeBackupRepository
import com.ggym.app.testing.FakeLanguagePreferences
import com.ggym.app.testing.FakeRestTimerCommands
import com.ggym.app.testing.FakeRestTimerStore
import com.ggym.app.testing.FakeThemePreferences
import com.ggym.app.testing.FakeWorkoutPreferences
import com.ggym.app.testing.RecordedCommand
import com.ggym.app.testing.awaitState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Data section's behaviour, driven through the state holder.
 *
 * Robolectric rather than a plain JVM test for one honest reason: the document a user picked is a
 * `Uri`, and there is no way to name one without Android. What is asserted is the ViewModel's own
 * decisions — a picked file is not read before it is confirmed, a finished restore reports its
 * counts, a refusal reports its case, and a successful restore takes a running rest with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class SettingsBackupTest {

    private val restStore = FakeRestTimerStore()
    private val restCommands = FakeRestTimerCommands()
    private val workoutPreferences = FakeWorkoutPreferences()
    private val backups = FakeBackupRepository()

    private val timer = RestTimerUseCases(
        store = restStore,
        commands = restCommands,
        preferences = workoutPreferences,
        now = { 1_000L },
    )

    private val viewModel by lazy {
        SettingsViewModel(
            themePreferences = FakeThemePreferences(),
            languagePreferences = FakeLanguagePreferences(),
            restTimer = timer,
            workoutPreferences = workoutPreferences,
            backups = backups,
        )
    }

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exporting says the file was saved and writes the picked document`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.exportTo(DOCUMENT)

            val state = awaitState { it.backupOutcome == BackupOutcome.Exported }
            assertEquals(listOf(DOCUMENT), backups.exported)
            assertFalse("a finished job is not still running", state.backupBusy)
            assertNull("and it leaves nothing pending", state.pendingImport)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a refused write says so`() = runTest {
        backups.exportResult = BackupExportResult.Failed

        viewModel.uiState.test {
            awaitItem()

            viewModel.exportTo(DOCUMENT)

            awaitState { it.backupOutcome == BackupOutcome.ExportFailed }
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The two steps of an import: picking a file only asks the question, and the answer is what
     * reads it.
     */
    @Test
    fun `a picked file waits for confirmation before it is read`() = runTest {
        backups.importResult = BackupImportResult.Restored(
            BackupSummary(routines = 3, workouts = 4, exercises = 5, weighIns = 6),
        )

        viewModel.uiState.test {
            awaitItem()

            viewModel.requestImport(DOCUMENT)

            assertEquals(DOCUMENT.toString(), awaitState { it.pendingImport != null }.pendingImport)
            assertTrue("nothing is read before the user agrees", backups.imported.isEmpty())

            viewModel.confirmImport()

            val state = awaitState { it.backupOutcome is BackupOutcome.Imported }
            assertEquals(listOf(DOCUMENT), backups.imported)
            assertEquals(
                BackupOutcome.Imported(BackupSummary(routines = 3, workouts = 4, exercises = 5, weighIns = 6)),
                state.backupOutcome,
            )
            assertNull("answering the question clears it", state.pendingImport)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `declining the confirmation reads nothing and reports nothing`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.requestImport(DOCUMENT)
            awaitState { it.pendingImport != null }

            viewModel.cancelImport()

            assertNull(awaitState { it.pendingImport == null }.pendingImport)
            assertTrue(backups.imported.isEmpty())
            assertNull(viewModel.uiState.value.backupOutcome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Answering once restores once: a second tap past the answer has nothing left to act on. */
    @Test
    fun `answering the question twice restores once`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.requestImport(DOCUMENT)
            viewModel.confirmImport()
            viewModel.confirmImport()

            awaitState { it.backupOutcome is BackupOutcome.Imported }
            assertEquals(1, backups.imported.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Every refusal is its own case, because what the user does next differs for each one. */
    @Test
    fun `a refused restore reports the case`() = runTest {
        backups.importResult = BackupImportResult.Failed(BackupFailure.Malformed)

        viewModel.uiState.test {
            awaitItem()

            viewModel.requestImport(DOCUMENT)
            viewModel.confirmImport()

            awaitState { it.backupOutcome == BackupOutcome.ImportFailed(BackupFailure.Malformed) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unsupported format keeps the numbers the sentence needs`() = runTest {
        backups.importResult = BackupImportResult.Failed(
            BackupFailure.UnsupportedVersion(found = 9, supported = 5),
        )

        viewModel.uiState.test {
            awaitItem()

            viewModel.requestImport(DOCUMENT)
            viewModel.confirmImport()

            awaitState {
                it.backupOutcome ==
                    BackupOutcome.ImportFailed(BackupFailure.UnsupportedVersion(found = 9, supported = 5))
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A restore replaces the workout a countdown may belong to, so the countdown goes with it. */
    @Test
    fun `a finished restore stops a running rest`() = runTest {
        restStore.set(
            RestTimerState(endsAtMillis = 2_000_000, totalSeconds = 90, exerciseLogId = 1, exerciseName = "Bench Press"),
        )

        viewModel.uiState.test {
            awaitItem()

            viewModel.requestImport(DOCUMENT)
            viewModel.confirmImport()

            awaitState { it.backupOutcome is BackupOutcome.Imported }
            assertEquals(listOf(RecordedCommand.Stop), restCommands.received)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Nothing was replaced, so nothing that was running has any reason to stop. */
    @Test
    fun `a refused restore leaves a running rest alone`() = runTest {
        backups.importResult = BackupImportResult.Failed(BackupFailure.Unreadable)
        restStore.set(
            RestTimerState(endsAtMillis = 2_000_000, totalSeconds = 90, exerciseLogId = 1, exerciseName = "Bench Press"),
        )

        viewModel.uiState.test {
            awaitItem()

            viewModel.requestImport(DOCUMENT)
            viewModel.confirmImport()

            awaitState { it.backupOutcome is BackupOutcome.ImportFailed }
            assertTrue(restCommands.received.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismissing the outcome clears it`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.exportTo(DOCUMENT)
            awaitState { it.backupOutcome != null }

            viewModel.dismissBackupOutcome()

            assertNull(awaitState { it.backupOutcome == null }.backupOutcome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        val DOCUMENT: Uri = Uri.parse("content://com.ggym.test/document/backup.json")
    }
}
