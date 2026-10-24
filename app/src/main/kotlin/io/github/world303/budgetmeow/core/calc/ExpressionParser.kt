package io.github.world303.budgetmeow.core.calc

/**
 * 递归下降的四则运算解析器，移植自 Web 端 safeEval.js。
 *
 * 为什么不用 eval：Web 端最初用过 new Function()，等于把用户输入当代码执行。
 * 这里只允许数字、+ - * / × ÷、小数点与括号，其余字符直接判为非法。
 */
object ExpressionParser {

    private val ALLOWED = Regex("^[0-9+\\-*/×÷.()\\s]+$")

    fun areBracketsBalanced(expr: String): Boolean {
        if (expr.isEmpty()) return true
        var depth = 0
        for (ch in expr) {
            when (ch) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth < 0) return false
        }
        return depth == 0
    }

    fun hasEmptyBrackets(expr: String): Boolean =
        expr.isNotEmpty() && Regex("\\(\\s*\\)").containsMatchIn(expr)

    /** 是否包含「除以 0」这一步运算 */
    fun isDivisionByZero(expr: String): Boolean {
        if (expr.isEmpty() || !ALLOWED.matches(expr)) return false
        if (hasEmptyBrackets(expr)) return false
        val parser = Parser(expr)
        parser.parse()
        return parser.divByZero
    }

    /** 求值；非法表达式或非法结果返回 null（Web 端返回 0，这里用 null 让上层能区分） */
    fun evaluate(expr: String): Double? {
        if (expr.isEmpty() || !ALLOWED.matches(expr)) return null
        if (hasEmptyBrackets(expr)) return null
        val parser = Parser(expr)
        val value = parser.parse()
        if (parser.divByZero) return null
        if (value.isNaN() || value.isInfinite()) return null
        return value
    }

    private class Parser(raw: String) {
        val expr: String = raw.replace(Regex("\\s+"), "")
        var pos: Int = 0
        var divByZero: Boolean = false

        fun parse(): Double {
            val result = parseAddSub()
            return if (pos < expr.length) Double.NaN else result
        }

        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (pos < expr.length) {
                when (expr[pos]) {
                    '+' -> { pos++; left += parseMulDiv() }
                    '-' -> { pos++; left -= parseMulDiv() }
                    else -> return left
                }
            }
            return left
        }

        private fun parseMulDiv(): Double {
            var left = parseUnary()
            while (pos < expr.length) {
                when (expr[pos]) {
                    '*', '×' -> { pos++; left *= parseUnary() }
                    '/', '÷' -> {
                        pos++
                        val right = parseUnary()
                        if (right == 0.0) {
                            divByZero = true
                            return Double.NaN
                        }
                        left /= right
                    }
                    else -> return left
                }
            }
            return left
        }

        private fun parseUnary(): Double {
            if (pos < expr.length && expr[pos] == '-') {
                pos++
                return -parsePrimary()
            }
            if (pos < expr.length && expr[pos] == '+') pos++
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            if (pos < expr.length && expr[pos] == '(') {
                pos++
                val result = parseAddSub()
                if (pos < expr.length && expr[pos] == ')') pos++
                return result
            }
            return parseNumber()
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < expr.length && (expr[pos].isDigit() || expr[pos] == '.')) pos++
            if (start == pos) return Double.NaN
            return expr.substring(start, pos).toDoubleOrNull() ?: Double.NaN
        }
    }
}
