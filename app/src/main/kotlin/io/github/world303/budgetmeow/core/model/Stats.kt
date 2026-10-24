package io.github.world303.budgetmeow.core.model

import kotlin.math.abs

data class MonthStats(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val saved: Double = 0.0,
    val netExpense: Double = 0.0,
    val tripExpense: Double = 0.0,
    val tripCount: Int = 0,
)

/**
 * 月度统计口径，逐条对齐 Web 端 computeStatsFromRecords：
 * - 收入：income / carryover_income / advance / trip_income，以及 trip_end（出行余额结转回主账本）
 * - 支出：expense / trip_expense / carryover / advance_deduct / trip_start / trip_add
 * - 省钱：save
 * - init / adjust / debug 不进收支统计
 * - 每结束一次出行（trip_end）记一次出行次数
 */
fun computeStats(records: List<BudgetRecord>): MonthStats {
    var income = 0.0
    var expense = 0.0
    var saved = 0.0
    var tripExpense = 0.0
    var tripCount = 0

    for (record in records) {
        val type = record.data.type
        val amount = abs(record.data.amount)
        when {
            type == RecordType.TripEnd -> {
                income += amount
                tripCount++
            }
            type.isIncomeLike -> income += amount
            type == RecordType.TripExpense -> {
                expense += amount
                tripExpense += amount
            }
            type.isExpenseLike -> expense += amount
            type == RecordType.Save -> saved += amount
            else -> Unit
        }
    }

    return MonthStats(
        income = income.round2(),
        expense = expense.round2(),
        saved = saved.round2(),
        netExpense = (expense - income).round2(),
        tripExpense = tripExpense.round2(),
        tripCount = tripCount,
    )
}

data class MonthPoint(
    val month: String,
    val monthNumber: Int,
    val income: Double,
    val expense: Double,
    val saved: Double,
    val net: Double,
)

data class YearStats(
    val months: List<MonthPoint>,
    val totalIncome: Double,
    val totalExpense: Double,
    val totalSaved: Double,
    val tripCount: Int,
)
