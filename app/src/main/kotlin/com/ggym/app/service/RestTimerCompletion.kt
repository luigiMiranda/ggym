package com.ggym.app.service

import com.ggym.app.domain.repository.RestTimerStore
import com.ggym.app.domain.repository.RestTimerState
import kotlinx.coroutines.flow.first

/**
 * The end of a rest, in one place — because a countdown ends in two of them.
 *
 * Normally [RestTimerService]'s own task reaches the deadline and calls [complete]; when the
 * process was asleep, suspended or gone, the exact alarm's [RestTimerAlertReceiver] does. Both go
 * through this class, so the alert, the notification and the stored state cannot disagree about
 * what happened, and both are the *only* thing that writes the timer outside the service's own
 * command handling.
 *
 * A completion is precise about what it ends: [complete] takes the state it is finishing and does
 * nothing when a different rest has started in the meantime. A set completed exactly as the
 * previous rest runs out must not have its countdown cancelled by the old one.
 */
class RestTimerCompletion(
    private val store: RestTimerStore,
    private val alarms: RestTimerAlarms,
    private val notifications: RestTimerNotifications,
    private val alerts: RestAlertPlayer,
) {

    /**
     * The rest that ran out: the alert sounds, the countdown's notification becomes the finished
     * one, the backstop is cancelled and the stored deadline is cleared so that nothing resumes it.
     *
     * Returns whether this call ended [state]; `false` means another path had already ended it (or
     * a newer rest replaced it), which makes the alarm and the service safe to have fire together.
     */
    suspend fun complete(state: RestTimerState): Boolean {
        val running = store.state.first() ?: return false
        if (running != state) return false

        store.set(null)
        alarms.cancel()
        notifications.post(notifications.finished(state))
        alerts.play()
        return true
    }

    /** The user ended the rest: nothing is left behind, and nothing is played. */
    suspend fun cancel() {
        store.set(null)
        alarms.cancel()
        notifications.cancel()
    }
}
