package com.ggym.app.service

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The exact alarm's backstop: the path that wakes a rest whose process was suspended, asleep or gone.
 *
 * Since M6 the receiver's job is **to hand the rest to the service**, not to end it, because a
 * ringing alert has to be owned by something that outlives a broadcast — so the interesting
 * assertions here are "the service was asked" and "the receiver did not do the work itself". Ending
 * the rest is the service's, and is covered by `RestTimerServiceTest`.
 *
 * The clock is the test's, so "the deadline has passed" is a fact the test decides rather than a race
 * it hopes to win.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class RestTimerAlertReceiverTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val context: Context = application

    private val store = FakeRestTimerStore()
    private val clock = TestClock(millis = 1_750_000_000_000L)
    private val elapsed = TestClock(millis = 42_000L)
    private val timers = RestTimerUseCases(
        store = store,
        commands = FakeRestTimerCommands(),
        preferences = FakeWorkoutPreferences(),
        now = clock::now,
    )
    private val alerts = FakeRestAlertPlayer()
    private val notifications = RestTimerNotifications(context)
    private val alarms = RestTimerAlarms(context, now = clock::now, elapsedRealtime = elapsed::now)
    private val preferences = FakeWorkoutPreferences()
    private val rest = RestTimerDependencies(store, timers, notifications, alarms, alerts, preferences)

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
        RestTimerEnvironment.override = null
    }

    /** The receiver's whole job: wake the service, and leave the ending to it. */
    @Test
    fun `the backstop wakes the service and does not end the rest itself`() {
        val running = rest(endsAtMillis = clock.millis - 1_000L)
        store.current.value = running

        deliver()

        assertEquals(RestTimerService::class.java.name, shadowOf(application).nextStartedService?.component?.className)
        assertEquals("the service owns the ring", running, store.current.value)
        assertEquals(0, alerts.started.size)
        assertNull(shadowOf(notificationManager).getNotification(RestTimerNotifications.NOTIFICATION_ID))
    }

    /**
     * The course is the receiver's, not the service's: waking it hands over a stored deadline that
     * the service then judges. An alarm that arrives early is re-armed there, which is why the
     * receiver does not look at the clock at all.
     */
    @Test
    fun `the receiver does not judge the deadline, it only passes it on`() {
        store.current.value = rest(endsAtMillis = clock.millis + 20_000L)

        deliver()

        assertEquals(RestTimerService::class.java.name, shadowOf(application).nextStartedService?.component?.className)
        assertEquals(clock.millis + 20_000L, store.current.value?.endsAtMillis)
    }

    @Test
    fun `a broadcast that is not the timer's is ignored`() {
        val running = rest(endsAtMillis = clock.millis - 1_000L)
        store.current.value = running

        deliver(action = "com.ggym.app.action.OTHER")

        assertEquals(running, store.current.value)
        assertNull(shadowOf(application).nextStartedService)
    }

    /**
     * The armed alarm delivers exactly the backstop it promised: the broadcast that ends a rest the
     * service did not live to see.
     */
    @Test
    fun `the armed alarm wakes the service through the manifest receiver`() {
        val running = rest(endsAtMillis = clock.millis - 1_000L)
        store.current.value = running
        alarms.arm(running.endsAtMillis)

        shadowOf(alarmManager).fireAlarm(shadowOf(alarmManager).peekNextScheduledAlarm()!!)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(RestTimerService::class.java.name, shadowOf(application).nextStartedService?.component?.className)
    }

    /**
     * The fallback, for the case where the platform refuses the service start: the rest is still
     * ended — the deadline must not come back to life — and the alert is posted.
     *
     * The **sound is deliberately not started**, and that is a change from M3, which played a
     * one-shot beep here. A ringing alert loops, and a loop started by a process that is about to
     * die has no owner to stop it: silence with a notification is better than an alarm that may never
     * go quiet.
     */
    @Test
    fun `a refused service start still ends the rest, without ringing`() {
        val running = rest(endsAtMillis = clock.millis - 1_000L)
        store.current.value = running

        RestTimerAlertReceiver().onReceive(
            RefusingContext(context),
            Intent(context, RestTimerAlertReceiver::class.java)
                .setAction(RestTimerAlertReceiver.ACTION_REST_FINISHED),
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(store.current.value)
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        assertEquals(0, alerts.started.size)
        assertEquals(
            RestTimerNotifications.CHANNEL_FINISHED,
            shadowOf(notificationManager).getNotification(RestTimerNotifications.NOTIFICATION_ID).channelId,
        )
    }

    /** The same fallback, for an alarm that came early: the rest is kept and the backstop re-armed. */
    @Test
    fun `a refused service start re-arms an alarm that fired early`() {
        val endsAt = clock.millis + 20_000L
        store.current.value = rest(endsAtMillis = endsAt)

        RestTimerAlertReceiver().onReceive(
            RefusingContext(context),
            Intent(context, RestTimerAlertReceiver::class.java)
                .setAction(RestTimerAlertReceiver.ACTION_REST_FINISHED),
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(endsAt, store.current.value?.endsAtMillis)
        assertEquals(elapsed.millis + 20_000L, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtMs)
        assertNull(shadowOf(notificationManager).getNotification(RestTimerNotifications.NOTIFICATION_ID))
    }

    /** Delivers the backstop the way the system does: a broadcast to the manifest receiver. */
    private fun deliver(action: String = RestTimerAlertReceiver.ACTION_REST_FINISHED) {
        context.sendBroadcast(
            Intent(context, RestTimerAlertReceiver::class.java).setAction(action),
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun rest(endsAtMillis: Long): RestTimerState = RestTimerState(
        endsAtMillis = endsAtMillis,
        totalSeconds = 90,
        exerciseLogId = 7,
        exerciseName = "Bench Press",
    )

    /**
     * A context that refuses to start a foreground service, which is what the platform does to an
     * app in the background that has no exemption.
     */
    private class RefusingContext(base: Context) : ContextWrapper(base) {

        override fun startForegroundService(service: Intent): ComponentName? =
            throw ForegroundServiceStartNotAllowedException("not allowed from the background")
    }
}
