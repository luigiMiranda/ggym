package com.ggym.app.di

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ggym.app.data.prefs.DataStoreThemePreferences
import com.ggym.app.data.prefs.ThemePreferences
import com.ggym.app.data.prefs.createPreferencesDataStore
import com.ggym.app.data.repository.RoomBodyWeightRepository
import com.ggym.app.data.repository.RoomExerciseRepository
import com.ggym.app.data.repository.RoomHistoryRepository
import com.ggym.app.data.repository.RoomRoutineRepository
import com.ggym.app.data.room.createGgymDatabase
import com.ggym.app.domain.repository.BodyWeightRepository
import com.ggym.app.domain.repository.ExerciseRepository
import com.ggym.app.domain.repository.RoutineRepository
import com.ggym.app.domain.repository.WorkoutHistoryRepository
import com.ggym.app.ui.settings.SettingsViewModel

/**
 * Manual dependency container — the app's composition root (docs/ARCHITECTURE.md).
 *
 * Created once by [com.ggym.app.GgymApplication]. Milestones add their repositories and
 * use-cases here; screens never reach for singletons themselves.
 *
 * Screens depend on the `domain.repository` interfaces, never on the Room types the
 * implementations are built from: the database and its DAOs stay private to this class.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val preferencesDataStore = createPreferencesDataStore(appContext)
    private val database = createGgymDatabase(appContext)

    val themePreferences: ThemePreferences = DataStoreThemePreferences(preferencesDataStore)

    val exerciseRepository: ExerciseRepository = RoomExerciseRepository(database)

    val routineRepository: RoutineRepository = RoomRoutineRepository(database)

    val workoutHistoryRepository: WorkoutHistoryRepository = RoomHistoryRepository(database)

    val bodyWeightRepository: BodyWeightRepository = RoomBodyWeightRepository(database)

    val settingsViewModelFactory = viewModelFactory {
        initializer { SettingsViewModel(themePreferences) }
    }
}
