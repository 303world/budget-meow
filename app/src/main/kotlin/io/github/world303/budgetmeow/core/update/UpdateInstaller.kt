package io.github.world303.budgetmeow.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新：下载 APK 后用系统的 PackageInstaller 装上。
 *
 * 不引第三方更新库：这里只有「下载一个文件 + 交给系统安装」两件事，
 * 引库反而要多一份签名校验与权限申请的复杂度。
 */
class UpdateInstaller(private val context: Context) {

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 没授权「安装未知应用」时，把用户送到本应用的授权页 */
    fun openInstallPermission() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    suspend fun download(
        info: UpdateInfo,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val url = info.apkUrl ?: error("发布页里没有 APK")
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("accept", "application/octet-stream")
                setRequestProperty("user-agent", "budget-meow-android")
            }
            try {
                if (connection.responseCode !in 200..299) error("下载失败（HTTP ${connection.responseCode}）")
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
                val target = File(context.cacheDir, "meow-update-${info.latestVersion}.apk")
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                val percent = (written * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
                if (target.length() < 100_000) error("下载的文件不完整")
                onProgress(100)
                target
            } finally {
                connection.disconnect()
            }
        }
    }

    /** 交给系统安装器；用户确认后覆盖安装（签名一致才能装） */
    fun install(apk: File): Result<Unit> = runCatching {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("meow-update.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }
}

/**
 * 安装结果回执。系统安装界面由 PackageInstaller 自己弹出，
 * 这里只负责在「需要用户操作」时把系统的 Intent 拉起来，以及处理失败。
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(confirm) }
        }
    }
}
