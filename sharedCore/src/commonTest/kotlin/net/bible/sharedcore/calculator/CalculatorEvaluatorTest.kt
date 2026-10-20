package net.bible.sharedcore.calculator

import kotlin.test.Test
import kotlin.test.assertEquals

class CalculatorEvaluatorTest {
    @Test
    fun arithmeticMatchesAndroidDisplay() {
        val cases = mapOf(
            "1+2" to "3", "7/2" to "3.5", "2+3*4" to "14",
            "(2+3)*4" to "20", "1/3" to "0.33333333", "2/3" to "0.66666667",
            "1.234567895" to "1.2345679", "-1.234567895" to "-1.2345679",
            "0.000000005" to "0.00000001", "0.000000004" to "0",
            "1000000000000*10" to "10000000000000", "0-0" to "0",
            "50/100*8" to "4", "2*-3" to "-6", "1--2" to "3",
        )
        for ((expression, expected) in cases) {
            assertEquals(EvalResult.Ok(expected), CalculatorEvaluator.evaluate(expression), expression)
        }
    }

    @Test
    fun divisionByZeroMatchesAndroidError() {
        for (expression in listOf("1/0", "0/0", "1/(2-2)")) {
            assertEquals(EvalResult.DivByZero, CalculatorEvaluator.evaluate(expression), expression)
        }
    }

    @Test
    fun malformedExpressionsDoNotCalculate() {
        for (expression in listOf("", "1+", "(2+3", "1..2", "abc", "()")) {
            assertEquals(EvalResult.Malformed, CalculatorEvaluator.evaluate(expression), expression)
        }
    }
}
