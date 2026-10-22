package com.ggym.app.ui.settings

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggym.app.data.prefs.AppLanguage
import com.ggym.app.data.prefs.LanguagePreferences
import com.ggym.app.data.prefs.ThemeMode
import com.ggym.app.data.prefs.ThemePreferences
import com.ggym.app.domain.repository.BackupExportResult
import com.ggym.app.domain.repository.BackupImportResult
import com.ggym.app.domain.repository.BackupRepository
import com.ggym.app.domain.repository.WorkoutPreferences
import com.ggym.app.domain.workout.AlertSound
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.domain.workout.RingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Settings tab's state holder: the theme, the rest timer's master switch, the alert's sound and
 * ring policy, and the Data section's two operations.
 *
 * The switch is written through [RestTimerUseCases.setEnabled], not straight into the preference:
 * turning it off also ends a rest that is running, and that rule is the use case's, so this screen
 * cannot forget it. The alert's two settings carry no such rule, so they go to
 * [WorkoutPreferences] the way the theme's do.
 *
 * A restore is the one action here that can change data this screen does not own. It is handled by
 * [BackupRepository], which reports what happened instead of throwing; this class turns that report
 * into [BackupOutcome] and, when a restore succeeded, stops the rest timer — the draft a countdown
 * belonged to may be exactly what the file replaced.
 */
class SettingsViewModel(
    private val themePreferences: ThemePreferences,
    private val languagePreferences: LanguagePreferences,
    private val restTimer: RestTimerUseCases,
    private val workoutPreferences: WorkoutPreferences,
    private val backups: BackupRepository,
) : ViewModel() {

    /**
     * A backup job in flight, and whatever it last reported.
     *
     * Kept apart from the preferences because it is not one: nothing here is stored, and the
     * preferences flow is a pure combination of five independent stores that would otherwise need a
     * sixth input and a sixth type parameter.
     */
    private val backupJob = MutableStateFlow(BackupJob())

    private val preferences: Flow<SettingsUiState> = combine(
        themePreferences.settings,
        languagePreferences.language,
        restTimer.isEnabled,
        workoutPreferences.alertSound,
        workoutPreferences.ringPolicy,
    ) { theme, language, restTimerEnabled, alertSound, ringPolicy ->
        SettingsUiState(
            theme = theme,
            language = language,
            restTimerEnabled = restTimerEnabled,
            alertSound = alertSound,
            ringPolicy = ringPolicy,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(preferences, backupJob) { state, job ->
        state.copy(
            backupBusy = job.busy,
            pendingImport = job.pendingImport,
            backupOutcome = job.outcome,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreferences.setMode(mode) }
    }

    fun setUseWallpaperColors(enabled: Boolean) {
        viewModelScope.launch { themePreferences.setUseWallpaperColors(enabled) }
    }

    /**
     * Writes the language choice. On Android 13+ the platform applies it and recreates the activity
     * itself; below that the activity notices the change and recreates, so the whole UI — and the
     * view models' default `Locale` — is rebuilt in the new language.
     */
    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { languagePreferences.setLanguage(language) }
    }

    fun setRestTimerEnabled(enabled: Boolean) {
        viewModelScope.launch { restTimer.setEnabled(enabled) }
    }

    fun setAlertSound(sound: AlertSound) {
        viewModelScope.launch { workoutPreferences.setAlertSound(sound) }
    }

    fun setRingPolicy(policy: RingPolicy) {
        viewModelScope.launch { workoutPreferences.setRingPolicy(policy) }
    }

    /**
     * One tap of the seconds stepper, in steps of ±1.
     *
     * The arithmetic and the bounds are [RingPolicy.step]'s, so the screen never holds a limit and
     * cannot store a value the alert could not use.
     */
    fun stepRingSeconds(steps: Int) {
        val seconds = (uiState.value.ringPolicy as? RingPolicy.Automatic)?.seconds
            ?: RingPolicy.DEFAULT_SECONDS
        setRingPolicy(RingPolicy.step(seconds, steps))
    }

    /**
     * Remembers the file the user picked and waits for an answer.
     *
     * Nothing is read from it yet: a restore replaces everything, and the picker's result is the
     * *offer* of a file, not consent to use it.
     */
    fun requestImport(document: Uri) {
        backupJob.update { it.copy(pendingImport = document.toString()) }
    }

    /** The user declined the replacement. Nothing was read and nothing changes. */
    fun cancelImport() {
        backupJob.update { it.copy(pendingImport = null) }
    }

    /** The user agreed: the picked file is now read and restored. */
    fun confirmImport() {
        val document = backupJob.value.pendingImport ?: return
        importFrom(document.toUri())
    }

    /**
     * Writes the whole database to the document the user picked in the system's own picker.
     *
     * Unlike a restore this needs no second question — the picker's answer is the destination, and
     * the worst a wrong one can do is overwrite a file the user chose to overwrite. The guard is
     * not only belt-and-braces either: the screen disables both buttons while a job runs, but a
     * launcher result can arrive after a rotation, and starting a second write over the first is
     * exactly the kind of thing a disabled button cannot promise.
     */
    fun exportTo(document: Uri) {
        if (uiState.value.backupBusy) return
        viewModelScope.launch {
            backupJob.value = BackupJob(busy = true)
            val outcome = when (backups.exportTo(document)) {
                BackupExportResult.Written -> BackupOutcome.Exported
                BackupExportResult.Failed -> BackupOutcome.ExportFailed
            }
            backupJob.value = BackupJob(outcome = outcome)
        }
    }

    /**
     * Replaces everything with the document the user picked.
     *
     * The replacement is the repository's decision to make and the user's to confirm; by the time
     * this runs the confirmation has already happened. A successful restore also ends a running
     * rest: the workout a countdown belonged to may be precisely what the file replaced, and a
     * countdown outliving its workout would point at nothing.
     */
    private fun importFrom(document: Uri) {
        if (uiState.value.backupBusy) return
        viewModelScope.launch {
            backupJob.value = BackupJob(busy = true)
            val outcome = when (val result = backups.importFrom(document)) {
                is BackupImportResult.Restored -> {
                    restTimer.stop()
                    BackupOutcome.Imported(result.summary)
                }

                is BackupImportResult.Failed -> BackupOutcome.ImportFailed(result.failure)
            }
            backupJob.value = BackupJob(outcome = outcome)
        }
    }

    /** Closes the outcome dialog. The result has been read by then, so nothing else changes. */
    fun dismissBackupOutcome() {
        backupJob.update { it.copy(outcome = null) }
    }

    private data class BackupJob(
        val busy: Boolean = false,
        val pendingImport: String? = null,
        val outcome: BackupOutcome? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
