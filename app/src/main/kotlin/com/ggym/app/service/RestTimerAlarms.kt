package com.ggym.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * The countdown's backstop: an exact alarm that fires at the deadline even if the process was
 * suspended, is dozing, or is gone.
 *
 * The foreground service keeps its own timer, and that is the path that normally ends a rest — but
 * a delayed callback in a throttled process is not a promise, while a wake-up alarm is. So every
 * time the deadline moves the alarm is re-armed, and what it triggers is
 * [RestTimerAlertReceiver]: a broadcast the system always delivers, with no foreground service left
 * to start.
 *
 * The deadline is stored as wall-clock millis (it has to outlive a reboot); an alarm needs the
 * elapsed-realtime base, which is why this converts at the moment it arms and buys nothing it does
 * not need: an already-past deadline is armed for *now* rather than in the past.
 */
class RestTimerAlarms(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Arms — or re-arms — the backstop for a rest that ends at [endsAtMillis]. */
    fun arm(endsAtMillis: Long) {
        val triggerAt = elapsedRealtime() + (endsAtMillis - now()).coerceAtLeast(0L)
        val alarm = alarmIntent()

        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarm)
        } else {
            // An ordinary alarm can be delayed by doze, which is exactly what the backstop exists
            // for; it is still better than no backstop at all, and the in-process timer above it is
            // unaffected.
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarm)
        }
    }

    /** Cancels the backstop: the rest is over, or ended by hand, before its alarm was due. */
    fun cancel() {
        alarmManager.cancel(alarmIntent())
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RestTimerAlertReceiver::class.java)
            .setAction(RestTimerAlertReceiver.ACTION_REST_FINISHED),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_CODE = 0
    }
}
