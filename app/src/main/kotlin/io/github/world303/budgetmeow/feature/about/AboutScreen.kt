package io.github.world303.budgetmeow.feature.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.ui.component.EmptyHint
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowPage
import io.github.world303.budgetmeow.ui.component.MeowRow
import io.github.world303.budgetmeow.ui.component.MeowSection
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

const val APP_VERSION = "0.1.0"
const val GITHUB_URL = "https://github.com/303world/budget-meow"

private const val PRIVACY_TEXT = """预算喵是离线应用，这一版没有任何网络请求。

1. 你的账目、配置档、设置全部存放在 App 的私有目录里，只有本机这个 App 能读写。
2. 没有账号系统，不上传任何数据，也不含统计 SDK、广告 SDK。
3. 导出的备份文件由你自己选择保存位置，文件内容不含昵称与头像。
4. 卸载 App 会连同数据一起删除。想保留请先导出备份。
5. App 需要「写入文件」权限只用于导出备份到你选定的位置。"""

private const val TERMS_TEXT = """1. 本应用以 GPL-3.0 协议开源，你可以自由使用、修改、分发，但分发衍生作品时必须以同一协议开源，且不提供任何担保。
2. 记账数据由你自己录入，应用只负责按你设定的规则计算与展示，不构成任何财务建议。
3. 因误操作、设备损坏、卸载或未备份导致的数据丢失，作者不承担责任。"""

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current

    MeowPage(
        title = "关于",
        onBack = onBack,
        snackbar = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                cornerRadius = 18.dp,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "预算喵", style = MiuixTheme.textStyles.title2)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "版本 $APP_VERSION · 计算器优先的离线预算记账",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            MeowSection(title = "开源") {
                MeowRow(
                    title = "许可证",
                    trailingText = "GPL-3.0",
                )
                MeowDivider()
                MeowRow(
                    title = "源码仓库",
                    summary = GITHUB_URL,
                    showArrow = true,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                        }
                    },
                )
            }

            MeowSection(title = "隐私政策") {
                Text(
                    text = PRIVACY_TEXT,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            MeowSection(title = "用户协议") {
                Text(
                    text = TERMS_TEXT,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            MeowSection(title = "第三方组件") {
                MeowRow("Miuix", summary = "top.yukonga.miuix.kmp · Apache-2.0")
                MeowDivider()
                MeowRow("Kotlin / Compose", summary = "JetBrains · Apache-2.0")
                MeowDivider()
                MeowRow("kotlinx.coroutines", summary = "Apache-2.0")
                MeowDivider()
                MeowRow("kotlinx.serialization", summary = "Apache-2.0")
            }

            Spacer(modifier = Modifier.height(8.dp))
            EmptyHint("数据在你自己手上，这是这一版唯一的设计目标。")
        }
    }
}
