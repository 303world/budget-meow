package io.github.world303.budgetmeow.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.model.formatMoney
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 标准页面骨架：顶栏 + 返回 + 右侧动作 + 提示条 */
@Composable
fun MeowPage(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        
        content = content,
    )
}

/** 分组卡片：可选小标题 + 一张卡片，卡片内部由调用方排版 */
@Composable
fun MeowSection(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            SmallTitle(
                text = title,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun MeowDivider(startPadding: Int = 16) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startPadding.dp),
        color = MiuixTheme.colorScheme.dividerLine,
    )
}

/** 一行设置项 */
@Composable
fun MeowRow(
    title: String,
    summary: String? = null,
    trailingText: String? = null,
    showArrow: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MiuixTheme.textStyles.body1)
            if (!summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
        if (showArrow) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.height(18.dp),
            )
        }
    }
}

@Composable
fun MeowSwitchRow(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MeowRow(
        title = title,
        summary = summary,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/** 金额文本 */
@Composable
fun MoneyText(
    amount: Double,
    style: TextStyle = MiuixTheme.textStyles.title2,
    color: Color = Color.Unspecified,
    showCurrency: Boolean = true,
    signed: Boolean = false,
) {
    val text = if (signed) {
        val sign = if (amount > 0) "+" else if (amount < 0) "-" else ""
        sign + kotlin.math.abs(amount).formatMoney()
    } else {
        amount.formatMoney()
    }
    Text(
        text = if (showCurrency) "¥$text" else text,
        style = style,
        color = color,
    )
}

@Composable
fun EmptyHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 二次确认弹窗 */
@Composable
fun ConfirmDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String = "确认",
    dismissText: String = "取消",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    OverlayDialog(
        show = show,
        title = title,
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = if (destructive) {
                    ButtonDefaults.textButtonColorsPrimary(color = MeowPalette.Red, textColor = Color.White)
                } else {
                    ButtonDefaults.textButtonColorsPrimary()
                },
            )
        }
    }
}

/** 单行输入弹窗（昵称、自定义顶栏、备注、金额） */
@Composable
fun InputDialog(
    show: Boolean,
    title: String,
    initialValue: String,
    placeholder: String,
    maxLength: Int = 50,
    keyboardType: KeyboardType = KeyboardType.Text,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    var value by remember(show, initialValue) { mutableStateOf(initialValue) }
    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = value,
                onValueChange = { value = it.take(maxLength) },
                label = placeholder,
                useLabelAsPlaceholder = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${value.length}/$maxLength",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "确定",
                    onClick = { onConfirm(value) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
