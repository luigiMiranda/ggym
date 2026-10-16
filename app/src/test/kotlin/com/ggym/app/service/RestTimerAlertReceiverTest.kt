package com.ggym.app.service

import android.Manifest
import android.app.AlarmManager
import android.app.Application
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
 * The exact alarm's backstop: the path that ends a rest when the process was suspended, asleep or
 * gone.
 *
 * The clock is the test's, so "the deadline has passed" is a fact the test decides rather than a
 * race it hopes to win.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class RestTimerAlertReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

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
    private val rest = RestTimerDependencies(store, timers, notifications, alarms, alerts)

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

    @Test
    fun `the backstop ends a rest whose deadline has passed`() {
        store.current.value = rest(endsAtMillis = clock.millis - 1_000L)

        deliver()

        assertNull(store.current.value)
        assertEquals(1, alerts.played)
        assertEquals(
            RestTimerNotifications.CHANNEL_FINISHED,
            shadowOf(notificationManager).getNotification(RestTimerNotifications.NOTIFICATION_ID).channelId,
        )
        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `an alarm that fires early keeps the rest and re-arms the backstop`() {
        val endsAt = clock.millis + 20_000L
        store.current.value = rest(endsAtMillis = endsAt)

        deliver()

        assertEquals(endsAt, store.current.value?.endsAtMillis)
        assertEquals(0, alerts.played)
        assertNull(shadowOf(notificationManager).getNotification(RestTimerNotifications.NOTIFICATION_ID))
        assertEquals(elapsed.millis + 20_000L, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtMs)
    }

    @Test
    fun `an alarm with no rest running does nothing`() {
        deliver()

        assertEquals(0, alerts.played)
        assertNull(shadowOf(notificationManager).getNotification(RestTimerNotifications.NOTIFICATION_ID))
    }

    @Test
    fun `a broadcast that is not the timer's is ignored`() {
        store.current.value = rest(endsAtMillis = clock.millis - 1_000L)

        deliver(action = "com.ggym.app.action.OTHER")

        assertEquals(rest(endsAtMillis = clock.millis - 1_000L), store.current.value)
        assertEquals(0, alerts.played)
    }

    @Test
    fun `the armed alarm delivers exactly the backstop it promised`() {
        val running = rest(endsAtMillis = clock.millis - 1_000L)
        store.current.value = running
        alarms.arm(running.endsAtMillis)

        shadowOf(alarmManager).fireAlarm(shadowOf(alarmManager).peekNextScheduledAlarm()!!)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(store.current.value)
        assertEquals(1, alerts.played)
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
}
