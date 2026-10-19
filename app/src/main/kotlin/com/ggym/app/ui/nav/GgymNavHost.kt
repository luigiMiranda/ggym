package com.ggym.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import kotlinx.coroutines.flow.first
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ggym.app.di.AppContainer
import com.ggym.app.ui.calendar.CalendarScreen
import com.ggym.app.ui.exercises.ExercisePickerScreen
import com.ggym.app.ui.settings.SettingsScreen
import com.ggym.app.ui.stats.StatsScreen
import com.ggym.app.ui.workout.ActiveWorkoutScreen
import com.ggym.app.ui.workouts.DayDetailScreen
import com.ggym.app.ui.workouts.RoutineDetailScreen
import com.ggym.app.ui.workouts.WorkoutsScreen

/**
 * The app shell: one activity, the four tabs, and the routine tree behind the Workouts tab.
 *
 * The bottom bar belongs to the tabs and disappears in the routine tree — those screens are a
 * drill-down with their own back path, not a fifth tab. Each tab keeps its own back stack and
 * scroll position, so returning to Workouts puts the user where they were.
 */
@Composable
fun GgymNavHost(
    container: AppContainer,
    /**
     * Bumped each time a rest-timer notification asks for the running workout. Zero means nothing
     * has asked, which is also the value on a cold start without one.
     */
    openWorkoutRequests: Int = 0,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val tab = Destination.fromRoute(backStackEntry?.destination?.route)

    // The notification's tap target: the workout that is running, if there still is one. A
    // notification for a workout that has since ended opens the app where it was, not nowhere.
    LaunchedEffect(openWorkoutRequests) {
        if (openWorkoutRequests == 0) return@LaunchedEffect
        val running = container.workoutHistoryRepository.observeDraft().first() ?: return@LaunchedEffect
        navController.navigate(Routes.workout(running.id)) { launchSingleTop = true }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tab != null) {
                GgymNavigationBar(
                    selected = tab,
                    onSelect = { destination -> navController.navigateToTab(destination) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.start.route,
            modifier = Modifier.padding(innerPadding),
            // Cross-fade between destinations: spatial motion is reserved for within-workout
            // navigation (pager swipes, set morphs) where it carries meaning.
            enterTransition = { fadeIn(tween(durationMillis = 180)) },
            exitTransition = { fadeOut(tween(durationMillis = 120)) },
        ) {
            composable(Destination.Workouts.route) {
                WorkoutsScreen(
                    viewModelFactory = container.workoutsViewModelFactory,
                    onOpenRoutine = { routineId -> navController.navigate(Routes.routine(routineId)) },
                    onOpenWorkout = { sessionId -> navController.navigate(Routes.workout(sessionId)) },
                )
            }
            composable(Destination.Calendar.route) { CalendarScreen() }
            composable(Destination.Stats.route) { StatsScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(viewModelFactory = container.settingsViewModelFactory)
            }

            composable(
                route = Routes.ROUTINE,
                arguments = listOf(navArgument(Routes.ROUTINE_ID) { type = NavType.LongType }),
            ) { entry ->
                val routineId = entry.longArgument(Routes.ROUTINE_ID)
                RoutineDetailScreen(
                    viewModelFactory = remember(routineId) { container.routineDetailViewModelFactory(routineId) },
                    onBack = { navController.popBackStack() },
                    onOpenDay = { dayId -> navController.navigate(Routes.day(routineId, dayId)) },
                )
            }

            composable(
                route = Routes.DAY,
                arguments = listOf(
                    navArgument(Routes.ROUTINE_ID) { type = NavType.LongType },
                    navArgument(Routes.DAY_ID) { type = NavType.LongType },
                ),
            ) { entry ->
                val routineId = entry.longArgument(Routes.ROUTINE_ID)
                val dayId = entry.longArgument(Routes.DAY_ID)
                DayDetailScreen(
                    viewModelFactory = remember(routineId, dayId) {
                        container.dayDetailViewModelFactory(routineId, dayId)
                    },
                    slotEditorViewModelFactory = remember(routineId) {
                        { plannedExerciseId: Long ->
                            container.slotEditorViewModelFactory(routineId, plannedExerciseId)
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onAddExercise = { navController.navigate(Routes.exercisePicker(dayId)) },
                    onOpenWorkout = { sessionId -> navController.navigate(Routes.workout(sessionId)) },
                )
            }

            composable(
                route = Routes.EXERCISE_PICKER,
                arguments = listOf(navArgument(Routes.DAY_ID) { type = NavType.LongType }),
            ) { entry ->
                val dayId = entry.longArgument(Routes.DAY_ID)
                ExercisePickerScreen(
                    routineDayId = dayId,
                    viewModelFactory = remember(dayId) { container.exercisePickerViewModelFactory(dayId) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.WORKOUT,
                arguments = listOf(navArgument(Routes.SESSION_ID) { type = NavType.LongType }),
            ) { entry ->
                val sessionId = entry.longArgument(Routes.SESSION_ID)
                ActiveWorkoutScreen(
                    viewModelFactory = remember(sessionId) { container.activeWorkoutViewModelFactory(sessionId) },
                    onWorkoutEnded = navController::returnToWorkouts,
                )
            }
        }
    }
}

/**
 * Leaving a workout — finished, discarded, or found already over — lands on the Workouts tab with
 * the workout gone from its stack.
 *
 * It cannot go through [navigateToTab]. That pattern saves the tab's back stack on the way out and
 * restores it on the way in, and the stack a workout is pushed onto *is* the Workouts stack — so
 * restoring put the user straight back on the dead workout screen, with the bottom bar hidden
 * because a workout is not a tab: the "Back to workouts" button and every tab appeared dead.
 * Dropping the saved state and popping the tab's stack to nothing is what makes the way out a way
 * out.
 *
 * The other tabs are untouched, so a routine half-edited in the Workouts tree is gone (it led to a
 * workout that no longer exists) while the Calendar and Stats keep their own stacks.
 */
private fun NavHostController.returnToWorkouts() {
    clearBackStack(Destination.Workouts.route)
    navigate(Destination.Workouts.route) {
        popUpTo(Destination.Workouts.route) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateToTab(destination: Destination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
