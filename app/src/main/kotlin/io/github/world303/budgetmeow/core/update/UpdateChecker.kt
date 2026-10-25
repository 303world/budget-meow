package io.github.world303.budgetmeow.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateSource(val wire: String) {
    /** 国内镜像：挂在自己域名上的 Worker，转 GitHub Releases */
    Mirror("mirror"),

    /** GitHub 直连 */
    GitHub("github"),
    ;

    companion object {
        fun fromWire(wire: String?): UpdateSource =
            entries.firstOrNull { it.wire == wire } ?: Mirror
    }
}

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val notes: String,
    /** Release 里 app-release.apk 的直链；没有就退回浏览器 */
    val apkUrl: String? = null,
    val apkSize: Long = 0L,
)

/**
 * 检查更新：直接读 GitHub Releases 的最新一条。
 * 不引第三方更新库（体积和权限都不值得），也没有静默下载安装——只告诉用户去哪下。
 */
object UpdateChecker {

    private const val MIRROR_ENDPOINT = "https://meow.303world.ccwu.cc/v1/release/latest"
    private const val GITHUB_ENDPOINT = "https://api.github.com/repos/303world/budget-meow/releases/latest"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 先走用户选的源；失败就自动换另一个。
     * 国内镜像默认开启——workers.dev 和 api.github.com 在国内都不稳。
     */
    suspend fun check(source: UpdateSource = UpdateSource.Mirror): Result<UpdateInfo> {
        val primary = if (source == UpdateSource.Mirror) MIRROR_ENDPOINT else GITHUB_ENDPOINT
        val secondary = if (source == UpdateSource.Mirror) GITHUB_ENDPOINT else MIRROR_ENDPOINT
        return runCatching { fetch(primary) }.recoverCatching { fetch(secondary) }
    }

    private suspend fun fetch(endpoint: String): UpdateInfo = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
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
                val assets = obj["assets"]?.jsonArray.orEmpty()
                val apk = assets.firstOrNull {
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true
                }?.jsonObject
                UpdateInfo(
                    latestVersion = obj["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("v").orEmpty(),
                    releaseUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull
                        ?: "https://github.com/303world/budget-meow/releases",
                    notes = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    apkUrl = apk?.get("browser_download_url")?.jsonPrimitive?.contentOrNull,
                    apkSize = apk?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
                )
            } catch (io: IOException) {
                error("网络不可用")
            } finally {
                connection.disconnect()
            }
        }.getOrThrow()
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
