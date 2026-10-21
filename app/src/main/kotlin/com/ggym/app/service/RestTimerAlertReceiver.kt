package com.ggym.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The countdown's backstop, delivered by the exact alarm [RestTimerAlarms] armed.
 *
 * It exists for the case the foreground service cannot cover: the process was suspended, asleep or
 * killed, and the task scheduled to end the rest never ran.
 *
 * Its job is **to wake the service**, not to end the rest. M3 ended the rest here, because ending it
 * was all there was to do; with a ringing alert it no longer is. A receiver lives for a few seconds,
 * and a `MediaPlayer` started in one dies with its process — so the receiver hands the elapsed rest
 * to [RestTimerService], which stays in the foreground for as long as the alert rings. That start is
 * allowed from the background because invoking an exact alarm to complete an action the user asked
 * for is one of the platform's documented exemptions from the restriction.
 *
 * [completeHere] is the fallback for the case where the platform refuses that start anyway: without a
 * process there is nothing to ring from, but the rest is still ended, so the deadline cannot come
 * back to life and the notification at least says the rest is over.
 */
class RestTimerAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_FINISHED) return

        // A null intent tells the service to decide from the stored deadline, which is also how it
        // finds an early alarm (a wall clock that moved backwards while the rest ran) and re-arms.
        val woke = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RestTimerService::class.java),
            )
        }.isSuccess

        if (!woke) completeHere(context)
    }

    /**
     * Ends the elapsed rest without a service, and posts the alert as an ordinary notification.
     *
     * `goAsync()` gives the receiver a few more seconds of life for the store write that follows. It
     * is null only when the receiver was called outside a real broadcast dispatch, which is not how
     * the system delivers this one — but a background component must not crash over a promise the
     * framework did not make.
     */
    private fun completeHere(context: Context) {
        val rest = RestTimerEnvironment.dependencies(context)
        val handling = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                val state = rest.store.state.first() ?: return@launch
                if (!rest.timers.hasElapsed(state)) {
                    rest.alarms.arm(state.endsAtMillis)
                    return@launch
                }
                if (rest.completion.complete(state)) {
                    rest.notifications.post(rest.notifications.finished(state))
                }
            } finally {
                handling?.finish()
            }
        }
    }

    companion object {
        /** The action the backstop's alarm carries. Only this app sends it. */
        const val ACTION_REST_FINISHED = "com.ggym.app.action.REST_TIMER_FINISHED"
    }
}
