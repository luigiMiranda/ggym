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
    /** Label resource id, so the tab labels can be localized. */
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

        /**
         * A route's base: everything before its first `?`.
         *
         * A destination declared with an optional argument — `stats?exerciseId={exerciseId}` —
         * reports *that pattern* as its `NavDestination.route` even when it is opened as plain
         * `stats`, so comparing full strings would stop recognising the Stats tab and the bottom
         * bar would light nothing. Matching on the base is what makes an argumented tab still a tab.
         */
        fun baseRoute(route: String?): String? = route?.substringBefore('?')

        /** The tab a plain tab route selects, or `null` for a route that is not a tab. */
        fun fromRoute(route: String?): Destination? {
            val base = baseRoute(route) ?: return null
            return entries.firstOrNull { it.route == base }
        }

        /**
         * The tab a route lives *under*, so a drill-down keeps its tab lit and the bar never leaves
         * the screen.
         *
         * The routine tree, the exercise picker, a running workout and the exercise library are all
         * reached from the Workouts tab and their exit returns there; a past workout is opened from
         * the calendar. Anything unrecognised selects nothing rather than guessing.
         */
        fun owningTab(route: String?): Destination? = when (baseRoute(route)) {
            Workouts.route -> Workouts
            Calendar.route -> Calendar
            Stats.route -> Stats
            Settings.route -> Settings
            Routes.ROUTINE, Routes.DAY, Routes.EXERCISE_PICKER, Routes.WORKOUT -> Workouts
            Routes.HISTORY -> Calendar
            else -> null
        }
    }
}
