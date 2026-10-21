package com.ggym.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ggym.app.data.prefs.AppLanguage
import com.ggym.app.data.prefs.withAppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * That Italian is not just a folder in the APK.
 *
 * `values-it/strings.xml` can exist, be complete and still change nothing if nothing ever asks for
 * a context that carries the locale — so this drives [withAppLanguage] the way the activity and the
 * notification builder do, and reads the copy back through the resources.
 *
 * The device here is English, which is the interesting case: an Italian app on an English phone is
 * exactly what the picker is for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rGB")
class LocalizationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `Italian resolves the Italian copy`() {
        val italian = context.withAppLanguage(AppLanguage.Italian)

        assertEquals("Schede", italian.getString(R.string.nav_workouts))
        assertEquals("Impostazioni", italian.getString(R.string.nav_settings))
        assertEquals("Carico di lavoro", italian.getString(R.string.slot_field_weight))
        assertEquals("Completa serie", italian.getString(R.string.workout_complete_set))
        // A placeholder survives the translation.
        assertEquals("Nessun allenamento in ottobre 2026", italian.getString(R.string.calendar_month_empty, "ottobre 2026"))
    }

    @Test
    fun `Italian plurals are the Italian ones`() {
        val italian = context.withAppLanguage(AppLanguage.Italian)

        assertEquals("1 giorno", italian.resources.getQuantityString(R.plurals.routine_day_count, 1, 1))
        assertEquals("2 giorni", italian.resources.getQuantityString(R.plurals.routine_day_count, 2, 2))
        // "serie" does not change between singular and plural in Italian.
        assertEquals("1 serie", italian.resources.getQuantityString(R.plurals.routine_set_count, 1, 1))
        assertEquals("4 serie", italian.resources.getQuantityString(R.plurals.routine_set_count, 4, 4))
    }

    @Test
    fun `English stays what the app ships`() {
        val english = context.withAppLanguage(AppLanguage.English)

        assertEquals("Workouts", english.getString(R.string.nav_workouts))
        assertEquals("2 days", english.resources.getQuantityString(R.plurals.routine_day_count, 2, 2))
    }

    @Test
    fun `following the device leaves the context exactly as the platform made it`() {
        assertSame(
            "System must not wrap: the platform's own locale has to come through untouched",
            context,
            context.withAppLanguage(AppLanguage.System),
        )
    }
}
