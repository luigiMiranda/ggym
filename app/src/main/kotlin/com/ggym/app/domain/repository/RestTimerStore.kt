package com.ggym.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The rest countdown that is running, or `null` for none.
 *
 * What is stored is the **deadline**, not the time left. A remaining count would have to be
 * decremented by a ticking coroutine, and a countdown that stops ticking when the process dies is
 * worth nothing in a gym: [endsAtMillis] is wall-clock epoch millis, so the number is still right
 * after the app is killed, the phone is locked or it reboots, and every place that shows the
 * countdown computes it as `endsAtMillis - now`. The countdown's own alarm is armed in the
 * elapsed-realtime base, because that is the clock an alarm needs; the deadline itself has to be
 * the wall clock for the stored value to still mean something after a restart.
 */
data class RestTimerState(
    /** Wall-clock epoch millis when the rest ends. */
    val endsAtMillis: Long,
    /**
     * The rest the set asked for, in seconds — the countdown's original length. `+15 s` / `-15 s`
     * move [endsAtMillis] and leave this alone: the deadline is what the user is waiting for, this
     * is only what the rest was meant to be.
     */
    val totalSeconds: Int,
    /** The exercise the rest belongs to, so the banner and the notification can name it. */
    val exerciseLogId: Long,
    val exerciseName: String,
)

/**
 * Where the running countdown is kept.
 *
 * There is exactly one writer: the foreground service that owns the timer. Everything else reads —
 * which is why the app, its notification and a process that was restarted halfway through a rest
 * can never disagree about what is running.
 */
interface RestTimerStore {

    /** The countdown, or `null` when there is none. */
    val state: Flow<RestTimerState?>

    /** Stores [state], or clears the timer when it is `null`. */
    suspend fun set(state: RestTimerState?)
}
