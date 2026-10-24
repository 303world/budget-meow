package io.github.world303.budgetmeow.core.sync

import io.github.world303.budgetmeow.core.model.BudgetDataset
import io.github.world303.budgetmeow.core.model.BudgetRecord
import io.github.world303.budgetmeow.core.model.BudgetRecordData
import io.github.world303.budgetmeow.core.model.DatasetMerge
import io.github.world303.budgetmeow.core.model.RecordType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 对**真实**同步服务的端到端测试：注册 → 拉取 → 推送 → 冲突 → 合并 → 注销。
 *
 * 默认跳过（避免 CI 每次都打网络、往线上库里塞测试账号）。手动跑：
 *
 * ```
 * MEOW_API_TEST=1 ./gradlew :app:testDebugUnitTest --tests '*SyncIntegrationTest'
 * ```
 */
class SyncIntegrationTest {

    @Test
    fun `注册到注销的完整往返`() = runBlocking {
        assumeTrue("未设置 MEOW_API_TEST=1，跳过真实网络测试", System.getenv("MEOW_API_TEST") == "1")

        val api = MeowApi()
        val email = "ci-${System.currentTimeMillis()}@example.com"
        val password = "meowmeow123"

        // 1. 注册
        val auth = api.register(email, password)
        assertTrue("注册应返回令牌", auth.token.isNotBlank())
        println("[sync-test] registered ${auth.email}")

        // 2. 空账号拉取
        val empty = api.getDataset(auth.token)
        assertEquals(0, empty.rev)
        assertEquals(null, empty.payload)

        // 3. 推送第一版
        val firstPayload = """{"marker":"v1"}"""
        val firstPut = api.putDataset(auth.token, 0, firstPayload)
        assertTrue("首次推送应成功", firstPut is PutResult.Ok)
        assertEquals(1, (firstPut as PutResult.Ok).rev)

        // 4. 用过期 rev 推送，应拿到 409 + 最新快照
        val conflict = api.putDataset(auth.token, 0, """{"marker":"stale"}""")
        assertTrue("过期 rev 应冲突", conflict is PutResult.Conflict)
        assertEquals(firstPayload, (conflict as PutResult.Conflict).payload)

        // 5. 拉取确认
        val fetched = api.getDataset(auth.token)
        assertEquals(firstPayload, fetched.payload)
        assertEquals(1, fetched.rev)

        // 6. 模拟另一台设备：本地多一条记录，与远端合并后再推
        val remoteDataset = BudgetDataset()
        val localRecord = BudgetRecord(
            id = "local-1",
            data = BudgetRecordData(
                userId = "p1",
                month = "2026-10",
                type = RecordType.Expense,
                amount = -12.34,
                note = "集成测试",
                createdAt = "2026-10-25T00:00:00Z",
            ),
        )
        val merged = DatasetMerge.merge(BudgetDataset(records = listOf(localRecord)), remoteDataset)
        assertEquals(1, merged.records.size)

        val secondPut = api.putDataset(auth.token, fetched.rev, """{"marker":"v2"}""")
        assertTrue(secondPut is PutResult.Ok)
        assertEquals(2, (secondPut as PutResult.Ok).rev)

        // 7. 重新登录（验证密码哈希）
        val relogin = api.login(email, password)
        assertNotNull(relogin.token)

        // 8. 错误密码应被拒
        val rejected = runCatching { api.login(email, "wrong-password") }
        assertTrue("错误密码应该失败", rejected.isFailure)
        println("[sync-test] bad password rejected: ${rejected.exceptionOrNull()?.message}")

        // 9. 注销账号（清掉测试数据）
        api.deleteAccount(relogin.token, password)
        val afterDelete = runCatching { api.login(email, password) }
        assertTrue("注销后应登不进去", afterDelete.isFailure)
        println("[sync-test] account deleted")
    }
}
