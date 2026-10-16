package com.ggym.app.service

import android.Manifest
import android.app.AlarmManager
import android.app.Service
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.domain.repository.RestTimerState
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.testing.FakeRestAlertPlayer
import com.ggym.app.testing.FakeRestTimerCommands
import com.ggym.app.testing.FakeRestTimerStore
import com.ggym.app.testing.FakeWorkoutPreferences
import com.ggym.app.testing.TestClock
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * The rest timer's foreground service, driven the way the app and the notification drive it: with
 * intents, against the real notification builder and the real alarm manager, and with a store that
 * is a fake only because a JVM test has no DataStore file to spare.
 *
 * Time is the platform clock here — Robolectric simulates it — so `idleFor` moves the countdown and
 * the deadline task together, exactly as ninety seconds would on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class RestTimerServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val store = FakeRestTimerStore()
    private val commands = FakeRestTimerCommands()
    private val preferences = FakeWorkoutPreferences()

    /** The wall clock the countdown is stored against, and the clock an alarm is armed on. */
    private val clock = TestClock(millis = 1_750_000_000_000L)
    private val elapsed = TestClock(millis = 42_000L)

    private val timers = RestTimerUseCases(store, commands, preferences, now = clock::now)
    private val alerts = FakeRestAlertPlayer()
    private val notifications = RestTimerNotifications(context)
    private val alarms = RestTimerAlarms(context, now = clock::now, elapsedRealtime = elapsed::now)
    private val rest = RestTimerDependencies(store, timers, notifications, alarms, alerts)

    private var controller: ServiceController<RestTimerService>? = null

    /** The start id the system would hand out, one per command. */
    private var startId = 1

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        RestTimerEnvironment.override = rest
        // The normal case: the user allows notifications. Denial is covered on its own below.
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        controller?.destroy()
        RestTimerEnvironment.override = null
    }

    @Test
    fun `starting a rest shows the countdown and arms the backstop`() {
        val endsAt = clock.millis + 90_000L

        val service = start(RestTimerService.startIntent(context, rest(endsAtMillis = endsAt)))

        assertEquals(rest(endsAtMillis = endsAt), store.current.value)

        val foreground = shadowOf(service).lastForegroundNotification
        assertEquals(RestTimerNotifications.NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
        assertEquals(RestTimerNotifications.CHANNEL_RUNNING, foreground.channelId)
        assertEquals(endsAt, foreground.`when`)
        assertTrue(foreground.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(foreground.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(foreground.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))

        // The buttons are the app's own commands, one tap away from the shade.
        assertEquals(
            listOf("+15 s", "-15 s", "Skip"),
            foreground.actions.map { it.title.toString() },
        )
        val addTime = shadowOf(foreground.actions[0].actionIntent).savedIntent
        assertEquals(RestTimerService.ACTION_EXTEND, addTime.action)
        assertEquals(RestTimerUseCases.STEP_SECONDS, addTime.getIntExtra(RestTimerService.EXTRA_DELTA_SECONDS, 0))
        assertEquals(
            RestTimerService.ACTION_SKIP,
            shadowOf(foreground.actions[2].actionIntent).savedIntent.action,
        )

        val backstop = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, backstop.getType())
        assertEquals(elapsed.millis + 90_000L, backstop.triggerAtMs)
    }

    @Test
    fun `plus fifteen moves the deadline, the notification and the backstop`() {
        val endsAt = clock.millis + 30_000L
        val service = start(RestTimerService.startIntent(context, rest(endsAtMillis = endsAt)))

        send(service, RestTimerService.extendIntent(context, RestTimerUseCases.STEP_SECONDS))

        assertEquals(endsAt + 15_000L, store.current.value?.endsAtMillis)
        assertEquals(endsAt + 15_000L, shadowOf(service).lastForegroundNotification.`when`)
        assertEquals(elapsed.millis + 45_000L, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtMs)
    }

    @Test
    fun `a minus fifteen that reaches zero finishes the rest on the spot`() {
        val service = start(
            RestTimerService.startIntent(context, rest(endsAtMillis = clock.millis + 10_000L)),
        )

        send(service, RestTimerService.extendIntent(context, -RestTimerUseCases.STEP_SECONDS))

        assertNull(store.current.value)
        assertEquals(1, alerts.played)
        assertEquals(
            RestTimerNotifications.CHANNEL_FINISHED,
            shadowOf(notificationManager).getNotification(RestTimerNotifications.NOTIFICATION_ID).channelId,
        )
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `skipping the rest leaves nothing behind and nothing to hear`() {
        val service = start(RestTimerService.startIntent(context, rest()))

        send(service, RestTimerService.skipIntent(context))

        assertNull(store.current.value)
        assertEquals(0, alerts.played)
        assertNull(shadowOf(notificationManager).getNotification(RestTimerNotifications.NOTIFICATION_ID))
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `stopping the timer clears it the same way`() {
        val service = start(RestTimerService.startIntent(context, rest()))

        send(service, RestTimerService.stopIntent(context))

        assertNull(store.current.value)
        assertEquals(0, alerts.played)
        assertNull(shadowOf(notificationManager).getNotification(RestTimerNotifications.NOTIFICATION_ID))
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `a rest that ran out while the app was gone comes back as an alert`() {
        store.current.value = rest(endsAtMillis = clock.millis - 1_000L)

        val service = start(intent = null)

        assertNull(store.current.value)
        assertEquals(1, alerts.played)
        assertEquals(
            RestTimerNotifications.CHANNEL_FINISHED,
            shadowOf(notificationManager).getNotification(RestTimerNotifications.NOTIFICATION_ID).channelId,
        )
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `a rest still running comes back from its deadline instead of starting over`() {
        val endsAt = clock.millis + 40_000L
        store.current.value = rest(endsAtMillis = endsAt, totalSeconds = 90)

        val service = start(intent = null)

        // Resumed where it was: the deadline is the same one, not ninety seconds from now.
        assertEquals(endsAt, store.current.value?.endsAtMillis)
        assertEquals(endsAt, shadowOf(service).lastForegroundNotification.`when`)
        assertEquals(0, alerts.played)

        // The forty seconds pass for both clocks: the looper's, which fires the task, and the
        // countdown's, which decides the rest is over.
        clock.advanceSeconds(40)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(40))

        assertNull(store.current.value)
        assertEquals(1, alerts.played)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `with notifications denied the rest still runs and its deadline still moves`() {
        shadowOf(notificationManager).setNotificationsEnabled(false)
        val endsAt = clock.millis + 30_000L
        val service = start(RestTimerService.startIntent(context, rest(endsAtMillis = endsAt)))

        send(service, RestTimerService.extendIntent(context, RestTimerUseCases.STEP_SECONDS))

        assertEquals(endsAt + 15_000L, store.current.value?.endsAtMillis)
        assertNotNull(shadowOf(service).lastForegroundNotification)
    }

    @Test
    fun `the service is sticky, so a rest survives a process death`() {
        val service = start(RestTimerService.startIntent(context, rest()))

        assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, startId++))
    }

    @Test
    fun `a command with no rest running leaves no service behind`() {
        val service = start(RestTimerService.extendIntent(context, RestTimerUseCases.STEP_SECONDS))

        assertEquals(0, alerts.played)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    /**
     * Creates the service and delivers [intent] as its start command — `null` is the restart the
     * system performs after the process was killed.
     */
    private fun start(intent: Intent?): RestTimerService {
        val created = Robolectric.buildService(RestTimerService::class.java).create()
        controller = created
        val service = created.get()
        service.onStartCommand(intent, 0, startId++)
        return service
    }

    /** Delivers another command to the running service, the way an intent would. */
    private fun send(service: RestTimerService, intent: Intent) {
        service.onStartCommand(intent, 0, startId++)
    }

    private fun rest(
        endsAtMillis: Long = clock.millis + 60_000L,
        totalSeconds: Int = 90,
    ): RestTimerState = RestTimerState(
        endsAtMillis = endsAtMillis,
        totalSeconds = totalSeconds,
        exerciseLogId = 7,
        exerciseName = "Bench Press",
    )
}
