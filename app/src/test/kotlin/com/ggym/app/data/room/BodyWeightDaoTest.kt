package com.ggym.app.data.room

import app.cash.turbine.test
import com.ggym.app.testing.RoomTest
import com.ggym.app.testing.TestData
import com.ggym.app.testing.countRows
import com.ggym.app.testing.givenBodyWeight
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Body-weight storage: the range the chart plots, and "the latest one" the header shows. */
class BodyWeightDaoTest : RoomTest() {

    @Test
    fun `a range covers the days it is asked about, oldest first`() = runTest {
        database.givenBodyWeight(date = TestData.date(day = 4), weightKg = 83.0)
        database.givenBodyWeight(date = TestData.date(day = 5), weightKg = 82.4)
        database.givenBodyWeight(date = TestData.date(day = 6), weightKg = 82.1)
        database.givenBodyWeight(date = TestData.date(day = 7), weightKg = 81.8)

        val range = database.bodyWeightDao()
            .observeRange(
                fromInclusive = TestData.timestamp(TestData.date(day = 5)),
                toExclusive = TestData.timestamp(TestData.date(day = 7)),
            )
            .first()

        assertEquals(listOf(82.4, 82.1), range.map { it.weightKg })
    }

    @Test
    fun `the latest entry is the newest date, and the newest row on a shared date`() = runTest {
        database.givenBodyWeight(date = TestData.date(day = 1), weightKg = 84.0)
        database.givenBodyWeight(date = TestData.date(day = 5), weightKg = 83.0)
        database.givenBodyWeight(date = TestData.date(day = 5), weightKg = 82.6)

        assertEquals(82.6, database.bodyWeightDao().latest()?.weightKg!!, 0.0)
    }

    @Test
    fun `weighing twice on the same day keeps both measurements`() = runTest {
        database.givenBodyWeight(date = TestData.date(day = 5), weightKg = 83.0)
        database.givenBodyWeight(date = TestData.date(day = 5), weightKg = 82.6)

        assertEquals(2, database.bodyWeightDao().getAll().size)
        assertEquals(2, database.countRows("body_weight_log"))
    }

    @Test
    fun `an entry can be looked up, corrected and deleted`() = runTest {
        val entryId = database.givenBodyWeight(date = TestData.date(day = 5), weightKg = 82.4)

        val stored = database.bodyWeightDao().findById(entryId)!!
        database.bodyWeightDao().update(stored.copy(weightKg = 81.9))

        assertEquals(81.9, database.bodyWeightDao().findById(entryId)?.weightKg!!, 0.0)

        database.bodyWeightDao().deleteById(entryId)
        assertNull(database.bodyWeightDao().findById(entryId))
        assertNull(database.bodyWeightDao().latest())
    }

    @Test
    fun `a saved measurement is visible to a collector already watching the range`() = runTest {
        database.bodyWeightDao()
            .observeRange(fromInclusive = 0, toExclusive = Long.MAX_VALUE)
            .test {
                assertEquals(emptyList<Any>(), awaitItem())

                database.givenBodyWeight(date = TestData.date(day = 5), weightKg = 82.4)
                assertEquals(82.4, awaitItem().single().weightKg, 0.0)

                cancelAndIgnoreRemainingEvents()
            }
    }
}
