package io.github.world303.budgetmeow.feature.home

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.world303.budgetmeow.core.model.BudgetRecord
import io.github.world303.budgetmeow.core.model.HeaderDisplayMode
import io.github.world303.budgetmeow.core.model.MeowSettings
import io.github.world303.budgetmeow.core.model.Profile
import io.github.world303.budgetmeow.core.model.RecordType
import io.github.world303.budgetmeow.core.model.TimeUtil
import io.github.world303.budgetmeow.core.model.formatMoney
import io.github.world303.budgetmeow.core.store.BudgetStore
import io.github.world303.budgetmeow.core.store.MessageKind
import io.github.world303.budgetmeow.feature.calculator.CalculatorSheet
import io.github.world303.budgetmeow.feature.onboarding.InitBudgetSheet
import io.github.world303.budgetmeow.ui.component.ConfirmDialog
import io.github.world303.budgetmeow.ui.component.InputDialog
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowRow
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

fun formatRecordTime(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(timeFormatter)
}.getOrDefault("")

@Composable
fun HomeScreen(
    store: BudgetStore,
    profile: Profile,
    onOpenHistory: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onHeaderModeChange: (HeaderDisplayMode) -> Unit,
    snackbar: SnackbarHostState,
) {
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val settings = profile.settings

    var calcType by remember { mutableStateOf<RecordType?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showTripSwitch by remember { mutableStateOf(false) }
    var showRenameTrip by remember { mutableStateOf(false) }
    var confirmEndTrip by remember { mutableStateOf(false) }
    var confirmAdvance by remember { mutableStateOf(false) }
    var isTripMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        store.messages.collect { msg ->
            snackbar.showSnackbar(msg.text)
        }
    }

    LaunchedEffect(state.activeTrip) {
        if (state.activeTrip == null) isTripMode = false
    }

    val headerText = when (settings.headerDisplayMode) {
        HeaderDisplayMode.Nickname -> profile.nickname
        HeaderDisplayMode.Custom -> settings.headerCustomText.ifBlank { "我的预算" }
        HeaderDisplayMode.Meow -> "预算喵"
        HeaderDisplayMode.Default -> "我的预算"
    }
    val tripName = state.activeTrip?.data?.tripName?.ifBlank { null }
        ?: state.activeTrip?.let { "${tripMonth(it.data.createdAt)}出行" }
        ?: "出行"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ---------------- 顶栏 ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        imageVector = MiuixIcons.Recent,
                        contentDescription = "历史记录",
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                HeaderPill(
                    text = if (isTripMode) tripName else headerText,
                    showChevron = state.activeTrip != null,
                    onClick = {
                        if (state.activeTrip != null) showTripSwitch = true
                    },
                    onLongClick = {
                        if (isTripMode) showRenameTrip = true else showHeaderMenu = true
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    val avatar = profile.avatarPath
                    if (settings.showAvatarAsSettingsIcon && avatar != null) {
                        AvatarImage(path = avatar)
                    } else {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = "设置",
                            tint = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ---------------- 主数值 ----------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isTripMode && state.activeTrip != null) {
                    Text(
                        text = "出行余额",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BigMoney(state.tripBalance)
                } else {
                    Text(
                        text = "剩余预算",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.balance < 0) {
                        Text(
                            text = "已超支",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MeowPalette.Red,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MeowPalette.Red.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    BigMoney(
                        amount = state.balance,
                        color = if (state.balance < 0) MeowPalette.Red else Color.Unspecified,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.summary?.data?.lastUpdatedAt?.let { "上次更新: ${formatRecordTime(it)}" }
                            ?: "暂无更新",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (settings.showSaved) {
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = "已节约",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        BigMoney(amount = state.savedAmount, color = MeowPalette.Green)
                    }
                }
            }

            // ---------------- 快捷操作 ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isTripMode && state.activeTrip != null) {
                    QuickAction("收入", Modifier.weight(1f), { PlusMinusIcon(true, MeowPalette.Green) }) {
                        calcType = RecordType.TripIncome
                    }
                    QuickAction("支出", Modifier.weight(1f), { PlusMinusIcon(false, MeowPalette.Red) }) {
                        calcType = RecordType.TripExpense
                    }
                    QuickAction("增加", Modifier.weight(1f), { Icon(MiuixIcons.AddCircle, "增加", tint = MeowPalette.Blue) }) {
                        calcType = RecordType.TripAdd
                    }
                    QuickAction("结束", Modifier.weight(1f), { Icon(MiuixIcons.Ok, "结束", tint = MeowPalette.Purple) }) {
                        confirmEndTrip = true
                    }
                } else {
                    QuickAction("收入", Modifier.weight(1f), { PlusMinusIcon(true, MeowPalette.Green) }) {
                        calcType = RecordType.Income
                    }
                    QuickAction("支出", Modifier.weight(1f), { PlusMinusIcon(false, MeowPalette.Red) }) {
                        calcType = RecordType.Expense
                    }
                    QuickAction("省钱", Modifier.weight(1f), { Icon(MiuixIcons.Favorites, "省钱", tint = MeowPalette.Blue) }) {
                        calcType = RecordType.Save
                    }
                    QuickAction("更多", Modifier.weight(1f), {
                        Icon(
                            MiuixIcons.More,
                            "更多",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }) {
                        showMoreMenu = true
                    }
                }
            }
        }
    }

    // ---------------- 各类弹层 ----------------
    MenuSheet(show = showMoreMenu, title = "更多", onDismiss = { showMoreMenu = false }) {
        MeowRow("结转", "结转到下个月再花", onClick = {
            showMoreMenu = false
            calcType = RecordType.Carryover
        })
        MeowDivider()
        MeowRow(
            title = "出行",
            summary = if (state.activeTrip != null) "正在出行中" else "开启一个独立的出行账本",
            onClick = {
                showMoreMenu = false
                if (state.activeTrip != null) {
                    scope.launch { snackbar.showSnackbar("正在出行中") }
                } else {
                    calcType = RecordType.TripStart
                }
            },
        )
        MeowDivider()
        MeowRow("预支", "提前花下个月的预算", onClick = {
            showMoreMenu = false
            confirmAdvance = true
        })
        MeowDivider()
        MeowRow("统计", "查看本月与年度收支", onClick = {
            showMoreMenu = false
            onOpenStats()
        })
        MeowDivider()
        MeowRow("搜索", "按关键词、金额、日期找记录", onClick = {
            showMoreMenu = false
            onOpenSearch()
        })
    }

    MenuSheet(show = showHeaderMenu, title = "顶栏显示", onDismiss = { showHeaderMenu = false }) {
        HeaderModeRow("昵称（${profile.nickname}）", HeaderDisplayMode.Nickname, settings) {
            showHeaderMenu = false
            onHeaderModeChange(HeaderDisplayMode.Nickname)
        }
        MeowDivider()
        HeaderModeRow("预算喵", HeaderDisplayMode.Meow, settings) {
            showHeaderMenu = false
            onHeaderModeChange(HeaderDisplayMode.Meow)
        }
        MeowDivider()
        HeaderModeRow("我的预算", HeaderDisplayMode.Default, settings) {
            showHeaderMenu = false
            onHeaderModeChange(HeaderDisplayMode.Default)
        }
        MeowDivider()
        MeowRow("自定义", settings.headerCustomText.ifBlank { "点击设置文字" }, onClick = {
            showHeaderMenu = false
            onOpenSettings()
        })
    }

    MenuSheet(show = showTripSwitch, title = "账本切换", onDismiss = { showTripSwitch = false }) {
        MeowRow(
            title = headerText,
            summary = if (!isTripMode) "当前" else null,
            onClick = {
                isTripMode = false
                showTripSwitch = false
            },
        )
        MeowDivider()
        MeowRow(
            title = tripName,
            summary = if (isTripMode) "当前" else null,
            onClick = {
                isTripMode = true
                showTripSwitch = false
            },
        )
    }

    // 计算器
    val type = calcType
    if (type != null) {
        CalculatorSheet(
            type = type,
            balance = state.balance,
            initialBudget = state.initialBudget,
            tripBalance = state.tripBalance,
            isTripMode = isTripMode,
            onDismiss = { calcType = null },
            onSubmit = { recordType, amount, note ->
                scope.launch {
                    when (recordType) {
                        RecordType.TripIncome -> store.addTripRecord(income = true, amount = amount, note = note)
                        RecordType.TripExpense -> store.addTripRecord(income = false, amount = amount, note = note)
                        RecordType.TripAdd -> store.addTripBudget(amount)
                        RecordType.TripStart -> store.startTrip(amount)
                        else -> store.addRecord(recordType, amount, note)
                    }
                    calcType = null
                    if (recordType == RecordType.TripStart) isTripMode = true
                }
            },
        )
    }

    // 本月预算初始化
    if (state.needsInit) {
        InitBudgetSheet(
            month = store.month,
            onConfirm = { amount -> scope.launch { store.setInitialBudget(amount) } },
        )
    }

    ConfirmDialog(
        show = confirmEndTrip,
        title = "结束出行",
        message = "确定结束本次出行，并把余额结转回主预算吗？",
        confirmText = "结束",
        onConfirm = {
            confirmEndTrip = false
            scope.launch {
                store.endTrip()
                isTripMode = false
            }
        },
        onDismiss = { confirmEndTrip = false },
    )

    ConfirmDialog(
        show = confirmAdvance,
        title = "预支下月",
        message = "请谨慎考虑是否预支下个月的预算。",
        confirmText = "继续",
        onConfirm = {
            confirmAdvance = false
            calcType = RecordType.Advance
        },
        onDismiss = { confirmAdvance = false },
    )

    if (showRenameTrip) {
        InputDialog(
            show = showRenameTrip,
            title = "自定义出行名称",
            initialValue = state.activeTrip?.data?.tripName ?: "",
            placeholder = "例如：国庆杭州",
            maxLength = 10,
            onConfirm = { name ->
                showRenameTrip = false
                scope.launch { store.updateTripName(name) }
            },
            onDismiss = { showRenameTrip = false },
        )
    }
}

private fun tripMonth(iso: String): Int = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).monthValue
}.getOrDefault(1)

@Composable
private fun HeaderPill(
    text: String,
    showChevron: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .pointerInput(text) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            },
        cornerRadius = 22.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
            )
            if (showChevron) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "›",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun BigMoney(amount: Double, color: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "¥",
            style = MiuixTheme.textStyles.title3,
            color = color,
        )
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = amount.formatMoney(),
            fontSize = 46.sp,
            color = color,
            style = MiuixTheme.textStyles.title1,
        )
    }
}

@Composable
private fun QuickAction(
    label: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(modifier = modifier, onClick = onClick, cornerRadius = 18.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            icon()
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlusMinusIcon(plus: Boolean, color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.4.dp.toPx()
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height / 2),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height / 2),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        if (plus) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.2f),
                end = androidx.compose.ui.geometry.Offset(size.width / 2, size.height * 0.8f),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun AvatarImage(path: String) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "头像",
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        )
    } else {
        Icon(
            imageVector = MiuixIcons.Settings,
            contentDescription = "设置",
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MenuSheet(
    show: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            content()
        }
    }
}

@Composable
private fun HeaderModeRow(
    title: String,
    mode: HeaderDisplayMode,
    settings: MeowSettings,
    onPicked: () -> Unit,
) {
    MeowRow(
        title = title,
        summary = if (settings.headerDisplayMode == mode) "当前" else null,
        onClick = onPicked,
    )
}
