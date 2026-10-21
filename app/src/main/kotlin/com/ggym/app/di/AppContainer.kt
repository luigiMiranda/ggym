package com.ggym.app.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ggym.app.data.prefs.DataStoreLanguagePreferences
import com.ggym.app.data.prefs.DataStoreRestTimerStore
import com.ggym.app.data.prefs.DataStoreThemePreferences
import com.ggym.app.data.prefs.DataStoreWorkoutPreferences
import com.ggym.app.data.prefs.LanguagePreferences
import com.ggym.app.data.prefs.ThemePreferences
import com.ggym.app.data.prefs.createPreferencesDataStore
import com.ggym.app.data.prefs.createRestTimerDataStore
import com.ggym.app.data.repository.RoomBodyWeightRepository
import com.ggym.app.data.repository.RoomExerciseRepository
import com.ggym.app.data.repository.RoomHistoryRepository
import com.ggym.app.data.repository.RoomRoutineRepository
import com.ggym.app.data.room.createGgymDatabase
import com.ggym.app.domain.repository.BodyWeightRepository
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RestTimerStore
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import com.ggym.app.domain.repository.WorkoutPreferences
import com.ggym.app.domain.exercise.ExerciseFormUseCases
import com.ggym.app.domain.workout.ActiveWorkoutUseCases
import com.ggym.app.domain.workout.RestTimerUseCases
import com.ggym.app.domain.stats.BodyWeightUseCases
import com.ggym.app.domain.workout.WorkingWeightUseCases
import com.ggym.app.service.AndroidRestAlertPlayer
import com.ggym.app.service.RestTimerAlarms
import com.ggym.app.service.RestTimerDependencies
import com.ggym.app.service.RestTimerNotifications
import com.ggym.app.service.ServiceRestTimerCommands
import com.ggym.app.ui.calendar.CalendarViewModel
import com.ggym.app.ui.exercises.ExercisePickerViewModel
import com.ggym.app.ui.history.HistoryDetailViewModel
import com.ggym.app.ui.settings.SettingsViewModel
import com.ggym.app.ui.stats.StatsViewModel
import com.ggym.app.ui.workout.ActiveWorkoutViewModel
import com.ggym.app.ui.workouts.DayDetailViewModel
import com.ggym.app.ui.workouts.RoutineDetailViewModel
import com.ggym.app.ui.workouts.WorkoutsViewModel

/**
 * Manual dependency container — the app's composition root (docs/ARCHITECTURE.md).
 *
 * Created once by [com.ggym.app.GgymApplication]. Milestones add their repositories, use-cases and
 * view-model factories here; screens never reach for singletons themselves.
 *
 * Screens depend on the `domain.repository` interfaces, never on the Room types the
 * implementations are built from: the database and its DAOs stay private to this class.
 *
 * A factory that needs a screen argument takes it as a function parameter
 * ([routineDetailViewModelFactory] and friends) rather than reading it out of `SavedStateHandle`,
 * so what a screen depends on is visible at the call site and in its tests.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val preferencesDataStore = createPreferencesDataStore(appContext)
    private val database = createGgymDatabase(appContext)

    val themePreferences: ThemePreferences = DataStoreThemePreferences(preferencesDataStore)

    /**
     * The language the app is shown in. The app's own store is the source of truth on every API
     * level; `MainActivity` and the rest-timer notification apply it to their contexts. See
     * [LanguagePreferences] for why the platform's own per-app language is not used as the store.
     */
    val languagePreferences: LanguagePreferences = DataStoreLanguagePreferences(preferencesDataStore)

    val workoutPreferences: WorkoutPreferences = DataStoreWorkoutPreferences(preferencesDataStore)

    private val restTimerDataStore = createRestTimerDataStore(appContext)

    /**
     * The running rest countdown, in its own DataStore file. The service is its only writer; the
     * active-workout screen reads it to draw the banner.
     */
    val restTimerStore: RestTimerStore = DataStoreRestTimerStore(restTimerDataStore)

    /**
     * The rest timer, in both of its halves: the rules the app calls, and the dependencies the
     * foreground service and its alarm backstop are built with.
     */
    val restTimerUseCases: RestTimerUseCases = RestTimerUseCases(
        store = restTimerStore,
        commands = ServiceRestTimerCommands(appContext),
        preferences = workoutPreferences,
    )

    /** Read by [com.ggym.app.service.RestTimerService] and its receiver, never by a screen. */
    val restTimerDependencies = RestTimerDependencies(
        store = restTimerStore,
        timers = restTimerUseCases,
        notifications = RestTimerNotifications(appContext),
        alarms = RestTimerAlarms(appContext),
        alerts = AndroidRestAlertPlayer(appContext),
        preferences = workoutPreferences,
    )

    val exerciseRepository: ExerciseRepository = RoomExerciseRepository(database)

    val routineRepository: RoutineRepository = RoomRoutineRepository(database)

    val workoutHistoryRepository: WorkoutHistoryRepository = RoomHistoryRepository(database)

    val bodyWeightRepository: BodyWeightRepository = RoomBodyWeightRepository(database)

    /**
     * The only writer of a body weight, and the home of the one-measurement-per-day rule. The
     * Stats tab's form is its only caller, and the rule holds for any caller a later screen adds.
     */
    val bodyWeightUseCases = BodyWeightUseCases(bodyWeightRepository)

    /** The "which weight do I load?" rule, shared by the day list and the slot editor. */
    private val workingWeightUseCases = WorkingWeightUseCases(
        routines = routineRepository,
        exercises = exerciseRepository,
        history = workoutHistoryRepository,
    )

    /** The exercise form's two writes: create a dictionary entry and plan it, or rewrite both rows. */
    private val exerciseFormUseCases = ExerciseFormUseCases(
        routines = routineRepository,
        exercises = exerciseRepository,
    )

    /** The workout that is running: starting one, working through it, ending it. */
    val activeWorkoutUseCases = ActiveWorkoutUseCases(
        routines = routineRepository,
        exercises = exerciseRepository,
        history = workoutHistoryRepository,
        weights = workingWeightUseCases,
    )

    val calendarViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { CalendarViewModel(workoutHistoryRepository) }
    }

    fun historyDetailViewModelFactory(sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { HistoryDetailViewModel(sessionId, workoutHistoryRepository) }
    }

    val statsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            StatsViewModel(
                history = workoutHistoryRepository,
                exercises = exerciseRepository,
                bodyWeights = bodyWeightUseCases,
            )
        }
    }

    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SettingsViewModel(themePreferences, languagePreferences, restTimerUseCases, workoutPreferences)
        }
    }

    val workoutsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { WorkoutsViewModel(routineRepository, workoutHistoryRepository) }
    }

    fun routineDetailViewModelFactory(routineId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { RoutineDetailViewModel(routineId, routineRepository) }
    }

    fun dayDetailViewModelFactory(routineId: Long, routineDayId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            DayDetailViewModel(
                routineId = routineId,
                routineDayId = routineDayId,
                routines = routineRepository,
                exercises = exerciseRepository,
                weights = workingWeightUseCases,
                history = workoutHistoryRepository,
                workout = activeWorkoutUseCases,
                restTimer = restTimerUseCases,
                formUseCases = exerciseFormUseCases,
            )
        }
    }

    fun exercisePickerViewModelFactory(routineDayId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ExercisePickerViewModel(
                routineDayId = routineDayId,
                routines = routineRepository,
                exercises = exerciseRepository,
                formUseCases = exerciseFormUseCases,
            )
        }
    }

    /**
     * The screen for one running workout. The id is the *draft's*: the factory is remembered per id
     * at the call site, so one screen watches one workout and never adopts the next one.
     */
    fun activeWorkoutViewModelFactory(sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ActiveWorkoutViewModel(
                sessionId = sessionId,
                history = workoutHistoryRepository,
                workout = activeWorkoutUseCases,
                restTimer = restTimerUseCases,
                preferences = workoutPreferences,
            )
        }
    }
}
