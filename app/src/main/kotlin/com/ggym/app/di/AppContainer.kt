package com.ggym.app.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ggym.app.data.prefs.DataStoreRestTimerStore
import com.ggym.app.data.prefs.DataStoreThemePreferences
import com.ggym.app.data.prefs.DataStoreWorkoutPreferences
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
import com.ggym.app.domain.workout.WorkingWeightUseCases
import com.ggym.app.ui.exercises.ExercisePickerViewModel
import com.ggym.app.ui.exercises.SlotEditorViewModel
import com.ggym.app.ui.settings.SettingsViewModel
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

    val workoutPreferences: WorkoutPreferences = DataStoreWorkoutPreferences(preferencesDataStore)

    private val restTimerDataStore = createRestTimerDataStore(appContext)

    /**
     * The running rest countdown, in its own DataStore file. The service is its only writer; the
     * active-workout screen reads it to draw the banner.
     */
    val restTimerStore: RestTimerStore = DataStoreRestTimerStore(restTimerDataStore)

    val exerciseRepository: ExerciseRepository = RoomExerciseRepository(database)

    val routineRepository: RoutineRepository = RoomRoutineRepository(database)

    val workoutHistoryRepository: WorkoutHistoryRepository = RoomHistoryRepository(database)

    val bodyWeightRepository: BodyWeightRepository = RoomBodyWeightRepository(database)

    /** The "which weight do I load?" rule, shared by the day list and the slot editor. */
    private val workingWeightUseCases = WorkingWeightUseCases(
        routines = routineRepository,
        exercises = exerciseRepository,
        history = workoutHistoryRepository,
    )

    val settingsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { SettingsViewModel(themePreferences) }
    }

    val workoutsViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { WorkoutsViewModel(routineRepository) }
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
            )
        }
    }

    fun exercisePickerViewModelFactory(routineDayId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer { ExercisePickerViewModel(routineDayId, routineRepository, exerciseRepository) }
    }

    fun slotEditorViewModelFactory(routineId: Long, plannedExerciseId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SlotEditorViewModel(
                routineId = routineId,
                plannedExerciseId = plannedExerciseId,
                routines = routineRepository,
                exercises = exerciseRepository,
                weights = workingWeightUseCases,
            )
        }
    }
}
