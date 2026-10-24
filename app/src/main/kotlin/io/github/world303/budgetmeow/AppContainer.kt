package io.github.world303.budgetmeow

import android.content.Context
import io.github.world303.budgetmeow.core.repo.BudgetRepository
import io.github.world303.budgetmeow.core.repo.LocalBudgetRepository
import io.github.world303.budgetmeow.core.storage.LocalStorage
import io.github.world303.budgetmeow.core.store.BudgetStore
import io.github.world303.budgetmeow.core.store.SettingsStore
import java.io.File

/**
 * 手写依赖容器。这个项目只有一个 Application 作用域，引 Hilt/Koin 属于杀鸡用牛刀，
 * 而且多一层注解处理就多一层 CI 变慢和踩坑的地方。
 */
class AppContainer(context: Context) {

    val storage: LocalStorage = LocalStorage(File(context.filesDir, "meow"))

    val settings: SettingsStore = SettingsStore(storage)

    private val repository: BudgetRepository = LocalBudgetRepository(storage)

    private val budgetStores = mutableMapOf<String, BudgetStore>()

    fun budgetFor(profileId: String): BudgetStore =
        budgetStores.getOrPut(profileId) { BudgetStore(repository, profileId) }

    fun dropBudget(profileId: String) {
        budgetStores.remove(profileId)
    }
}
