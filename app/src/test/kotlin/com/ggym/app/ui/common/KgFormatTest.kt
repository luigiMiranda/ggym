package com.ggym.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Weights as text. The `kg` suffix is not this object's business — it is a string resource — so
 * these are about the number and its separator.
 */
class KgFormatTest {

    private val english = Locale.US
    private val italian = Locale.ITALY

    @Test
    fun `whole weights have no trailing zero`() {
        assertEquals("80", KgFormat.formatNumber(80.0, english))
        assertEquals("80", KgFormat.formatNumber(80.0, italian))
        assertEquals("0", KgFormat.formatNumber(0.0, english))
    }

    @Test
    fun `a half kilo is written the way the locale writes decimals`() {
        assertEquals("82.5", KgFormat.formatNumber(82.5, english))
        assertEquals("82,5", KgFormat.formatNumber(82.5, italian))
        assertEquals("2.5", KgFormat.formatNumber(2.5, english))
    }

    @Test
    fun `two decimals are kept, and a third is rounded`() {
        assertEquals("82.25", KgFormat.formatNumber(82.25, english))
        assertEquals("82.26", KgFormat.formatNumber(82.256, english))
        assertEquals("82,26", KgFormat.formatNumber(82.256, italian))
    }
}
