package com.ggym.app.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ggym.app.R
import com.ggym.app.data.prefs.ThemeMode
import com.ggym.app.ui.common.GgymCardShape
import com.ggym.app.ui.common.GgymPill
import com.ggym.app.ui.common.GgymScreenHeader
import com.ggym.app.ui.common.GgymSectionLabel
import com.ggym.app.ui.common.PillTone
import com.ggym.app.ui.common.appNotificationSettingsIntent
import com.ggym.app.ui.common.rememberNotificationsAllowed

/**
 * Settings tab: appearance and the workout's rest timer, laid out as the preview does it — a
 * section label, a card, a row, repeat. The backup section joins in M6.
 *
 * The notification row only appears when GGym cannot post notifications: then the countdown still
 * runs in the app, and the row says what is missing and sends the user to the system page to fix it.
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

            GgymSectionLabel(
                text = stringResource(R.string.settings_section_workout),
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

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The rest timer's switch, named for the Compose tests that drive it. */
internal const val REST_TIMER_SWITCH_TAG = "rest_timer_switch"

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
