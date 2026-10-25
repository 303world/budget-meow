package io.github.world303.budgetmeow.feature.about

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.update.UpdateChecker
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowPage
import io.github.world303.budgetmeow.ui.component.MeowRow
import io.github.world303.budgetmeow.ui.component.MeowSection
import io.github.world303.budgetmeow.ui.component.MeowSwitchRow
import io.github.world303.budgetmeow.ui.component.MeowToast
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

const val APP_VERSION = "1.1.1"
const val GITHUB_URL = "https://github.com/303world/budget-meow"
const val RELEASES_URL = "https://github.com/303world/budget-meow/releases/latest"
const val ORIGINAL_SITE_URL = "https://app-1778154199542-u11q8myzx-default.tacore.ai/"

private const val PRIVACY_TEXT = """预算喵是给大学生管生活费的记账工具：不登录也能完整使用，登录只是为了在多台设备之间同步。

1. 账目、配置档、设置都存在 App 的私有目录里，只有本机能读写。
2. 不登录时，App 不发出任何网络请求（检查更新除外，可以关掉）。
3. 登录后，账目经 HTTPS 上传到同步服务；服务端只保存账目快照、邮箱与加盐后的密码哈希，不保存明文密码。
4. 同步服务可以自建，代码在仓库 server/ 目录；官方实例是作者用免费额度搭的，不承诺可用性。
5. 不含任何统计 SDK、广告 SDK、崩溃上报 SDK。
6. 导出的备份文件由你选择保存位置，内容不含昵称与头像。
7. 卸载 App 会连同本地数据一起删除；云端数据要在「账号」页手动注销账号才会清除。"""

private const val TERMS_TEXT = """1. 本应用以 GPL-3.0 协议开源，可自由使用、修改、分发；分发衍生作品须以同一协议开源，且不提供任何担保。
2. 记账数据由你自己录入，应用只按你设定的规则计算与展示，不构成财务建议。
3. 因误操作、设备损坏、卸载或未备份导致的数据丢失，作者不承担责任。
4. 云端同步服务可能随时中断，重要数据请自行导出备份。"""

@Composable
fun AboutScreen(
    autoCheckUpdate: Boolean,
    onToggleAutoCheck: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { MeowToast.warning("没有可打开链接的应用") }
    }

    MeowPage(title = "关于", onBack = onBack) { padding ->
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
                        text = "版本 $APP_VERSION · 大学生生活费预算",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "把每个月的生活费先定下来，每花一笔就从里面扣。计算器式记账，随手一按就是一笔。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            // ---------------- 版本与更新 ----------------
            MeowSection(title = "版本") {
                MeowRow(
                    title = "检查更新",
                    summary = "从 GitHub Releases 读取最新版本",
                    trailingText = if (checking) "检查中…" else "v$APP_VERSION",
                    showArrow = !checking,
                    onClick = {
                        if (checking) return@MeowRow
                        checking = true
                        scope.launch {
                            UpdateChecker.check()
                                .onSuccess { info ->
                                    checking = false
                                    if (UpdateChecker.isNewer(info.latestVersion, APP_VERSION)) {
                                        MeowToast.show("发现新版本 v${info.latestVersion}")
                                        openUrl(info.releaseUrl)
                                    } else {
                                        MeowToast.success("已是最新版本")
                                    }
                                }
                                .onFailure {
                                    checking = false
                                    MeowToast.error(it.message ?: "检查失败")
                                }
                        }
                    },
                )
                MeowDivider()
                MeowSwitchRow(
                    title = "自动检查更新",
                    summary = "启动时静默检查一次，发现新版本只提示，不自动下载",
                    checked = autoCheckUpdate,
                    onCheckedChange = onToggleAutoCheck,
                )
            }

            // ---------------- 网页版 / App ----------------
            MeowSection(title = "网页版与 App") {
                MeowRow(
                    title = "网页版（已停止维护）",
                    summary = ORIGINAL_SITE_URL,
                    showArrow = true,
                    onClick = { openUrl(ORIGINAL_SITE_URL) },
                )
                MeowDivider()
                MeowRow(
                    title = "Android 测试版下载",
                    summary = "GitHub Releases，优先使用这个版本",
                    showArrow = true,
                    onClick = { openUrl(RELEASES_URL) },
                )
                MeowDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "网页版与 App 的数据**不互通**：网页版已停止维护，推荐直接用 App。\n" +
                            "如果想把手上的网页版数据搬过来，可以在网页版导出 JSON，再在 App 的「设置 → 备份 → 恢复数据」里导入。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            // ---------------- 开源 ----------------
            MeowSection(title = "开源") {
                MeowRow(title = "许可证", trailingText = "GPL-3.0")
                MeowDivider()
                MeowRow(
                    title = "源码仓库",
                    summary = GITHUB_URL,
                    showArrow = true,
                    onClick = { openUrl(GITHUB_URL) },
                )
                MeowDivider()
                MeowRow(
                    title = "自建同步服务",
                    summary = "server/ 目录里有 Worker + D1 的完整代码和部署说明",
                )
            }

            // ---------------- 致谢 ----------------
            MeowSection(title = "致谢") {
                MeowRow("Miuix", summary = "HyperOS 风格 Compose 组件库 · Apache-2.0")
                MeowDivider()
                MeowRow("NexioSchedule", summary = "课程表 App，本项目的界面与液态玻璃参考 · 特别致谢")
                MeowDivider()
                MeowRow("kyant backdrop", summary = "液态玻璃的先行实现 · Apache-2.0")
                MeowDivider()
                MeowRow("Kotlin / Compose / kotlinx", summary = "JetBrains · Apache-2.0")
            }

            // ---------------- 隐私 ----------------
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

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Text(
                    text = "数据和代码都在你自己手上。",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MeowPalette.Blue,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
