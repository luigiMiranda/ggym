package com.ggym.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The weight field's reader.
 *
 * The interesting cases are the ones a numeric keyboard still lets through: a lone separator, a
 * third decimal, a number nobody can lift. Blank is not an error — it is how a slot goes back to
 * inheriting the exercise's default.
 */
class WeightInputTest {

    @Test
    fun `a blank field means no weight of its own`() {
        listOf("", "   ").forEach { text ->
            assertEquals("'$text'", WeightParse.Weight(null), WeightInput.parse(text))
        }
    }

    @Test
    fun `whole and fractional weights are read, with either decimal separator`() {
        assertEquals(WeightParse.Weight(80.0), WeightInput.parse("80"))
        assertEquals(WeightParse.Weight(82.5), WeightInput.parse("82.5"))
        assertEquals("an Italian keyboard's comma means the same thing", WeightParse.Weight(82.5), WeightInput.parse("82,5"))
        assertEquals(WeightParse.Weight(0.0), WeightInput.parse("0"))
        assertEquals(WeightParse.Weight(2.25), WeightInput.parse("2.25"))
        assertEquals("surrounding spaces are the user's, not a mistake", WeightParse.Weight(100.0), WeightInput.parse(" 100 "))
    }

    @Test
    fun `a third decimal, a negative number and four digits are refused`() {
        listOf("82.555", "82,555", "-5", "-0.5", "1000", "82.5.5", ",5", "82,", "abc", "8 2", "1e3", "--").forEach { text ->
            assertEquals("'$text' should be refused", WeightParse.Invalid, WeightInput.parse(text))
        }
    }

    @Test
    fun `a step from a blank field lands on one step, and never goes below zero`() {
        assertEquals(2.5, WeightInput.stepped("")!!, 0.0)
        assertEquals(82.5, WeightInput.stepped("80")!!, 0.0)
        assertEquals(77.5, WeightInput.stepped("80", deltaKg = -2.5)!!, 0.0)
        assertEquals("there is no -2.5 kg on the bar", 0.0, WeightInput.stepped("1", deltaKg = -2.5)!!, 0.0)
    }

    @Test
    fun `a field that holds no number is not stepped at all`() {
        assertNull(WeightInput.stepped("nonsense"))
        assertNull(WeightInput.stepped("82.555"))
    }
}
