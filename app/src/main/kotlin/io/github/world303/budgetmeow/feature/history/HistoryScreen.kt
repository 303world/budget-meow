package io.github.world303.budgetmeow.feature.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.model.BudgetRecord
import io.github.world303.budgetmeow.core.model.BudgetSummary
import io.github.world303.budgetmeow.core.model.RecordType
import io.github.world303.budgetmeow.core.model.formatMoney
import io.github.world303.budgetmeow.core.store.BudgetStore
import io.github.world303.budgetmeow.feature.home.formatRecordTime
import io.github.world303.budgetmeow.ui.component.ConfirmDialog
import io.github.world303.budgetmeow.ui.component.EmptyHint
import io.github.world303.budgetmeow.ui.component.InputDialog
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowPage
import io.github.world303.budgetmeow.ui.component.MeowRow
import io.github.world303.budgetmeow.ui.component.MeowToast
import io.github.world303.budgetmeow.ui.component.MoneyText
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.YearMonth

fun recordLabel(type: RecordType): String = when (type) {
    RecordType.Income -> "收入"
    RecordType.Expense -> "支出"
    RecordType.Save -> "省钱"
    RecordType.Carryover -> "结转"
    RecordType.Advance -> "预支"
    RecordType.TripStart -> "开启出行"
    RecordType.TripAdd -> "增加出行"
    RecordType.TripEnd -> "出行结转"
    RecordType.TripIncome -> "出行收入"
    RecordType.TripExpense -> "出行支出"
    RecordType.Init -> "初始预算"
    RecordType.Adjust -> "调整预算"
    RecordType.CarryoverIncome -> "上月结转"
    RecordType.AdvanceDeduct -> "上月预支"
    RecordType.Debug -> "调试修改"
}

fun recordColor(type: RecordType): Color = when (type) {
    RecordType.Income, RecordType.CarryoverIncome, RecordType.TripIncome -> MeowPalette.Green
    RecordType.Expense, RecordType.TripExpense -> MeowPalette.Red
    RecordType.Save, RecordType.TripStart, RecordType.TripAdd -> MeowPalette.Blue
    RecordType.Carryover, RecordType.Advance, RecordType.TripEnd -> MeowPalette.Orange
    else -> MeowPalette.Orange
}

@Composable
fun RecordRow(
    record: BudgetRecord,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val data = record.data
    val color = recordColor(data.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(record.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            SelectionDot(selected = selected)
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = data.note.ifBlank { recordLabel(data.type) },
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${recordLabel(data.type)} · ${formatRecordTime(data.createdAt)}",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        MoneyText(
            amount = kotlin.math.abs(data.amount),
            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
            color = color,
            showCurrency = false,
        )
    }
}

@Composable
private fun ChartIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.width(22.dp).height(22.dp)) {
        val barWidth = size.width * 0.2f
        val gap = size.width * 0.14f
        val heights = listOf(0.5f, 0.95f, 0.72f)
        heights.forEachIndexed { index, ratio ->
            val left = size.width * 0.08f + index * (barWidth + gap)
            val barHeight = size.height * ratio
            drawRoundRect(
                color = tint,
                topLeft = androidx.compose.ui.geometry.Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
            )
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    val color = if (selected) MeowPalette.Blue else MiuixTheme.colorScheme.dividerLine
    Canvas(modifier = Modifier.width(20.dp).height(20.dp)) {
        drawCircle(color = color, radius = size.minDimension / 2)
        if (selected) {
            drawCircle(color = Color.White, radius = size.minDimension / 5)
        }
    }
}

/**
 * 历史记录。支持翻月——换月后旧账不会被覆盖，这里能看到每个月的流水与当月预算摘要。
 */
@Composable
fun HistoryScreen(
    store: BudgetStore,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentMonth = store.month
    var month by remember { mutableStateOf(currentMonth) }
    var records by remember { mutableStateOf<List<BudgetRecord>>(emptyList()) }
    var summary by remember { mutableStateOf<BudgetSummary?>(null) }

    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editing by remember { mutableStateOf<BudgetRecord?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRevert by remember { mutableStateOf(false) }
    var actionRecord by remember { mutableStateOf<BudgetRecord?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(month, store) {
        records = store.recordsOfMonth(month)
        summary = store.summaryOfMonth(month)
        loaded = true
        selected = emptySet()
        selecting = false
    }

    val isCurrentMonth = month == currentMonth
    val selectedRecords = records.filter { it.id in selected }

    MeowPage(
        title = if (selecting) "已选 ${selected.size} 条" else "历史记录",
        onBack = {
            if (selecting) {
                selecting = false
                selected = emptySet()
            } else {
                onBack()
            }
        },
        actions = {
            if (!selecting) {
                IconButton(onClick = onOpenStats) {
                    ChartIcon(
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { contentDescription = "统计" },
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = "搜索",
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        bottomBar = {
            if (selecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        text = "撤回",
                        onClick = {
                            val revertable = selectedRecords.filter { store.isRevertable(it) }
                            when {
                                !isCurrentMonth -> MeowToast.warning("历史月份不支持撤回，只能删除")
                                revertable.isEmpty() ->
                                    MeowToast.warning("选中的记录不能撤回：初始预算、结转、出行开启/结束属于系统性记录")
                                else -> confirmRevert = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selected.isNotEmpty(),
                    )
                    TextButton(
                        text = "删除",
                        onClick = { confirmDelete = true },
                        modifier = Modifier.weight(1f),
                        enabled = selected.isNotEmpty(),
                        colors = ButtonDefaults.textButtonColorsPrimary(
                            color = MeowPalette.Red,
                            textColor = Color.White,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MonthHeader(
                month = month,
                summary = summary,
                isCurrentMonth = isCurrentMonth,
                onPrev = { month = YearMonth.parse(month).minusMonths(1).toString() },
                onNext = {
                    val next = YearMonth.parse(month).plusMonths(1).toString()
                    if (next <= currentMonth) month = next
                },
            )

            if (loaded && records.isEmpty()) {
                EmptyHint(
                    if (isCurrentMonth) {
                        "本月还没有记录。\n回到首页，点「支出」记第一笔。"
                    } else {
                        "这个月没有记录。"
                    },
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(records, key = { it.id }) { record ->
                        RecordRow(
                            record = record,
                            selected = record.id in selected,
                            selecting = selecting,
                            onClick = {
                                if (selecting) {
                                    selected = if (record.id in selected) {
                                        selected - record.id
                                    } else {
                                        selected + record.id
                                    }
                                } else {
                                    actionRecord = record
                                }
                            },
                            onLongClick = {
                                selecting = true
                                selected = selected + record.id
                            },
                        )
                        MeowDivider()
                    }
                }
            }
        }
    }

    val action = actionRecord
    if (action != null) {
        val revertable = isCurrentMonth && store.isRevertable(action)
        OverlayBottomSheet(
            show = true,
            title = "记录操作",
            onDismissRequest = { actionRecord = null },
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                MeowRow(
                    title = "修改备注",
                    summary = action.data.note.ifBlank { "无备注" },
                    onClick = {
                        actionRecord = null
                        editing = action
                    },
                )
                MeowDivider()
                MeowRow(
                    title = "撤回这条",
                    summary = when {
                        !isCurrentMonth -> "历史月份不支持撤回，只能删除"
                        !store.isRevertable(action) -> "初始预算 / 结转 / 出行开启·结束 属于系统性记录，不能撤回"
                        else -> "做一笔反向记账，把余额和汇总还原"
                    },
                    showArrow = revertable,
                    onClick = if (revertable) {
                        {
                            actionRecord = null
                            scope.launch {
                                store.revertRecord(action)
                                records = store.recordsOfMonth(month)
                                summary = store.summaryOfMonth(month)
                            }
                        }
                    } else {
                        null
                    },
                )
                MeowDivider()
                MeowRow(
                    title = "删除这条",
                    summary = "只从列表里去掉，不改余额",
                    onClick = {
                        actionRecord = null
                        scope.launch {
                            store.batchDelete(listOf(action))
                            records = store.recordsOfMonth(month)
                            summary = store.summaryOfMonth(month)
                        }
                    },
                )
            }
        }
    }

    val editingRecord = editing
    if (editingRecord != null) {
        InputDialog(
            show = true,
            title = "修改备注",
            initialValue = editingRecord.data.note,
            placeholder = "最多 50 字",
            maxLength = 50,
            onConfirm = { note ->
                editing = null
                scope.launch {
                    store.updateRecordNote(editingRecord, note)
                    records = store.recordsOfMonth(month)
                }
            },
            onDismiss = { editing = null },
        )
    }

    ConfirmDialog(
        show = confirmDelete,
        title = "删除记录",
        message = "删除不会改动余额，只是把这几条流水从列表里去掉。要连余额一起还原请用「撤回」。",
        confirmText = "删除",
        destructive = true,
        onConfirm = {
            confirmDelete = false
            val targets = selectedRecords
            selecting = false
            selected = emptySet()
            scope.launch {
                store.batchDelete(targets)
                records = store.recordsOfMonth(month)
                summary = store.summaryOfMonth(month)
            }
        },
        onDismiss = { confirmDelete = false },
    )

    ConfirmDialog(
        show = confirmRevert,
        title = "撤回记录",
        message = "撤回会做一笔反向记账，把余额和汇总字段都还原。",
        confirmText = "撤回",
        onConfirm = {
            confirmRevert = false
            val targets = selectedRecords
            selecting = false
            selected = emptySet()
            scope.launch {
                store.batchRevert(targets)
                records = store.recordsOfMonth(month)
                summary = store.summaryOfMonth(month)
            }
        },
        onDismiss = { confirmRevert = false },
    )
}

@Composable
private fun MonthHeader(
    month: String,
    summary: BudgetSummary?,
    isCurrentMonth: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        cornerRadius = 16.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev) {
                    Icon(
                        imageVector = MiuixIcons.ChevronBackward,
                        contentDescription = "上个月",
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = month,
                    style = MiuixTheme.textStyles.headline2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (isCurrentMonth) "本月" else "历史月份",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                IconButton(onClick = onNext, enabled = !isCurrentMonth) {
                    Icon(
                        imageVector = MiuixIcons.ChevronForward,
                        contentDescription = "下个月",
                        tint = if (isCurrentMonth) {
                            MiuixTheme.colorScheme.disabledOnSurface
                        } else {
                            MiuixTheme.colorScheme.onSurface
                        },
                    )
                }
            }
            val data = summary?.data
            if (data != null) {
                MeowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MiniStat("初始预算", data.initialBudget)
                    MiniStat("当前余额", data.currentBalance)
                    MiniStat("结转下月", data.carryOverToNext)
                    MiniStat("预支下月", data.advanceForNext)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Double) {
    Column {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "¥${value.formatMoney()}",
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}
