package com.ggym.app.domain.stats

import com.ggym.app.domain.model.BodyWeightLog
import com.ggym.app.domain.repository.BodyWeightWriteResult
import com.ggym.app.testing.RepositoryTest
import com.ggym.app.testing.TestData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The one write path for body weight: the range rule, the one-entry-per-day rule, and the edit
 * that can move a measurement to another day.
 *
 * Assertions are made against the repository, because "one entry per day" is a promise about the
 * rows, not about what the chart happened to draw.
 */
class BodyWeightUseCasesTest : RepositoryTest() {

    private val useCases by lazy { BodyWeightUseCases(bodyWeightRepository) }
    private val today = TestData.date(2026, 10, 19)

    @Test
    fun `the range is strictly inside 25 and 300 kg`() = runTest {
        assertTrue(useCases.log(today, 25.0) is BodyWeightWriteResult.InvalidWeight)
        assertTrue(useCases.log(today, 300.0) is BodyWeightWriteResult.InvalidWeight)
        assertTrue(useCases.log(today, 0.0) is BodyWeightWriteResult.InvalidWeight)
        assertTrue(useCases.log(today, Double.NaN) is BodyWeightWriteResult.InvalidWeight)
        assertTrue(entries().isEmpty())

        assertTrue(useCases.log(today.minusDays(1), 25.1) is BodyWeightWriteResult.Saved)
        assertTrue(useCases.log(today, 299.9) is BodyWeightWriteResult.Saved)
        assertEquals(listOf(299.9, 25.1), entries().map { it.weightKg })
    }

    @Test
    fun `two decimals are kept`() = runTest {
        useCases.log(today, 82.46)
        assertEquals(82.46, entries().single().weightKg, 0.0)
    }

    @Test
    fun `logging the same day twice updates that day instead of adding a row`() = runTest {
        useCases.log(today, 82.4)
        val second = useCases.log(today, 82.7)

        assertTrue(second is BodyWeightWriteResult.Saved)
        assertEquals(1, entries().size)
        assertEquals(82.7, entries().single().weightKg, 0.0)
    }

    @Test
    fun `the first log of the day and the second share the same row`() = runTest {
        val first = useCases.log(today, 82.4) as BodyWeightWriteResult.Saved
        val second = useCases.log(today, 82.7) as BodyWeightWriteResult.Saved

        assertEquals(first.entryId, second.entryId)
    }

    @Test
    fun `an edit can move a measurement to another day`() = runTest {
        useCases.log(TestData.date(2026, 10, 18), 82.4)
        val stored = entries().single()

        val result = useCases.update(stored.copy(date = today, weightKg = 82.9))

        assertTrue(result is BodyWeightWriteResult.Saved)
        val after = entries().single()
        assertEquals(today, after.date)
        assertEquals(82.9, after.weightKg, 0.0)
        assertEquals(stored.id, after.id)
    }

    @Test
    fun `moving a measurement onto an occupied day merges into one row`() = runTest {
        useCases.log(TestData.date(2026, 10, 18), 82.4)
        useCases.log(today, 82.7)
        val older = entries().first { it.date == TestData.date(2026, 10, 18) }
        val todays = entries().first { it.date == today }

        val result = useCases.update(older.copy(date = today, weightKg = 83.0))

        assertTrue(result is BodyWeightWriteResult.Saved)
        assertEquals(1, entries().size)
        assertEquals(today, entries().single().date)
        assertEquals(83.0, entries().single().weightKg, 0.0)
        assertEquals("the target row is the one that survives", todays.id, entries().single().id)
    }

    @Test
    fun `deleting removes exactly the one measurement`() = runTest {
        useCases.log(TestData.date(2026, 10, 18), 82.4)
        useCases.log(today, 82.7)
        val older = entries().first { it.date == TestData.date(2026, 10, 18) }

        useCases.delete(older.id)

        assertEquals(listOf(today), entries().map { it.date })
    }

    @Test
    fun `the log is newest first and the chart window is the chosen range`() = runTest {
        useCases.log(TestData.date(2025, 1, 5), 90.0)
        useCases.log(TestData.date(2026, 9, 1), 83.0)
        useCases.log(today, 82.4)

        val log = useCases.observeAll(today).first()
        assertEquals(listOf(today, TestData.date(2026, 9, 1), TestData.date(2025, 1, 5)), log.map { it.date })

        val oneMonth = useCases.observe(BodyWeightRange.ONE_MONTH, today).first()
        assertEquals(listOf(today), oneMonth.map { it.date })

        val all = useCases.observe(BodyWeightRange.ALL, today).first()
        assertEquals(3, all.size)
    }

    @Test
    fun `an invalid edit is refused and the old value stays`() = runTest {
        useCases.log(today, 82.4)
        val stored = entries().single()

        val result = useCases.update(stored.copy(weightKg = 400.0))

        assertTrue(result is BodyWeightWriteResult.InvalidWeight)
        assertEquals(82.4, entries().single().weightKg, 0.0)
    }

    private suspend fun entries(): List<BodyWeightLog> =
        useCases.observeAll(today).first()
}
