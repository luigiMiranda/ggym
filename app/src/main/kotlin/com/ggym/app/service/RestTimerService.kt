package com.ggym.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.ggym.app.domain.repository.RestTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The rest countdown, as a foreground service — what makes a rest outlive the app it started in.
 *
 * Everything it does, it does through *commands*: `start`, `extend`, `skip`, `stop`, arriving as
 * intents from the app ([ServiceRestTimerCommands]) and from the notification's own buttons, which
 * send exactly the same intents. Handling one means reading the stored countdown, applying the
 * command, writing it back, redrawing the notification and moving the backstop alarm. The stored
 * deadline is the truth here; nothing in this class counts seconds, so a process that comes back
 * after ten minutes resumes the right number instead of ten minutes' worth of drift.
 *
 * Two things end a rest, and both go through [RestTimerCompletion]:
 *
 * - the task scheduled for the deadline — precise, and the path used while the process is alive;
 * - [RestTimerAlertReceiver] — the exact alarm's backstop for when it is not.
 *
 * The service is the countdown's only writer while a rest runs; the completion is the only thing
 * that clears it. That is what keeps the app's banner, the shade and a restarted process telling
 * the same story.
 */
class RestTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Commands are applied one at a time: two taps must not read the same deadline. */
    private val handling = Mutex()

    /** The task that ends the rest on time. Only ever one, and only while a rest runs. */
    private var deadline: Job? = null

    private lateinit var rest: RestTimerDependencies

    override fun onCreate() {
        super.onCreate()
        rest = RestTimerEnvironment.dependencies(this)
        rest.notifications.ensureChannels()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            handling.withLock { handle(intent) }
        }
        // A rest that is running is worth coming back for: if the system kills the process, the
        // service is recreated with a null intent and picks the countdown up from the deadline.
        return START_STICKY
    }

    override fun onDestroy() {
        deadline?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_EXTEND -> extend(intent.getIntExtra(EXTRA_DELTA_SECONDS, 0))
            ACTION_SKIP, ACTION_STOP -> end()
            // Nothing to go on: either the system restarted the service, or an intent it does not
            // know. The stored deadline decides what happens, not the intent.
            else -> restore()
        }
    }

    /** The app started a rest: the deadline it computed, shown at once and backed up. */
    private suspend fun start(intent: Intent) {
        val state = intent.state() ?: return shutDown()

        rest.store.set(state)
        showRunning(state)
        rest.alarms.arm(state.endsAtMillis)
        scheduleDeadline()
    }

    /** `+15 s` / `-15 s`. A negative delta that reaches the deadline finishes the rest here. */
    private suspend fun extend(deltaSeconds: Int) {
        val running = rest.store.state.first() ?: return shutDown()
        val moved = rest.timers.moveDeadline(running, deltaSeconds) ?: return complete(running)

        rest.store.set(moved)
        showRunning(moved)
        rest.alarms.arm(moved.endsAtMillis)
        scheduleDeadline()
    }

    /** `Skip` and the app's teardown: the countdown goes away without a sound. */
    private suspend fun end() {
        deadline?.cancel()
        rest.completion.cancel()
        removeForeground()
        stopSelf()
    }

    /**
     * The path taken when the system recreates the service (or when it is started without a
     * command): the stored deadline decides, and a rest that already ran out ends — with its alert —
     * instead of coming back to life.
     */
    private suspend fun restore() {
        val running = rest.store.state.first() ?: return shutDown()
        if (rest.timers.hasElapsed(running)) return complete(running)

        showRunning(running)
        rest.alarms.arm(running.endsAtMillis)
        scheduleDeadline()
    }

    /**
     * Schedules the moment this rest is over, and keeps it honest: after waking, the deadline is
     * re-read, so a wall clock that moved while the process slept ends the rest at the right moment
     * rather than by the sleep's arithmetic.
     */
    private fun scheduleDeadline() {
        deadline?.cancel()
        deadline = scope.launch {
            while (true) {
                val running = rest.store.state.first() ?: return@launch
                val remaining = rest.timers.remainingMillis(running)
                if (remaining <= 0L) {
                    deadline = null
                    complete(running)
                    return@launch
                }
                delay(remaining)
            }
        }
    }

    /** The rest is over: the alert and the finished notification, then the service's own end. */
    private suspend fun complete(state: RestTimerState) {
        deadline?.cancel()
        deadline = null
        rest.completion.complete(state)
        // Detached, not removed: what is on screen now is the alert, and it should stay there until
        // the user looks at it.
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun showRunning(state: RestTimerState) {
        startForeground(RestTimerNotifications.NOTIFICATION_ID, rest.notifications.running(state))
    }

    /**
     * The service has nothing to run: a command that arrived after the rest ended, or a restart with
     * nothing stored. A foreground start that stops itself right away is the documented way out.
     */
    private fun shutDown() {
        removeForeground()
        stopSelf()
    }

    private fun removeForeground() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** The stored countdown an intent carries, or `null` when it is not a well-formed start. */
    private fun Intent.state(): RestTimerState? {
        val endsAtMillis = getLongExtra(EXTRA_ENDS_AT_MILLIS, 0L)
        val exerciseName = getStringExtra(EXTRA_EXERCISE_NAME) ?: return null
        if (endsAtMillis <= 0L) return null

        return RestTimerState(
            endsAtMillis = endsAtMillis,
            totalSeconds = getIntExtra(EXTRA_TOTAL_SECONDS, 0),
            exerciseLogId = getLongExtra(EXTRA_EXERCISE_LOG_ID, 0L),
            exerciseName = exerciseName,
        )
    }

    companion object {
        const val ACTION_START = "com.ggym.app.action.REST_TIMER_START"
        const val ACTION_EXTEND = "com.ggym.app.action.REST_TIMER_EXTEND"
        const val ACTION_SKIP = "com.ggym.app.action.REST_TIMER_SKIP"
        const val ACTION_STOP = "com.ggym.app.action.REST_TIMER_STOP"

        const val EXTRA_ENDS_AT_MILLIS = "com.ggym.app.extra.REST_TIMER_ENDS_AT_MILLIS"
        const val EXTRA_TOTAL_SECONDS = "com.ggym.app.extra.REST_TIMER_TOTAL_SECONDS"
        const val EXTRA_EXERCISE_LOG_ID = "com.ggym.app.extra.REST_TIMER_EXERCISE_LOG_ID"
        const val EXTRA_EXERCISE_NAME = "com.ggym.app.extra.REST_TIMER_EXERCISE_NAME"
        const val EXTRA_DELTA_SECONDS = "com.ggym.app.extra.REST_TIMER_DELTA_SECONDS"

        /** Starts a rest for [state]. Its deadline, not a duration, is what travels. */
        fun startIntent(context: Context, state: RestTimerState): Intent =
            Intent(context, RestTimerService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ENDS_AT_MILLIS, state.endsAtMillis)
                .putExtra(EXTRA_TOTAL_SECONDS, state.totalSeconds)
                .putExtra(EXTRA_EXERCISE_LOG_ID, state.exerciseLogId)
                .putExtra(EXTRA_EXERCISE_NAME, state.exerciseName)

        /** Moves the running rest by [deltaSeconds] — what `+15 s` and `-15 s` send. */
        fun extendIntent(context: Context, deltaSeconds: Int): Intent =
            Intent(context, RestTimerService::class.java)
                .setAction(ACTION_EXTEND)
                .putExtra(EXTRA_DELTA_SECONDS, deltaSeconds)

        /** Ends the running rest silently: the user is going to the next set. */
        fun skipIntent(context: Context): Intent =
            Intent(context, RestTimerService::class.java).setAction(ACTION_SKIP)

        /** Ends the running rest and the service with it: the workout is over, or the timer is off. */
        fun stopIntent(context: Context): Intent =
            Intent(context, RestTimerService::class.java).setAction(ACTION_STOP)
    }
}
