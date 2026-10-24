package io.github.world303.budgetmeow.core

import io.github.world303.budgetmeow.core.model.BudgetDataset
import io.github.world303.budgetmeow.core.model.BudgetRecord
import io.github.world303.budgetmeow.core.model.BudgetRecordData
import io.github.world303.budgetmeow.core.model.BudgetSummary
import io.github.world303.budgetmeow.core.model.BudgetSummaryData
import io.github.world303.budgetmeow.core.model.DatasetMerge
import io.github.world303.budgetmeow.core.model.RecordType
import io.github.world303.budgetmeow.core.model.TripRecord
import io.github.world303.budgetmeow.core.model.TripRecordData
import io.github.world303.budgetmeow.core.model.TripRecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetMergeTest {

    private fun record(
        id: String,
        amount: Double = -10.0,
        updatedAt: String,
        deleted: Boolean = false,
    ) = BudgetRecord(
        id = id,
        data = BudgetRecordData(
            userId = "u",
            month = "2026-10",
            type = RecordType.Expense,
            amount = amount,
            note = "",
            createdAt = "2026-10-01T00:00:00Z",
            updatedAt = updatedAt,
            deleted = deleted,
        ),
    )

    @Test
    fun `只有一边有的记录都会保留`() {
        val merged = DatasetMerge.merge(
            BudgetDataset(records = listOf(record("a", updatedAt = "2026-10-01T00:00:00Z"))),
            BudgetDataset(records = listOf(record("b", updatedAt = "2026-10-02T00:00:00Z"))),
        )
        assertEquals(setOf("a", "b"), merged.records.map { it.id }.toSet())
    }

    @Test
    fun `较新的修改获胜`() {
        val merged = DatasetMerge.merge(
            BudgetDataset(records = listOf(record("a", amount = -10.0, updatedAt = "2026-10-05T00:00:00Z"))),
            BudgetDataset(records = listOf(record("a", amount = -99.0, updatedAt = "2026-10-01T00:00:00Z"))),
        )
        assertEquals(-10.0, merged.records.single().data.amount, 1e-9)
    }

    @Test
    fun `墓碑会覆盖旧版本`() {
        val merged = DatasetMerge.merge(
            BudgetDataset(records = listOf(record("a", updatedAt = "2026-10-05T00:00:00Z", deleted = true))),
            BudgetDataset(records = listOf(record("a", updatedAt = "2026-10-01T00:00:00Z"))),
        )
        assertTrue(merged.records.single().data.deleted)
        // 展示层要过滤掉
        assertTrue(merged.recordsOf("2026-10").isEmpty())
    }

    @Test
    fun `另一端删掉的记录不会被复活`() {
        val merged = DatasetMerge.merge(
            BudgetDataset(records = listOf(record("a", updatedAt = "2026-10-01T00:00:00Z"))),
            BudgetDataset(records = listOf(record("a", updatedAt = "2026-10-06T00:00:00Z", deleted = true))),
        )
        assertTrue(merged.records.single().data.deleted)
    }

    @Test
    fun `汇总按月份取更新的那份`() {
        fun summary(balance: Double, stamp: String) = BudgetSummary(
            id = "s-$stamp",
            data = BudgetSummaryData(
                userId = "u",
                month = "2026-10",
                initialBudget = 3000.0,
                currentBalance = balance,
                lastUpdatedAt = stamp,
            ),
        )
        val merged = DatasetMerge.merge(
            BudgetDataset(summaries = listOf(summary(100.0, "2026-10-05T00:00:00Z"))),
            BudgetDataset(summaries = listOf(summary(200.0, "2026-10-07T00:00:00Z"))),
        )
        assertEquals(1, merged.summaries.size)
        assertEquals(200.0, merged.summaries.single().data.currentBalance, 1e-9)
    }

    @Test
    fun `行程明细也按时间戳合并`() {
        fun tripRecord(amount: Double, stamp: String, deleted: Boolean = false) = TripRecord(
            id = "t1",
            data = TripRecordData(
                userId = "u",
                tripId = "trip",
                type = TripRecordType.Expense,
                amount = amount,
                note = "",
                createdAt = "2026-10-01T00:00:00Z",
                updatedAt = stamp,
                deleted = deleted,
            ),
        )
        val merged = DatasetMerge.merge(
            BudgetDataset(tripRecords = listOf(tripRecord(-50.0, "2026-10-02T00:00:00Z"))),
            BudgetDataset(tripRecords = listOf(tripRecord(-80.0, "2026-10-03T00:00:00Z"))),
        )
        assertEquals(-80.0, merged.tripRecords.single().data.amount, 1e-9)
        assertFalse(merged.tripRecords.single().data.deleted)
    }
}
