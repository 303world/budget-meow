package io.github.world303.budgetmeow.core.calc

import io.github.world303.budgetmeow.core.model.RecordType
import io.github.world303.budgetmeow.core.model.formatMoney

/**
 * 二次确认规则，逐条移植 Web 端 getConfirmMessage。
 * 返回 null 表示不需要确认。
 */
object ConfirmRules {

    fun message(
        type: RecordType,
        amount: Double,
        balance: Double,
        initialBudget: Double,
        isTripMode: Boolean,
        tripBalance: Double,
    ): String? {
        val relevantBalance = if (isTripMode) tripBalance else balance
        return when (type) {
            RecordType.Income ->
                if (amount >= 10_000) "单次收入 ¥${amount.formatMoney()}，金额较大，确认记录吗？" else null

            RecordType.Expense -> when {
                amount > relevantBalance ->
                    "支出 ¥${amount.formatMoney()} 超过剩余预算 ¥${relevantBalance.formatMoney()}，确认记录吗？"
                amount >= 10_000 -> "单次支出 ¥${amount.formatMoney()}，金额较大，确认记录吗？"
                else -> null
            }

            RecordType.Save -> when {
                amount > relevantBalance ->
                    "省钱 ¥${amount.formatMoney()} 超过剩余预算 ¥${relevantBalance.formatMoney()}，确认记录吗？"
                amount >= 10_000 -> "省钱 ¥${amount.formatMoney()}，金额较大，确认记录吗？"
                else -> null
            }

            RecordType.Carryover ->
                if (amount >= balance) "结转 ¥${amount.formatMoney()} 将用完剩余预算 ¥${balance.formatMoney()}，确认记录吗？" else null

            RecordType.Advance ->
                if (amount >= initialBudget) "预支 ¥${amount.formatMoney()} 达到或超过本月预算 ¥${initialBudget.formatMoney()}，确认记录吗？" else null

            RecordType.TripStart -> when {
                amount >= balance ->
                    "出行预算 ¥${amount.formatMoney()} 将用完剩余预算 ¥${balance.formatMoney()}，确认开启吗？"
                amount >= 100_000 -> "出行预算 ¥${amount.formatMoney()}，金额较大，确认开启吗？"
                else -> null
            }

            RecordType.TripAdd ->
                if (amount > balance) "增加 ¥${amount.formatMoney()} 超过主账本剩余预算 ¥${balance.formatMoney()}，确认增加吗？" else null

            RecordType.TripIncome ->
                if (amount >= 10_000) "出行收入 ¥${amount.formatMoney()}，金额较大，确认记录吗？" else null

            RecordType.TripExpense -> when {
                amount > tripBalance ->
                    "出行支出 ¥${amount.formatMoney()} 超过出行余额 ¥${tripBalance.formatMoney()}，确认记录吗？"
                amount >= 10_000 -> "出行支出 ¥${amount.formatMoney()}，金额较大，确认记录吗？"
                else -> null
            }

            else -> null
        }
    }

    fun title(type: RecordType): String = when (type) {
        RecordType.Income -> "记收入"
        RecordType.Expense -> "记支出"
        RecordType.Save -> "记省钱"
        RecordType.Carryover -> "记结转（结转到下个月再花）"
        RecordType.Advance -> "预支下月"
        RecordType.TripStart -> "开启出行预算"
        RecordType.TripAdd -> "增加本次出行预算"
        RecordType.TripIncome -> "出行收入"
        RecordType.TripExpense -> "出行支出"
        else -> "记账"
    }

    fun confirmTitle(type: RecordType): String = when (type) {
        RecordType.Income -> "确认记收入"
        RecordType.Expense -> "确认记支出"
        RecordType.Save -> "确认记省钱"
        RecordType.Carryover -> "确认结转"
        RecordType.Advance -> "确认预支"
        RecordType.TripStart -> "确认开启出行"
        RecordType.TripAdd -> "确认增加出行预算"
        RecordType.TripIncome -> "确认出行收入"
        RecordType.TripExpense -> "确认出行支出"
        else -> "确认记账"
    }
}
