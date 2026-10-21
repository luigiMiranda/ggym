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
import com.ggym.app.GgymApplication
import com.ggym.app.MainActivity
import com.ggym.app.R
import com.ggym.app.data.prefs.withAppLanguage
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.workout.RestTimerUseCases
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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

    /**
     * The context the notification's copy is read from, with the app's language applied.
     *
     * The activity's `attachBaseContext` covers every screen; this is the one surface outside it,
     * and this class is built by the container with the *application* context, which no activity
     * ever wrapped. The read is a preference lookup — an in-memory value after the first one.
     */
    private fun stringsContext(): Context {
        val preferences = (context.applicationContext as GgymApplication).container.languagePreferences
        return context.withAppLanguage(runBlocking { preferences.language.first() })
    }

    /** Creates both channels. Idempotent, so every start can call it. */
    fun ensureChannels() {
        NotificationManagerCompat.from(context).createNotificationChannels(
            listOf(runningChannel(), finishedChannel()),
        )
    }

    /** The countdown, as the foreground service's notification. */
    fun running(state: RestTimerState): Notification = base(CHANNEL_RUNNING, openWorkout())
        .setContentTitle(state.exerciseName)
        .setContentText(stringsContext().getString(R.string.rest_notification_text))
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
            stringsContext().getString(R.string.rest_action_add_time),
            command(RestTimerService.extendIntent(context, RestTimerUseCases.STEP_SECONDS), REQUEST_CODE_ADD_TIME),
        )
        .addAction(
            0,
            stringsContext().getString(R.string.rest_action_subtract_time),
            command(RestTimerService.extendIntent(context, -RestTimerUseCases.STEP_SECONDS), REQUEST_CODE_SUBTRACT_TIME),
        )
        .addAction(
            0,
            stringsContext().getString(R.string.rest_action_skip),
            command(RestTimerService.skipIntent(context), REQUEST_CODE_SKIP),
        )
        .build()

    /**
     * The alert, once the rest is over.
     *
     * It carries **Stop**: M3 could leave this notification actionless because the sound was 700 ms
     * long, and a ringing alert has to have a way out. Tapping the body also silences it (through
     * [EXTRA_SILENCE_ALERT]) and opens the workout, which is what the running notification does.
     */
    fun finished(state: RestTimerState): Notification = base(CHANNEL_FINISHED, openWorkoutAndSilence())
        .setContentTitle(stringsContext().getString(R.string.rest_finished_notification_title))
        .setContentText(stringsContext().getString(R.string.rest_finished_notification_text, state.exerciseName))
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .addAction(
            0,
            stringsContext().getString(R.string.rest_action_stop),
            command(RestTimerService.silenceIntent(context), REQUEST_CODE_SILENCE),
        )
        .build()

    /**
     * The alert, once it has been silenced: the same notification **without** its Stop action, and
     * without the body tap's silence.
     *
     * The action has to go, and this is not cosmetic. Stop starts the service as a *foreground* one,
     * which is allowed while the alert rings (the service is in the foreground and a notification tap
     * is an exemption anyway). Left on screen after the ring has stopped itself, the same tap would
     * start a service that has nothing to do, and one started as foreground owes the platform a
     * `startForeground` call within five seconds — the tap would be answered with a crash. Removing
     * the action is how "nothing is ringing" stops being tappable.
     */
    fun silenced(state: RestTimerState?): Notification = base(CHANNEL_FINISHED, openWorkout())
        .setContentTitle(stringsContext().getString(R.string.rest_finished_notification_title))
        .setContentText(
            state?.let { stringsContext().getString(R.string.rest_finished_notification_text, it.exerciseName) }
                ?: stringsContext().getString(R.string.rest_finished_notification_text_fallback),
        )
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

    private fun base(channelId: String, content: PendingIntent): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentIntent(content)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    /**
     * Tapping a running countdown opens the workout it belongs to. The timer only runs inside a
     * workout, so the activity is asked to open it ([EXTRA_OPEN_WORKOUT]) rather than being left on
     * whatever screen the user last saw: after a process death that screen is gone, and the workout
     * is not.
     */
    private fun openWorkout(): PendingIntent =
        openApp(CONTENT_REQUEST_CODE, silenceAlert = false)

    /** The alert's body tap: the same navigation, plus the silence the alert needs. */
    private fun openWorkoutAndSilence(): PendingIntent =
        openApp(ALERT_CONTENT_REQUEST_CODE, silenceAlert = true)

    /**
     * The two callers above use different request codes on purpose: two pending intents built from
     * the same activity intent are *the same intent* as far as the framework is concerned, extras
     * and all, so a shared request code would let whichever was built last decide whether a tap
     * silences — which is exactly the bug the per-action request codes below avoid.
     */
    private fun openApp(requestCode: Int, silenceAlert: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_WORKOUT, true)
            if (silenceAlert) putExtra(EXTRA_SILENCE_ALERT, true)
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
            stringsContext().getString(R.string.rest_channel_running_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = stringsContext().getString(R.string.rest_channel_running_description)
            setShowBadge(false)
        }

    private fun finishedChannel(): NotificationChannel =
        NotificationChannel(
            CHANNEL_FINISHED,
            stringsContext().getString(R.string.rest_channel_finished_name),
            // Heads-up, because a rest ending is worth looking at; vibration, because the phone may
            // be in a pocket; and no sound, because the sound is ours and ducks.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = stringsContext().getString(R.string.rest_channel_finished_description)
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
        private const val REQUEST_CODE_SILENCE = 4

        /** The alert's body tap, kept apart from [CONTENT_REQUEST_CODE] so the extras cannot merge. */
        private const val ALERT_CONTENT_REQUEST_CODE = 5
        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
