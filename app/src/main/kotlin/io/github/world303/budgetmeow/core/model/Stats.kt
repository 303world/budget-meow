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
 * 月度统计口径（相对网页版修正过）。
 *
 * 网页版把「出行划转」也算进收支：开启出行 300 记一笔支出，行程里花 120 又记一笔支出，
 * 于是这个月支出变成 420，而实际只花了 120；结束出行的余额退回又记成收入。
 * 预支、结转同理——它们是预算的搬运，不是当月的赚与花。
 *
 * 现在只统计真正的收支：
 * - 收入：income / trip_income
 * - 支出：expense / trip_expense
 * - 省钱：save
 * - 出行次数：trip_end 的条数（含手动校准）
 * 其余（init / adjust / debug / advance / advance_deduct / carryover / carryover_income /
 * trip_start / trip_add / trip_end）都视为预算搬运，不进收支。
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
        when (type) {
            RecordType.Income, RecordType.TripIncome -> income += amount
            RecordType.Expense -> expense += amount
            RecordType.TripExpense -> {
                expense += amount
                tripExpense += amount
            }
            RecordType.Save -> saved += amount
            RecordType.TripEnd -> tripCount++
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
