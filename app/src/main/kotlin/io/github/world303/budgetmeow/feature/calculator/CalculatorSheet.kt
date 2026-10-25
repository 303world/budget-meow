package io.github.world303.budgetmeow.feature.calculator

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.world303.budgetmeow.core.calc.CalcState
import io.github.world303.budgetmeow.core.calc.CalculatorEngine
import io.github.world303.budgetmeow.core.calc.ConfirmRules
import io.github.world303.budgetmeow.core.calc.Keypad
import io.github.world303.budgetmeow.core.calc.MAX_AMOUNT
import io.github.world303.budgetmeow.core.calc.MAX_NOTE_LENGTH
import io.github.world303.budgetmeow.core.calc.cleanTrailingOperators
import io.github.world303.budgetmeow.core.calc.formatCalcNumber
import io.github.world303.budgetmeow.core.model.RecordType
import io.github.world303.budgetmeow.core.model.formatMoney
import io.github.world303.budgetmeow.core.model.round2
import io.github.world303.budgetmeow.ui.component.ConfirmDialog
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import io.github.world303.budgetmeow.ui.theme.meowGlassSurface
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 记账计算器面板。
 *
 * 与 Web 端一致的三件事：
 * 1. 表达式可以点选光标位置，运算符高亮
 * 2. 长按表达式后左右滑动，会出现圆形放大镜（本实现直接把手指位置映射成光标）
 * 3. 提交前按 [ConfirmRules] 做二次确认，负数、除零、括号不闭合一律拦下
 */
@Composable
fun CalculatorSheet(
    glassEffect: Boolean,
    type: RecordType,
    balance: Double,
    initialBudget: Double,
    tripBalance: Double,
    isTripMode: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (RecordType, Double, String) -> Unit,
) {
    var state by remember(type) { mutableStateOf(CalcState()) }
    var note by remember(type) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmMessage by remember { mutableStateOf<String?>(null) }
    var pendingAmount by remember { mutableStateOf(0.0) }

    fun validate(): String? {
        val expr = cleanTrailingOperators(state.expression)
        return when {
            expr.isEmpty() -> "请输入金额"
            !state.isBalanced -> "括号没有闭合"
            state.hasEmptyBrackets -> "不能出现空括号"
            state.hasDivisionByZero -> "不能除以 0"
            state.preview == null -> "表达式不合法"
            state.preview!! <= 0 -> "金额必须大于 0"
            state.preview!! > MAX_AMOUNT -> "单笔金额上限 ¥${MAX_AMOUNT.formatMoney()}"
            else -> null
        }
    }

    val previewValue = state.preview
    val previewText = when {
        state.hasDivisionByZero -> "= 不能除以 0"
        previewValue == null -> if (state.expression.isEmpty()) "" else "= 表达式不合法"
        else -> {
            val number = formatCalcNumber(previewValue)
            if (previewValue < 0) "= $number（负数无法记账）" else "= $number"
        }
    }
    val previewColor = when {
        state.hasDivisionByZero || previewValue == null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        previewValue < 0 -> MeowPalette.Red
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    OverlayBottomSheet(
        show = true,
        title = ConfirmRules.title(type),
        backgroundColor = if (glassEffect) Color.Transparent else MiuixTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .then(
                    if (glassEffect) {
                        Modifier.meowGlassSurface(
                            shape = RoundedCornerShape(26.dp),
                            enabled = true,
                            blurRadius = 30f,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 14.dp),
        ) {
            ExpressionDisplay(
                state = state,
                previewText = previewText,
                previewColor = previewColor,
                onCursorChange = { state = CalculatorEngine.setCursor(state, it) },
            )

            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                value = note,
                onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
                label = "备注",
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${note.length}/$MAX_NOTE_LENGTH",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                textAlign = TextAlign.End,
            )

            Spacer(modifier = Modifier.height(10.dp))
            KeypadGrid { key ->
                state = CalculatorEngine.onKey(state, key)
                error = null
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error!!,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MeowPalette.Red,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val problem = validate()
                    if (problem != null) {
                        error = problem
                        return@Button
                    }
                    val amount = state.preview!!.round2()
                    val message = ConfirmRules.message(
                        type = type,
                        amount = amount,
                        balance = balance,
                        initialBudget = initialBudget,
                        isTripMode = isTripMode,
                        tripBalance = tripBalance,
                    )
                    if (message == null) {
                        onSubmit(type, amount, note.trim())
                    } else {
                        pendingAmount = amount
                        confirmMessage = message
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(text = "确定", color = MiuixTheme.colorScheme.onPrimary)
            }
        }
    }

    val message = confirmMessage
    ConfirmDialog(
        show = message != null,
        title = ConfirmRules.confirmTitle(type),
        message = message ?: "",
        confirmText = "记录",
        onConfirm = {
            confirmMessage = null
            onSubmit(type, pendingAmount, note.trim())
        },
        onDismiss = { confirmMessage = null },
    )
}

@Composable
private fun ExpressionDisplay(
    state: CalcState,
    previewText: String,
    previewColor: Color,
    onCursorChange: (Int) -> Unit,
) {
    val display = state.displayText()
    val isEmpty = display.isEmpty()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val primary = MiuixTheme.colorScheme.primary
    val textStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
    val cursorAlpha by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
    )

    val annotated = remember(display) {
        buildAnnotatedString {
            val text = if (display.isEmpty()) "0" else display
            text.forEach { ch ->
                if (ch == '×' || ch == '÷' || ch == '+' || ch == '-') {
                    withStyle(SpanStyle(color = primary)) { append(ch) }
                } else {
                    append(ch)
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            val containerWidth = maxWidth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isEmpty) {
                        detectTapGestures { offset ->
                            layout?.let { l ->
                                onCursorChange(
                                    l.getOffsetForPosition(offset).coerceIn(0, l.layoutInput.text.length),
                                )
                            }
                        }
                    }
                    .pointerInput(isEmpty) {
                        // 直接拖动即可把光标挪到想去的位置
                        detectDragGestures(
                            onDragStart = { offset ->
                                layout?.let { l ->
                                    onCursorChange(
                                        l.getOffsetForPosition(offset).coerceIn(0, l.layoutInput.text.length),
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                layout?.let { l ->
                                    onCursorChange(
                                        l.getOffsetForPosition(change.position)
                                            .coerceIn(0, l.layoutInput.text.length),
                                    )
                                }
                            },
                        )
                    },
            ) {
                if (previewText.isNotEmpty()) {
                    Text(
                        text = previewText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = previewColor,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopEnd),
                    )
                }

                Text(
                    text = annotated,
                    style = textStyle,
                    color = if (isEmpty) MiuixTheme.colorScheme.onSurfaceVariantSummary else Color.Unspecified,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    onTextLayout = { layout = it },
                )

                val textLayout = layout
                if (textLayout != null) {
                    // 布局可能落后一帧，必须按布局自身长度夹取，否则越界崩溃
                    val safeCursor = state.cursor.coerceIn(0, textLayout.layoutInput.text.length)
                    val cursorRect = textLayout.getCursorRect(safeCursor)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val barWidth = 2.dp.toPx()
                        // Canvas 铺满整张卡片，而文字是底部对齐的，要补上文本的纵向偏移
                        val textTop = (size.height - textLayout.size.height).coerceAtLeast(0f)
                        drawRoundRect(
                            color = primary.copy(alpha = cursorAlpha),
                            topLeft = Offset(cursorRect.left - barWidth / 2, textTop + cursorRect.top),
                            size = Size(barWidth, cursorRect.height),
                            cornerRadius = CornerRadius(barWidth / 2),
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun KeypadGrid(onKey: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Keypad.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        modifier = Modifier.weight(1f),
                        onClick = { onKey(key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(key: String, modifier: Modifier, onClick: () -> Unit) {
    val isOperator = key == "+" || key == "-" || key == "*" || key == "/"
    val isBracket = key == "(" || key == ")"
    val color = when {
        key == Keypad.CLEAR -> MeowPalette.Red
        isOperator -> MiuixTheme.colorScheme.primary
        isBracket -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        else -> MiuixTheme.colorScheme.onSurface
    }
    val label = when (key) {
        "*" -> "×"
        "/" -> "÷"
        Keypad.DELETE -> "⌫"
        else -> key
    }
    Card(
        modifier = modifier,
        cornerRadius = 14.dp,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.title3,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
    }
}
