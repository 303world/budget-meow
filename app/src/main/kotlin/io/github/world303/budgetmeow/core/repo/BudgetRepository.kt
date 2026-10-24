package io.github.world303.budgetmeow.core.repo

import io.github.world303.budgetmeow.core.model.BudgetDataset
import io.github.world303.budgetmeow.core.storage.LocalStorage

/**
 * 账目数据仓库。v1 只有本地实现；云端同步进来时新增一个 RemoteBackedRepository 即可，
 * 上层 Store 不需要改动。
 */
interface BudgetRepository {
    suspend fun load(profileId: String): BudgetDataset

    suspend fun save(profileId: String, dataset: BudgetDataset)
}

class LocalBudgetRepository(private val storage: LocalStorage) : BudgetRepository {
    override suspend fun load(profileId: String): BudgetDataset = storage.loadBudget(profileId)

    override suspend fun save(profileId: String, dataset: BudgetDataset) = storage.saveBudget(profileId, dataset)
}
