package com.ggym.app.testing

import com.ggym.app.domain.repository.RestTimerCommands
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.repository.RestTimerStore
import com.ggym.app.domain.repository.WorkoutPreferences
import com.ggym.app.service.RestAlertPlayer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Test doubles for the timer, so its rules can be driven without a service, a notification or a
 * real clock: [FakeRestTimerStore] is what the service would have written, [FakeRestTimerCommands]
 * is what it would have received, and [TestClock] is when it all happens.
 */

/** The countdown, in memory. */
internal class FakeRestTimerStore(initial: RestTimerState? = null) : RestTimerStore {

    val current = MutableStateFlow(initial)

    override val state: Flow<RestTimerState?> = current

    override suspend fun set(state: RestTimerState?) {
        current.value = state
    }
}

/** One command that was sent, kept in the order it was sent. */
internal sealed interface RecordedCommand {
    data class Start(val state: RestTimerState) : RecordedCommand
    data class Extend(val deltaSeconds: Int) : RecordedCommand
    data object Skip : RecordedCommand
    data object Stop : RecordedCommand
}

/** The command channel, recording instead of sending. */
internal class FakeRestTimerCommands : RestTimerCommands {

    val received = mutableListOf<RecordedCommand>()

    override suspend fun start(state: RestTimerState) {
        received += RecordedCommand.Start(state)
    }

    override suspend fun extend(deltaSeconds: Int) {
        received += RecordedCommand.Extend(deltaSeconds)
    }

    override suspend fun skip() {
        received += RecordedCommand.Skip
    }

    override suspend fun stop() {
        received += RecordedCommand.Stop
    }
}

/** The master switch, in memory. */
internal class FakeWorkoutPreferences(initialRestTimerEnabled: Boolean = true) : WorkoutPreferences {

    val enabled = MutableStateFlow(initialRestTimerEnabled)

    override val restTimerEnabled: Flow<Boolean> = enabled

    override suspend fun setRestTimerEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }
}

/**
 * A clock the test moves by hand.
 *
 * Deadlines are the timer's whole business, so a test that waited for one would be slower and
 * flakier than one that decides when "now" is.
 */
internal class TestClock(var millis: Long = 1_750_000_000_000L) {

    fun now(): Long = millis

    fun advanceSeconds(seconds: Int) {
        millis += seconds * 1_000L
    }
}

/**
 * The alert, counted instead of played: the service's job is to ask for it, and the tone and the
 * audio focus are `AndroidRestAlertPlayer`'s own business.
 */
internal class FakeRestAlertPlayer : RestAlertPlayer {

    var played = 0
        private set

    override fun play() {
        played++
    }
}
