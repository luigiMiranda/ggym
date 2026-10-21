package com.ggym.app.data.prefs

import app.cash.turbine.test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The language against the real DataStore, on a temp file. It shares the app's preferences file in
 * production, which is what this exercises: the implementation only ever sees a
 * `DataStore<Preferences>`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguagePreferencesDataStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("language-${System.nanoTime()}.preferences_pb")
        }

    @Test
    fun `the app follows the device until the user chooses`() = runTest {
        val preferences = DataStoreLanguagePreferences(createDataStore(backgroundScope))

        assertEquals(AppLanguage.System, preferences.language.first())
    }

    @Test
    fun `the choice reaches existing collectors and survives the round trip`() = runTest {
        val preferences = DataStoreLanguagePreferences(createDataStore(backgroundScope))

        preferences.language.test {
            assertEquals(AppLanguage.System, awaitItem())

            preferences.setLanguage(AppLanguage.Italian)
            assertEquals(AppLanguage.Italian, awaitItem())

            preferences.setLanguage(AppLanguage.System)
            assertEquals(AppLanguage.System, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }

        preferences.setLanguage(AppLanguage.Italian)
        assertEquals(AppLanguage.Italian, preferences.language.first())
    }
}
