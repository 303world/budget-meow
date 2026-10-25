package io.github.world303.budgetmeow.feature.stats

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.model.MonthStats
import io.github.world303.budgetmeow.core.model.YearStats
import io.github.world303.budgetmeow.core.model.formatMoney
import io.github.world303.budgetmeow.core.store.BudgetStore
import io.github.world303.budgetmeow.ui.component.InputDialog
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowPage
import io.github.world303.budgetmeow.ui.component.MeowRow
import io.github.world303.budgetmeow.ui.component.MeowSection
import io.github.world303.budgetmeow.ui.component.MoneyText
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

@Composable
fun StatsScreen(
    store: BudgetStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var monthStats by remember { mutableStateOf<MonthStats?>(null) }
    var yearStats by remember { mutableStateOf<YearStats?>(null) }
    var showCalibrate by remember { mutableStateOf(false) }
    val month = store.month
    val year = LocalDate.now().year

    LaunchedEffect(tab, month) {
        monthStats = store.monthStats(month)
        if (yearStats == null) yearStats = store.yearStats(year)
    }
    LaunchedEffect(tab) {
        if (tab == 1) yearStats = store.yearStats(year)
    }

    MeowPage(
        title = "统计",
        onBack = onBack,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                tabs = listOf("本月", "年度"),
                selectedTabIndex = tab,
                onTabSelected = { tab = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                if (tab == 0) {
                    val stats = monthStats
                    if (stats == null) {
                        Text(
                            text = "统计中…",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        StatsCard(month, stats)
                        MeowSection(title = "出行") {
                            MeowRow(
                                title = "出行次数",
                                trailingText = "${stats.tripCount} 次",
                                summary = "每次「结束出行」记一次",
                            )
                            MeowDivider()
                            MeowRow(
                                title = "出行支出",
                                trailingText = "¥${stats.tripExpense.formatMoney()}",
                            )
                            MeowDivider()
                            MeowRow(
                                title = "校准出行次数",
                                summary = "历史数据补录过的话，这里可以手动对齐",
                                showArrow = true,
                                onClick = { showCalibrate = true },
                            )
                        }
                    }
                } else {
                    val stats = yearStats
                    if (stats == null) {
                        Text(
                            text = "统计中…",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        YearChartCard(year, stats)
                        MeowSection(title = "$year 年合计") {
                            MeowRow("总收入", trailingText = "¥${stats.totalIncome.formatMoney()}")
                            MeowDivider()
                            MeowRow("总支出", trailingText = "¥${stats.totalExpense.formatMoney()}")
                            MeowDivider()
                            MeowRow("总省钱", trailingText = "¥${stats.totalSaved.formatMoney()}")
                            MeowDivider()
                            MeowRow("出行次数", trailingText = "${stats.tripCount} 次")
                        }
                    }
                }
            }
        }
    }

    if (showCalibrate) {
        InputDialog(
            show = true,
            title = "校准本月出行次数",
            initialValue = (monthStats?.tripCount ?: 0).toString(),
            placeholder = "输入次数",
            maxLength = 3,
            keyboardType = KeyboardType.Number,
            onConfirm = { value ->
                showCalibrate = false
                val count = value.toIntOrNull() ?: 0
                scope.launch {
                    store.saveTripCountOverride(month, count)
                    monthStats = store.monthStats(month)
                }
            },
            onDismiss = { showCalibrate = false },
        )
    }
}

@Composable
private fun StatsCard(month: String, stats: MonthStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        cornerRadius = 18.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = month,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("收入", stats.income, MeowPalette.Green, Modifier.weight(1f))
                StatCell("支出", stats.expense, MeowPalette.Red, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("净支出", stats.netExpense, MiuixTheme.colorScheme.onSurface, Modifier.weight(1f))
                StatCell("省钱", stats.saved, MeowPalette.Blue, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, amount: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "¥${amount.formatMoney()}",
            style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

@Composable
private fun YearChartCard(year: Int, stats: YearStats) {
    val maxValue = (stats.months.maxOfOrNull { maxOf(it.income, it.expense) } ?: 0.0)
        .coerceAtLeast(1.0)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        cornerRadius = 18.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$year 年收支趋势",
                    style = MiuixTheme.textStyles.headline2,
                )
                Spacer(modifier = Modifier.width(12.dp))
                LegendDot("收入", MeowPalette.Green)
                Spacer(modifier = Modifier.width(8.dp))
                LegendDot("支出", MeowPalette.Red)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                stats.months.forEach { point ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Bar(point.income / maxValue, MeowPalette.Green, Modifier.weight(1f))
                            Bar(point.expense / maxValue, MeowPalette.Red, Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${point.monthNumber}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(fraction: Double, color: Color, modifier: Modifier = Modifier) {
    val height = (fraction.coerceIn(0.0, 1.0) * 120).dp
    Box(
        modifier = modifier
            .height(if (height < 2.dp) 2.dp else height)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(color.copy(alpha = 0.85f)),
    )
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
