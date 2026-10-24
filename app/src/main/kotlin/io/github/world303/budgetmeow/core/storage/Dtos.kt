package io.github.world303.budgetmeow.core.storage

import io.github.world303.budgetmeow.core.model.BudgetDataset
import io.github.world303.budgetmeow.core.model.BudgetRecord
import io.github.world303.budgetmeow.core.model.BudgetRecordData
import io.github.world303.budgetmeow.core.model.BudgetSummary
import io.github.world303.budgetmeow.core.model.BudgetSummaryData
import io.github.world303.budgetmeow.core.model.RecordType
import io.github.world303.budgetmeow.core.model.TripRecord
import io.github.world303.budgetmeow.core.model.TripRecordData
import io.github.world303.budgetmeow.core.model.TripRecordType
import io.github.world303.budgetmeow.core.model.TripStatus
import io.github.world303.budgetmeow.core.model.TripSummary
import io.github.world303.budgetmeow.core.model.TripSummaryData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 本地磁盘格式。与 Web 端备份格式刻意分开：
 * - 磁盘格式只服务于本 App，schema 变化时靠 version 做迁移
 * - 备份格式（BackupService）沿用 Web 端的 5 张表结构，保证两边的备份能互相认
 */
@Serializable
data class BudgetFileDto(
    val version: Int = 1,
    val summaries: List<SummaryDto> = emptyList(),
    val records: List<RecordDto> = emptyList(),
    val trips: List<TripDto> = emptyList(),
    val tripRecords: List<TripRecordDto> = emptyList(),
)

@Serializable
data class SummaryDto(
    val id: String,
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
)

@Serializable
data class RecordDto(
    val id: String,
    val userId: String,
    val month: String,
    val type: String,
    val amount: Double,
    val note: String = "",
    val createdAt: String,
)

@Serializable
data class TripDto(
    val id: String,
    val userId: String,
    val status: String,
    val balance: Double,
    val initialAmount: Double = 0.0,
    val tripName: String = "",
    val startRecordId: String? = null,
    val createdAt: String,
    val endedAt: String? = null,
)

@Serializable
data class TripRecordDto(
    val id: String,
    val userId: String,
    val tripId: String,
    val type: String,
    val amount: Double,
    val note: String = "",
    val createdAt: String,
)

@Serializable
data class SettingsDto(
    val theme: String = "system",
    val scheduledDarkMode: Boolean = false,
    val darkStartTime: String = "22:00",
    val darkEndTime: String = "07:00",
    val hasSetScheduleTime: Boolean = false,
    val showSaved: Boolean = true,
    val boldText: Boolean = false,
    val headerDisplayMode: String = "default",
    val headerCustomText: String = "",
    val appTheme: String = "default",
    val glassEffect: Boolean = false,
    val showAvatarAsSettingsIcon: Boolean = false,
    val noKeepLogin: Boolean = false,
    val wallpaperPath: String? = null,
    val fullScreen: Boolean = false,
)

@Serializable
data class ProfileDto(
    val id: String,
    val nickname: String = "账本主人",
    val avatarPath: String? = null,
    val createdAt: String,
    val settings: SettingsDto = SettingsDto(),
)

@Serializable
data class ProfileIndexDto(
    val version: Int = 1,
    val profiles: List<ProfileDto> = emptyList(),
    val activeProfileId: String? = null,
)

/** 备份文件里每条记录的壳，字段名跟 Web 端一致（wyID + data） */
@Serializable
data class BackupDocDto(
    val wyID: String,
    val data: kotlinx.serialization.json.JsonObject,
    @SerialName("_permissions") val permissions: kotlinx.serialization.json.JsonObject? = null,
)

@Serializable
data class BackupMetaDto(
    val version: Int = 3,
    val exportedAt: String,
    val userId: String,
)

@Serializable
data class BackupFileDto(
    // 早期备份（v1）没有 _exportMeta，允许缺失
    @SerialName("_exportMeta") val meta: BackupMetaDto? = null,
    val budget_summary: List<BackupDocDto> = emptyList(),
    val budget_record: List<BackupDocDto> = emptyList(),
    val trip_summary: List<BackupDocDto> = emptyList(),
    val trip_record: List<BackupDocDto> = emptyList(),
    val user_profile: List<BackupDocDto> = emptyList(),
)

fun BudgetDataset.toDto(): BudgetFileDto = BudgetFileDto(
    summaries = summaries.map { it.toDto() },
    records = records.map { it.toDto() },
    trips = trips.map { it.toDto() },
    tripRecords = tripRecords.map { it.toDto() },
)

fun BudgetFileDto.toDomain(): BudgetDataset = BudgetDataset(
    summaries = summaries.mapNotNull { it.toDomain() },
    records = records.mapNotNull { it.toDomain() },
    trips = trips.mapNotNull { it.toDomain() },
    tripRecords = tripRecords.mapNotNull { it.toDomain() },
)

private fun BudgetSummary.toDto() = SummaryDto(
    id = id,
    userId = data.userId,
    month = data.month,
    initialBudget = data.initialBudget,
    currentBalance = data.currentBalance,
    savedAmount = data.savedAmount,
    carryOverToNext = data.carryOverToNext,
    advanceForNext = data.advanceForNext,
    tripCountOverride = data.tripCountOverride,
    tripEndCountAtCalibration = data.tripEndCountAtCalibration,
    lastUpdatedAt = data.lastUpdatedAt,
)

private fun SummaryDto.toDomain() = BudgetSummary(
    id = id,
    data = BudgetSummaryData(
        userId = userId,
        month = month,
        initialBudget = initialBudget,
        currentBalance = currentBalance,
        savedAmount = savedAmount,
        carryOverToNext = carryOverToNext,
        advanceForNext = advanceForNext,
        tripCountOverride = tripCountOverride,
        tripEndCountAtCalibration = tripEndCountAtCalibration,
        lastUpdatedAt = lastUpdatedAt,
    ),
)

private fun BudgetRecord.toDto() = RecordDto(
    id = id,
    userId = data.userId,
    month = data.month,
    type = data.type.wire,
    amount = data.amount,
    note = data.note,
    createdAt = data.createdAt,
)

private fun RecordDto.toDomain(): BudgetRecord? {
    val recordType = RecordType.fromWire(type) ?: return null
    return BudgetRecord(
        id = id,
        data = BudgetRecordData(
            userId = userId,
            month = month,
            type = recordType,
            amount = amount,
            note = note,
            createdAt = createdAt,
        ),
    )
}

private fun TripSummary.toDto() = TripDto(
    id = id,
    userId = data.userId,
    status = data.status.wire,
    balance = data.balance,
    initialAmount = data.initialAmount,
    tripName = data.tripName,
    startRecordId = data.startRecordId,
    createdAt = data.createdAt,
    endedAt = data.endedAt,
)

private fun TripDto.toDomain(): TripSummary? {
    val tripStatus = TripStatus.fromWire(status) ?: return null
    return TripSummary(
        id = id,
        data = TripSummaryData(
            userId = userId,
            status = tripStatus,
            balance = balance,
            initialAmount = initialAmount,
            tripName = tripName,
            startRecordId = startRecordId,
            createdAt = createdAt,
            endedAt = endedAt,
        ),
    )
}

private fun TripRecord.toDto() = TripRecordDto(
    id = id,
    userId = data.userId,
    tripId = data.tripId,
    type = data.type.wire,
    amount = data.amount,
    note = data.note,
    createdAt = data.createdAt,
)

private fun TripRecordDto.toDomain(): TripRecord? {
    val tripRecordType = TripRecordType.fromWire(type) ?: return null
    return TripRecord(
        id = id,
        data = TripRecordData(
            userId = userId,
            tripId = tripId,
            type = tripRecordType,
            amount = amount,
            note = note,
            createdAt = createdAt,
        ),
    )
}
