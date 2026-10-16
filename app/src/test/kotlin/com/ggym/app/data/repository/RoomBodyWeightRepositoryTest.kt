package com.ggym.app.data.repository

import app.cash.turbine.test
import com.ggym.app.domain.repository.BodyWeightWriteResult
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.TestData
import com.ggym.app.testing.countRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Body weight as the tracker will use it: a daily value stored at local midnight, a range that
 * covers whole days, and the one write in the data layer that validates its input, because a
 * human body has a documented range and a text field is where a bad number arrives.
 */
class RoomBodyWeightRepositoryTest : RepositoryTest() {

    @Test
    fun `a measurement is stored on the day it belongs to`() = runTest {
        val result = bodyWeightRepository.add(date = TestData.date(day = 5), weightKg = 82.4)

        assertEquals(BodyWeightWriteResult.Saved(entryId = 1), result)

        val stored = bodyWeightRepository.latest()!!
        assertEquals(TestData.date(day = 5), stored.date)
        assertEquals(82.4, stored.weightKg, 0.0)
        assertEquals("stored at local midnight", TestData.timestamp(TestData.date(day = 5)), database.bodyWeightDao().getAll().single().dateTimestamp)
    }

    @Test
    fun `a weight outside the human range is refused and nothing is written`() = runTest {
        listOf(0.0, 24.9, 25.0, 300.0, 300.1, 1000.0, Double.NaN).forEach { weight ->
            val result = bodyWeightRepository.add(date = TestData.date(day = 5), weightKg = weight)

            assertTrue("$weight kg should be refused, got $result", result is BodyWeightWriteResult.InvalidWeight)
            assertEquals("$weight kg", weight, (result as BodyWeightWriteResult.InvalidWeight).weightKg, 0.0)
        }

        assertEquals(0, database.countRows("body_weight_log"))
        assertNull(bodyWeightRepository.latest())
    }

    @Test
    fun `the edge of the accepted range is refused on both sides`() = runTest {
        assertTrue(bodyWeightRepository.add(TestData.date(day = 5), 25.0) is BodyWeightWriteResult.InvalidWeight)
        assertTrue(bodyWeightRepository.add(TestData.date(day = 5), 25.1) is BodyWeightWriteResult.Saved)
        assertTrue(bodyWeightRepository.add(TestData.date(day = 5), 299.9) is BodyWeightWriteResult.Saved)
        assertTrue(bodyWeightRepository.add(TestData.date(day = 5), 300.0) is BodyWeightWriteResult.InvalidWeight)

        assertEquals(2, database.countRows("body_weight_log"))
    }

    @Test
    fun `the latest measurement is the newest day, not the last one written`() = runTest {
        bodyWeightRepository.add(date = TestData.date(day = 10), weightKg = 82.0)
        bodyWeightRepository.add(date = TestData.date(day = 5), weightKg = 83.0)

        assertEquals(TestData.date(day = 10), bodyWeightRepository.latest()?.date)
        assertEquals(82.0, bodyWeightRepository.latest()?.weightKg!!, 0.0)
    }

    @Test
    fun `a range includes both ends and comes back oldest first`() = runTest {
        bodyWeightRepository.add(TestData.date(day = 4), 83.0)
        bodyWeightRepository.add(TestData.date(day = 5), 82.4)
        bodyWeightRepository.add(TestData.date(day = 6), 82.1)
        bodyWeightRepository.add(TestData.date(day = 7), 81.8)

        val range = bodyWeightRepository.observeRange(from = TestData.date(day = 5), to = TestData.date(day = 6)).first()

        assertEquals(listOf(82.4, 82.1), range.map { it.weightKg })
        assertEquals(listOf(5, 6), range.map { it.date.dayOfMonth })
    }

    @Test
    fun `weighing in twice on the same day keeps both measurements`() = runTest {
        bodyWeightRepository.add(TestData.date(day = 5), 83.0)
        bodyWeightRepository.add(TestData.date(day = 5), 82.6)

        val entries = bodyWeightRepository.observeRange(TestData.date(day = 5), TestData.date(day = 5)).first()

        assertEquals(listOf(83.0, 82.6), entries.map { it.weightKg })
        assertEquals(82.6, bodyWeightRepository.latest()?.weightKg!!, 0.0)
    }

    @Test
    fun `a measurement can be corrected`() = runTest {
        bodyWeightRepository.add(TestData.date(day = 5), 82.4)
        val stored = bodyWeightRepository.latest()!!

        val result = bodyWeightRepository.update(stored.copy(weightKg = 81.9))

        assertEquals(BodyWeightWriteResult.Saved(stored.id), result)
        assertEquals(81.9, bodyWeightRepository.latest()?.weightKg!!, 0.0)
        assertEquals("the date did not move", TestData.date(day = 5), bodyWeightRepository.latest()?.date)
    }

    @Test
    fun `a correction cannot introduce an impossible weight`() = runTest {
        bodyWeightRepository.add(TestData.date(day = 5), 82.4)
        val stored = bodyWeightRepository.latest()!!

        val result = bodyWeightRepository.update(stored.copy(weightKg = 500.0))

        assertTrue(result is BodyWeightWriteResult.InvalidWeight)
        assertEquals("the stored value is untouched", 82.4, bodyWeightRepository.latest()?.weightKg!!, 0.0)
    }

    @Test
    fun `a measurement can be deleted`() = runTest {
        bodyWeightRepository.add(TestData.date(day = 5), 82.4)
        val stored = bodyWeightRepository.latest()!!

        bodyWeightRepository.delete(stored.id)

        assertNull(bodyWeightRepository.latest())
        assertEquals(0, database.countRows("body_weight_log"))
    }

    @Test
    fun `a saved measurement reaches a collector that is already watching`() = runTest {
        bodyWeightRepository.observeRange(TestData.date(day = 1), TestData.date(day = 31)).test {
            assertTrue(awaitItem().isEmpty())

            bodyWeightRepository.add(TestData.date(day = 5), 82.4)
            assertEquals(82.4, awaitItem().single().weightKg, 0.0)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
