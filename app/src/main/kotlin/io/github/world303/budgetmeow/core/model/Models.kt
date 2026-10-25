package io.github.world303.budgetmeow.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.random.Random

/**
 * 流水类型。wire 值与 Web 端备份文件保持一致，导入导出才能互认。
 * 新增类型时：这里加枚举 + [RecordType.isIncomeLike] / [RecordType.isExpenseLike] 里归类 + 统计口径。
 */
enum class RecordType(val wire: String) {
    Income("income"),
    Expense("expense"),
    Save("save"),
    Carryover("carryover"),
    Advance("advance"),
    TripStart("trip_start"),
    TripAdd("trip_add"),
    TripEnd("trip_end"),
    TripIncome("trip_income"),
    TripExpense("trip_expense"),

    // 下面这些不由用户直接记账产生，而是初始化 / 改预算 / 调试时写入的调整记录
    Init("init"),
    Adjust("adjust"),
    Debug("debug"),
    CarryoverIncome("carryover_income"),
    AdvanceDeduct("advance_deduct"),
    ;

    /** 计入「收入」统计 */
    val isIncomeLike: Boolean
        get() = this == Income || this == CarryoverIncome || this == Advance ||
            this == TripIncome || this == TripEnd

    /** 计入「支出」统计 */
    val isExpenseLike: Boolean
        get() = this == Expense || this == TripExpense || this == Carryover ||
            this == AdvanceDeduct || this == TripStart || this == TripAdd

    /** 出行相关类型（改名时要同步改备注前缀） */
    val isTripRelated: Boolean
        get() = this == TripStart || this == TripAdd || this == TripEnd ||
            this == TripIncome || this == TripExpense

    /** 不可撤回（结构性记录） */
    val isStructural: Boolean
        get() = this == Init || this == CarryoverIncome || this == AdvanceDeduct ||
            this == TripStart || this == TripEnd

    companion object {
        fun fromWire(wire: String): RecordType? = entries.firstOrNull { it.wire == wire }
    }
}

enum class TripStatus(val wire: String) {
    Active("active"),
    Ended("ended"),
    ;

    companion object {
        fun fromWire(wire: String): TripStatus? = entries.firstOrNull { it.wire == wire }
    }
}

/** 出行子账本里的明细类型，只用来区分加钱还是花钱 */
enum class TripRecordType(val wire: String) {
    Income("income"),
    Expense("expense"),
    Add("add"),
    ;

    companion object {
        fun fromWire(wire: String): TripRecordType? = entries.firstOrNull { it.wire == wire }
    }
}

data class BudgetSummaryData(
    val userId: String,
    val month: String,
    val initialBudget: Double,
    val currentBalance: Double,
    val savedAmount: Double = 0.0,
    val carryOverToNext: Double = 0.0,
    val advanceForNext: Double = 0.0,
    val tripCountOverride: Double? = null,
    val tripEndCountAtCalibration: Double? = null,
    val lastUpdatedAt: String,
    val deleted: Boolean = false,
)

data class BudgetSummary(val id: String, val data: BudgetSummaryData)

data class BudgetRecordData(
    val userId: String,
    val month: String,
    val type: RecordType,
    val amount: Double,
    val note: String,
    val createdAt: String,
    /** 同步用的版本戳；老数据默认等于 createdAt */
    val updatedAt: String = createdAt,
    /** 墓碑：删除不真删，打标记才能同步到其它设备 */
    val deleted: Boolean = false,
)

data class BudgetRecord(val id: String, val data: BudgetRecordData) {
    val absAmount: Double get() = abs(data.amount)
}

data class TripSummaryData(
    val userId: String,
    val status: TripStatus,
    val balance: Double,
    val initialAmount: Double = 0.0,
    val tripName: String = "",
    val startRecordId: String? = null,
    val createdAt: String,
    val endedAt: String? = null,
    val updatedAt: String = createdAt,
    val deleted: Boolean = false,
)

data class TripSummary(val id: String, val data: TripSummaryData)

data class TripRecordData(
    val userId: String,
    val tripId: String,
    val type: TripRecordType,
    val amount: Double,
    val note: String,
    val createdAt: String,
    val updatedAt: String = createdAt,
    val deleted: Boolean = false,
)

data class TripRecord(val id: String, val data: TripRecordData)

/**
 * 一个配置档的全部账目数据。本地存储与云同步都以它为最小单位。
 */
data class BudgetDataset(
    val summaries: List<BudgetSummary> = emptyList(),
    val records: List<BudgetRecord> = emptyList(),
    val trips: List<TripSummary> = emptyList(),
    val tripRecords: List<TripRecord> = emptyList(),
) {
    fun summaryOf(month: String): BudgetSummary? =
        summaries.firstOrNull { it.data.month == month && !it.data.deleted }

    fun recordsOf(month: String): List<BudgetRecord> =
        records.filter { it.data.month == month && !it.data.deleted }
            .sortedByDescending { it.data.createdAt }

    fun activeTrip(): TripSummary? =
        trips.firstOrNull { it.data.status == TripStatus.Active && !it.data.deleted }

    fun tripRecordsOf(tripId: String): List<TripRecord> =
        tripRecords.filter { it.data.tripId == tripId && !it.data.deleted }
            .sortedByDescending { it.data.createdAt }

    /** 参与同步的全部实体（含墓碑），对比与推送都用它 */
    fun liveOrTombstone(): BudgetDataset = this
}

/** 月份键，形如 2026-10，按本地时区计算（与 Web 端 date-fns 的行为一致） */
object MonthKey {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM")

    fun of(date: LocalDate = LocalDate.now()): String = date.format(formatter)

    fun now(): String = of()

    fun previous(month: String): String = YearMonth.parse(month).minusMonths(1).toString()

    fun ofInstant(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        YearMonth.from(instant.atZone(zone)).toString()
}

object TimeUtil {
    fun nowIso(): String = Instant.now().toString()

    fun isoToMillis(iso: String): Long = runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)

    fun isoToLocalDate(iso: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        runCatching { Instant.parse(iso).atZone(zone).toLocalDate() }.getOrNull()

    /**
     * 筛选用的本地日期（yyyy-MM-dd）。
     * Web 端用的是 ISO 字符串前 10 位，那是 UTC 日期：北京时间 8 点前记的账会被算成前一天。
     */
    fun isoLocalDatePart(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
        runCatching { Instant.parse(iso).atZone(zone).toLocalDate().toString() }
            .getOrElse { if (iso.length >= 10) iso.substring(0, 10) else iso }
}

object Ids {
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun newId(): String {
        val ts = System.currentTimeMillis()
        val suffix = (1..9).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")
        return "local_${ts}_$suffix"
    }
}

/** 金额一律保留两位小数，避免浮点误差累积（0.1 + 0.2 这类） */
fun Double.round2(): Double = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP).toDouble()

/** ¥1,234.56 —— 与 Web 端 toLocaleString 的观感对齐 */
fun Double.formatMoney(): String {
    val rounded = BigDecimal.valueOf(this).setScale(2, RoundingMode.HALF_UP)
    val negative = rounded.signum() < 0
    val abs = rounded.abs().toPlainString()
    val intPart = abs.substringBefore('.')
    val decPart = abs.substringAfter('.', "00")
    val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
    return (if (negative) "-" else "") + grouped + "." + decPart
}

/** 带符号的金额，用于流水列表：+12.00 / -12.00 */
fun Double.formatSigned(): String {
    val sign = if (this > 0) "+" else if (this < 0) "-" else ""
    return sign + abs(this).formatMoney()
}


/** 月度预算上限。到这一步，记账软件已经不是最缺的东西了 */
const val MAX_MONTHLY_BUDGET = 10_000_000.0

/** 超上限时的提示，两个入口共用一句，避免口径不一致 */
const val BUDGET_LIMIT_JOKE = "预算上限是一千万 —— 到这个数，你大概已经不需要这款软件了 😹"
