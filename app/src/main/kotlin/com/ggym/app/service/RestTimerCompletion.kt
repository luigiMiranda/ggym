package com.ggym.app.service

import com.ggym.app.domain.repository.RestTimerStore
import com.ggym.app.domain.repository.RestTimerState
import kotlinx.coroutines.flow.first

/**
 * The end of a rest, in one place.
 *
 * What *ending* a rest means is small and must not be written twice: the stored deadline goes, and
 * the backstop alarm that would otherwise end it again goes with it.
 *
 * What happens **after** that deliberately is not here, because it is not the same for both callers:
 *
 * - [RestTimerService] turns the countdown's notification into the alert and rings it, and it has to
 *   stay in the foreground to keep the ring alive — a notification posted from this class could not
 *   hold the process open;
 * - [RestTimerAlertReceiver]'s fallback, for the case where the platform refuses a service start,
 *   posts the alert and lets it be, because it has no process to ring from.
 *
 * A completion is precise about what it ends: [complete] takes the state it is finishing, and does
 * nothing when a different rest has started in the meantime. A set completed exactly as the previous
 * rest runs out must not have its countdown cancelled by the old one.
 */
class RestTimerCompletion(
    private val store: RestTimerStore,
    private val alarms: RestTimerAlarms,
    private val notifications: RestTimerNotifications,
) {

    /**
     * The rest that ran out: the deadline is cleared and the backstop cancelled, so that nothing
     * resumes it.
     *
     * Returns whether this call ended [state]; `false` means another path had already ended it (or a
     * newer rest replaced it), which is what makes the alarm and the service safe to fire together.
     */
    suspend fun complete(state: RestTimerState): Boolean {
        val running = store.state.first() ?: return false
        if (running != state) return false

        store.set(null)
        alarms.cancel()
        return true
    }

    /** The user ended the rest: nothing is left behind, and nothing is played. */
    suspend fun cancel() {
        store.set(null)
        alarms.cancel()
        notifications.cancel()
    }
}
