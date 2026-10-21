package com.ggym.app.data.prefs

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * The language the app is shown in, or the device's own.
 *
 * Three values rather than a locale string: the app ships two translations, and an open-ended
 * string would let a stored value name a language the app does not have.
 */
enum class AppLanguage {
    /** Follow the device. The default, and what a user who never opens Settings gets. */
    System,

    English,

    Italian,
    ;

    /** The BCP-47 tag of this choice, or `null` for "follow the device". */
    fun tag(): String? = when (this) {
        System -> null
        English -> "en"
        Italian -> "it"
    }

    /** The [Locale] this choice means, or `null` for "leave the context alone". */
    fun locale(): Locale? = when (this) {
        System -> null
        English -> Locale.ENGLISH
        Italian -> Locale.ITALIAN
    }

    companion object {
        /** Tolerant parse, in the same spirit as `ThemeMode.fromStorage`. */
        fun fromStorage(raw: String?): AppLanguage =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: System

        /** A language tag from anywhere — a `LocaleList`, a platform value — mapped back to a choice. */
        fun fromTag(tag: String?): AppLanguage = when (tag?.substringBefore('-')?.lowercase()) {
            "en" -> English
            "it" -> Italian
            else -> System
        }
    }
}

/**
 * Where the language choice is kept.
 *
 * **The app's own store is the single source of truth, on every API level.** Android 13 added the
 * platform's per-app language (`LocaleManager`), but its SDK exposes no way to *observe* the value
 * changing — verified against `platforms/android-37.0/android.jar`, whose `LocaleManager` has
 * `getApplicationLocales` / `setApplicationLocales` and nothing else — so using it as the store
 * would mean a Settings screen that cannot see a change made in system Settings. Instead the choice
 * lives here, and [withAppLanguage] applies it to the contexts the app owns: the activity's (every
 * screen) and the rest-timer notification's. `android:localeConfig` still declares en/it, so the
 * platform knows which languages the app has.
 */
interface LanguagePreferences {

    val language: Flow<AppLanguage>

    suspend fun setLanguage(language: AppLanguage)
}

/** The choice, in the preferences DataStore, stored by name so reordering the enum cannot renumber it. */
class DataStoreLanguagePreferences(
    private val dataStore: DataStore<Preferences>,
) : LanguagePreferences {

    override val language: Flow<AppLanguage> = dataStore.data.map { preferences ->
        AppLanguage.fromStorage(preferences[KEY_APP_LANGUAGE])
    }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_APP_LANGUAGE] = language.name }
    }

    private companion object {
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
    }
}

/**
 * [this] with [language] applied to its resources, or [this] when there is nothing to apply.
 *
 * A base context cannot be wrapped after it is attached, so this is called from `attachBaseContext`
 * — of the activity, which covers every screen, and of the notification builder, which is the one
 * surface outside the activity that shows the user copy. `System` returns the context untouched, so
 * the device's own language comes through exactly as the platform resolved it.
 */
fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = language.locale() ?: return this
    val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(configuration)
}
