package com.ggym.app.ui.workouts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ggym.app.R
import com.ggym.app.ui.common.EmptyState

/** Workouts tab. Routine management is still to come, so the tab shows an honest empty state. */
@Composable
fun WorkoutsScreen() {
    EmptyState(
        icon = Icons.Outlined.FitnessCenter,
        title = stringResource(R.string.workouts_empty_title),
        message = stringResource(R.string.workouts_empty_message),
    )
}
