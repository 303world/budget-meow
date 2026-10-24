package io.github.world303.budgetmeow.core.backup

import io.github.world303.budgetmeow.core.model.BudgetDataset
import io.github.world303.budgetmeow.core.model.BudgetRecord
import io.github.world303.budgetmeow.core.model.BudgetRecordData
import io.github.world303.budgetmeow.core.model.BudgetSummary
import io.github.world303.budgetmeow.core.model.BudgetSummaryData
import io.github.world303.budgetmeow.core.model.MonthKey
import io.github.world303.budgetmeow.core.model.RecordType
import io.github.world303.budgetmeow.core.model.TimeUtil
import io.github.world303.budgetmeow.core.model.TripRecord
import io.github.world303.budgetmeow.core.model.TripRecordData
import io.github.world303.budgetmeow.core.model.TripRecordType
import io.github.world303.budgetmeow.core.model.TripStatus
import io.github.world303.budgetmeow.core.model.TripSummary
import io.github.world303.budgetmeow.core.model.TripSummaryData
import io.github.world303.budgetmeow.core.storage.BackupDocDto
import io.github.world303.budgetmeow.core.storage.BackupFileDto
import io.github.world303.budgetmeow.core.storage.BackupMetaDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class BackupException(message: String) : Exception(message)

data class ImportResult(
    val dataset: BudgetDataset,
    val skipped: Int,
    val sourceUserId: String?,
)

/**
 * 备份导入导出。格式沿用 Web 端的 5 张表 + _exportMeta（v1/v2/v3），
 * 所以以前在网页版导出的 json 可以直接导进来，反过来也能导回网页版。
 */
object BackupService {

    private const val CURRENT_VERSION = 3
    private val SUPPORTED_VERSIONS = setOf(1, 2, 3)
    private const val MAX_FILE_CHARS = 50 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun exportJson(dataset: BudgetDataset, userId: String): String {
        val dto = BackupFileDto(
            meta = BackupMetaDto(
                version = CURRENT_VERSION,
                exportedAt = TimeUtil.nowIso(),
                userId = userId,
            ),
            budget_summary = dataset.summaries.map { summary ->
                doc(summary.id) {
                    put("userId", userId)
                    put("month", summary.data.month)
                    put("initialBudget", summary.data.initialBudget)
                    put("currentBalance", summary.data.currentBalance)
                    put("savedAmount", summary.data.savedAmount)
                    put("carryOverToNext", summary.data.carryOverToNext)
                    put("advanceForNext", summary.data.advanceForNext)
                    summary.data.tripCountOverride?.let { put("tripCountOverride", it) }
                    summary.data.tripEndCountAtCalibration?.let { put("tripEndCountAtCalibration", it) }
                    put("lastUpdatedAt", summary.data.lastUpdatedAt)
                }
            },
            budget_record = dataset.records.map { record ->
                doc(record.id) {
                    put("userId", userId)
                    put("month", record.data.month)
                    put("type", record.data.type.wire)
                    put("amount", record.data.amount)
                    put("note", record.data.note)
                    put("createdAt", record.data.createdAt)
                }
            },
            trip_summary = dataset.trips.map { trip ->
                doc(trip.id) {
                    put("userId", userId)
                    put("status", trip.data.status.wire)
                    put("balance", trip.data.balance)
                    put("initialAmount", trip.data.initialAmount)
                    put("tripName", trip.data.tripName)
                    trip.data.startRecordId?.let { put("startRecordId", it) }
                    put("createdAt", trip.data.createdAt)
                    trip.data.endedAt?.let { put("endedAt", it) }
                }
            },
            trip_record = dataset.tripRecords.map { record ->
                doc(record.id) {
                    put("userId", userId)
                    put("tripId", record.data.tripId)
                    put("type", record.data.type.wire)
                    put("amount", record.data.amount)
                    put("note", record.data.note)
                    put("createdAt", record.data.createdAt)
                }
            },
            // 与 Web 端一致：备份里不带昵称与头像，避免把身份信息一起传出去
            user_profile = listOf(
                doc("profile_$userId") {
                    put("userId", userId)
                },
            ),
        )
        return json.encodeToString(BackupFileDto.serializer(), dto)
    }

    private fun doc(id: String, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): BackupDocDto =
        BackupDocDto(wyID = id, data = buildJsonObject { block() })

    fun importJson(text: String, userId: String): ImportResult {
        if (text.length > MAX_FILE_CHARS) {
            throw BackupException("备份文件过大，最大支持 50MB")
        }
        val file = runCatching { json.decodeFromString(BackupFileDto.serializer(), text) }
            .getOrElse { throw BackupException("备份文件格式错误：无法解析 JSON，请确认文件未损坏") }

        val version = file.meta?.version
        if (version != null && version !in SUPPORTED_VERSIONS) {
            throw BackupException(
                "不支持的备份版本 v$version，当前支持 v${SUPPORTED_VERSIONS.sorted().joinToString("/v")}",
            )
        }

        var skipped = 0

        val summaries = file.budget_summary.mapNotNull { d ->
            val data = d.data
            val month = data.str("month")
            val initialBudget = data.num("initialBudget")
            val currentBalance = data.num("currentBalance")
            if (month == null || initialBudget == null || currentBalance == null) {
                skipped++
                return@mapNotNull null
            }
            BudgetSummary(
                id = d.wyID,
                data = BudgetSummaryData(
                    userId = userId,
                    month = month,
                    initialBudget = initialBudget,
                    currentBalance = currentBalance,
                    savedAmount = data.num("savedAmount") ?: 0.0,
                    carryOverToNext = data.num("carryOverToNext") ?: 0.0,
                    advanceForNext = data.num("advanceForNext") ?: 0.0,
                    tripCountOverride = data.num("tripCountOverride"),
                    tripEndCountAtCalibration = data.num("tripEndCountAtCalibration"),
                    lastUpdatedAt = data.str("lastUpdatedAt") ?: TimeUtil.nowIso(),
                ),
            )
        }

        val records = file.budget_record.mapNotNull { d ->
            val data = d.data
            val type = data.str("type")?.let { RecordType.fromWire(it) }
            val amount = data.num("amount")
            val createdAt = data.str("createdAt")
            if (type == null || amount == null || createdAt == null) {
                skipped++
                return@mapNotNull null
            }
            val month = data.str("month") ?: MonthKey.ofInstant(runCatching {
                java.time.Instant.parse(createdAt)
            }.getOrElse { java.time.Instant.now() })
            BudgetRecord(
                id = d.wyID,
                data = BudgetRecordData(
                    userId = userId,
                    month = month,
                    type = type,
                    amount = amount,
                    note = data.str("note") ?: "",
                    createdAt = createdAt,
                ),
            )
        }

        val trips = file.trip_summary.mapNotNull { d ->
            val data = d.data
            val status = data.str("status")?.let { TripStatus.fromWire(it) }
            val balance = data.num("balance")
            val createdAt = data.str("createdAt")
            if (status == null || balance == null || createdAt == null) {
                skipped++
                return@mapNotNull null
            }
            TripSummary(
                id = d.wyID,
                data = TripSummaryData(
                    userId = userId,
                    status = status,
                    balance = balance,
                    initialAmount = data.num("initialAmount") ?: 0.0,
                    tripName = data.str("tripName") ?: "",
                    startRecordId = data.str("startRecordId"),
                    createdAt = createdAt,
                    endedAt = data.str("endedAt"),
                ),
            )
        }

        val tripRecords = file.trip_record.mapNotNull { d ->
            val data = d.data
            val type = data.str("type")?.let { TripRecordType.fromWire(it) }
            val amount = data.num("amount")
            val tripId = data.str("tripId")
            val createdAt = data.str("createdAt")
            if (type == null || amount == null || tripId == null || createdAt == null) {
                skipped++
                return@mapNotNull null
            }
            TripRecord(
                id = d.wyID,
                data = TripRecordData(
                    userId = userId,
                    tripId = tripId,
                    type = type,
                    amount = amount,
                    note = data.str("note") ?: "",
                    createdAt = createdAt,
                ),
            )
        }

        return ImportResult(
            dataset = BudgetDataset(
                summaries = summaries,
                records = records,
                trips = trips,
                tripRecords = tripRecords,
            ),
            skipped = skipped,
            sourceUserId = file.meta?.userId,
        )
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.str(key: String): String? = primitive(key)?.content

    private fun JsonObject.num(key: String): Double? = primitive(key)?.content?.toDoubleOrNull()

    @Suppress("unused")
    private fun JsonObject.bool(key: String): Boolean? = primitive(key)?.content?.toBooleanStrictOrNull()
}
