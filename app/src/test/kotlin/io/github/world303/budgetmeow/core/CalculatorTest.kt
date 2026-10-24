package io.github.world303.budgetmeow.core

import io.github.world303.budgetmeow.core.calc.CalcState
import io.github.world303.budgetmeow.core.calc.CalculatorEngine
import io.github.world303.budgetmeow.core.calc.ExpressionParser
import io.github.world303.budgetmeow.core.calc.Keypad
import io.github.world303.budgetmeow.core.calc.formatCalcNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionParserTest {

    @Test
    fun `四则运算优先级`() {
        assertEquals(7.0, ExpressionParser.evaluate("1+2*3")!!, 1e-9)
        assertEquals(9.0, ExpressionParser.evaluate("(1+2)*3")!!, 1e-9)
        assertEquals(2.5, ExpressionParser.evaluate("10/4")!!, 1e-9)
        assertEquals(6.0, ExpressionParser.evaluate("10-4")!!, 1e-9)
    }

    @Test
    fun `支持全角乘除号`() {
        assertEquals(12.0, ExpressionParser.evaluate("3×4")!!, 1e-9)
        assertEquals(3.0, ExpressionParser.evaluate("12÷4")!!, 1e-9)
    }

    @Test
    fun `负数与一元运算`() {
        assertEquals(-5.0, ExpressionParser.evaluate("-5")!!, 1e-9)
        assertEquals(3.0, ExpressionParser.evaluate("2-(-1)")!!, 1e-9)
        assertEquals(-6.0, ExpressionParser.evaluate("2*-3")!!, 1e-9)
    }

    @Test
    fun `非法输入返回 null`() {
        assertNull(ExpressionParser.evaluate("1+")!!)
        assertNull(ExpressionParser.evaluate("abc")!!)
        assertNull(ExpressionParser.evaluate("()")!!)
        assertNull(ExpressionParser.evaluate("1 2")!!)
        assertNull(ExpressionParser.evaluate("2/0")!!)
    }

    @Test
    fun `除以零能被识别`() {
        assertTrue(ExpressionParser.isDivisionByZero("1/0"))
        assertTrue(ExpressionParser.isDivisionByZero("5/(2-2)"))
        assertFalse(ExpressionParser.isDivisionByZero("1/2"))
    }

    @Test
    fun `括号匹配检查`() {
        assertTrue(ExpressionParser.areBracketsBalanced("(1+2)"))
        assertFalse(ExpressionParser.areBracketsBalanced("(1+2"))
        assertFalse(ExpressionParser.areBracketsBalanced("1+2)"))
    }

    @Test
    fun `数字格式化去掉多余的零`() {
        assertEquals("15", formatCalcNumber(15.0))
        assertEquals("15.5", formatCalcNumber(15.50))
        assertEquals("0", formatCalcNumber(0.0))
        assertEquals("-3", formatCalcNumber(-3.0))
        assertEquals("0.1", formatCalcNumber(0.30000000000000004 - 0.2))
    }
}

class CalculatorEngineTest {

    private fun type(keys: String): CalcState {
        var state = CalcState()
        for (key in keys.toCharArray()) {
            state = CalculatorEngine.onKey(state, key.toString())
        }
        return state
    }

    @Test
    fun `基本输入与求值`() {
        val state = type("1+2=")
        assertEquals("3", state.expression)
        assertEquals(1, state.cursor)
        assertTrue(state.justEvaluated)
    }

    @Test
    fun `连续运算符会被替换`() {
        val state = type("1+*2")
        assertEquals("1*2", state.expression)
    }

    @Test
    fun `负号可以叠加在运算符后`() {
        val state = type("2*-3")
        assertEquals("2*-3", state.expression)
    }

    @Test
    fun `开头不能是加减乘除（负号除外）`() {
        assertEquals("", type("+").expression)
        assertEquals("", type("*").expression)
        assertEquals("-", type("-").expression)
    }

    @Test
    fun `小数点不会重复`() {
        assertEquals("1.2", type("1.2").expression)
        assertEquals("1.2", type("1.2.").expression)
        assertEquals("1.2+0.", type("1.2+0.").expression)
    }

    @Test
    fun `按等号后输入数字会重新开始`() {
        var state = type("1+2=")
        state = CalculatorEngine.onKey(state, "5")
        assertEquals("5", state.expression)
    }

    @Test
    fun `按等号后输入运算符会继续计算`() {
        var state = type("1+2=")
        state = CalculatorEngine.onKey(state, "+")
        assertEquals("3+", state.expression)
    }

    @Test
    fun `表达式长度上限 30`() {
        val longNumber = "1".repeat(30)
        var state = CalcState()
        for (ch in longNumber) state = CalculatorEngine.onKey(state, ch.toString())
        assertEquals(30, state.expression.length)
        state = CalculatorEngine.onKey(state, "5")
        assertEquals(30, state.expression.length)
        // 运算符不受长度限制
        state = CalculatorEngine.onKey(state, "+")
        assertEquals(31, state.expression.length)
    }

    @Test
    fun `删除键按光标位置删除`() {
        var state = type("12+3")
        state = CalculatorEngine.setCursor(state, 1)
        state = CalculatorEngine.onKey(state, Keypad.DELETE)
        assertEquals("2+3", state.expression)
        assertEquals(0, state.cursor)
    }

    @Test
    fun `清空键复位`() {
        val state = CalculatorEngine.onKey(type("123"), Keypad.CLEAR)
        assertEquals(CalcState(), state)
    }

    @Test
    fun `除以零时不产生结果`() {
        val state = type("5/0")
        assertNull(state.preview)
        assertTrue(state.hasDivisionByZero)
    }
}
