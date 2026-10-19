package com.ggym.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The one list operation both a drag and a screen reader's "Move down" reduce to. */
class ReorderTest {

    @Test
    fun `moving down shifts the rows between`() {
        assertEquals(listOf("b", "c", "a", "d"), listOf("a", "b", "c", "d").moved(from = 0, to = 2))
    }

    @Test
    fun `moving up shifts the rows between`() {
        assertEquals(listOf("a", "d", "b", "c"), listOf("a", "b", "c", "d").moved(from = 3, to = 1))
    }

    @Test
    fun `a move to the same place is the same list`() {
        val list = listOf("a", "b", "c")
        assertSame("no copy when nothing moves", list, list.moved(from = 1, to = 1))
    }

    @Test
    fun `an out-of-range move is refused rather than clamped`() {
        val list = listOf("a", "b", "c")
        assertSame(list, list.moved(from = -1, to = 1))
        assertSame(list, list.moved(from = 1, to = 3))
        assertSame(list, list.moved(from = 3, to = 0))
    }

    @Test
    fun `moving onto either end keeps every element exactly once`() {
        val list = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("e", "a", "b", "c", "d"), list.moved(from = 4, to = 0))
        assertEquals(listOf("b", "c", "d", "e", "a"), list.moved(from = 0, to = 4))
    }
}
