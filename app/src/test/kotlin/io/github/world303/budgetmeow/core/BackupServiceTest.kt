package io.github.world303.budgetmeow.core

import io.github.world303.budgetmeow.core.backup.BackupException
import io.github.world303.budgetmeow.core.backup.BackupService
import io.github.world303.budgetmeow.core.model.BudgetDataset
import io.github.world303.budgetmeow.core.model.BudgetRecord
import io.github.world303.budgetmeow.core.model.BudgetRecordData
import io.github.world303.budgetmeow.core.model.BudgetSummary
import io.github.world303.budgetmeow.core.model.BudgetSummaryData
import io.github.world303.budgetmeow.core.model.RecordType
import io.github.world303.budgetmeow.core.model.TimeUtil
import io.github.world303.budgetmeow.core.model.TripRecord
import io.github.world303.budgetmeow.core.model.TripRecordData
import io.github.world303.budgetmeow.core.model.TripRecordType
import io.github.world303.budgetmeow.core.model.TripStatus
import io.github.world303.budgetmeow.core.model.TripSummary
import io.github.world303.budgetmeow.core.model.TripSummaryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupServiceTest {

    private fun sampleDataset(): BudgetDataset = BudgetDataset(
        summaries = listOf(
            BudgetSummary(
                id = "s1",
                data = BudgetSummaryData(
                    userId = "old-user",
                    month = "2026-10",
                    initialBudget = 3000.0,
                    currentBalance = 1200.5,
                    savedAmount = 100.0,
                    carryOverToNext = 50.0,
                    advanceForNext = 0.0,
                    lastUpdatedAt = TimeUtil.nowIso(),
                ),
            ),
        ),
        records = listOf(
            BudgetRecord(
                id = "r1",
                data = BudgetRecordData(
                    userId = "old-user",
                    month = "2026-10",
                    type = RecordType.Expense,
                    amount = -30.0,
                    note = "咖啡",
                    createdAt = "2026-10-20T02:03:04Z",
                ),
            ),
        ),
        trips = listOf(
            TripSummary(
                id = "t1",
                data = TripSummaryData(
                    userId = "old-user",
                    status = TripStatus.Ended,
                    balance = 0.0,
                    initialAmount = 500.0,
                    tripName = "国庆",
                    startRecordId = "r0",
                    createdAt = "2026-10-01T00:00:00Z",
                    endedAt = "2026-10-08T00:00:00Z",
                ),
            ),
        ),
        tripRecords = listOf(
            TripRecord(
                id = "tr1",
                data = TripRecordData(
                    userId = "old-user",
                    tripId = "t1",
                    type = TripRecordType.Expense,
                    amount = -120.0,
                    note = "打车",
                    createdAt = "2026-10-02T00:00:00Z",
                ),
            ),
        ),
    )

    @Test
    fun `导出再导入能还原数据`() {
        val json = BackupService.exportJson(sampleDataset(), "p1")
        val result = BackupService.importJson(json, "p1")

        assertEquals(0, result.skipped)
        assertEquals(1, result.dataset.summaries.size)
        assertEquals(1, result.dataset.records.size)
        assertEquals(1, result.dataset.trips.size)
        assertEquals(1, result.dataset.tripRecords.size)
        assertEquals("p1", result.dataset.records.first().data.userId)
        assertEquals(-30.0, result.dataset.records.first().data.amount, 1e-9)
        assertEquals(RecordType.Expense, result.dataset.records.first().data.type)
    }

    @Test
    fun `能导入 Web 端备份结构`() {
        val webBackup = """
            {
              "_exportMeta": { "version": 3, "exportedAt": "2026-10-20T00:00:00.000Z", "userId": "user_x" },
              "budget_summary": [
                { "wyID": "w1", "data": { "userId": "user_x", "month": "2026-09", "initialBudget": 2500,
                  "currentBalance": 300.25, "savedAmount": 20, "lastUpdatedAt": "2026-09-30T00:00:00.000Z" } }
              ],
              "budget_record": [
                { "wyID": "w2", "data": { "userId": "user_x", "month": "2026-09", "type": "income",
                  "amount": 300.5, "note": "工资", "createdAt": "2026-09-01T01:00:00.000Z" } },
                { "wyID": "w3", "data": { "userId": "user_x", "month": "2026-09", "type": "unknown_type",
                  "amount": 1, "createdAt": "2026-09-01T01:00:00.000Z" } }
              ],
              "trip_summary": [],
              "trip_record": [],
              "user_profile": [ { "wyID": "w4", "data": { "userId": "user_x" } } ]
            }
        """.trimIndent()

        val result = BackupService.importJson(webBackup, "p1")
        assertEquals(1, result.dataset.summaries.size)
        assertEquals(1, result.dataset.records.size)
        assertEquals(1, result.skipped) // 未知类型被跳过
        assertEquals("user_x", result.sourceUserId)
        assertEquals(300.5, result.dataset.records.first().data.amount, 1e-9)
    }

    @Test
    fun `旧版本备份也能导入`() {
        val v1 = """
            {
              "budget_summary": [],
              "budget_record": [
                { "wyID": "w2", "data": { "type": "expense", "amount": -12.5, "createdAt": "2026-08-01T01:00:00.000Z" } }
              ]
            }
        """.trimIndent()

        val result = BackupService.importJson(v1, "p1")
        assertEquals(1, result.dataset.records.size)
        // 缺 month 时按 createdAt 推算
        assertEquals("2026-08", result.dataset.records.first().data.month)
    }

    @Test(expected = BackupException::class)
    fun `不支持的版本会报错`() {
        val future = """{ "_exportMeta": { "version": 9, "exportedAt": "x", "userId": "u" } }"""
        BackupService.importJson(future, "p1")
    }

    @Test(expected = BackupException::class)
    fun `损坏的 JSON 会报错`() {
        BackupService.importJson("{ not json", "p1")
    }

    @Test
    fun `备份不含昵称头像`() {
        val json = BackupService.exportJson(sampleDataset(), "p1")
        assertTrue(!json.contains("nickname"))
        assertTrue(!json.contains("avatar"))
        assertTrue(json.contains("_exportMeta"))
    }
}
