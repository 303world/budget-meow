package io.github.world303.budgetmeow.core.store

import io.github.world303.budgetmeow.core.model.BudgetDataset
import io.github.world303.budgetmeow.core.model.BudgetRecord
import io.github.world303.budgetmeow.core.model.BudgetRecordData
import io.github.world303.budgetmeow.core.model.BudgetSummary
import io.github.world303.budgetmeow.core.model.BudgetSummaryData
import io.github.world303.budgetmeow.core.model.Ids
import io.github.world303.budgetmeow.core.model.MonthKey
import io.github.world303.budgetmeow.core.model.MonthPoint
import io.github.world303.budgetmeow.core.model.MonthStats
import io.github.world303.budgetmeow.core.model.RecordType
import io.github.world303.budgetmeow.core.model.TimeUtil
import io.github.world303.budgetmeow.core.model.TripRecord
import io.github.world303.budgetmeow.core.model.TripRecordData
import io.github.world303.budgetmeow.core.model.TripRecordType
import io.github.world303.budgetmeow.core.model.TripStatus
import io.github.world303.budgetmeow.core.model.TripSummary
import io.github.world303.budgetmeow.core.model.TripSummaryData
import io.github.world303.budgetmeow.core.model.YearStats
import io.github.world303.budgetmeow.core.model.computeStats
import io.github.world303.budgetmeow.core.model.formatMoney
import io.github.world303.budgetmeow.core.model.round2
import io.github.world303.budgetmeow.core.repo.BudgetRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import kotlin.math.abs

data class BudgetUiState(
    val loading: Boolean = true,
    val month: String = MonthKey.now(),
    val summary: BudgetSummary? = null,
    val activeTrip: TripSummary? = null,
    val visibleRecords: List<BudgetRecord> = emptyList(),
    val totalRecords: Int = 0,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
) {
    val needsInit: Boolean get() = !loading && summary == null
    val balance: Double get() = summary?.data?.currentBalance ?: 0.0
    val initialBudget: Double get() = summary?.data?.initialBudget ?: 0.0
    val savedAmount: Double get() = summary?.data?.savedAmount ?: 0.0
    val tripBalance: Double get() = activeTrip?.data?.balance ?: 0.0
}

/**
 * 记账业务核心，逐条移植 Web 端 BudgetStore。
 *
 * 所有写操作走同一把锁：这是单机 App，并发只可能来自快速连点，
 * 顺序化之后就不会出现"余额算了两遍"这类问题（Web 端当年为此加过操作锁）。
 */
class BudgetStore(
    private val repository: BudgetRepository,
    private val profileId: String,
) {
    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_SEARCH_RECORDS = 1000
        const val TRIP_TAG_FORMAT = "［%s］"
    }

    private val mutex = Mutex()
    private val currentMonth: String = MonthKey.now()

    private var dataset: BudgetDataset = BudgetDataset()
    private var loadedPages = 1

    private val _state = MutableStateFlow(BudgetUiState(month = currentMonth))
    val state: StateFlow<BudgetUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val month: String get() = currentMonth

    private suspend fun message(text: String, kind: MessageKind = MessageKind.Info) {
        _messages.emit(UiMessage(text, kind))
    }

    // ---------------------------------------------------------------- 读取

    suspend fun load() = mutex.withLock {
        _state.value = _state.value.copy(loading = true)
        dataset = repository.load(profileId)
        loadedPages = 1
        publishRecords()
    }

    suspend fun reload() = mutex.withLock {
        dataset = repository.load(profileId)
        publishRecords()
    }

    suspend fun loadMore() {
        if (!_state.value.hasMore || _state.value.loadingMore) return
        mutex.withLock {
            _state.value = _state.value.copy(loadingMore = true)
            loadedPages += 1
            publishRecords()
        }
    }

    private fun publishRecords() {
        val summary = dataset.summaryOf(currentMonth)
        val all = dataset.recordsOf(currentMonth)
        val visible = all.take(loadedPages * PAGE_SIZE)
        _state.value = _state.value.copy(
            loading = false,
            loadingMore = false,
            month = currentMonth,
            summary = summary,
            activeTrip = dataset.activeTrip(),
            visibleRecords = visible,
            totalRecords = all.size,
            hasMore = all.size > visible.size,
        )
    }

    private suspend fun persist() {
        repository.save(profileId, dataset)
        publishRecords()
    }

    // ---------------------------------------------------------------- 初始化预算

    suspend fun setInitialBudget(amount: Double) = mutex.withLock {
        if (amount <= 0) {
            message("预算金额必须大于 0", MessageKind.Warning)
            return@withLock
        }
        if (dataset.summaryOf(currentMonth) != null) {
            message("本月预算已初始化", MessageKind.Info)
            return@withLock
        }
        val lastMonth = MonthKey.previous(currentMonth)
        val last = dataset.summaryOf(lastMonth)?.data
        val carryOver = last?.carryOverToNext ?: 0.0
        val advance = last?.advanceForNext ?: 0.0
        val adjusted = (amount + carryOver - advance).round2()

        val now = TimeUtil.nowIso()
        val summary = BudgetSummary(
            id = Ids.newId(),
            data = BudgetSummaryData(
                userId = profileId,
                month = currentMonth,
                initialBudget = amount,
                currentBalance = adjusted,
                lastUpdatedAt = now,
            ),
        )
        val newRecords = mutableListOf(
            newRecord(RecordType.Init, amount, "初始预算", now),
        )
        if (carryOver > 0) newRecords += newRecord(RecordType.CarryoverIncome, carryOver, "上月结转", now)
        if (advance > 0) newRecords += newRecord(RecordType.AdvanceDeduct, -advance, "上月预支扣除", now)

        dataset = dataset.copy(
            summaries = dataset.summaries + summary,
            records = dataset.records + newRecords,
        )
        persist()
    }

    // ---------------------------------------------------------------- 记账

    suspend fun addRecord(type: RecordType, amount: Double, note: String = "") = mutex.withLock {
        val summary = dataset.summaryOf(currentMonth)
        if (summary == null) {
            message("请先初始化本月预算", MessageKind.Warning)
            return@withLock
        }
        if (amount <= 0) {
            message("金额必须大于 0", MessageKind.Warning)
            return@withLock
        }
        val data = summary.data
        var balanceChange = 0.0
        var savedChange = 0.0
        var carryChange = 0.0
        var advanceChange = 0.0
        var recordAmount = amount
        var recordNote = note

        when (type) {
            RecordType.Income -> balanceChange = amount
            RecordType.Expense -> {
                balanceChange = -amount
                recordAmount = -amount
            }
            RecordType.Save -> {
                balanceChange = -amount
                savedChange = amount
                recordNote = note.ifBlank { "省钱" }
            }
            RecordType.Carryover -> {
                balanceChange = -amount
                carryChange = amount
                recordAmount = -amount
                recordNote = note.ifBlank { "结转下月" }
            }
            RecordType.Advance -> {
                balanceChange = amount
                advanceChange = amount
                recordNote = note.ifBlank { "预支下月" }
            }
            else -> {
                message("不支持的记账类型", MessageKind.Error)
                return@withLock
            }
        }

        val updated = data.copy(
            currentBalance = (data.currentBalance + balanceChange).round2(),
            savedAmount = (data.savedAmount + savedChange).round2(),
            carryOverToNext = (data.carryOverToNext + carryChange).round2(),
            advanceForNext = (data.advanceForNext + advanceChange).round2(),
            lastUpdatedAt = TimeUtil.nowIso(),
        )
        dataset = dataset.copy(
            summaries = dataset.summaries.map { if (it.id == summary.id) summary.copy(data = updated) else it },
            records = dataset.records + newRecord(type, recordAmount, recordNote, TimeUtil.nowIso()),
        )
        persist()
        message("记账成功", MessageKind.Success)
    }

    private fun newRecord(type: RecordType, amount: Double, note: String, createdAt: String): BudgetRecord =
        BudgetRecord(
            id = Ids.newId(),
            data = BudgetRecordData(
                userId = profileId,
                month = MonthKey.ofInstant(java.time.Instant.parse(createdAt)),
                type = type,
                amount = amount,
                note = note,
                createdAt = createdAt,
            ),
        )

    // ---------------------------------------------------------------- 出行

    suspend fun startTrip(amount: Double) = mutex.withLock {
        val summary = dataset.summaryOf(currentMonth)
        if (summary == null) {
            message("请先初始化本月预算", MessageKind.Warning)
            return@withLock
        }
        if (amount <= 0) {
            message("出行预算必须大于 0", MessageKind.Warning)
            return@withLock
        }
        if (amount > summary.data.currentBalance) {
            message("出行预算不能超过剩余预算", MessageKind.Warning)
            return@withLock
        }
        if (dataset.activeTrip() != null) {
            message("已有进行中的出行", MessageKind.Warning)
            return@withLock
        }
        val now = TimeUtil.nowIso()
        val startRecord = newRecord(RecordType.TripStart, -amount, "开启出行", now)
        val trip = TripSummary(
            id = Ids.newId(),
            data = TripSummaryData(
                userId = profileId,
                status = TripStatus.Active,
                balance = amount,
                initialAmount = amount,
                startRecordId = startRecord.id,
                createdAt = now,
            ),
        )
        dataset = dataset.copy(
            summaries = dataset.summaries.map {
                if (it.id == summary.id) {
                    it.copy(
                        data = it.data.copy(
                            currentBalance = (it.data.currentBalance - amount).round2(),
                            lastUpdatedAt = now,
                        ),
                    )
                } else {
                    it
                }
            },
            records = dataset.records + startRecord,
            trips = dataset.trips + trip,
        )
        persist()
        message("出行已开启", MessageKind.Success)
    }

    suspend fun addTripBudget(amount: Double) = mutex.withLock {
        val summary = dataset.summaryOf(currentMonth)
        val trip = dataset.activeTrip()
        if (summary == null || trip == null) {
            message("当前没有进行中的出行", MessageKind.Warning)
            return@withLock
        }
        if (amount <= 0) {
            message("金额必须大于 0", MessageKind.Warning)
            return@withLock
        }
        if (amount > summary.data.currentBalance) {
            message("增加金额不能超过剩余预算", MessageKind.Warning)
            return@withLock
        }
        val now = TimeUtil.nowIso()
        val prefix = tripPrefix(trip)
        dataset = dataset.copy(
            summaries = dataset.summaries.map {
                if (it.id == summary.id) {
                    it.copy(
                        data = it.data.copy(
                            currentBalance = (it.data.currentBalance - amount).round2(),
                            lastUpdatedAt = now,
                        ),
                    )
                } else {
                    it
                }
            },
            records = dataset.records + newRecord(
                RecordType.TripAdd, -amount, TRIP_TAG_FORMAT.format(prefix) + "增加出行预算", now,
            ),
            trips = dataset.trips.map {
                if (it.id == trip.id) {
                    it.copy(
                        data = it.data.copy(
                            balance = (it.data.balance + amount).round2(),
                            updatedAt = now,
                        ),
                    )
                } else {
                    it
                }
            },
            tripRecords = dataset.tripRecords + TripRecord(
                id = Ids.newId(),
                data = TripRecordData(
                    userId = profileId,
                    tripId = trip.id,
                    type = TripRecordType.Add,
                    amount = amount,
                    note = "增加预算",
                    createdAt = now,
                ),
            ),
        )
        persist()
        message("出行预算已增加", MessageKind.Success)
    }

    suspend fun addTripRecord(income: Boolean, amount: Double, note: String = "") = mutex.withLock {
        val trip = dataset.activeTrip()
        if (trip == null) {
            message("当前没有进行中的出行", MessageKind.Warning)
            return@withLock
        }
        if (amount <= 0) {
            message("金额必须大于 0", MessageKind.Warning)
            return@withLock
        }
        val now = TimeUtil.nowIso()
        val prefix = tripPrefix(trip)
        val displayNote = note.ifBlank { if (income) "出行收入" else "出行支出" }
        val signed = if (income) amount else -amount
        dataset = dataset.copy(
            records = dataset.records + newRecord(
                if (income) RecordType.TripIncome else RecordType.TripExpense,
                signed,
                TRIP_TAG_FORMAT.format(prefix) + displayNote,
                now,
            ),
            trips = dataset.trips.map {
                if (it.id == trip.id) {
                    it.copy(
                        data = it.data.copy(
                            balance = (it.data.balance + signed).round2(),
                            updatedAt = now,
                        ),
                    )
                } else {
                    it
                }
            },
            tripRecords = dataset.tripRecords + TripRecord(
                id = Ids.newId(),
                data = TripRecordData(
                    userId = profileId,
                    tripId = trip.id,
                    type = if (income) TripRecordType.Income else TripRecordType.Expense,
                    amount = signed,
                    note = note,
                    createdAt = now,
                ),
            ),
        )
        persist()
        message("记账成功", MessageKind.Success)
    }

    suspend fun endTrip() = mutex.withLock {
        val summary = dataset.summaryOf(currentMonth)
        val trip = dataset.activeTrip()
        if (summary == null || trip == null) {
            message("当前没有进行中的出行", MessageKind.Warning)
            return@withLock
        }
        val remaining = trip.data.balance
        val initial = trip.data.initialAmount
        val prefix = tripPrefix(trip)
        val tag = TRIP_TAG_FORMAT.format(prefix)
        val tripRecords = dataset.tripRecords.filter { it.data.tripId == trip.id }
        val hasActivity = tripRecords.any {
            it.data.type == TripRecordType.Income || it.data.type == TripRecordType.Expense
        }
        val isEmptyTrip = abs(remaining - initial) < 0.01 && !hasActivity
        val now = TimeUtil.nowIso()

        if (isEmptyTrip) {
            // 没真正出行过：清掉开启记录与相关明细，余额原样退回
            val recordIdsToDelete = mutableSetOf<String>()
            trip.data.startRecordId?.let { recordIdsToDelete += it }
            dataset.records
                .filter { it.data.type == RecordType.TripAdd && it.data.note.contains(tag) }
                .forEach { recordIdsToDelete += it.id }
            dataset = dataset.copy(
                summaries = dataset.summaries.map {
                    if (it.id == summary.id) {
                        it.copy(
                            data = it.data.copy(
                                currentBalance = (it.data.currentBalance + remaining).round2(),
                                lastUpdatedAt = now,
                            ),
                        )
                    } else {
                        it
                    }
                },
                records = dataset.records.map {
                    if (it.id in recordIdsToDelete) {
                        it.copy(data = it.data.copy(deleted = true, updatedAt = now))
                    } else {
                        it
                    }
                },
                trips = dataset.trips.map {
                    if (it.id == trip.id) it.copy(data = it.data.copy(deleted = true, updatedAt = now)) else it
                },
                tripRecords = dataset.tripRecords.map {
                    if (it.data.tripId == trip.id) {
                        it.copy(data = it.data.copy(deleted = true, updatedAt = now))
                    } else {
                        it
                    }
                },
            )
            persist()
            message("本次未出行，已清理相关记录", MessageKind.Info)
            return@withLock
        }

        dataset = dataset.copy(
            summaries = dataset.summaries.map {
                if (it.id == summary.id) {
                    it.copy(
                        data = it.data.copy(
                            currentBalance = (it.data.currentBalance + remaining).round2(),
                            lastUpdatedAt = now,
                        ),
                    )
                } else {
                    it
                }
            },
            records = dataset.records + newRecord(
                RecordType.TripEnd, remaining, tag + "结转", now,
            ),
            trips = dataset.trips.map {
                if (it.id == trip.id) {
                    it.copy(
                        data = it.data.copy(
                            status = TripStatus.Ended,
                            balance = 0.0,
                            endedAt = now,
                            updatedAt = now,
                        ),
                    )
                } else {
                    it
                }
            },
        )
        persist()
        message("出行已结束，余额已结转", MessageKind.Success)
    }

    suspend fun updateTripName(name: String) = mutex.withLock {
        val trip = dataset.activeTrip() ?: return@withLock
        val oldPrefix = tripPrefix(trip)
        val newName = name.take(10)
        val newPrefix = newName.ifBlank {
            java.time.Instant.parse(trip.data.createdAt)
                .atZone(java.time.ZoneId.systemDefault())
                .monthValue.toString() + "月出行"
        }
        val oldTag = TRIP_TAG_FORMAT.format(oldPrefix)
        val newTag = TRIP_TAG_FORMAT.format(newPrefix)

        dataset = dataset.copy(
            trips = dataset.trips.map {
                if (it.id == trip.id) {
                    it.copy(data = it.data.copy(tripName = newName, updatedAt = TimeUtil.nowIso()))
                } else {
                    it
                }
            },
            records = if (oldTag == newTag) {
                dataset.records
            } else {
                dataset.records.map { record ->
                    if (record.data.type.isTripRelated && record.data.note.contains(oldTag)) {
                        record.copy(data = record.data.copy(note = record.data.note.replace(oldTag, newTag)))
                    } else {
                        record
                    }
                }
            },
        )
        persist()
        message("出行名称已更新", MessageKind.Success)
    }

    private fun tripPrefix(trip: TripSummary): String {
        if (trip.data.tripName.isNotBlank()) return trip.data.tripName
        val month = runCatching {
            java.time.Instant.parse(trip.data.createdAt).atZone(java.time.ZoneId.systemDefault()).monthValue
        }.getOrDefault(LocalDate.now().monthValue)
        return "${month}月出行"
    }

    // ---------------------------------------------------------------- 撤回 / 删除

    fun isRevertable(record: BudgetRecord): Boolean {
        val type = record.data.type
        if (type.isStructural) return false
        if (type == RecordType.TripIncome || type == RecordType.TripExpense || type == RecordType.TripAdd) {
            return dataset.activeTrip() != null
        }
        return true
    }

    suspend fun revertRecord(record: BudgetRecord) = mutex.withLock {
        val summary = dataset.summaryOf(currentMonth) ?: return@withLock
        if (!isRevertable(record)) {
            message("该记录不支持撤回", MessageKind.Warning)
            return@withLock
        }
        var data = summary.data
        var trips = dataset.trips
        var tripRecords = dataset.tripRecords
        val amount = record.data.amount
        val activeTrip = dataset.activeTrip()
        val stamp = TimeUtil.nowIso()

        when (record.data.type) {
            RecordType.Income, RecordType.Expense -> data = data.copy(
                currentBalance = (data.currentBalance - amount).round2(),
            )
            RecordType.Save -> data = data.copy(
                currentBalance = (data.currentBalance + abs(amount)).round2(),
                savedAmount = (data.savedAmount - abs(amount)).round2(),
            )
            RecordType.Carryover -> data = data.copy(
                currentBalance = (data.currentBalance - amount).round2(),
                carryOverToNext = (data.carryOverToNext - abs(amount)).coerceAtLeast(0.0).round2(),
            )
            RecordType.Advance -> data = data.copy(
                currentBalance = (data.currentBalance - amount).round2(),
                advanceForNext = (data.advanceForNext - abs(amount)).coerceAtLeast(0.0).round2(),
            )
            RecordType.TripAdd -> if (activeTrip != null) {
                data = data.copy(currentBalance = (data.currentBalance + abs(amount)).round2())
                trips = trips.map {
                    if (it.id == activeTrip.id) {
                        it.copy(data = it.data.copy(balance = (it.data.balance - abs(amount)).round2()))
                    } else {
                        it
                    }
                }
                tripRecords = tripRecords.map {
                    if (it.data.tripId == activeTrip.id &&
                        it.data.type == TripRecordType.Add &&
                        it.data.createdAt == record.data.createdAt
                    ) {
                        it.copy(data = it.data.copy(deleted = true, updatedAt = stamp))
                    } else {
                        it
                    }
                }
            }
            RecordType.TripIncome, RecordType.TripExpense -> if (activeTrip != null) {
                val delta = if (record.data.type == RecordType.TripIncome) -abs(amount) else abs(amount)
                trips = trips.map {
                    if (it.id == activeTrip.id) {
                        it.copy(data = it.data.copy(balance = (it.data.balance + delta).round2()))
                    } else {
                        it
                    }
                }
                val targetType =
                    if (record.data.type == RecordType.TripIncome) TripRecordType.Income else TripRecordType.Expense
                tripRecords = tripRecords.map {
                    if (it.data.tripId == activeTrip.id &&
                        it.data.type == targetType &&
                        it.data.createdAt == record.data.createdAt
                    ) {
                        it.copy(data = it.data.copy(deleted = true, updatedAt = stamp))
                    } else {
                        it
                    }
                }
            }
            else -> Unit
        }

        val now = TimeUtil.nowIso()
        data = data.copy(lastUpdatedAt = now)
        dataset = dataset.copy(
            summaries = dataset.summaries.map { if (it.id == summary.id) it.copy(data = data) else it },
            records = dataset.records.map {
                if (it.id == record.id) it.copy(data = it.data.copy(deleted = true, updatedAt = now)) else it
            },
            trips = trips,
            tripRecords = tripRecords,
        )
        persist()
        message("已撤回", MessageKind.Success)
    }

    suspend fun batchDelete(records: List<BudgetRecord>) = mutex.withLock {
        if (records.isEmpty()) return@withLock
        val ids = records.map { it.id }.toSet()
        val now = TimeUtil.nowIso()
        dataset = dataset.copy(
            records = dataset.records.map {
                if (it.id in ids) it.copy(data = it.data.copy(deleted = true, updatedAt = now)) else it
            },
        )
        persist()
        message("已删除 ${records.size} 条记录", MessageKind.Success)
    }

    suspend fun batchRevert(records: List<BudgetRecord>) = mutex.withLock {
        val summary = dataset.summaryOf(currentMonth) ?: return@withLock
        var data = summary.data
        var trips = dataset.trips
        var tripRecords = dataset.tripRecords
        val deletedIds = mutableSetOf<String>()
        val loopStamp = TimeUtil.nowIso()
        var success = 0
        var failed = 0

        for (record in records) {
            if (!isRevertable(record)) {
                failed++
                continue
            }
            val activeTrip = trips.firstOrNull { it.data.status == TripStatus.Active }
            val amount = record.data.amount
            when (record.data.type) {
                RecordType.Income, RecordType.Expense -> data = data.copy(
                    currentBalance = (data.currentBalance - amount).round2(),
                )
                RecordType.Save -> data = data.copy(
                    currentBalance = (data.currentBalance + abs(amount)).round2(),
                    savedAmount = (data.savedAmount - abs(amount)).round2(),
                )
                RecordType.Carryover -> data = data.copy(
                    currentBalance = (data.currentBalance - amount).round2(),
                    carryOverToNext = (data.carryOverToNext - abs(amount)).coerceAtLeast(0.0).round2(),
                )
                RecordType.Advance -> data = data.copy(
                    currentBalance = (data.currentBalance - amount).round2(),
                    advanceForNext = (data.advanceForNext - abs(amount)).coerceAtLeast(0.0).round2(),
                )
                RecordType.TripAdd -> if (activeTrip != null) {
                    data = data.copy(currentBalance = (data.currentBalance + abs(amount)).round2())
                    trips = trips.map {
                        if (it.id == activeTrip.id) {
                            it.copy(data = it.data.copy(balance = (it.data.balance - abs(amount)).round2()))
                        } else {
                            it
                        }
                    }
                    tripRecords = tripRecords.map {
                        if (it.data.tripId == activeTrip.id &&
                            it.data.type == TripRecordType.Add &&
                            it.data.createdAt == record.data.createdAt
                        ) {
                            it.copy(data = it.data.copy(deleted = true, updatedAt = loopStamp))
                        } else {
                            it
                        }
                    }
                }
                RecordType.TripIncome, RecordType.TripExpense -> if (activeTrip != null) {
                    val delta = if (record.data.type == RecordType.TripIncome) -abs(amount) else abs(amount)
                    trips = trips.map {
                        if (it.id == activeTrip.id) {
                            it.copy(data = it.data.copy(balance = (it.data.balance + delta).round2()))
                        } else {
                            it
                        }
                    }
                    val targetType =
                        if (record.data.type == RecordType.TripIncome) TripRecordType.Income else TripRecordType.Expense
                    tripRecords = tripRecords.map {
                        if (it.data.tripId == activeTrip.id &&
                            it.data.type == targetType &&
                            it.data.createdAt == record.data.createdAt
                        ) {
                            it.copy(data = it.data.copy(deleted = true, updatedAt = loopStamp))
                        } else {
                            it
                        }
                    }
                }
                else -> Unit
            }
            deletedIds += record.id
            success++
        }

        val now = TimeUtil.nowIso()
        data = data.copy(lastUpdatedAt = now)
        dataset = dataset.copy(
            summaries = dataset.summaries.map { if (it.id == summary.id) it.copy(data = data) else it },
            records = dataset.records.map {
                if (it.id in deletedIds) it.copy(data = it.data.copy(deleted = true, updatedAt = now)) else it
            },
            trips = trips,
            tripRecords = tripRecords,
        )
        persist()
        message(
            if (failed > 0) "撤回完成：成功 $success 条，失败 $failed 条" else "已撤回 $success 条记录",
            if (failed > 0) MessageKind.Warning else MessageKind.Success,
        )
    }

    suspend fun updateRecordNote(record: BudgetRecord, note: String) = mutex.withLock {
        val now = TimeUtil.nowIso()
        dataset = dataset.copy(
            records = dataset.records.map {
                if (it.id == record.id) {
                    it.copy(data = it.data.copy(note = note.take(50), updatedAt = now))
                } else {
                    it
                }
            },
        )
        persist()
        message("备注已更新", MessageKind.Success)
    }

    // ---------------------------------------------------------------- 预算调整

    suspend fun updateInitialBudget(newAmount: Double) = mutex.withLock {
        val summary = dataset.summaryOf(currentMonth) ?: return@withLock
        if (newAmount <= 0) {
            message("预算金额必须大于 0", MessageKind.Warning)
            return@withLock
        }
        val diff = (newAmount - summary.data.initialBudget).round2()
        val now = TimeUtil.nowIso()
        dataset = dataset.copy(
            summaries = dataset.summaries.map {
                if (it.id == summary.id) {
                    it.copy(
                        data = it.data.copy(
                            initialBudget = newAmount,
                            currentBalance = (it.data.currentBalance + diff).round2(),
                            lastUpdatedAt = now,
                        ),
                    )
                } else {
                    it
                }
            },
            records = dataset.records + newRecord(
                RecordType.Adjust,
                diff,
                "修改初始预算: ¥${summary.data.initialBudget.format2()} → ¥${newAmount.format2()}",
                now,
            ),
        )
        persist()
        message("初始预算已修改", MessageKind.Success)
    }

    suspend fun debugUpdateBalance(newBalance: Double?, newSaved: Double?) = mutex.withLock {
        val summary = dataset.summaryOf(currentMonth) ?: return@withLock
        val now = TimeUtil.nowIso()
        val notes = mutableListOf<String>()
        var data = summary.data
        if (newBalance != null) {
            notes += "余额: ¥${data.currentBalance.format2()} → ¥${newBalance.format2()}"
            data = data.copy(currentBalance = newBalance.round2())
        }
        if (newSaved != null) {
            notes += "省钱: ¥${data.savedAmount.format2()} → ¥${newSaved.format2()}"
            data = data.copy(savedAmount = newSaved.round2())
        }
        data = data.copy(lastUpdatedAt = now)
        dataset = dataset.copy(
            summaries = dataset.summaries.map { if (it.id == summary.id) it.copy(data = data) else it },
            records = dataset.records + newRecord(RecordType.Debug, 0.0, "调试修改: " + notes.joinToString(", "), now),
        )
        persist()
        message("调试修改成功", MessageKind.Success)
    }

    suspend fun clearAllRecords() = mutex.withLock {
        // 出行进行中清空会导致主账本与出行子账本对不上，先让用户结束出行
        if (dataset.activeTrip() != null) {
            message("出行进行中，请先结束出行", MessageKind.Warning)
            return@withLock
        }
        val summary = dataset.summaryOf(currentMonth)
        val now = TimeUtil.nowIso()
        val tombstoned = dataset.records.map {
            if (it.data.month == currentMonth && !it.data.deleted) {
                it.copy(data = it.data.copy(deleted = true, updatedAt = now))
            } else {
                it
            }
        }
        if (summary != null) {
            val initial = summary.data.initialBudget
            dataset = dataset.copy(
                summaries = dataset.summaries.map {
                    if (it.id == summary.id) {
                        it.copy(
                            data = it.data.copy(
                                currentBalance = initial,
                                savedAmount = 0.0,
                                carryOverToNext = 0.0,
                                advanceForNext = 0.0,
                                lastUpdatedAt = now,
                            ),
                        )
                    } else {
                        it
                    }
                },
                records = tombstoned + newRecord(RecordType.Init, initial, "初始预算（清空记录后重建）", now),
            )
        } else {
            dataset = dataset.copy(records = tombstoned)
        }
        persist()
        message("历史记录已清空，余额已重置为初始预算", MessageKind.Success)
    }

    suspend fun resetAccount() = mutex.withLock {
        val now = TimeUtil.nowIso()
        dataset = dataset.copy(
            summaries = dataset.summaries.map { it.copy(data = it.data.copy(deleted = true, lastUpdatedAt = now)) },
            records = dataset.records.map { it.copy(data = it.data.copy(deleted = true, updatedAt = now)) },
            trips = dataset.trips.map { it.copy(data = it.data.copy(deleted = true, updatedAt = now)) },
            tripRecords = dataset.tripRecords.map {
                it.copy(data = it.data.copy(deleted = true, updatedAt = now))
            },
        )
        persist()
        message("数据已清空", MessageKind.Success)
    }

    // ---------------------------------------------------------------- 查询与统计

    data class SearchQuery(
        val keyword: String = "",
        val amountMin: Double? = null,
        val amountMax: Double? = null,
        val dateFrom: String? = null,
        val dateTo: String? = null,
    )

    suspend fun search(query: SearchQuery): List<BudgetRecord> = mutex.withLock {
        val hasDateFilter = !query.dateFrom.isNullOrBlank() || !query.dateTo.isNullOrBlank()
        val source = if (hasDateFilter) dataset.records else dataset.recordsOf(currentMonth)
        val limited = source.sortedByDescending { it.data.createdAt }.take(MAX_SEARCH_RECORDS)
        val keyword = query.keyword.trim().lowercase()
        limited.filter { record ->
            val data = record.data
            if (keyword.isNotEmpty() && !data.note.lowercase().contains(keyword)) return@filter false
            val absAmount = abs(data.amount)
            if (query.amountMin != null && absAmount < query.amountMin) return@filter false
            if (query.amountMax != null && absAmount > query.amountMax) return@filter false
            val day = TimeUtil.isoLocalDatePart(data.createdAt)
            if (!query.dateFrom.isNullOrBlank() && day < query.dateFrom) return@filter false
            if (!query.dateTo.isNullOrBlank() && day > query.dateTo) return@filter false
            true
        }
    }

    suspend fun monthStats(month: String): MonthStats = mutex.withLock {
        val records = dataset.records.filter { it.data.month == month }
        var stats = computeStats(records)
        val summary = dataset.summaryOf(month)?.data
        val override = summary?.tripCountOverride
        if (override != null) {
            val calibratedCount = summary.tripEndCountAtCalibration ?: 0.0
            val newTripEnds = stats.tripCount - calibratedCount
            stats = stats.copy(tripCount = (override + newTripEnds.coerceAtLeast(0.0)).toInt())
        }
        stats
    }

    suspend fun yearStats(year: Int): YearStats = mutex.withLock {
        val today = LocalDate.now()
        val lastMonth = if (year == today.year) today.monthValue else 12
        val months = mutableListOf<MonthPoint>()
        var income = 0.0
        var expense = 0.0
        var saved = 0.0
        var tripCount = 0
        for (m in 1..lastMonth) {
            val monthKey = "%04d-%02d".format(year, m)
            var stats = computeStats(dataset.records.filter { it.data.month == monthKey })
            val summary = dataset.summaryOf(monthKey)?.data
            val override = summary?.tripCountOverride
            if (override != null) {
                val calibrated = summary.tripEndCountAtCalibration ?: 0.0
                val newTripEnds = stats.tripCount - calibrated
                stats = stats.copy(tripCount = (override + newTripEnds.coerceAtLeast(0.0)).toInt())
            }
            months += MonthPoint(monthKey, m, stats.income, stats.expense, stats.saved, stats.netExpense)
            income += stats.income
            expense += stats.expense
            saved += stats.saved
            tripCount += stats.tripCount
        }
        YearStats(
            months = months,
            totalIncome = income.round2(),
            totalExpense = expense.round2(),
            totalSaved = saved.round2(),
            tripCount = tripCount,
        )
    }

    suspend fun saveTripCountOverride(month: String, count: Int) = mutex.withLock {
        val summary = dataset.summaryOf(month) ?: return@withLock
        val currentTripEnds = dataset.records.count {
            it.data.month == month && it.data.type == RecordType.TripEnd
        }.toDouble()
        dataset = dataset.copy(
            summaries = dataset.summaries.map {
                if (it.id == summary.id) {
                    it.copy(
                        data = it.data.copy(
                            tripCountOverride = count.toDouble(),
                            tripEndCountAtCalibration = currentTripEnds,
                        ),
                    )
                } else {
                    it
                }
            },
        )
        persist()
        message("出行次数已校准", MessageKind.Success)
    }

    // ---------------------------------------------------------------- 跨月查看

    /** 指定月份的全部流水（含更早的月份，用于历史页翻月） */
    suspend fun recordsOfMonth(month: String): List<BudgetRecord> = mutex.withLock {
        dataset.recordsOf(month)
    }

    suspend fun summaryOfMonth(month: String): BudgetSummary? = mutex.withLock {
        dataset.summaryOf(month)
    }

    /** 有数据的月份，倒序 */
    suspend fun availableMonths(): List<String> = mutex.withLock {
        (dataset.summaries.map { it.data.month } + dataset.records.map { it.data.month })
            .distinct()
            .sortedDescending()
    }

    /** 供备份导出使用：直接给出一份数据快照 */
    suspend fun snapshot(): BudgetDataset = mutex.withLock { dataset }

    suspend fun importDataset(newDataset: BudgetDataset) = mutex.withLock {
        // 另一台设备的 profileId 与本机不同，导入时统一归一到当前档，避免同一个账本里混着两个 userId
        dataset = newDataset.copy(
            summaries = newDataset.summaries.map { it.copy(data = it.data.copy(userId = profileId)) },
            records = newDataset.records.map { it.copy(data = it.data.copy(userId = profileId)) },
            trips = newDataset.trips.map { it.copy(data = it.data.copy(userId = profileId)) },
            tripRecords = newDataset.tripRecords.map { it.copy(data = it.data.copy(userId = profileId)) },
        )
        loadedPages = 1
        persist()
    }

    suspend fun currentProfileId(): String = profileId
}

private fun Double.format2(): String = formatMoney()
