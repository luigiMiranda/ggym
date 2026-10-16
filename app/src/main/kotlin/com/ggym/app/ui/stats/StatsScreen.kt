package com.ggym.app.ui.stats

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ggym.app.R
import com.ggym.app.ui.common.EmptyState

/** Stats and body weight tab. Charts and the body-weight log are still to come. */
@Composable
fun StatsScreen() {
    EmptyState(
        icon = Icons.Outlined.Insights,
        title = stringResource(R.string.stats_empty_title),
        message = stringResource(R.string.stats_empty_message),
    )
}
