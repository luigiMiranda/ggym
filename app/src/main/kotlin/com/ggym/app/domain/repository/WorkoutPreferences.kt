package com.ggym.app.domain.repository

import com.ggym.app.domain.workout.AlertSound
import com.ggym.app.domain.workout.RingPolicy
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

    /**
     * Which sound the rest-over alert makes. Absent means [AlertSound.SystemDefault].
     *
     * A preference rather than a constant because the sound is played by the app, not by the
     * notification channel, so the system's own sound setting for the channel cannot reach it.
     */
    val alertSound: Flow<AlertSound>

    suspend fun setAlertSound(sound: AlertSound)

    /**
     * Whether the alert rings until stopped or stops by itself, and for how long.
     *
     * Absent means [RingPolicy.Default] — automatic, [RingPolicy.DEFAULT_SECONDS] seconds.
     */
    val ringPolicy: Flow<RingPolicy>

    suspend fun setRingPolicy(policy: RingPolicy)
}
