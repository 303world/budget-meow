package io.github.world303.budgetmeow.core.calc

import kotlin.math.abs

const val MAX_EXPRESSION_LENGTH = 30
const val MAX_AMOUNT = 99_999_999.99
const val MAX_NOTE_LENGTH = 50

/** 计算器按下一次键后的完整状态 */
data class CalcState(
    val expression: String = "",
    val cursor: Int = 0,
    val justEvaluated: Boolean = false,
) {
    val isBlank: Boolean get() = expression.isEmpty()

    /** 预览值：null 表示当前表达式还算不出数（或以 0 做除数） */
    val preview: Double? get() = ExpressionParser.evaluate(cleanTrailingOperators(expression))

    val hasDivisionByZero: Boolean get() = ExpressionParser.isDivisionByZero(cleanTrailingOperators(expression))

    val isBalanced: Boolean get() = ExpressionParser.areBracketsBalanced(expression)

    val hasEmptyBrackets: Boolean get() = ExpressionParser.hasEmptyBrackets(expression)

    fun displayText(): String = expression.map { toDisplayChar(it) }.joinToString("")
}

object Keypad {
    const val CLEAR = "C"
    const val DELETE = "DEL"
    const val EQUALS = "="

    val rows: List<List<String>> = listOf(
        listOf("(", ")", DELETE, CLEAR),
        listOf("7", "8", "9", "/"),
        listOf("4", "5", "6", "*"),
        listOf("1", "2", "3", "-"),
        listOf("0", ".", EQUALS, "+"),
    )
}

private val OPERATORS = setOf('+', '-', '*', '/')

fun isOperator(ch: Char): Boolean = ch in OPERATORS

fun cleanTrailingOperators(expr: String): String {
    var end = expr.length
    while (end > 0 && isOperator(expr[end - 1])) end--
    return expr.substring(0, end)
}

fun hasTrailingOperator(expr: String): Boolean = expr.isNotEmpty() && isOperator(expr.last())

/** 光标所在的数字片段（用于小数点查重） */
fun currentNumberSegment(expr: String, pos: Int): String {
    val before = expr.substring(0, pos.coerceIn(0, expr.length))
    for (i in before.indices.reversed()) {
        if (isOperator(before[i]) || before[i] == '(') return before.substring(i + 1)
    }
    return before
}

fun isExpressionStartAt(expr: String, pos: Int): Boolean =
    expr.isEmpty() || pos == 0 || expr[pos - 1] == '('

fun toDisplayChar(ch: Char): Char = when (ch) {
    '*' -> '×'
    '/' -> '÷'
    else -> ch
}

/**
 * 计算器输入状态机，逐行移植 Web 端 CalculatorPanel.handleKey。
 *
 * 这些规则看着琐碎，但每一条都对应线上踩过的坑（连续运算符、按等号后再输入、
 * 小数点重复、开头不能是加减乘除、长度上限 30），移植时不要"顺手优化"。
 */
object CalculatorEngine {

    fun onKey(state: CalcState, key: String): CalcState = when (key) {
        Keypad.CLEAR -> CalcState()
        Keypad.DELETE -> delete(state)
        Keypad.EQUALS -> evaluate(state)
        else -> input(state, key)
    }

    private fun delete(state: CalcState): CalcState {
        if (state.cursor <= 0) return state.copy(justEvaluated = false)
        val next = state.expression.removeRange(state.cursor - 1, state.cursor)
        return CalcState(
            expression = next,
            cursor = state.cursor - 1,
            justEvaluated = false,
        )
    }

    private fun evaluate(state: CalcState): CalcState {
        val cleaned = cleanTrailingOperators(state.expression)
        if (cleaned.isEmpty()) return state
        val value = ExpressionParser.evaluate(cleaned) ?: return state
        val text = formatCalcNumber(value)
        return CalcState(expression = text, cursor = text.length, justEvaluated = true)
    }

    private fun input(state: CalcState, key: String): CalcState {
        if (key.length != 1) return state
        val expr = state.expression
        val pos = state.cursor.coerceIn(0, expr.length)
        val op = key.length == 1 && isOperator(key[0])
        val bracket = key == "(" || key == ")"

        // 刚按过等号：运算符接着用，数字/小数点/括号则重开一段
        if (state.justEvaluated) {
            return when {
                op -> CalcState(expression = expr + key, cursor = expr.length + 1)
                key == "." -> CalcState(expression = "0.", cursor = 2)
                bracket -> CalcState(expression = key, cursor = 1)
                else -> CalcState(expression = key, cursor = 1)
            }
        }

        // 括号不占长度上限，其余字符受限
        if (expr.length >= MAX_EXPRESSION_LENGTH && !op && !bracket) return state

        if (bracket) {
            return CalcState(expr.substring(0, pos) + key + expr.substring(pos), pos + 1)
        }

        if (op && isExpressionStartAt(expr, pos)) {
            // 表达式开头只允许负号
            if (key == "-") return CalcState(expr.substring(0, pos) + key + expr.substring(pos), pos + 1)
            return state
        }

        if (op && pos > 0) {
            val prev = expr[pos - 1]
            if (isOperator(prev)) {
                // 已有运算符时：负号允许叠加成 a*-b，其它运算符直接替换
                if (key == "-" && prev != '-') {
                    return CalcState(expr.substring(0, pos) + key + expr.substring(pos), pos + 1)
                }
                val secondPrev = if (pos >= 2) expr[pos - 2] else null
                if (secondPrev != null && isOperator(secondPrev)) {
                    val next = expr.substring(0, pos - 2) + key + expr.substring(pos)
                    return CalcState(next, pos - 1)
                }
                val next = expr.substring(0, pos - 1) + key + expr.substring(pos)
                return CalcState(next, pos)
            }
            return CalcState(expr.substring(0, pos) + key + expr.substring(pos), pos + 1)
        }

        if (key == ".") {
            if (currentNumberSegment(expr, pos).contains('.')) return state
            return CalcState(expr.substring(0, pos) + key + expr.substring(pos), pos + 1)
        }

        if (op) {
            return CalcState(expr.substring(0, pos) + key + expr.substring(pos), pos + 1)
        }

        if (!key[0].isDigit()) return state
        return CalcState(expr.substring(0, pos) + key + expr.substring(pos), pos + 1)
    }

    fun moveCursor(state: CalcState, delta: Int): CalcState =
        state.copy(cursor = (state.cursor + delta).coerceIn(0, state.expression.length))

    fun setCursor(state: CalcState, pos: Int): CalcState =
        state.copy(cursor = pos.coerceIn(0, state.expression.length))
}

/** 计算器显示用：整数不带小数点，小数最多 10 位并去掉尾随 0 */
fun formatCalcNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    val rounded = java.math.BigDecimal.valueOf(value)
        .setScale(10, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return if (rounded.compareTo(java.math.BigDecimal.ZERO) == 0) "0" else rounded.toPlainString()
}
