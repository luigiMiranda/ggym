package com.ggym.app.domain.repository

/**
 * What the app asks the running rest timer to do.
 *
 * The countdown is owned by a foreground service, which is the only thing that writes
 * [RestTimerStore], so a screen never changes the timer directly: it sends one of these, and the
 * implementation turns it into an intent to that service. The notification's `+15 s` / `-15 s` /
 * `Skip` buttons send the same intents, so a tap in the shade and a tap in the app take exactly
 * the same path.
 *
 * The transport carries no rules: what a rest is, what a delta does to a deadline and when a rest
 * is over live in `domain/workout/RestTimerUseCases`.
 */
interface RestTimerCommands {

    /** Starts a countdown, replacing whatever was running. */
    suspend fun start(state: RestTimerState)

    /** Moves the running countdown's deadline by [deltaSeconds]; negative to cut a rest short. */
    suspend fun extend(deltaSeconds: Int)

    /** Ends the running countdown without an alert: the user is moving on to the next set. */
    suspend fun skip()

    /** Ends the running countdown and shuts the timer down: the workout is over, or the switch is off. */
    suspend fun stop()

    /**
     * Silences an alert that is ringing.
     *
     * Unlike [skip] this ends no countdown: an alert only exists once the rest is over, so the store
     * is already empty by the time it rings. It is sent unconditionally for that reason — a caller
     * cannot check "is an alert ringing?" by looking at the countdown, and the service treats a
     * silence with nothing to silence as a no-op.
     */
    suspend fun silence()
}
