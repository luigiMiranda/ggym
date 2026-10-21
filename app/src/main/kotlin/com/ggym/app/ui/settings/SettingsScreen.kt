package com.ggym.app.ui.settings

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.data.prefs.AppLanguage
import com.ggym.app.data.prefs.ThemeMode
import com.ggym.app.domain.workout.AlertSound
import com.ggym.app.domain.workout.RingPolicy
import com.ggym.app.ui.common.GgymCardShape
import com.ggym.app.ui.common.GgymPill
import com.ggym.app.ui.common.GgymScreenHeader
import com.ggym.app.ui.common.GgymSectionLabel
import com.ggym.app.ui.common.GgymStepper
import com.ggym.app.ui.common.PillTone
import com.ggym.app.ui.common.appNotificationSettingsIntent
import com.ggym.app.ui.common.rememberNotificationsAllowed

/**
 * Settings tab, grouped the way a phone's own settings are: a section label, the cards that
 * belong to it, repeat. Three sections, one scrolling page — no second screen.
 *
 * - **Appearance** — theme and wallpaper colours.
 * - **Rest timer & notifications** — the master switch, the alert's sound and ring policy, and the
 *   system-notification warning when GGym cannot post notifications.
 * - **Language** — the app's own language, kept apart from the visual settings.
 */
@Composable
fun SettingsScreen(
    viewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.theme
    val notificationsAllowed by rememberNotificationsAllowed()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        GgymScreenHeader(title = stringResource(R.string.settings_title))

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GgymSectionLabel(stringResource(R.string.settings_section_appearance))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = GgymCardShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.settings_theme_label),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        GgymPill(text = settings.mode.label(), tone = PillTone.Lime)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = settings.mode == mode
                            GgymPill(
                                text = mode.label(),
                                tone = if (selected) PillTone.Lime else PillTone.Ghost,
                                onClick = { viewModel.setThemeMode(mode) },
                            )
                        }
                    }
                }
            }

            // Dynamic colour needs an API 31+ device; hidden rather than shown-as-dead elsewhere.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsRow(
                    title = stringResource(R.string.settings_wallpaper_colors_title),
                    supporting = stringResource(R.string.settings_wallpaper_colors_supporting),
                    trailing = {
                        Switch(
                            checked = settings.useWallpaperColors,
                            onCheckedChange = viewModel::setUseWallpaperColors,
                        )
                    },
                )
            }

            GgymSectionLabel(
                text = stringResource(R.string.settings_section_rest_timer),
                modifier = Modifier.padding(top = 8.dp),
            )

            SettingsRow(
                title = stringResource(R.string.settings_rest_timer_title),
                supporting = stringResource(R.string.settings_rest_timer_supporting),
                trailing = {
                    Switch(
                        checked = state.restTimerEnabled,
                        onCheckedChange = viewModel::setRestTimerEnabled,
                        modifier = Modifier.testTag(REST_TIMER_SWITCH_TAG),
                    )
                },
            )

            AlertSoundCard(
                sound = state.alertSound,
                enabled = state.restTimerEnabled,
                onSelect = viewModel::setAlertSound,
            )

            RingingCard(
                policy = state.ringPolicy,
                enabled = state.restTimerEnabled,
                onSelect = viewModel::setRingPolicy,
                onStep = viewModel::stepRingSeconds,
            )

            if (!notificationsAllowed) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = GgymCardShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.settings_notifications_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.settings_notifications_supporting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { context.openNotificationSettings() }) {
                            Text(stringResource(R.string.settings_notifications_open))
                        }
                    }
                }
            }

            GgymSectionLabel(
                text = stringResource(R.string.settings_section_language),
                modifier = Modifier.padding(top = 8.dp),
            )

            LanguageCard(
                language = state.language,
                onSelect = viewModel::setLanguage,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The rest timer's switch, named for the Compose tests that drive it. */
internal const val REST_TIMER_SWITCH_TAG = "rest_timer_switch"

internal const val ALERT_SOUND_SYSTEM_TAG = "alert_sound_system"
internal const val ALERT_SOUND_RINGTONE_TAG = "alert_sound_ringtone"
internal const val ALERT_SOUND_NONE_TAG = "alert_sound_none"
internal const val RINGING_AUTOMATIC_TAG = "ringing_automatic"
internal const val RINGING_MANUAL_TAG = "ringing_manual"
internal const val RINGING_SECONDS_TAG = "ringing_seconds"
internal const val LANGUAGE_SYSTEM_TAG = "language_system"
internal const val LANGUAGE_ENGLISH_TAG = "language_english"
internal const val LANGUAGE_ITALIAN_TAG = "language_italian"

/** How far the two alert cards fade when the rest timer is off and they do nothing. */
private const val INACTIVE_ALPHA = 0.45f

private fun Context.openNotificationSettings() {
    startActivity(appNotificationSettingsIntent())
}

/** One card with a title, an explanation and a control on the right. */
@Composable
private fun SettingsRow(
    title: String,
    supporting: String,
    trailing: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = GgymCardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
    }
}

@Composable
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.System -> R.string.settings_theme_system
        ThemeMode.Dark -> R.string.settings_theme_dark
    },
)

/**
 * The alert's sound: the platform default, a ringtone the user picked, or nothing.
 *
 * A row of pills rather than a chooser, because that is how the theme is chosen three cards up and
 * three options do not need a second screen. The middle pill opens the platform's ringtone picker,
 * which previews a ringtone as it is tapped — so the sound is heard before it is chosen, and no
 * separate "test" button is needed.
 */
@Composable
private fun AlertSoundCard(
    sound: AlertSound,
    enabled: Boolean,
    onSelect: (AlertSound) -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val picked = result.data?.let { data ->
            IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } ?: return@rememberLauncherForActivityResult
        onSelect(AlertSound.Ringtone(picked.toString()))
    }

    AlertCard(
        title = stringResource(R.string.settings_alert_sound_title),
        supporting = alertSoundSupporting(context, sound, enabled),
        enabled = enabled,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GgymPill(
                text = stringResource(R.string.settings_alert_sound_system),
                tone = if (sound == AlertSound.SystemDefault) PillTone.Lime else PillTone.Ghost,
                onClick = if (enabled) { { onSelect(AlertSound.SystemDefault) } } else null,
                modifier = Modifier.testTag(ALERT_SOUND_SYSTEM_TAG),
            )
            GgymPill(
                text = stringResource(R.string.settings_alert_sound_ringtone),
                tone = if (sound is AlertSound.Ringtone) PillTone.Lime else PillTone.Ghost,
                onClick = if (enabled) { { picker.launch(ringtonePicker(context, sound)) } } else null,
                modifier = Modifier.testTag(ALERT_SOUND_RINGTONE_TAG),
            )
            GgymPill(
                text = stringResource(R.string.settings_alert_sound_none),
                tone = if (sound == AlertSound.None) PillTone.Lime else PillTone.Ghost,
                onClick = if (enabled) { { onSelect(AlertSound.None) } } else null,
                modifier = Modifier.testTag(ALERT_SOUND_NONE_TAG),
            )
        }
    }
}

/**
 * How long the alert rings: until stopped, or for a chosen number of seconds.
 *
 * The stepper only exists in automatic mode, and its ends are [RingPolicy]'s, so the buttons simply
 * disappear at 5 s and at 120 s rather than the screen clamping a value it was handed.
 */
@Composable
private fun RingingCard(
    policy: RingPolicy,
    enabled: Boolean,
    onSelect: (RingPolicy) -> Unit,
    onStep: (Int) -> Unit,
) {
    val automatic = policy as? RingPolicy.Automatic

    AlertCard(
        title = stringResource(R.string.settings_ringing_title),
        supporting = when {
            !enabled -> stringResource(R.string.settings_alert_inactive)
            automatic == null -> stringResource(R.string.settings_ringing_manual_supporting)
            else -> stringResource(R.string.settings_ringing_supporting)
        },
        enabled = enabled,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GgymPill(
                text = stringResource(R.string.settings_ringing_automatic),
                tone = if (automatic != null) PillTone.Lime else PillTone.Ghost,
                onClick = if (enabled) {
                    {
                        onSelect(
                            RingPolicy.automatic(automatic?.seconds ?: RingPolicy.DEFAULT_SECONDS),
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.testTag(RINGING_AUTOMATIC_TAG),
            )
            GgymPill(
                text = stringResource(R.string.settings_ringing_manual),
                tone = if (automatic == null) PillTone.Lime else PillTone.Ghost,
                onClick = if (enabled) { { onSelect(RingPolicy.Manual) } } else null,
                modifier = Modifier.testTag(RINGING_MANUAL_TAG),
            )
        }

        if (automatic != null) {
            GgymStepper(
                label = stringResource(R.string.settings_ringing_seconds),
                value = stringResource(R.string.rest_seconds_short, automatic.seconds),
                onDecrease = if (enabled && automatic.seconds > RingPolicy.MIN_SECONDS) {
                    { onStep(-1) }
                } else {
                    null
                },
                onIncrease = if (enabled && automatic.seconds < RingPolicy.MAX_SECONDS) {
                    { onStep(1) }
                } else {
                    null
                },
                decreaseLabel = stringResource(R.string.settings_ringing_less),
                increaseLabel = stringResource(R.string.settings_ringing_more),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RINGING_SECONDS_TAG),
            )
        }
    }
}

/** One alert card: a title, an explanation, and whatever controls the setting has. */
@Composable
private fun AlertCard(
    title: String,
    supporting: String,
    enabled: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = GgymCardShape,
        modifier = Modifier
            .fillMaxWidth()
            // Dimmed rather than hidden: the settings still exist, they just do nothing while the
            // rest timer is off, and the supporting line says so.
            .alpha(if (enabled) 1f else INACTIVE_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/**
 * The sound's own explanation: the shared line, except where the choice has something to say — the
 * name of the ringtone that was picked, or that "None" still vibrates.
 */
@Composable
private fun alertSoundSupporting(context: Context, sound: AlertSound, enabled: Boolean): String = when {
    !enabled -> stringResource(R.string.settings_alert_inactive)
    sound == AlertSound.None -> stringResource(R.string.settings_alert_sound_none_supporting)
    sound is AlertSound.Ringtone -> ringtoneTitle(context, sound.uri)
        ?: stringResource(R.string.settings_alert_sound_supporting)

    else -> stringResource(R.string.settings_alert_sound_supporting)
}

/**
 * The language the app is shown in, in its own section so it is not read as part of the theme.
 *
 * The same three-pill shape as the theme, and for the same reason: three options do not need a
 * second screen. *System* is the first pill because it is the default, and because it is also the
 * way back from a choice the user has stopped wanting.
 */
@Composable
private fun LanguageCard(
    language: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = GgymCardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppLanguage.entries.forEach { candidate ->
                    GgymPill(
                        text = candidate.label(),
                        tone = if (language == candidate) PillTone.Lime else PillTone.Ghost,
                        onClick = { onSelect(candidate) },
                        modifier = Modifier.testTag(candidate.testTag()),
                    )
                }
            }
        }
    }
}

/**
 * A language's own name, in its own language — "English", not "Inglese".
 *
 * That is the convention a language picker follows everywhere, and it is the only wording that
 * helps the person who cannot read the language the app is currently in.
 */
@Composable
private fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.System -> R.string.settings_language_system
        AppLanguage.English -> R.string.settings_language_english
        AppLanguage.Italian -> R.string.settings_language_italian
    },
)

private fun AppLanguage.testTag(): String = when (this) {
    AppLanguage.System -> LANGUAGE_SYSTEM_TAG
    AppLanguage.English -> LANGUAGE_ENGLISH_TAG
    AppLanguage.Italian -> LANGUAGE_ITALIAN_TAG
}

/** The platform picker, pointed at alarm sounds and pre-selecting whatever is chosen today. */
private fun ringtonePicker(context: Context, sound: AlertSound): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.settings_alert_sound_ringtone_pick))
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        // The silent case is our own explicit "None" pill, so the picker does not offer a second one.
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        (sound as? AlertSound.Ringtone)?.let {
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it.uri.toUri())
        }
    }

/** The display name of a picked ringtone, or `null` when it has since been deleted or unmounted. */
private fun ringtoneTitle(context: Context, uri: String): String? = runCatching {
    RingtoneManager.getRingtone(context, uri.toUri())?.getTitle(context)
}.getOrNull()
