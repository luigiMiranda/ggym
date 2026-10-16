package com.ggym.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The four top-level destinations. Kept as a plain enum with string routes: four static,
 * non-argumented destinations don't need a serializable route graph.
 */
enum class Destination(
    val route: String,
    /** Label resource id — localized in M6. */
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Workouts(
        route = "workouts",
        labelRes = com.ggym.app.R.string.nav_workouts,
        selectedIcon = Icons.Filled.FitnessCenter,
        unselectedIcon = Icons.Outlined.FitnessCenter,
    ),
    Calendar(
        route = "calendar",
        labelRes = com.ggym.app.R.string.nav_calendar,
        selectedIcon = Icons.Filled.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
    ),
    Stats(
        route = "stats",
        labelRes = com.ggym.app.R.string.nav_stats,
        selectedIcon = Icons.Filled.Insights,
        unselectedIcon = Icons.Outlined.Insights,
    ),
    Settings(
        route = "settings",
        labelRes = com.ggym.app.R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
    ;

    companion object {
        val start = Workouts

        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}
