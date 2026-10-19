package com.ggym.app.service

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.MainActivity
import com.ggym.app.domain.repository.RestTimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The countdown's notification opens the workout, not the app's start screen: the tap target is
 * the one thing the notification promises the user after a process death.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37])
class RestTimerNotificationsTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = RestTimerNotifications(context)

    private val running = RestTimerState(
        endsAtMillis = 1_750_000_090_000,
        totalSeconds = 90,
        exerciseLogId = 3,
        exerciseName = "Bench Press",
    )

    @Test
    fun `tapping the countdown opens the app asking for the running workout`() {
        val tap = shadowOf(notifications.running(running).contentIntent).savedIntent

        assertEquals(MainActivity::class.java.name, tap.component?.className)
        assertTrue(tap.getBooleanExtra(EXTRA_OPEN_WORKOUT, false))
    }

    @Test
    fun `the countdown's own buttons do not ask for a workout to open`() {
        // The buttons are commands to the service. Only the body of the notification is a way in.
        val addTime = shadowOf(notifications.running(running).actions[0].actionIntent).savedIntent

        assertTrue(!addTime.hasExtra(EXTRA_OPEN_WORKOUT))
    }
}
