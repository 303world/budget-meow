package io.github.world303.budgetmeow.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val notes: String,
)

/**
 * 检查更新：直接读 GitHub Releases 的最新一条。
 * 不引第三方更新库（体积和权限都不值得），也没有静默下载安装——只告诉用户去哪下。
 */
object UpdateChecker {

    private const val ENDPOINT = "https://api.github.com/repos/303world/budget-meow/releases/latest"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun check(): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 18_000
                setRequestProperty("accept", "application/vnd.github+json")
                setRequestProperty("user-agent", "budget-meow-android")
            }
            try {
                if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val obj = json.parseToJsonElement(text).jsonObject
                UpdateInfo(
                    latestVersion = obj["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("v").orEmpty(),
                    releaseUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull
                        ?: "https://github.com/303world/budget-meow/releases",
                    notes = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            } catch (io: IOException) {
                error("网络不可用")
            } finally {
                connection.disconnect()
            }
        }
    }

    /** a 是否比 b 新（1.2.10 > 1.2.9） */
    fun isNewer(a: String, b: String): Boolean {
        val left = a.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l > r
        }
        return false
    }
}
