package net.bible.sharedcore.calculator

/** Evaluates the controller's normalized keypad expressions without the JVM-only exp4j dependency. */
object CalculatorEvaluator {
    fun evaluate(expression: String): EvalResult = try {
        val value = Parser(expression).parse()
        if (!value.isFinite()) EvalResult.DivByZero else EvalResult.Ok(format(value))
    } catch (_: ArithmeticException) {
        EvalResult.DivByZero
    } catch (_: IllegalArgumentException) {
        EvalResult.Malformed
    }

    // Android scales Double.toString() with BigDecimal HALF_UP, not the binary double itself.
    // Round the decimal digits directly to preserve that distinction on Native as well as JVM.
    private fun format(value: Double): String {
        val raw = value.toString().lowercase()
        val negative = raw.startsWith('-')
        val unsigned = raw.removePrefix("-")
        val mantissa = unsigned.substringBefore('e')
        val exponent = unsigned.substringAfter('e', "0").toInt()
        val digits = mantissa.replace(".", "")
        val decimalPoint = mantissa.substringBefore('.').length + exponent
        val whole = if (decimalPoint <= 0) "0" else digits.take(decimalPoint).padEnd(decimalPoint, '0')
        val fraction = if (decimalPoint < 0) "0".repeat(-decimalPoint) + digits else digits.drop(decimalPoint)
        var scaled = (whole + fraction.padEnd(9, '0').take(8)).trimStart('0').ifEmpty { "0" }
        if (fraction.getOrElse(8) { '0' } >= '5') {
            val chars = scaled.toCharArray()
            var index = chars.lastIndex
            while (index >= 0 && chars[index] == '9') { chars[index] = '0'; index-- }
            scaled = if (index < 0) "1" + chars.concatToString() else {
                chars[index] = chars[index] + 1
                chars.concatToString()
            }
        }
        val padded = scaled.padStart(9, '0')
        val result = (padded.dropLast(8) + "." + padded.takeLast(8)).trimEnd('0').trimEnd('.')
        return if (negative && result != "0") "-$result" else result
    }

    private class Parser(private val expression: String) {
        private var position = 0

        fun parse(): Double {
            val result = sum()
            require(position == expression.length)
            return result
        }

        private fun sum(): Double {
            var result = product()
            while (true) {
                result = when {
                    take('+') -> result + product()
                    take('-') -> result - product()
                    else -> return result
                }
            }
        }

        private fun product(): Double {
            var result = unary()
            while (true) {
                result = when {
                    take('*') -> result * unary()
                    take('/') -> {
                        val divisor = unary()
                        if (divisor == 0.0) throw ArithmeticException()
                        result / divisor
                    }
                    else -> return result
                }
            }
        }

        private fun unary(): Double = when {
            take('+') -> unary()
            take('-') -> -unary()
            take('(') -> sum().also { require(take(')')) }
            else -> {
                val start = position
                while (position < expression.length && (expression[position] in '0'..'9' || expression[position] == '.')) position++
                require(position > start)
                requireNotNull(expression.substring(start, position).toDoubleOrNull())
            }
        }

        private fun take(char: Char): Boolean {
            if (expression.getOrNull(position) != char) return false
            position++
            return true
        }
    }
}
