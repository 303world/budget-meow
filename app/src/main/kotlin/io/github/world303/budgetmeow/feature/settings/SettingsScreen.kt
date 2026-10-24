package io.github.world303.budgetmeow.feature.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.model.AppTheme
import io.github.world303.budgetmeow.core.model.HeaderDisplayMode
import io.github.world303.budgetmeow.core.model.MeowSettings
import io.github.world303.budgetmeow.core.model.Profile
import io.github.world303.budgetmeow.core.model.ThemeMode
import io.github.world303.budgetmeow.ui.component.ConfirmDialog
import io.github.world303.budgetmeow.ui.component.InputDialog
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowPage
import io.github.world303.budgetmeow.ui.component.MeowRow
import io.github.world303.budgetmeow.ui.component.MeowSection
import io.github.world303.budgetmeow.ui.component.MeowSwitchRow
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    profile: Profile,
    month: String,
    onBack: () -> Unit,
    onUpdateSettings: ((MeowSettings) -> MeowSettings) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenAbout: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onRenameNickname: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onEditBudget: () -> Unit,
    onClearMonth: () -> Unit,
    onResetAll: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val settings = profile.settings
    var showThemePicker by remember { mutableStateOf(false) }
    var showDarkPicker by remember { mutableStateOf(false) }
    var showHeaderPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showTimeEdit by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    MeowPage(
        title = "设置",
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
            // ---------------- 账号 ----------------
            MeowSection(title = "账号") {
                MeowRow(
                    title = profile.nickname,
                    summary = "本地配置档 · 数据只在这台设备上",
                    showArrow = true,
                    onClick = onOpenAccount,
                )
                MeowDivider()
                MeowRow(
                    title = "云端同步",
                    summary = "v2 计划：登录后跨设备同步，当前版本纯离线",
                    trailingText = "即将推出",
                    onClick = { },
                )
            }

            // ---------------- 外观 ----------------
            MeowSection(title = "外观") {
                MeowRow(
                    title = "配色",
                    trailingText = if (settings.appTheme == AppTheme.Pink) "粉色" else "默认",
                    showArrow = true,
                    onClick = { showThemePicker = true },
                )
                MeowDivider()
                MeowRow(
                    title = "深色模式",
                    trailingText = when (settings.theme) {
                        ThemeMode.System -> "跟随系统"
                        ThemeMode.Light -> "浅色"
                        ThemeMode.Dark -> "深色"
                        ThemeMode.Scheduled -> "${settings.darkStartTime} - ${settings.darkEndTime}"
                    },
                    showArrow = true,
                    onClick = { showDarkPicker = true },
                )
                MeowDivider()
                MeowSwitchRow(
                    title = "粗体字",
                    summary = "整体字重加粗一档",
                    checked = settings.boldText,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.copy(boldText = checked) }
                    },
                )
                MeowDivider()
                MeowSwitchRow(
                    title = "显示已节约",
                    summary = "首页多显示一行省钱累计",
                    checked = settings.showSaved,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.copy(showSaved = checked) }
                    },
                )
                MeowDivider()
                MeowRow(
                    title = "顶栏显示",
                    trailingText = when (settings.headerDisplayMode) {
                        HeaderDisplayMode.Nickname -> profile.nickname
                        HeaderDisplayMode.Default -> "我的预算"
                        HeaderDisplayMode.Meow -> "预算喵"
                        HeaderDisplayMode.Custom -> settings.headerCustomText.ifBlank { "自定义" }
                    },
                    showArrow = true,
                    onClick = { showHeaderPicker = true },
                )
                MeowDivider()
                MeowSwitchRow(
                    title = "用头像当设置按钮",
                    checked = settings.showAvatarAsSettingsIcon,
                    onCheckedChange = { checked ->
                        onUpdateSettings { it.copy(showAvatarAsSettingsIcon = checked) }
                    },
                )
            }

            // ---------------- 记账 ----------------
            MeowSection(title = "记账") {
                MeowRow(
                    title = "修改本月预算",
                    summary = "$month · 差额会自动调整余额并留一条调整记录",
                    showArrow = true,
                    onClick = onEditBudget,
                )
                MeowDivider()
                MeowRow(
                    title = "清空本月记录",
                    summary = "余额重置为本月初始预算",
                    onClick = { confirmClear = true },
                )
                MeowDivider()
                MeowRow(
                    title = "重置全部数据",
                    summary = "清空所有月份、出行与配置档下的账目",
                    onClick = { confirmReset = true },
                )
            }

            // ---------------- 数据 ----------------
            MeowSection(title = "数据") {
                MeowRow(
                    title = "导出备份",
                    summary = "导出为 JSON，可直接导入网页版",
                    showArrow = true,
                    onClick = onExport,
                )
                MeowDivider()
                MeowRow(
                    title = "导入备份",
                    summary = "支持网页版 v1 / v2 / v3 备份，会覆盖当前账目",
                    showArrow = true,
                    onClick = onImport,
                )
            }

            // ---------------- 关于 ----------------
            MeowSection(title = "关于") {
                MeowRow(
                    title = "昵称",
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
                    title = "关于预算喵",
                    summary = "GPL-3.0 · 开源地址与法律条款",
                    showArrow = true,
                    onClick = onOpenAbout,
                )
            }
        }
    }

    PickerSheet(
        show = showThemePicker,
        title = "配色",
        onDismiss = { showThemePicker = false },
        options = listOf(
            "默认（蓝）" to (settings.appTheme == AppTheme.Default),
            "粉色" to (settings.appTheme == AppTheme.Pink),
        ),
        onPick = { index ->
            showThemePicker = false
            onUpdateSettings {
                it.copy(appTheme = if (index == 0) AppTheme.Default else AppTheme.Pink)
            }
        },
    )

    PickerSheet(
        show = showDarkPicker,
        title = "深色模式",
        onDismiss = { showDarkPicker = false },
        options = listOf(
            "跟随系统" to (settings.theme == ThemeMode.System),
            "浅色" to (settings.theme == ThemeMode.Light),
            "深色" to (settings.theme == ThemeMode.Dark),
            "定时切换" to (settings.theme == ThemeMode.Scheduled),
        ),
        onPick = { index ->
            showDarkPicker = false
            val mode = when (index) {
                0 -> ThemeMode.System
                1 -> ThemeMode.Light
                2 -> ThemeMode.Dark
                else -> ThemeMode.Scheduled
            }
            if (mode == ThemeMode.Scheduled && !settings.hasSetScheduleTime) {
                showTimeEdit = true
            }
            onUpdateSettings { it.copy(theme = mode) }
        },
    )

    PickerSheet(
        show = showHeaderPicker,
        title = "顶栏显示",
        onDismiss = { showHeaderPicker = false },
        options = listOf(
            "昵称（${profile.nickname}）" to (settings.headerDisplayMode == HeaderDisplayMode.Nickname),
            "预算喵" to (settings.headerDisplayMode == HeaderDisplayMode.Meow),
            "我的预算" to (settings.headerDisplayMode == HeaderDisplayMode.Default),
            "自定义" to (settings.headerDisplayMode == HeaderDisplayMode.Custom),
        ),
        onPick = { index ->
            showHeaderPicker = false
            val mode = when (index) {
                0 -> HeaderDisplayMode.Nickname
                1 -> HeaderDisplayMode.Meow
                2 -> HeaderDisplayMode.Default
                else -> HeaderDisplayMode.Custom
            }
            onUpdateSettings { it.copy(headerDisplayMode = mode) }
        },
    )

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

    if (showTimeEdit) {
        InputDialog(
            show = true,
            title = "定时深色（开始时间）",
            initialValue = settings.darkStartTime,
            placeholder = "22:00",
            maxLength = 5,
            onConfirm = { start ->
                showTimeEdit = false
                onUpdateSettings {
                    it.copy(
                        darkStartTime = start.ifBlank { "22:00" },
                        hasSetScheduleTime = true,
                    )
                }
            },
            onDismiss = { showTimeEdit = false },
        )
    }

    ConfirmDialog(
        show = confirmClear,
        title = "清空本月记录",
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
        title = "重置全部数据",
        message = "所有月份的账目、出行记录都会被清空。建议先导出备份。",
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
private fun PickerSheet(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    options: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
) {
    if (!show) return
    OverlayBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            options.forEachIndexed { index, (label, checked) ->
                MeowRow(
                    title = label,
                    summary = if (checked) "当前" else null,
                    onClick = { onPick(index) },
                )
                if (index != options.lastIndex) MeowDivider()
            }
        }
    }
}

@Composable
fun AccountScreen(
    profiles: List<Profile>,
    activeId: String,
    onBack: () -> Unit,
    onSwitch: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    snackbar: SnackbarHostState,
) {
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }

    MeowPage(
        title = "账号",
        onBack = onBack,
        snackbar = snackbar,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                cornerRadius = 18.dp,
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "本机配置档",
                        style = MiuixTheme.textStyles.headline2,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "每个配置档的账目完全隔离，等价于网页版的多账号。当前版本不联网，数据只存在这台设备上。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            MeowSection(title = "配置档") {
                profiles.forEachIndexed { index, profile ->
                    MeowRow(
                        title = profile.nickname,
                        summary = if (profile.id == activeId) "当前使用" else "点击切换",
                        trailingText = if (profile.id == activeId) "✓" else null,
                        onClick = {
                            if (profile.id != activeId) onSwitch(profile.id)
                        },
                    )
                    if (profile.id != activeId) {
                        MeowRow(
                            title = "删除这个配置档",
                            summary = "连同它的账目一起删除",
                            onClick = { pendingDelete = profile },
                        )
                    }
                    if (index != profiles.lastIndex) MeowDivider()
                }
                MeowDivider()
                MeowRow(
                    title = "新建配置档",
                    summary = "比如「家庭账本」「旅行专项」",
                    showArrow = true,
                    onClick = { showCreate = true },
                )
            }

            MeowSection(title = "云端") {
                MeowRow(
                    title = "登录同步",
                    summary = "v2 计划：邮箱验证码登录 + Cloudflare 同步，已有数据会保留并做首次合并",
                    trailingText = "即将推出",
                    onClick = { },
                )
            }
        }
    }

    if (showCreate) {
        InputDialog(
            show = true,
            title = "新建配置档",
            initialValue = "",
            placeholder = "账本名称，最多 10 字",
            maxLength = 10,
            onConfirm = { name ->
                showCreate = false
                onCreate(name)
            },
            onDismiss = { showCreate = false },
        )
    }

    val deleting = pendingDelete
    if (deleting != null) {
        ConfirmDialog(
            show = true,
            title = "删除配置档",
            message = "「${deleting.nickname}」下的所有账目会被一起删除，无法恢复。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                onDelete(deleting.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
