package io.github.world303.budgetmeow.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.Legal
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowRow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首次启动的用户协议同意页。
 *
 * 为什么需要：App 不登录时纯本地，但一旦登录同步就会处理邮箱与账目，
 * 首次启动把口径讲清楚，比事后解释省事。不同意就退出——没有可降级的形态。
 */
@Composable
fun ConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    var showDoc by remember { mutableStateOf<String?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "欢迎使用预算喵",
                style = MiuixTheme.textStyles.title1,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "给大学生管生活费的预算记账 App。",
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Spacer(modifier = Modifier.height(20.dp))
            Card(cornerRadius = 16.dp) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    MeowRow(
                        title = "数据默认只在这台手机上",
                        summary = "不登录也能完整记账，卸载即删除，请自行导出备份",
                    )
                    MeowDivider()
                    MeowRow(
                        title = "登录才会联网同步",
                        summary = "只上传账目与邮箱，密码加盐存储；同步服务可自建",
                    )
                    MeowDivider()
                    MeowRow(
                        title = "没有广告与统计 SDK",
                        summary = "不收集设备标识、通讯录、位置",
                    )
                    MeowDivider()
                    MeowRow(
                        title = "开源、无担保",
                        summary = "GPL-3.0，账目由你自己录入，不构成财务建议",
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    text = "《隐私政策》",
                    onClick = { showDoc = Legal.PRIVACY },
                )
                TextButton(
                    text = "《用户协议》",
                    onClick = { showDoc = Legal.TERMS },
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = "同意并继续", color = MiuixTheme.colorScheme.onPrimary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                text = "不同意，退出",
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "点击「同意并继续」表示你已阅读并同意上述条款。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    val doc = showDoc
    if (doc != null) {
        OverlayDialog(
            show = true,
            title = if (doc == Legal.PRIVACY) "隐私政策" else "用户协议",
            onDismissRequest = { showDoc = null },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = doc,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                text = "关闭",
                onClick = { showDoc = null },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
