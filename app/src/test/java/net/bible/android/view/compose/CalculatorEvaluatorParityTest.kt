package net.bible.android.view.compose

import net.bible.sharedcore.calculator.CalculatorEvaluator
import net.bible.sharedcore.calculator.EvalResult
import net.objecthunter.exp4j.ExpressionBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class CalculatorEvaluatorParityTest {
    @Test
    fun commonEvaluatorMatchesUnchangedAndroidEngine() {
        val expressions = listOf(
            "1+2", "7/2", "2+3*4", "(2+3)*4", "1/3", "2/3",
            "1.234567895", "-1.234567895", "0.000000005", "0.000000004",
            "1000000000000*10", "0-0", "50/100*8", "2*-3", "1--2",
            "1/0", "0/0", "1/(2-2)", "", "1+", "(2+3", "1..2", "abc", "()",
        )
        for (expression in expressions) {
            assertEquals(expression, androidEvaluate(expression), CalculatorEvaluator.evaluate(expression))
        }
        // Exercise decimal carry, negatives and both sides of half-up boundaries.
        for (numerator in -100..100) {
            for (divisor in listOf(3, 7, 128, 200000000)) {
                val expression = "$numerator/$divisor"
                assertEquals(expression, androidEvaluate(expression), CalculatorEvaluator.evaluate(expression))
            }
        }
    }

    private fun androidEvaluate(expression: String): EvalResult = try {
        val raw = ExpressionBuilder(expression).build().evaluate().toString()
        if (raw == "Infinity" || raw == "-Infinity" || raw == "NaN") EvalResult.DivByZero else {
            val scaled = BigDecimal(raw).setScale(8, RoundingMode.HALF_UP).toPlainString()
            EvalResult.Ok(scaled.replace(Regex("\\.?0*$"), ""))
        }
    } catch (_: ArithmeticException) {
        EvalResult.DivByZero
    } catch (_: Exception) {
        EvalResult.Malformed
    }
}
