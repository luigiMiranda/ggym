package com.ggym.app.ui.calendar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ggym.app.R
import com.ggym.app.ui.common.EmptyState

/** Calendar tab. Workout history and day inspection are still to come. */
@Composable
fun CalendarScreen() {
    EmptyState(
        icon = Icons.Outlined.CalendarMonth,
        title = stringResource(R.string.calendar_empty_title),
        message = stringResource(R.string.calendar_empty_message),
    )
}
