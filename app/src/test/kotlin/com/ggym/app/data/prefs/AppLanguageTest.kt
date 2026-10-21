package com.ggym.app.data.prefs

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The language choice is stored as a word and read back from more than one place — the preferences
 * file and, potentially, a language tag — so both directions are pinned here rather than trusted.
 */
class AppLanguageTest {

    @Test
    fun `a stored word maps back to the choice it names`() {
        assertEquals(AppLanguage.Italian, AppLanguage.fromStorage("italian"))
        assertEquals(AppLanguage.English, AppLanguage.fromStorage("English"))
        assertEquals("a value the app does not know follows the device, it does not crash", AppLanguage.System, AppLanguage.fromStorage("klingon"))
        assertEquals(AppLanguage.System, AppLanguage.fromStorage(null))
    }

    @Test
    fun `each choice names the tag and the locale it means`() {
        assertNull(AppLanguage.System.tag())
        assertEquals("en", AppLanguage.English.tag())
        assertEquals("it", AppLanguage.Italian.tag())

        assertNull(AppLanguage.System.locale())
        assertEquals(Locale.ENGLISH, AppLanguage.English.locale())
        assertEquals(Locale.ITALIAN, AppLanguage.Italian.locale())
    }

    @Test
    fun `a language tag maps back to a choice, region and all`() {
        assertEquals(AppLanguage.Italian, AppLanguage.fromTag("it"))
        assertEquals(AppLanguage.Italian, AppLanguage.fromTag("it-IT"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("en-GB"))
        assertEquals("a language the app does not translate follows the device", AppLanguage.System, AppLanguage.fromTag("fr-FR"))
        assertEquals(AppLanguage.System, AppLanguage.fromTag(null))
    }
}
