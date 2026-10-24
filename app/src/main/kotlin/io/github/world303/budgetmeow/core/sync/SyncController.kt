package io.github.world303.budgetmeow.core.sync

import io.github.world303.budgetmeow.core.model.BudgetDataset
import io.github.world303.budgetmeow.core.model.DatasetMerge
import io.github.world303.budgetmeow.core.storage.LocalStorage
import io.github.world303.budgetmeow.core.store.BudgetStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncState(
    val loggedIn: Boolean = false,
    val email: String? = null,
    val lastSyncAt: Long = 0L,
    val syncing: Boolean = false,
    val error: String? = null,
)

/**
 * 账号 + 同步的调度层。
 *
 * 合并策略：先拉远端快照，与本机按实体合并；合并结果落盘后再带 baseRev 推送。
 * 推送撞上 409 说明别的设备写过，就用返回的快照再合并一轮，最多重试 4 次。
 */
class SyncController(
    private val api: MeowApi,
    private val authStore: AuthStore,
    private val storage: LocalStorage,
) {

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun refresh(profileId: String) {
        _state.value = SyncState(
            loggedIn = authStore.isLoggedIn(profileId),
            email = authStore.email(profileId),
            lastSyncAt = authStore.lastSyncAt(profileId),
            syncing = false,
            error = null,
        )
    }

    suspend fun register(profileId: String, email: String, password: String): Result<String> =
        runCatching {
            val result = api.register(email.trim(), password)
            authStore.saveAuth(profileId, result.token, result.email, result.userId)
            refresh(profileId)
            result.email
        }.recoverCatching { throw it.asFriendly() }

    suspend fun login(profileId: String, email: String, password: String): Result<String> =
        runCatching {
            val result = api.login(email.trim(), password)
            authStore.saveAuth(profileId, result.token, result.email, result.userId)
            refresh(profileId)
            result.email
        }.recoverCatching { throw it.asFriendly() }

    suspend fun logout(profileId: String) {
        val token = authStore.token(profileId)
        if (token != null) {
            runCatching { api.logout(token) }
        }
        authStore.clearAuth(profileId)
        authStore.setLastRev(profileId, 0)
        refresh(profileId)
    }

    suspend fun deleteAccount(profileId: String, password: String): Result<Unit> = runCatching {
        val token = authStore.token(profileId) ?: throw ApiException(401, "not_logged_in")
        api.deleteAccount(token, password)
        authStore.clearAuth(profileId)
        authStore.setLastRev(profileId, 0)
        refresh(profileId)
    }.recoverCatching { throw it.asFriendly() }

    suspend fun sync(profileId: String): Result<String> {
        val token = authStore.token(profileId)
            ?: return Result.failure(ApiException(401, "not_logged_in"))

        _state.value = _state.value.copy(syncing = true, error = null)
        return try {
            val message = syncInternal(profileId, token)
            _state.value = _state.value.copy(syncing = false, error = null)
            Result.success(message)
        } catch (t: Throwable) {
            val friendly = t.asFriendly()
            _state.value = _state.value.copy(syncing = false, error = friendly.message)
            Result.failure(friendly)
        }
    }

    private suspend fun syncInternal(profileId: String, token: String): String {
        val store = storeFactory?.invoke(profileId)
            ?: return "同步未就绪"

        // 同步前先从磁盘重读一遍：启动时的自动同步可能与 store.load() 抢跑，
        // 拿空 dataset 去合并会把本地未上传的记录冲掉
        store.reload()

        val remote = api.getDataset(token)
        var rev = remote.rev
        var remotePayload = remote.payload
        var merged = if (remotePayload.isNullOrBlank()) {
            store.snapshot()
        } else {
            val remoteDataset = storage.decodeDataset(remotePayload) ?: BudgetDataset()
            DatasetMerge.merge(store.snapshot(), remoteDataset)
        }

        var attempts = 0
        var pushed = false
        while (attempts < 4) {
            val payload = storage.encodeDataset(merged)
            if (payload == remotePayload) break
            when (val result = api.putDataset(token, rev, payload)) {
                is PutResult.Ok -> {
                    rev = result.rev
                    remotePayload = payload
                    pushed = true
                    break
                }

                is PutResult.Conflict -> {
                    attempts++
                    val remoteDataset = result.payload?.let { storage.decodeDataset(it) }
                    if (remoteDataset != null) {
                        merged = DatasetMerge.merge(merged, remoteDataset)
                    }
                    rev = result.rev
                    remotePayload = result.payload
                }
            }
        }

        // 合并结果落盘（哪怕没推送，也可能吸收了远端的新内容）
        store.importDataset(merged)
        authStore.setLastRev(profileId, rev)
        authStore.setLastSyncAt(profileId, System.currentTimeMillis())
        _state.value = _state.value.copy(
            loggedIn = true,
            email = authStore.email(profileId),
            lastSyncAt = authStore.lastSyncAt(profileId),
        )

        val recordCount = merged.records.count { !it.data.deleted }
        return if (pushed) "已同步（$recordCount 条记录）" else "已是最新（$recordCount 条记录）"
    }

    /** 由容器注入，避免 SyncController 直接依赖 BudgetStore 的构造细节 */
    var storeFactory: ((String) -> BudgetStore?)? = null

    private fun Throwable.asFriendly(): ApiException {
        if (this is ApiException) {
            val message = when {
                code == 0 -> "网络不可用，稍后再试"
                error.contains("email_taken") -> "这个邮箱已经注册过了"
                error.contains("invalid_credentials") -> "邮箱或密码不对"
                error.contains("invalid_email") -> "邮箱格式不对"
                error.contains("weak_password") -> "密码至少 8 位"
                error.contains("payload_too_large") -> "账目数据太大，暂时无法上传"
                code == 401 -> "登录已过期，请重新登录"
                code >= 500 -> "服务器开小差了（$code）"
                else -> "同步失败（$code）"
            }
            return ApiException(code, message)
        }
        return ApiException(-1, message ?: "未知错误")
    }
}
