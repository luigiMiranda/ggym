package com.ggym.app.domain.workout

import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.testing.FakeRestTimerCommands
import com.ggym.app.testing.FakeRestTimerStore
import com.ggym.app.testing.FakeWorkoutPreferences
import com.ggym.app.testing.RecordedCommand
import com.ggym.app.testing.TestClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rest timer's rules, over a clock the test owns, a store it can read and a command channel it
 * can inspect. No service, no notification, no sleeping.
 */
class RestTimerUseCasesTest {

    private val store = FakeRestTimerStore()
    private val commands = FakeRestTimerCommands()
    private val preferences = FakeWorkoutPreferences()
    private val clock = TestClock()
    private val timers = RestTimerUseCases(
        store = store,
        commands = commands,
        preferences = preferences,
        now = clock::now,
    )

    // -- starting a rest -------------------------------------------------------

    @Test
    fun `a completed set starts a rest for its own exercise`() = runTest {
        val started = timers.startForSet(
            workoutExerciseLogId = 7,
            exerciseName = "Bench Press",
            restSeconds = 90,
        )

        assertEquals(
            RestTimerState(
                endsAtMillis = clock.millis + 90_000L,
                totalSeconds = 90,
                exerciseLogId = 7,
                exerciseName = "Bench Press",
            ),
            started,
        )
        assertEquals(listOf(RecordedCommand.Start(started!!)), commands.received)
    }

    @Test
    fun `with the timer switched off no rest is started and nothing is sent`() = runTest {
        preferences.setRestTimerEnabled(false)

        val started = timers.startForSet(
            workoutExerciseLogId = 7,
            exerciseName = "Bench Press",
            restSeconds = 90,
        )

        assertNull(started)
        assertTrue(commands.received.isEmpty())
        assertNull(store.current.value)
    }

    @Test
    fun `a set with no rest time starts nothing`() = runTest {
        val started = timers.startForSet(
            workoutExerciseLogId = 7,
            exerciseName = "Bench Press",
            restSeconds = 0,
        )

        assertNull(started)
        assertTrue(commands.received.isEmpty())
    }

    @Test
    fun `a later set replaces the countdown that was running`() = runTest {
        val first = timers.startForSet(
            workoutExerciseLogId = 7,
            exerciseName = "Bench Press",
            restSeconds = 90,
        )
        clock.advanceSeconds(30)

        val second = timers.startForSet(
            workoutExerciseLogId = 8,
            exerciseName = "Squat",
            restSeconds = 120,
        )

        assertEquals(clock.millis + 120_000L, second?.endsAtMillis)
        assertEquals(
            listOf(RecordedCommand.Start(first!!), RecordedCommand.Start(second!!)),
            commands.received,
        )
    }

    // -- the deadline maths ----------------------------------------------------

    @Test
    fun `plus fifteen moves the deadline later`() {
        val state = rest(endsAtMillis = clock.millis + 20_000L)

        assertEquals(
            state.copy(endsAtMillis = state.endsAtMillis + 15_000L),
            timers.moveDeadline(state, RestTimerUseCases.STEP_SECONDS),
        )
    }

    @Test
    fun `minus fifteen moves the deadline earlier while there is time left`() {
        val state = rest(endsAtMillis = clock.millis + 60_000L)

        assertEquals(
            state.copy(endsAtMillis = clock.millis + 45_000L),
            timers.moveDeadline(state, -RestTimerUseCases.STEP_SECONDS),
        )
    }

    @Test
    fun `a minus fifteen that reaches zero finishes the rest`() {
        val state = rest(endsAtMillis = clock.millis + 15_000L)

        assertNull(timers.moveDeadline(state, -RestTimerUseCases.STEP_SECONDS))
    }

    @Test
    fun `a minus fifteen past the deadline finishes the rest too`() {
        val state = rest(endsAtMillis = clock.millis + 5_000L)

        assertNull(timers.moveDeadline(state, -RestTimerUseCases.STEP_SECONDS))
    }

    @Test
    fun `extending does not rewrite the length the set asked for`() {
        val state = rest(endsAtMillis = clock.millis + 20_000L, totalSeconds = 90)

        assertEquals(90, timers.moveDeadline(state, RestTimerUseCases.STEP_SECONDS)?.totalSeconds)
    }

    // -- the remaining time ----------------------------------------------------

    @Test
    fun `the remaining time is measured from the deadline, not from the seconds spent`() = runTest {
        // A rest meant to last 90 s, 50 s into it, as a restarted process would read it back.
        store.current.value = rest(endsAtMillis = clock.millis + 40_000L, totalSeconds = 90)

        val restored = timers.state.first()

        assertEquals(40_000L, timers.remainingMillis(restored!!))
        clock.advanceSeconds(10)
        assertEquals(30_000L, timers.remainingMillis(restored))
    }

    @Test
    fun `a deadline that is already in the past has nothing left`() {
        val state = rest(endsAtMillis = clock.millis - 1_000L)

        assertEquals(0L, timers.remainingMillis(state))
        assertTrue(timers.hasElapsed(state))
    }

    @Test
    fun `a deadline exactly now has nothing left`() {
        assertTrue(timers.hasElapsed(rest(endsAtMillis = clock.millis)))
    }

    @Test
    fun `a deadline still to come has time left`() {
        val state = rest(endsAtMillis = clock.millis + 1_000L)

        assertFalse(timers.hasElapsed(state))
        assertEquals(1_000L, timers.remainingMillis(state))
    }

    // -- what the app asks the service for -------------------------------------

    @Test
    fun `extending a running rest asks the service`() = runTest {
        store.current.value = rest()

        timers.extend(RestTimerUseCases.STEP_SECONDS)

        assertEquals(listOf(RecordedCommand.Extend(RestTimerUseCases.STEP_SECONDS)), commands.received)
    }

    @Test
    fun `extending with nothing running sends nothing`() = runTest {
        timers.extend(RestTimerUseCases.STEP_SECONDS)

        assertTrue(commands.received.isEmpty())
    }

    @Test
    fun `skipping ends a running rest without an alert`() = runTest {
        store.current.value = rest()

        timers.skip()

        assertEquals(listOf(RecordedCommand.Skip), commands.received)
    }

    @Test
    fun `skipping with nothing running sends nothing`() = runTest {
        timers.skip()

        assertTrue(commands.received.isEmpty())
    }

    @Test
    fun `stopping tears a running countdown down`() = runTest {
        store.current.value = rest()

        timers.stop()

        assertEquals(listOf(RecordedCommand.Stop), commands.received)
    }

    @Test
    fun `stopping with nothing running sends nothing`() = runTest {
        timers.stop()

        assertTrue(commands.received.isEmpty())
    }

    // -- the master switch -----------------------------------------------------

    @Test
    fun `switching the timer off writes the switch and ends a running rest`() = runTest {
        store.current.value = rest()

        timers.setEnabled(false)

        assertFalse(preferences.enabled.value)
        assertEquals(listOf(RecordedCommand.Stop), commands.received)
    }

    @Test
    fun `switching the timer off with nothing running only writes the switch`() = runTest {
        timers.setEnabled(false)

        assertFalse(preferences.enabled.value)
        assertTrue(commands.received.isEmpty())
    }

    @Test
    fun `switching the timer on starts nothing by itself`() = runTest {
        timers.setEnabled(false)
        timers.setEnabled(true)

        assertTrue(preferences.enabled.value)
        assertTrue(commands.received.isEmpty())
    }

    @Test
    fun `the switch the screen reads is the one the rule writes`() = runTest {
        assertTrue(timers.isEnabled.first())

        timers.setEnabled(false)

        assertFalse(timers.isEnabled.first())
    }

    /** The countdown the service wrote and the app is about to render. */
    private fun rest(
        endsAtMillis: Long = clock.millis + 60_000L,
        totalSeconds: Int = 90,
    ): RestTimerState = RestTimerState(
        endsAtMillis = endsAtMillis,
        totalSeconds = totalSeconds,
        exerciseLogId = 7,
        exerciseName = "Bench Press",
    )
}
