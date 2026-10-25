package io.github.world303.budgetmeow.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 同步服务地址。换后端只改这里 */
// workers.dev 在国内经常被 DNS 污染/阻断，这里走挂在自有域名上的同一个 Worker
const val DEFAULT_API_BASE = "https://meow.303world.ccwu.cc"
// 备用地址（海外或自有域名异常时可在代码里切换）
const val FALLBACK_API_BASE = "https://budget-meow.714945046.workers.dev"

class ApiException(val code: Int, val error: String) : Exception(error)

data class AuthResult(val token: String, val userId: String, val email: String)

data class RemoteDataset(val rev: Int, val updatedAt: String?, val payload: String?)

sealed interface PutResult {
    data class Ok(val rev: Int) : PutResult

    /** 别的设备先写了：把最新快照带回来，合并后重试 */
    data class Conflict(val rev: Int, val payload: String?) : PutResult
}

/**
 * 极简 HTTP 客户端。只有几个接口，用 HttpURLConnection 就够了，
 * 不值得为它引 OkHttp/Retrofit。
 */
class MeowApi(private val baseUrl: String = DEFAULT_API_BASE) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun register(email: String, password: String): AuthResult {
        val (code, body) = call(
            "POST",
            "/v1/register",
            buildJsonObject {
                put("email", email)
                put("password", password)
            },
        )
        if (code == 200) {
            return AuthResult(
                token = body.str("token") ?: throw ApiException(code, "missing_token"),
                userId = body.str("userId").orEmpty(),
                email = body.str("email") ?: email,
            )
        }
        throw ApiException(code, body.str("error") ?: "register_failed")
    }

    suspend fun login(email: String, password: String): AuthResult {
        val (code, body) = call(
            "POST",
            "/v1/login",
            buildJsonObject {
                put("email", email)
                put("password", password)
            },
        )
        if (code == 200) {
            return AuthResult(
                token = body.str("token") ?: throw ApiException(code, "missing_token"),
                userId = body.str("userId").orEmpty(),
                email = body.str("email") ?: email,
            )
        }
        throw ApiException(code, body.str("error") ?: "login_failed")
    }

    suspend fun logout(token: String) {
        call("POST", "/v1/logout", token = token)
    }

    suspend fun getDataset(token: String): RemoteDataset {
        val (code, body) = call("GET", "/v1/dataset", token = token)
        if (code != 200) throw ApiException(code, body.str("error") ?: "pull_failed")
        return RemoteDataset(
            rev = body["rev"]?.jsonPrimitive?.intOrNull ?: 0,
            updatedAt = body.str("updatedAt"),
            payload = body.str("payload"),
        )
    }

    suspend fun putDataset(token: String, baseRev: Int, payload: String): PutResult {
        val (code, body) = call(
            "PUT",
            "/v1/dataset",
            buildJsonObject {
                put("baseRev", baseRev)
                put("payload", payload)
            },
            token = token,
        )
        return when (code) {
            200 -> PutResult.Ok(body["rev"]?.jsonPrimitive?.intOrNull ?: (baseRev + 1))
            409 -> PutResult.Conflict(
                rev = body["rev"]?.jsonPrimitive?.intOrNull ?: 0,
                payload = body.str("payload"),
            )
            else -> throw ApiException(code, body.str("error") ?: "push_failed")
        }
    }

    suspend fun deleteAccount(token: String, password: String) {
        val (code, body) = call(
            "DELETE",
            "/v1/account",
            buildJsonObject { put("password", password) },
            token = token,
        )
        if (code != 200) throw ApiException(code, body.str("error") ?: "delete_failed")
    }

    suspend fun changePassword(token: String, oldPassword: String, newPassword: String) {
        val (code, body) = call(
            "POST",
            "/v1/password",
            buildJsonObject {
                put("oldPassword", oldPassword)
                put("newPassword", newPassword)
            },
            token = token,
        )
        if (code != 200) throw ApiException(code, body.str("error") ?: "password_failed")
    }

    private suspend fun call(
        method: String,
        path: String,
        body: JsonObject? = null,
        token: String? = null,
    ): Pair<Int, JsonObject> = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("accept", "application/json")
            token?.let { setRequestProperty("authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("content-type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse { JsonObject(emptyMap()) }
            code to parsed
        } catch (io: IOException) {
            throw ApiException(0, "network:${io.message ?: "unreachable"}")
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() && it != "null" }
}
