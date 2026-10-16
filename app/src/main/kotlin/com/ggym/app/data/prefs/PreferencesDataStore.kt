package com.ggym.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app's single preferences DataStore. Built explicitly (rather than via the
 * `by preferencesDataStore` delegate) so the same construction path — and therefore the same
 * test path — covers app and unit tests.
 */
fun createPreferencesDataStore(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    scope = scope,
    produceFile = { context.preferencesDataStoreFile(STORE_NAME) },
)

internal const val STORE_NAME = "ggym_preferences"
