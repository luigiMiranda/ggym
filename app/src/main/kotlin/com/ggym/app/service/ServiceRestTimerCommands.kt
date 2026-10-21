package com.ggym.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ggym.app.domain.repository.RestTimerCommands
import com.ggym.app.domain.repository.RestTimerState

/**
 * The app's end of the timer's wire: [RestTimerCommands] as intents to [RestTimerService].
 *
 * Every command is sent as a *foreground* service start, the same way the notification's buttons
 * send theirs (`PendingIntent.getForegroundService`) — the two paths are one path. The service
 * answers a start with the countdown's notification, and stops itself when there is no rest to run,
 * so a command that arrives after the rest ended cannot leave a service behind.
 */
class ServiceRestTimerCommands(private val context: Context) : RestTimerCommands {

    override suspend fun start(state: RestTimerState) = send(RestTimerService.startIntent(context, state))

    override suspend fun extend(deltaSeconds: Int) =
        send(RestTimerService.extendIntent(context, deltaSeconds))

    override suspend fun skip() = send(RestTimerService.skipIntent(context))

    override suspend fun stop() = send(RestTimerService.stopIntent(context))

    override suspend fun silence() = send(RestTimerService.silenceIntent(context))

    private fun send(intent: Intent) {
        ContextCompat.startForegroundService(context, intent)
    }
}
