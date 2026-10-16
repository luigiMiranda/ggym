package com.ggym.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ggym.app.di.AppContainer
import com.ggym.app.ui.calendar.CalendarScreen
import com.ggym.app.ui.settings.SettingsScreen
import com.ggym.app.ui.stats.StatsScreen
import com.ggym.app.ui.workouts.WorkoutsScreen

/**
 * The app shell: one activity, four top-level destinations, expressive bottom navigation.
 *
 * Tab switching uses the standard save/restore pattern so each tab keeps its own scroll
 * position and back stack — that matters once the workout screens grow scrollable lists.
 */
@Composable
fun GgymNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(backStackEntry?.destination?.route) ?: Destination.start

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = destination == current
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(destination) },
                        icon = {
                            Icon(
                                imageVector = if (selected) {
                                    destination.selectedIcon
                                } else {
                                    destination.unselectedIcon
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.start.route,
            modifier = Modifier.padding(innerPadding),
            // Cross-fade between tabs: spatial motion is reserved for within-workout
            // navigation (pager swipes, set morphs) where it carries meaning.
            enterTransition = { fadeIn(tween(durationMillis = 180)) },
            exitTransition = { fadeOut(tween(durationMillis = 120)) },
        ) {
            composable(Destination.Workouts.route) { WorkoutsScreen() }
            composable(Destination.Calendar.route) { CalendarScreen() }
            composable(Destination.Stats.route) { StatsScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(viewModelFactory = container.settingsViewModelFactory)
            }
        }
    }
}

private fun NavHostController.navigateToTab(destination: Destination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
