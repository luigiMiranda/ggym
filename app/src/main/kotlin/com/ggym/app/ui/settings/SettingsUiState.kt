package com.ggym.app.ui.settings

import com.ggym.app.data.prefs.AppLanguage
import com.ggym.app.data.prefs.ThemeSettings
import com.ggym.app.domain.workout.AlertSound
import com.ggym.app.domain.workout.RingPolicy

/**
 * The Settings tab, as the screen shows it: the appearance, the rest timer's master switch, and the
 * alert's own two settings.
 *
 * Notification access is deliberately not in here. It is a fact about the system, read by the
 * screen itself on every resume, so a ViewModel never needs an Android dependency to know it.
 */
data class SettingsUiState(
    val theme: ThemeSettings = ThemeSettings(),
    /** Which language the app is shown in; [AppLanguage.System] follows the device. */
    val language: AppLanguage = AppLanguage.System,
    /** On until the user turns it off, matching the preference's own default. */
    val restTimerEnabled: Boolean = true,
    /** What the rest-over alert sounds like. Mirrors the preference's own default. */
    val alertSound: AlertSound = AlertSound.SystemDefault,
    /** Whether the alert rings until stopped or stops by itself. */
    val ringPolicy: RingPolicy = RingPolicy.Default,
)
