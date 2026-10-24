package io.github.world303.budgetmeow.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowRow
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 本月预算初始化。第一次进入当月、或导入备份后没有当月汇总时出现，
 * 不可能通过返回键绕过（allowDismiss = false），与 Web 端强制引导一致。
 */
@Composable
fun InitBudgetSheet(
    month: String,
    onConfirm: (Double) -> Unit,
    onImportBackup: () -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = input.toDoubleOrNull()

    OverlayBottomSheet(
        show = true,
        title = "开启 $month 预算",
        allowDismiss = false,
        onDismissRequest = {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "本月还没有预算。先定一个数，之后每一笔收支都会从它里面扣。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = input,
                onValueChange = {
                    input = it.filter { ch -> ch.isDigit() || ch == '.' }
                    error = null
                },
                label = "本月初始预算",
                useLabelAsPlaceholder = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = error!!,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MeowPalette.Red,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    when {
                        amount == null || amount <= 0 -> error = "请输入大于 0 的金额"
                        else -> onConfirm(amount)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = "开启本月预算", color = MiuixTheme.colorScheme.onPrimary)
            }

            Spacer(modifier = Modifier.height(12.dp))
            MeowDivider(startPadding = 0)
            MeowRow(
                title = "从备份恢复",
                summary = "导入网页版导出的 JSON 备份",
                showArrow = true,
                onClick = onImportBackup,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    text = "上次的结转与预支会自动带入",
                    onClick = {},
                    enabled = false,
                )
            }
        }
    }
}
