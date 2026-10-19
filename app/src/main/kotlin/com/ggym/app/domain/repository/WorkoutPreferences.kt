package com.ggym.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * How the user wants a workout to behave, as opposed to how the app looks
 * (`data/prefs/ThemePreferences`).
 */
interface WorkoutPreferences {

    /**
     * The rest timer's master switch, on until the user turns it off.
     *
     * Off means a completed set is logged and nothing else happens: no countdown, no notification,
     * no alert. The rule itself — including that switching it off ends a rest that is already
     * running — lives in `domain/workout/RestTimerUseCases`, so no screen has to remember it.
     */
    val restTimerEnabled: Flow<Boolean>

    suspend fun setRestTimerEnabled(enabled: Boolean)

    /**
     * Whether the app has already asked for notification permission. The question is asked once,
     * when a workout is first opened, and never again: a second prompt after a denial is noise, and
     * the Settings screen is where a user who changed their mind goes.
     */
    val notificationPromptShown: Flow<Boolean>

    suspend fun setNotificationPromptShown(shown: Boolean)
}
