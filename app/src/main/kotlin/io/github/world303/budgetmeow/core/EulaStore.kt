package io.github.world303.budgetmeow.core

import android.content.Context

/** 记录用户是否同意了当前版本的用户协议/隐私政策（全局，不分本机账本） */
class EulaStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("meow_eula", Context.MODE_PRIVATE)

    fun hasAccepted(): Boolean = prefs.getInt("accepted_version", 0) >= Legal.POLICY_VERSION

    fun accept() {
        prefs.edit().putInt("accepted_version", Legal.POLICY_VERSION).apply()
    }
}
