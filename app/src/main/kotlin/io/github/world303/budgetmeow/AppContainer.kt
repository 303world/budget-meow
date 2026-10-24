package io.github.world303.budgetmeow

import android.content.Context
import io.github.world303.budgetmeow.core.repo.BudgetRepository
import io.github.world303.budgetmeow.core.repo.LocalBudgetRepository
import io.github.world303.budgetmeow.core.storage.LocalStorage
import io.github.world303.budgetmeow.core.store.BudgetStore
import io.github.world303.budgetmeow.core.store.SettingsStore
import io.github.world303.budgetmeow.core.sync.AuthStore
import io.github.world303.budgetmeow.core.sync.MeowApi
import io.github.world303.budgetmeow.core.sync.SyncController
import java.io.File

/**
 * 手写依赖容器。这个项目只有一个 Application 作用域，引 Hilt/Koin 属于杀鸡用牛刀，
 * 而且多一层注解处理就多一层 CI 变慢和踩坑的地方。
 */
class AppContainer(context: Context) {

    val storage: LocalStorage = LocalStorage(File(context.filesDir, "meow"))

    val settings: SettingsStore = SettingsStore(storage)

    val auth: AuthStore = AuthStore(context)

    val sync: SyncController = SyncController(
        api = MeowApi(),
        authStore = auth,
        storage = storage,
    )

    private val repository: BudgetRepository = LocalBudgetRepository(storage)

    private val budgetStores = mutableMapOf<String, BudgetStore>()

    init {
        sync.storeFactory = { profileId -> budgetStores[profileId] ?: budgetFor(profileId) }
    }

    fun budgetFor(profileId: String): BudgetStore =
        budgetStores.getOrPut(profileId) { BudgetStore(repository, profileId) }

    fun dropBudget(profileId: String) {
        budgetStores.remove(profileId)
    }
}
