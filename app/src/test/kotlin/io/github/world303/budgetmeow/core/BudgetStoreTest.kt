package io.github.world303.budgetmeow.core

import io.github.world303.budgetmeow.core.model.BudgetDataset
import io.github.world303.budgetmeow.core.model.BudgetRecord
import io.github.world303.budgetmeow.core.model.BudgetRecordData
import io.github.world303.budgetmeow.core.model.BudgetSummary
import io.github.world303.budgetmeow.core.model.BudgetSummaryData
import io.github.world303.budgetmeow.core.model.MonthKey
import io.github.world303.budgetmeow.core.model.RecordType
import io.github.world303.budgetmeow.core.model.TimeUtil
import io.github.world303.budgetmeow.core.model.computeStats
import io.github.world303.budgetmeow.core.repo.BudgetRepository
import io.github.world303.budgetmeow.core.store.BudgetStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRepository : BudgetRepository {
    var data: BudgetDataset = BudgetDataset()
    var saveCount: Int = 0

    override suspend fun load(profileId: String): BudgetDataset = data

    override suspend fun save(profileId: String, dataset: BudgetDataset) {
        data = dataset
        saveCount++
    }
}

class StatsTest {

    private fun record(type: RecordType, amount: Double) = BudgetRecord(
        id = "r",
        data = BudgetRecordData(
            userId = "u",
            month = "2026-10",
            type = type,
            amount = amount,
            note = "",
            createdAt = "2026-10-01T00:00:00Z",
        ),
    )

    @Test
    fun `统计只算真实收支，出行划转不算`() {
        val records = listOf(
            record(RecordType.Init, 3000.0),
            record(RecordType.Income, 100.0),
            record(RecordType.Expense, -50.0),
            record(RecordType.Save, 30.0),
            record(RecordType.Carryover, -20.0),
            record(RecordType.Advance, 40.0),
            record(RecordType.TripStart, -200.0),
            record(RecordType.TripExpense, -70.0),
            record(RecordType.TripEnd, 130.0),
            record(RecordType.Adjust, 0.0),
        )
        val stats = computeStats(records)
        // 网页版会把 trip_start/trip_end/carryover/advance 也算进收支，导致重复计数
        assertEquals(100.0, stats.income, 1e-9)
        assertEquals(120.0, stats.expense, 1e-9)
        assertEquals(30.0, stats.saved, 1e-9)
        assertEquals(70.0, stats.tripExpense, 1e-9)
        assertEquals(1, stats.tripCount)
        assertEquals(20.0, stats.netExpense, 1e-9)
    }
}

class BudgetStoreTest {

    private val month = MonthKey.now()

    private fun preloaded(): BudgetDataset {
        val lastMonth = MonthKey.previous(month)
        return BudgetDataset(
            summaries = listOf(
                BudgetSummary(
                    id = "last",
                    data = BudgetSummaryData(
                        userId = "p1",
                        month = lastMonth,
                        initialBudget = 2000.0,
                        currentBalance = 800.0,
                        carryOverToNext = 100.0,
                        advanceForNext = 20.0,
                        lastUpdatedAt = TimeUtil.nowIso(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `初始化预算会带上月结转与预支`() = runTest {
        val repo = FakeRepository().apply { data = preloaded() }
        val store = BudgetStore(repo, "p1")
        store.load()
        assertTrue(store.state.value.needsInit)

        store.setInitialBudget(3000.0)

        val state = store.state.value
        assertNotNull(state.summary)
        // 3000 + 100 - 20
        assertEquals(3080.0, state.balance, 1e-9)
        assertEquals(3000.0, state.initialBudget, 1e-9)
        assertEquals(3, state.visibleRecords.size)
        assertTrue(state.visibleRecords.any { it.data.type == RecordType.CarryoverIncome })
        assertTrue(state.visibleRecords.any { it.data.type == RecordType.AdvanceDeduct })
    }

    @Test
    fun `记支出扣余额并可撤回`() = runTest {
        val repo = FakeRepository()
        val store = BudgetStore(repo, "p1")
        store.load()
        store.setInitialBudget(1000.0)

        store.addRecord(RecordType.Expense, 250.0, "午饭")
        assertEquals(750.0, store.state.value.balance, 1e-9)

        val expense = store.state.value.visibleRecords.first { it.data.type == RecordType.Expense }
        store.revertRecord(expense)
        assertEquals(1000.0, store.state.value.balance, 1e-9)
        assertNull(store.state.value.visibleRecords.firstOrNull { it.id == expense.id })
    }

    @Test
    fun `省钱与结转同时影响余额和汇总字段`() = runTest {
        val repo = FakeRepository()
        val store = BudgetStore(repo, "p1")
        store.load()
        store.setInitialBudget(1000.0)

        store.addRecord(RecordType.Save, 100.0)
        assertEquals(900.0, store.state.value.balance, 1e-9)
        assertEquals(100.0, store.state.value.savedAmount, 1e-9)

        store.addRecord(RecordType.Carryover, 50.0)
        assertEquals(850.0, store.state.value.balance, 1e-9)
        assertEquals(50.0, store.state.value.summary!!.data.carryOverToNext, 1e-9)
    }

    @Test
    fun `出行全流程`() = runTest {
        val repo = FakeRepository()
        val store = BudgetStore(repo, "p1")
        store.load()
        store.setInitialBudget(1000.0)

        store.startTrip(300.0)
        assertEquals(700.0, store.state.value.balance, 1e-9)
        assertEquals(300.0, store.state.value.tripBalance, 1e-9)

        store.addTripRecord(income = false, amount = 120.0, note = "打车")
        assertEquals(180.0, store.state.value.tripBalance, 1e-9)
        // 出行消费不动主账本余额，只进统计
        assertEquals(700.0, store.state.value.balance, 1e-9)

        store.addTripBudget(50.0)
        assertEquals(650.0, store.state.value.balance, 1e-9)
        assertEquals(230.0, store.state.value.tripBalance, 1e-9)

        store.endTrip()
        assertNull(store.state.value.activeTrip)
        // 700 + 230
        assertEquals(880.0, store.state.value.balance, 1e-9)
    }

    @Test
    fun `未出行的空行程会退回全部金额且不留记录`() = runTest {
        val repo = FakeRepository()
        val store = BudgetStore(repo, "p1")
        store.load()
        store.setInitialBudget(1000.0)
        store.startTrip(300.0)
        store.endTrip()

        assertEquals(1000.0, store.state.value.balance, 1e-9)
        assertTrue(store.state.value.visibleRecords.none { it.data.type == RecordType.TripStart })
        assertTrue(store.state.value.visibleRecords.none { it.data.type == RecordType.TripEnd })
    }

    @Test
    fun `修改初始预算会按差额调整余额`() = runTest {
        val repo = FakeRepository()
        val store = BudgetStore(repo, "p1")
        store.load()
        store.setInitialBudget(1000.0)
        store.updateInitialBudget(1500.0)

        assertEquals(1500.0, store.state.value.balance, 1e-9)
        assertTrue(store.state.value.visibleRecords.any { it.data.type == RecordType.Adjust })
    }

    @Test
    fun `清空记录后余额回到初始预算`() = runTest {
        val repo = FakeRepository()
        val store = BudgetStore(repo, "p1")
        store.load()
        store.setInitialBudget(1000.0)
        store.addRecord(RecordType.Expense, 400.0)
        store.addRecord(RecordType.Save, 100.0)

        store.clearAllRecords()
        assertEquals(1000.0, store.state.value.balance, 1e-9)
        assertEquals(0.0, store.state.value.savedAmount, 1e-9)
        assertEquals(1, store.state.value.visibleRecords.size)
    }

    @Test
    fun `搜索按关键词与金额过滤`() = runTest {
        val repo = FakeRepository()
        val store = BudgetStore(repo, "p1")
        store.load()
        store.setInitialBudget(1000.0)
        store.addRecord(RecordType.Expense, 30.0, "咖啡")
        store.addRecord(RecordType.Expense, 300.0, "超市采购")
        store.addRecord(RecordType.Income, 50.0, "红包")

        val coffee = store.search(BudgetStore.SearchQuery(keyword = "咖啡"))
        assertEquals(1, coffee.size)

        val big = store.search(BudgetStore.SearchQuery(amountMin = 100.0))
        assertEquals(2, big.size) // 300 支出 + 1000 初始预算
    }
}
