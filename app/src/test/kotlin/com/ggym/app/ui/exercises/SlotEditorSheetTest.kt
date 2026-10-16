package com.ggym.app.ui.exercises

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.R
import com.ggym.app.domain.model.PlannedExercise
import com.ggym.app.domain.workout.WorkingWeightUseCases
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.ui.theme.GgymTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The slot editor, driven as a user drives it — and the regression that a device check found and no
 * ViewModel test could: the sheet must edit **the slot it was opened for**.
 *
 * The bug this pins down was composition wiring, not logic: `viewModel(factory = …)` caches one
 * instance per `ViewModelStoreOwner` and class, so before [slotEditorViewModelKey] existed the
 * second opening of the sheet on a screen was handed the first slot's form — it showed the other
 * exercise and `save()` wrote the other row.
 *
 * Runs on the JVM under Robolectric (the activity `createComposeRule` launches comes from the
 * `ui-test-manifest` artifact), against the real repositories on an in-memory database.
 *
 * **Why SDK 36 and not the app's 37**: the Compose test framework idles through Espresso under
 * Robolectric, and Espresso 3.5 initializes its input injector by reflecting on the hidden
 * `android.hardware.input.InputManager.getInstance()` — a method AOSP removed in Android 17, so the
 * API 37 runtime jar does not have it and every `waitForIdle` throws. The framework jar a UI test
 * draws its widgets from is not what this milestone is verifying, so these tests run one level down
 * (API 36). The database and repository tests keep the app's API 37, which is the level that
 * matters for the data layer.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SlotEditorSheetTest : RepositoryTest() {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the sheet follows the slot it is opened for, and saves that one`() {
        val seeded = runBlocking { seedPushDay() }
        val weights = WorkingWeightUseCases(
            routines = routineRepository,
            exercises = exerciseRepository,
            history = historyRepository,
        )

        // What the day screen does when the user taps a row: the sheet's subject changes.
        val editedSlotId = mutableStateOf(seeded.benchSlotId)
        // The sheet closes itself when the editor reports the slot written, which is the UI's own
        // signal that the save ran — the test waits for that instead of guessing.
        val saved = AtomicBoolean(false)

        compose.setContent {
            GgymTheme {
                SlotEditorSheet(
                    plannedExerciseId = editedSlotId.value,
                    viewModelFactory = { slotId ->
                        viewModelFactory {
                            initializer {
                                SlotEditorViewModel(
                                    routineId = seeded.routineId,
                                    plannedExerciseId = slotId,
                                    routines = routineRepository,
                                    exercises = exerciseRepository,
                                    weights = weights,
                                    locale = Locale.ENGLISH,
                                )
                            }
                        }
                    },
                    onDismiss = { saved.set(true) },
                )
            }
        }

        // 1. Opened for the bench press: its exercise, its 4 sets, its own 80 kg.
        waitForText(seeded.benchExerciseName)
        compose.onNodeWithText("80").assertExists()
        compose.onNodeWithText("Cable Fly").assertDoesNotExist()

        // 2. The user closes it and opens the *other* slot: the sheet must be about that one.
        compose.runOnIdle { editedSlotId.value = seeded.flySlotId }
        waitForText(seeded.flyExerciseName)
        compose.onNodeWithText(seeded.benchExerciseName).assertDoesNotExist()
        compose.onNodeWithText("70").assertExists()

        // 3. Stepping the weight and saving writes the second slot and leaves the first alone.
        compose.onNodeWithContentDescription(string(R.string.weight_increase)).performClick()
        compose.onNodeWithText("72.5").assertExists()
        compose.onNodeWithText(string(R.string.action_save)).performClick()

        // The save is a view-model coroutine: the sheet closing is the signal that it finished.
        awaitSaved(saved)

        val stored = runBlocking { readSlots(seeded.routineId) }
        assertEquals(72.5, stored.getValue(seeded.flySlotId).weightKg)
        assertEquals(80.0, stored.getValue(seeded.benchSlotId).weightKg)
    }

    /**
     * Idles until the editor reports the slot written.
     *
     * A plain `waitUntil` is not enough here: the save is a coroutine on `viewModelScope`, which is
     * the *main* dispatcher, and under Robolectric that dispatcher only makes progress while the
     * looper is idled — which is what [ComposeTestRule.waitForIdle] does and what polling the
     * condition alone does not.
     */
    private fun awaitSaved(saved: AtomicBoolean) {
        repeat(WAIT_ATTEMPTS) {
            compose.waitForIdle()
            if (saved.get()) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("the editor never reported the slot as saved")
    }

    /** Every slot of [routineId], read straight from the database through a fresh query. */
    private suspend fun readSlots(routineId: Long): Map<Long, PlannedExercise> =
        routineRepository.observeRoutine(routineId).first()
            ?.days
            ?.flatMap { day -> day.plannedExercises }
            ?.associateBy { slot -> slot.id }
            .orEmpty()

    private fun string(resId: Int): String = context.getString(resId)

    /** A routine day with two exercises, each slot carrying its own weight. */
    private suspend fun seedPushDay(): SeededDay {
        val bench = exerciseRepository.findOrCreate("Bench Press")
        val fly = exerciseRepository.findOrCreate("Cable Fly")
        val routineId = routineRepository.createRoutine("Push Day")
        val dayId = routineRepository.addDay(routineId, "Push")
        val benchSlotId = routineRepository.addPlannedExercise(
            routineDayId = dayId,
            exerciseId = bench.id,
            targetSets = 4,
            targetReps = "8-10",
            restSeconds = 90,
            weightKg = 80.0,
        )
        val flySlotId = routineRepository.addPlannedExercise(
            routineDayId = dayId,
            exerciseId = fly.id,
            targetSets = 3,
            targetReps = "10",
            restSeconds = 120,
            weightKg = 70.0,
        )
        return SeededDay(
            routineId = routineId,
            benchSlotId = benchSlotId,
            benchExerciseName = bench.name,
            flySlotId = flySlotId,
            flyExerciseName = fly.name,
        )
    }

    /** The sheet animates in from `Hidden`; waiting for its first words is what tells us it is up. */
    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private data class SeededDay(
        val routineId: Long,
        val benchSlotId: Long,
        val benchExerciseName: String,
        val flySlotId: Long,
        val flyExerciseName: String,
    )

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val WAIT_ATTEMPTS = 100
        const val POLL_MILLIS = 50L
    }
}
