package com.ggym.app.ui.settings

import com.ggym.app.data.prefs.AppLanguage
import com.ggym.app.data.prefs.ThemeSettings
import com.ggym.app.domain.repository.BackupFailure
import com.ggym.app.domain.repository.BackupSummary
import com.ggym.app.domain.workout.AlertSound
import com.ggym.app.domain.workout.RingPolicy

/**
 * The Settings tab, as the screen shows it: the appearance, the rest timer's master switch, the
 * alert's own two settings, and the state of a backup job.
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
    /**
     * True while a backup file is being written or read. The screen disables both buttons on it:
     * the confirm dialog has already been answered by the time a restore starts, and a second tap
     * would start a second restore over the first.
     */
    val backupBusy: Boolean = false,
    /**
     * The document the user picked but has not yet agreed to restore from, as its URI string, or
     * `null` when no replacement is waiting on an answer.
     *
     * Picking a file and replacing the data are two separate decisions, and this is the gap between
     * them. It is a `String` rather than a `Uri` so this file stays free of Android types, exactly
     * like the rest of the state.
     */
    val pendingImport: String? = null,
    /**
     * The result of the last backup job, waiting to be read and dismissed — or `null` when there is
     * nothing to report.
     *
     * It lives in the state rather than in a one-shot event on purpose: a restore replaces
     * everything the user has, and the sentence saying so must survive a rotation. A `SharedFlow`
     * is for something the user can miss; this is not.
     */
    val backupOutcome: BackupOutcome? = null,
)

/**
 * What became of the last thing the user asked the Data section to do.
 *
 * The cases carry no message text. The screen localizes each one, so the app's two languages stay
 * in `strings.xml` instead of in this file, and a failure keeps only the fact it needs to
 * distinguish itself: a version mismatch shows the two numbers, everything else is a sentence.
 */
sealed interface BackupOutcome {

    /** The file was written to wherever the user chose. */
    data object Exported : BackupOutcome

    /** The document could not be written; the device's data is untouched. */
    data object ExportFailed : BackupOutcome

    /** The restore happened; [summary] says how much came back. */
    data class Imported(val summary: BackupSummary) : BackupOutcome

    /** The restore did not happen; the device's data is untouched. */
    data class ImportFailed(val failure: BackupFailure) : BackupOutcome
}
