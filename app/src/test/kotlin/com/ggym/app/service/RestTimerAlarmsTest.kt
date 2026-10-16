package com.ggym.app.service

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.testing.TestClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The backstop's alarm itself: a wake-up alarm for the deadline, in the one time base the platform
 * lets an alarm use.
 *
 * The two clocks are deliberately different values — a wall-clock deadline and an elapsed-realtime
 * base — because the conversion between them is the only interesting thing this class does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class RestTimerAlarmsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    private val clock = TestClock(millis = 1_750_000_000_000L)
    private val elapsed = TestClock(millis = 42_000L)
    private val alarms = RestTimerAlarms(context, now = clock::now, elapsedRealtime = elapsed::now)

    @After
    fun tearDown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `the backstop is a wake-up alarm on the deadline, in the alarm clock's own base`() {
        val endsAt = clock.millis + 45_000L

        alarms.arm(endsAt)

        val scheduled = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, scheduled.getType())
        assertTrue(scheduled.isAllowWhileIdle)
        // 45 s from the elapsed base, not the wall-clock deadline passed through unchanged.
        assertEquals(elapsed.millis + 45_000L, scheduled.triggerAtMs)
        // That this is the receiver's own alarm is proven end to end by
        // RestTimerAlertReceiverTest, which fires the scheduled alarm and watches it arrive.
    }

    @Test
    fun `a deadline that is already in the past is armed for now`() {
        alarms.arm(clock.millis - 5_000L)

        assertEquals(elapsed.millis, shadowOf(alarmManager).peekNextScheduledAlarm()!!.triggerAtMs)
    }

    @Test
    fun `cancelling the backstop leaves no alarm behind`() {
        alarms.arm(clock.millis + 45_000L)

        alarms.cancel()

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `a device that refuses exact alarms still gets a backstop`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        alarms.arm(clock.millis + 45_000L)

        val scheduled = shadowOf(alarmManager).peekNextScheduledAlarm()!!
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, scheduled.getType())
        assertEquals(elapsed.millis + 45_000L, scheduled.triggerAtMs)
    }
}
