package com.ggym.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The countdown's backstop, delivered by the exact alarm [RestTimerAlarms] armed.
 *
 * It exists for the case the foreground service cannot cover: the process was suspended, asleep or
 * killed, and the task scheduled to end the rest never ran. A manifest receiver is always allowed
 * to do this little work — unlike starting a foreground service from the background — so it ends
 * the rest itself, through the same [RestTimerCompletion] the service uses.
 *
 * If the alarm turns out to be early (a wall clock that moved backwards while the rest ran), the
 * rest is not over: the backstop is simply re-armed for the deadline that is still ahead.
 */
class RestTimerAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_FINISHED) return

        val rest = RestTimerEnvironment.dependencies(context)
        // `goAsync()` gives the receiver a few more seconds of life for the store write that
        // follows. It is null only when the receiver was called outside a real broadcast dispatch,
        // which is not how the system delivers this one — but a background component must not crash
        // over a promise the framework did not make.
        val handling = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                val state = rest.store.state.first() ?: return@launch
                if (rest.timers.hasElapsed(state)) {
                    rest.completion.complete(state)
                } else {
                    rest.alarms.arm(state.endsAtMillis)
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
