package io.github.world303.budgetmeow.core.sync

import android.content.Context

/**
 * 账号凭据（每个本地配置档一份）。
 * 令牌存在 SharedPreferences 里——这是单机 App 的常规做法；
 * 真正敏感的是账目本身，而账目仍然只在本机 + 你自己的服务器上。
 */
class AuthStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("meow_sync", Context.MODE_PRIVATE)

    fun token(profileId: String): String? = prefs.getString("token_$profileId", null)

    fun email(profileId: String): String? = prefs.getString("email_$profileId", null)

    fun userId(profileId: String): String? = prefs.getString("userId_$profileId", null)

    fun isLoggedIn(profileId: String): Boolean = !token(profileId).isNullOrBlank()

    fun saveAuth(profileId: String, token: String, email: String, userId: String) {
        prefs.edit()
            .putString("token_$profileId", token)
            .putString("email_$profileId", email)
            .putString("userId_$profileId", userId)
            .apply()
    }

    fun clearAuth(profileId: String) {
        prefs.edit()
            .remove("token_$profileId")
            .remove("email_$profileId")
            .remove("userId_$profileId")
            .apply()
    }

    fun lastSyncAt(profileId: String): Long = prefs.getLong("lastSync_$profileId", 0L)

    fun setLastSyncAt(profileId: String, at: Long) {
        prefs.edit().putLong("lastSync_$profileId", at).apply()
    }

    fun lastRev(profileId: String): Int = prefs.getInt("rev_$profileId", 0)

    fun setLastRev(profileId: String, rev: Int) {
        prefs.edit().putInt("rev_$profileId", rev).apply()
    }
}
