package com.ggym.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ggym.app.MainActivity
import com.ggym.app.R
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.workout.RestTimerUseCases

/**
 * The timer's two notifications and the two channels they arrive on.
 *
 * **Running**: the foreground service's own notification — low importance, silent, ongoing, with the
 * countdown drawn by the system (`usesChronometer` + `chronometerCountDown`, based on the stored
 * deadline, so it keeps ticking while the app is suspended and never drifts) and the three commands
 * as actions. The actions are what the app's own banner sends, down to the intent: one place
 * handles a tap whether it came from the shade or from the screen.
 *
 * **Finished**: a separate high-importance channel with vibration on and no sound — the sound is the
 * app's own tone ([RestAlertPlayer]), which is the only way it can duck the music. Both notifications
 * share one id: the timer has one notification, which turns into the alert when the rest is over.
 */
class RestTimerNotifications(private val context: Context) {

    /** Creates both channels. Idempotent, so every start can call it. */
    fun ensureChannels() {
        NotificationManagerCompat.from(context).createNotificationChannels(
            listOf(runningChannel(), finishedChannel()),
        )
    }

    /** The countdown, as the foreground service's notification. */
    fun running(state: RestTimerState): Notification = base(CHANNEL_RUNNING)
        .setContentTitle(state.exerciseName)
        .setContentText(context.getString(R.string.rest_notification_text))
        .setOngoing(true)
        .setSilent(true)
        .setOnlyAlertOnce(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setWhen(state.endsAtMillis)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .addAction(
            0,
            context.getString(R.string.rest_action_add_time),
            command(RestTimerService.extendIntent(context, RestTimerUseCases.STEP_SECONDS), REQUEST_CODE_ADD_TIME),
        )
        .addAction(
            0,
            context.getString(R.string.rest_action_subtract_time),
            command(RestTimerService.extendIntent(context, -RestTimerUseCases.STEP_SECONDS), REQUEST_CODE_SUBTRACT_TIME),
        )
        .addAction(
            0,
            context.getString(R.string.rest_action_skip),
            command(RestTimerService.skipIntent(context), REQUEST_CODE_SKIP),
        )
        .build()

    /** The alert, once the rest is over. */
    fun finished(state: RestTimerState): Notification = base(CHANNEL_FINISHED)
        .setContentTitle(context.getString(R.string.rest_finished_notification_title))
        .setContentText(context.getString(R.string.rest_finished_notification_text, state.exerciseName))
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .build()

    /**
     * Posts [notification] for the timer's single id. Used by the alarm backstop, which ends a rest
     * the service's process did not live to see.
     *
     * Nothing is posted when the user has not allowed notifications. That is an ordinary outcome,
     * not an error: the timer, its in-app banner and the alert's tone all work without the drawer,
     * and the platform would drop the notification anyway.
     */
    fun post(notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed || !manager.areNotificationsEnabled()) return

        manager.notify(NOTIFICATION_ID, notification)
    }

    /** Removes the timer's notification: the countdown is gone, and nothing happened to announce. */
    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun base(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentIntent(openApp())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    /**
     * Tapping a timer notification opens the running workout. The timer only runs inside a workout,
     * so the activity is asked to open it ([EXTRA_OPEN_WORKOUT]) rather than being left on whatever
     * screen the user last saw: after a process death that screen is gone, and the workout is not.
     */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        CONTENT_REQUEST_CODE,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_WORKOUT, true)
        },
        PENDING_INTENT_FLAGS,
    )

    /**
     * One notification action, as a *foreground* service intent: the user is acting on a
     * notification, which is exactly the case in which the app may ask for one.
     *
     * Each action carries its own request code: two pending intents built from the same action are
     * the same intent as far as the framework is concerned, extras and all, so `+15 s` would
     * otherwise be delivered with whatever the last one put in the extras.
     */
    private fun command(intent: Intent, requestCode: Int): PendingIntent = PendingIntent.getForegroundService(
        context,
        requestCode,
        intent,
        PENDING_INTENT_FLAGS,
    )

    private fun runningChannel(): NotificationChannel =
        NotificationChannel(
            CHANNEL_RUNNING,
            context.getString(R.string.rest_channel_running_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.rest_channel_running_description)
            setShowBadge(false)
        }

    private fun finishedChannel(): NotificationChannel =
        NotificationChannel(
            CHANNEL_FINISHED,
            context.getString(R.string.rest_channel_finished_name),
            // Heads-up, because a rest ending is worth looking at; vibration, because the phone may
            // be in a pocket; and no sound, because the sound is ours and ducks.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.rest_channel_finished_description)
            setSound(null, null)
            enableVibration(true)
            setShowBadge(false)
        }

    companion object {
        const val CHANNEL_RUNNING = "rest_timer_running"
        const val CHANNEL_FINISHED = "rest_timer_finished"

        /** One id for the whole timer: the countdown and its alert are the same notification. */
        const val NOTIFICATION_ID = 1001

        private const val CONTENT_REQUEST_CODE = 0
        private const val REQUEST_CODE_ADD_TIME = 1
        private const val REQUEST_CODE_SUBTRACT_TIME = 2
        private const val REQUEST_CODE_SKIP = 3
        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
