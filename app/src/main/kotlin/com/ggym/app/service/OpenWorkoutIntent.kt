package com.ggym.app.service

/**
 * Set on the intent a rest-timer notification opens the app with: "show the workout that is
 * running". The activity consumes it (removes it) on the first read, so a configuration change
 * does not replay the navigation.
 */
const val EXTRA_OPEN_WORKOUT = "com.ggym.app.extra.OPEN_WORKOUT"

/**
 * Set alongside [EXTRA_OPEN_WORKOUT] on the intent the **finished** alert opens the app with:
 * "stop ringing".
 *
 * By then the rest is over and the countdown is gone, so the tap is the notification's Stop action
 * with the app opened on top of it. It travels as an extra on the activity intent because a single
 * `PendingIntent` cannot both open an activity and command a service, and a phone that is ringing on
 * the desk should go quiet the moment its owner picks it up.
 */
const val EXTRA_SILENCE_ALERT = "com.ggym.app.extra.SILENCE_ALERT"
