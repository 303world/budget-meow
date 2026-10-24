package io.github.world303.budgetmeow.feature.history

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.model.BudgetRecord
import io.github.world303.budgetmeow.core.store.BudgetStore
import io.github.world303.budgetmeow.ui.component.EmptyHint
import io.github.world303.budgetmeow.ui.component.InputDialog
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowPage
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * 复合搜索：关键词 + 金额区间 + 日期区间。
 * 日期用文本框输入（yyyy-MM-dd），另给「本月/近7天/近30天/不限」快捷区间。
 */
@Composable
fun SearchScreen(
    store: BudgetStore,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }
    var fromText by remember { mutableStateOf("") }
    var toText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<BudgetRecord>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BudgetRecord?>(null) }

    fun applyRange(days: Int) {
        val today = LocalDate.now()
        val from = if (days <= 0) today.withDayOfMonth(1) else today.minusDays((days - 1).toLong())
        fromText = from.format(dayFormatter)
        toText = today.format(dayFormatter)
    }

    fun runSearch() {
        scope.launch {
            loading = true
            results = store.search(
                BudgetStore.SearchQuery(
                    keyword = keyword,
                    amountMin = minText.toDoubleOrNull(),
                    amountMax = maxText.toDoubleOrNull(),
                    dateFrom = fromText.ifBlank { null },
                    dateTo = toText.ifBlank { null },
                ),
            )
            loading = false
            searched = true
        }
    }

    MeowPage(
        title = "搜索记录",
        onBack = onBack,
        snackbar = snackbar,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                TextField(
                    value = keyword,
                    onValueChange = { keyword = it.take(30) },
                    label = "备注关键词",
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = minText,
                        onValueChange = { minText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "最小金额",
                        useLabelAsPlaceholder = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = maxText,
                        onValueChange = { maxText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "最大金额",
                        useLabelAsPlaceholder = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = fromText,
                        onValueChange = { fromText = it.take(10) },
                        label = "开始日期",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextField(
                        value = toText,
                        onValueChange = { toText = it.take(10) },
                        label = "结束日期",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Chip("本月") { applyRange(0) }
                    Chip("近 7 天") { applyRange(7) }
                    Chip("近 30 天") { applyRange(30) }
                    Chip("不限日期") {
                        fromText = ""
                        toText = ""
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { runSearch() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(text = if (loading) "搜索中…" else "搜索", color = MiuixTheme.colorScheme.onPrimary)
                    }
                    TextButton(
                        text = "清空",
                        onClick = {
                            keyword = ""
                            minText = ""
                            maxText = ""
                            fromText = ""
                            toText = ""
                            results = emptyList()
                            searched = false
                        },
                        modifier = Modifier.width(88.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (searched) {
                Text(
                    text = "共 ${results.size} 条结果",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (searched && results.isEmpty()) {
                EmptyHint("没有符合条件的记录，换个关键词或放宽金额试试。")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { record ->
                        RecordRow(
                            record = record,
                            selected = false,
                            selecting = false,
                            onClick = { editing = record },
                            onLongClick = {},
                        )
                        MeowDivider()
                    }
                }
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
                    results = results.map {
                        if (it.id == editingRecord.id) it.copy(data = it.data.copy(note = note)) else it
                    }
                }
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Card(cornerRadius = 14.dp, onClick = onClick) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = if (text.startsWith("不限")) MeowPalette.Blue else MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
