package com.ggym.app.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Whether the notification permission has been granted. Always true before API 33, where it does not exist. */
fun Context.notificationPermissionGranted(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Whether GGym may show notifications right now: the runtime permission (API 33+) and the app's
 * own switch in the system settings, which the user can flip without any permission changing.
 */
fun Context.notificationsAllowed(): Boolean =
    notificationPermissionGranted() && NotificationManagerCompat.from(this).areNotificationsEnabled()

/** The system page where this app's notifications are switched on or off. */
fun Context.appNotificationSettingsIntent(): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

/**
 * [notificationsAllowed], read again each time the screen resumes: the user may have just come back
 * from the system page where they changed it. A lookup of the system state, not app state, which is
 * why it lives in the screen and not in the ViewModel.
 */
@Composable
fun rememberNotificationsAllowed(): State<Boolean> {
    val context = LocalContext.current
    val allowed = remember { mutableStateOf(context.notificationsAllowed()) }
    LifecycleResumeEffect(Unit) {
        allowed.value = context.notificationsAllowed()
        onPauseOrDispose {}
    }
    return allowed
}
