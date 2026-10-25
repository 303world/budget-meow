package io.github.world303.budgetmeow.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.update.UpdateInfo
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 发现新版本弹窗。progress = -1 表示还没开始下载；0..100 是下载进度。
 * 点「更新」后在应用内下载，再交给系统安装器（不跳浏览器）。
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    progress: Int,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = progress >= 0
    OverlayDialog(
        show = true,
        title = "发现新版本 v${info.latestVersion}",
        summary = "当前 v$currentVersion → 最新 v${info.latestVersion}",
        onDismissRequest = { if (!downloading) onDismiss() },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (downloading) {
                Text(
                    text = if (progress >= 100) "下载完成，正在唤起安装…" else "正在下载 $progress%",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(10.dp))
            } else if (info.notes.isNotBlank()) {
                Text(
                    text = info.notes.trim().take(160),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "稍后",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !downloading,
                )
                TextButton(
                    text = if (downloading) "下载中…" else "更新",
                    onClick = onUpdate,
                    modifier = Modifier.weight(1f),
                    enabled = !downloading,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
