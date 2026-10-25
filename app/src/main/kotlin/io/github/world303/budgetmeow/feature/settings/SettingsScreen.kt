package io.github.world303.budgetmeow.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.model.AppTheme
import io.github.world303.budgetmeow.core.model.MeowSettings
import io.github.world303.budgetmeow.core.model.Profile
import io.github.world303.budgetmeow.core.model.ThemeMode
import io.github.world303.budgetmeow.core.model.formatMoney
import io.github.world303.budgetmeow.feature.about.APP_VERSION
import io.github.world303.budgetmeow.ui.component.ConfirmDialog
import io.github.world303.budgetmeow.ui.component.InputDialog
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowPage
import io.github.world303.budgetmeow.ui.component.MeowRow
import io.github.world303.budgetmeow.ui.component.MeowSwitchRow
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val CONTACT_EMAIL = "714945046@qq.com"

/**
 * 设置页。分组与顺序对齐网页版原版：
 * 本月预算卡 → 通用 → 账号 → 隐私 → 备份 → 联系我们/关于。
 */
@Composable
fun SettingsScreen(
    profile: Profile,
    month: String,
    budget: Double,
    onBack: () -> Unit,
    onUpdateSettings: ((MeowSettings) -> MeowSettings) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenAbout: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRenameNickname: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onPickWallpaper: () -> Unit,
    onRemoveWallpaper: () -> Unit,
    syncState: io.github.world303.budgetmeow.core.sync.SyncState,
    onSyncNow: () -> Unit,
    onEditBudget: () -> Unit,
    onClearMonth: () -> Unit,
    onResetAll: () -> Unit,
) {
    val settings = profile.settings
    val context = LocalContext.current

    var generalOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    var backupOpen by remember { mutableStateOf(false) }

    var showScheduleEditor by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }

    MeowPage(title = "设置", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            // ---------------- 本月预算 ----------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable { onEditBudget() },
                cornerRadius = 16.dp,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "¥${budget.formatMoney()}",
                            style = MiuixTheme.textStyles.title2,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "修改本月预算 · $month",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            }

            // ---------------- 通用 ----------------
            MeowCollapsible(
                title = "通用",
                expanded = generalOpen,
                onToggle = { generalOpen = !generalOpen },
            ) {
                // 深色模式（定时开启时置灰，与网页版一致）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "深色模式",
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.weight(1f),
                    )
                    MeowSegmented(
                        options = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = when (settings.theme) {
                            ThemeMode.Light -> 1
                            ThemeMode.Dark -> 2
                            else -> 0
                        },
                        enabled = !settings.scheduledDarkMode,
                        onSelect = { index ->
                            onUpdateSettings {
                                it.copy(
                                    theme = when (index) {
                                        1 -> ThemeMode.Light
                                        2 -> ThemeMode.Dark
                                        else -> ThemeMode.System
                                    },
                                )
                            }
                        },
                    )
                }
                MeowDivider()

                // 自定义时间 + 定时深色
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "自定义时间",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (settings.hasSetScheduleTime) {
                                "${settings.darkStartTime}~${settings.darkEndTime}"
                            } else {
                                "22:00~07:00"
                            },
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.clickable { showScheduleEditor = true },
                        )
                    }
                    Box {
                        Switch(
                            checked = settings.scheduledDarkMode,
                            onCheckedChange = { checked ->
                                if (checked && !settings.hasSetScheduleTime) showScheduleEditor = true
                                onUpdateSettings {
                                    it.copy(
                                        scheduledDarkMode = checked,
                                        theme = if (checked) ThemeMode.Scheduled else ThemeMode.System,
                                    )
                                }
                            },
                        )
                    }
                }
                MeowDivider()


                MeowRow(
                    title = "导入壁纸",
                    summary = if (settings.wallpaperPath != null) "已设置" else "玻璃效果需要壁纸才看得出层次",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (settings.wallpaperPath != null) {
                                MeowPillButton("移除", MeowPalette.Red, onRemoveWallpaper)
                            }
                            MeowPillButton("选择", MeowPalette.Blue, onPickWallpaper)
                        }
                    },
                )
                MeowDivider()

                MeowSwitchRow(
                    title = "粗体字",
                    checked = settings.boldText,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(boldText = checked) } },
                )
                MeowDivider()

                MeowSwitchRow(
                    title = "全屏模式",
                    summary = "隐藏状态栏与导航栏",
                    checked = settings.fullScreen,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(fullScreen = checked) } },
                )
                MeowDivider()

                MeowSwitchRow(
                    title = "显示已节约",
                    checked = settings.showSaved,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(showSaved = checked) } },
                )
                MeowDivider()

                MeowSwitchRow(
                    title = "设置齿轮显示头像",
                    checked = settings.showAvatarAsSettingsIcon,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.copy(showAvatarAsSettingsIcon = checked) }
                    },
                )
            }

            // ---------------- 账号 ----------------
            MeowCollapsible(
                title = "账号",
                expanded = accountOpen,
                onToggle = { accountOpen = !accountOpen },
            ) {
                MeowRow(
                    title = "当前账号",
                    summary = "本地配置档 · 不联网",
                    trailingText = profile.nickname,
                )
                MeowDivider()
                MeowRow(
                    title = "修改昵称",
                    trailingText = profile.nickname,
                    showArrow = true,
                    onClick = { showRename = true },
                )
                MeowDivider()
                MeowRow(
                    title = "设置头像",
                    showArrow = true,
                    onClick = onPickAvatar,
                )
                MeowDivider()
                MeowRow(
                    title = "切换 / 新建配置档",
                    summary = "每份配置档的账目完全隔离",
                    showArrow = true,
                    onClick = onOpenAccount,
                )
                MeowDivider()
                MeowRow(
                    title = "云端同步",
                    summary = if (syncState.loggedIn) {
                        syncState.email.orEmpty()
                    } else {
                        "未登录 · 登录后自动跨设备合并"
                    },
                    trailingText = when {
                        syncState.syncing -> "同步中"
                        syncState.loggedIn -> "已登录"
                        else -> "去登录"
                    },
                    showArrow = true,
                    onClick = onOpenAccount,
                )
                if (syncState.loggedIn) {
                    MeowDivider()
                    MeowRow(
                        title = "立即同步",
                        summary = "拉取云端改动并与本机合并",
                        onClick = onSyncNow,
                    )
                }
            }

            // ---------------- 隐私 ----------------
            MeowCollapsible(
                title = "隐私",
                expanded = privacyOpen,
                onToggle = { privacyOpen = !privacyOpen },
            ) {
                MeowRow(
                    title = "离线优先",
                    summary = "不登录时零网络请求；登录后只同步账目",
                    trailingText = "默认",
                )
                MeowDivider()
                MeowRow(
                    title = "清空历史记录",
                    summary = "只清本月流水，余额回到初始预算",
                    onClick = { confirmClear = true },
                )
                MeowDivider()
                MeowRow(
                    title = "清空全部数据",
                    summary = "所有月份与出行记录都会被删除",
                    onClick = { confirmReset = true },
                )
            }

            // ---------------- 备份 ----------------
            MeowCollapsible(
                title = "备份",
                expanded = backupOpen,
                onToggle = { backupOpen = !backupOpen },
            ) {
                MeowRow(
                    title = "备份数据",
                    summary = "导出 JSON，可导回网页版",
                    showArrow = true,
                    onClick = onExport,
                )
                MeowDivider()
                MeowRow(
                    title = "恢复数据",
                    summary = "支持网页版 v1/v2/v3 备份，会覆盖当前账目",
                    showArrow = true,
                    onClick = onImport,
                )
            }

            // ---------------- 联系我们 / 关于 ----------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                cornerRadius = 16.dp,
            ) {
                MeowRow(
                    title = "联系我们",
                    summary = CONTACT_EMAIL,
                    onClick = { showContact = true },
                )
                MeowDivider()
                MeowRow(
                    title = "关于",
                    trailingText = "v$APP_VERSION",
                    showArrow = true,
                    onClick = onOpenAbout,
                )
            }

        }
    }

    if (showRename) {
        InputDialog(
            show = true,
            title = "修改昵称",
            initialValue = profile.nickname,
            placeholder = "最多 10 字",
            maxLength = 10,
            onConfirm = { name ->
                showRename = false
                onRenameNickname(name)
            },
            onDismiss = { showRename = false },
        )
    }

    if (showScheduleEditor) {
        InputDialog(
            show = true,
            title = "定时深色（开始时间）",
            initialValue = settings.darkStartTime,
            placeholder = "22:00",
            maxLength = 5,
            onConfirm = { start ->
                showScheduleEditor = false
                onUpdateSettings {
                    it.copy(darkStartTime = start.ifBlank { "22:00" }, hasSetScheduleTime = true)
                }
            },
            onDismiss = { showScheduleEditor = false },
        )
    }

    if (showContact) {
        OverlayDialog(
            show = true,
            title = "联系我们",
            summary = "有问题、想提建议、或者想反馈 bug，发邮件到下面这个地址：",
            onDismissRequest = { showContact = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = CONTACT_EMAIL,
                    style = MiuixTheme.textStyles.title4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        text = "复制邮箱",
                        onClick = {
                            val clipboard = context.getSystemService(
                                android.content.Context.CLIPBOARD_SERVICE,
                            ) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("邮箱", CONTACT_EMAIL),
                            )
                            showContact = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "关闭",
                        onClick = { showContact = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }

    ConfirmDialog(
        show = confirmClear,
        title = "清空历史记录",
        message = "本月所有流水会被删除，余额重置为初始预算。该操作不可撤销。",
        confirmText = "清空",
        destructive = true,
        onConfirm = {
            confirmClear = false
            onClearMonth()
        },
        onDismiss = { confirmClear = false },
    )

    ConfirmDialog(
        show = confirmReset,
        title = "清空全部数据",
        message = "所有月份的账目、出行记录都会被清空。建议先备份数据。",
        confirmText = "全部清空",
        destructive = true,
        onConfirm = {
            confirmReset = false
            onResetAll()
        },
        onDismiss = { confirmReset = false },
    )
}

@Composable
private fun MeowCollapsible(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        cornerRadius = 16.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.rotate(rotation),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column { content() }
            }
        }
    }
}

@Composable
private fun MeowSegmented(
    options: List<String>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant)
            .padding(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) colors.surface else Color.Transparent)
                    .then(if (enabled) Modifier.clickable { onSelect(index) } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = when {
                        !enabled -> colors.onSurfaceVariantSummary.copy(alpha = 0.4f)
                        selected -> colors.onSurface
                        else -> colors.onSurfaceVariantSummary
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MeowPillButton(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
