package com.ggym.app.service

/**
 * Set on the intent a rest-timer notification opens the app with: "show the workout that is
 * running". The activity consumes it (removes it) on the first read, so a configuration change
 * does not replay the navigation.
 */
const val EXTRA_OPEN_WORKOUT = "com.ggym.app.extra.OPEN_WORKOUT"
