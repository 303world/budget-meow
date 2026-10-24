package io.github.world303.budgetmeow.core.model

import java.time.Instant

/**
 * 两端账目的合并。
 *
 * 规则很简单，但必须严格：同一条记录两边都有时，取 updatedAt 更新的那个；
 * 只有一边有的直接带上（包括墓碑）。删除在本地是打 deleted 标记，
 * 所以「A 设备删了、B 设备没动」也能正确合并成删除，而不是复活。
 *
 * 汇总按月合并（两台设备可能各自建了同月的 summary，id 不同），出行按 id 合并。
 */
object DatasetMerge {

    fun merge(local: BudgetDataset, remote: BudgetDataset): BudgetDataset = BudgetDataset(
        summaries = mergeSummaries(local.summaries, remote.summaries),
        records = mergeById(
            local = local.records,
            remote = remote.records,
            key = { it.id },
            stamp = { it.data.updatedAt },
        ),
        trips = mergeById(
            local = local.trips,
            remote = remote.trips,
            key = { it.id },
            stamp = { it.data.updatedAt },
        ),
        tripRecords = mergeById(
            local = local.tripRecords,
            remote = remote.tripRecords,
            key = { it.id },
            stamp = { it.data.updatedAt },
        ),
    )

    private fun mergeSummaries(
        local: List<BudgetSummary>,
        remote: List<BudgetSummary>,
    ): List<BudgetSummary> {
        val byMonth = LinkedHashMap<String, BudgetSummary>()
        local.forEach { byMonth[it.data.month] = it }
        remote.forEach { incoming ->
            val existing = byMonth[incoming.data.month]
            byMonth[incoming.data.month] = when {
                existing == null -> incoming
                millis(incoming.data.lastUpdatedAt) >= millis(existing.data.lastUpdatedAt) -> incoming
                else -> existing
            }
        }
        return byMonth.values.toList()
    }

    private fun <T> mergeById(
        local: List<T>,
        remote: List<T>,
        key: (T) -> String,
        stamp: (T) -> String,
    ): List<T> {
        val byId = LinkedHashMap<String, T>()
        local.forEach { byId[key(it)] = it }
        remote.forEach { incoming ->
            val existing = byId[key(incoming)]
            byId[key(incoming)] = when {
                existing == null -> incoming
                millis(stamp(incoming)) >= millis(stamp(existing)) -> incoming
                else -> existing
            }
        }
        return byId.values.toList()
    }

    private fun millis(iso: String): Long =
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
}
