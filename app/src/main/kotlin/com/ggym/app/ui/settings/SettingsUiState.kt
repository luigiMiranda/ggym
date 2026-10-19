package com.ggym.app.ui.settings

import com.ggym.app.data.prefs.ThemeSettings

/**
 * The Settings tab, as the screen shows it: the appearance and the rest timer's master switch.
 *
 * Notification access is deliberately not in here. It is a fact about the system, read by the
 * screen itself on every resume, so a ViewModel never needs an Android dependency to know it.
 */
data class SettingsUiState(
    val theme: ThemeSettings = ThemeSettings(),
    /** On until the user turns it off, matching the preference's own default. */
    val restTimerEnabled: Boolean = true,
)
