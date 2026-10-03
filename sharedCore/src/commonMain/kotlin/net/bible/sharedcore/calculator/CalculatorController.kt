/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedcore.calculator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The 19 calculator keys. Glyphs shown on the buttons live in the composable; the display text
 *  uses the app's internal operator glyphs ("x", "÷", "%") exactly as the classic activity. */
enum class CalcKey {
    D0, D1, D2, D3, D4, D5, D6, D7, D8, D9,
    PLUS, MINUS, TIMES, DIV, PERCENT, PARENS, DOT, CLEAR, EQUALS,
}

/**
 * A user-facing calculator error, mirroring the three Toasts the classic `CalculatorActivity`
 * showed. The composable maps these to `LocalStrings.current` (`calcWrongFormat` /
 * `calcWrongFormatOperand` / `calcDivisionByZero`) so the strings pipeline is exercised at runtime.
 */
enum class CalcError { WRONG_FORMAT, WRONG_FORMAT_OPERAND, DIVISION_BY_ZERO }

/**
 * Outcome of the injected arithmetic engine. Richer than a plain `String?` so the controller can
 * tell a division-by-zero apart from a general parse/eval failure and raise the matching
 * [CalcError] — the classic activity conflated these (see `CalculatorController` docs).
 */
sealed interface EvalResult {
    data class Ok(val value: String) : EvalResult
    data object DivByZero : EvalResult
    data object Malformed : EvalResult
}

/**
 * Pure calculator display/expression logic shared with the classic `CalculatorActivity`. It is a
 * faithful, framework-free port of that activity's state machine (`addNumber` / `addOperand` /
 * `addParenthesis` / `addDot` / `calculate` / `saveLastExpression`), so the disguise keypad behaves
 * identically whether rendered by the XML view or Compose.
 *
 * The two things it must NOT know are injected as seams, keeping the real PIN and the arithmetic
 * engine (exp4j + BigDecimal, JVM-only) in `:app`:
 *  - [evaluate]: takes the already-normalised ASCII expression (`%`→`/100`, `x`→`*`, `÷`→`/`)
 *    and returns an [EvalResult] — [EvalResult.Ok] with the formatted result string,
 *    [EvalResult.DivByZero], or [EvalResult.Malformed]. Both failure results leave the display
 *    unchanged and raise a [CalcError] (mirroring the old exception / division-by-zero paths).
 *  - [isPin]: whether the raw display equals the (leading-zero-stripped) unlock PIN, or the PIN is
 *    blank. The controller adds the "…and the input is not an arithmetic operation" guard itself.
 *  - [onUnlock]: performs the `setResult(RESULT_OK); finish()` unlock.
 *
 * Mirrors `CalculatorActivity.calculate()` (see that file for the exact original semantics). The
 * one deliberate improvement: the classic activity's `calc_division_by_zero` Toast was in fact
 * dead code (`BigDecimal("Infinity")` threw first, so a div-by-zero surfaced as `calc_wrong_format`);
 * routing div-by-zero through [EvalResult.DivByZero] restores that string's intended meaning.
 *
 * [error] holds the current [CalcError] (or `null`). It is cleared on the next key press, so a
 * fresh entry hides the previous error — matching the transient Toast feedback of the old activity.
 */
class CalculatorController(
    private val evaluate: (expr: String) -> EvalResult,
    private val isPin: (input: String) -> Boolean,
    private val onUnlock: () -> Unit,
) {
    private val _display = MutableStateFlow("")
    val display: StateFlow<String> = _display.asStateFlow()

    private val _error = MutableStateFlow<CalcError?>(null)
    val error: StateFlow<CalcError?> = _error.asStateFlow()

    private var text: String
        get() = _display.value
        set(value) { _display.value = value }

    private var openParenthesis = 0
    private var dotUsed = false
    private var equalClicked = false
    private var lastExpression = ""

    fun onKey(key: CalcKey) {
        // A fresh key press hides any previous error; the handlers below may raise a new one.
        _error.value = null
        when (key) {
            CalcKey.D0 -> { if (addNumber("0")) equalClicked = false }
            CalcKey.D1 -> { if (addNumber("1")) equalClicked = false }
            CalcKey.D2 -> { if (addNumber("2")) equalClicked = false }
            CalcKey.D3 -> { if (addNumber("3")) equalClicked = false }
            CalcKey.D4 -> { if (addNumber("4")) equalClicked = false }
            CalcKey.D5 -> { if (addNumber("5")) equalClicked = false }
            CalcKey.D6 -> { if (addNumber("6")) equalClicked = false }
            CalcKey.D7 -> { if (addNumber("7")) equalClicked = false }
            CalcKey.D8 -> { if (addNumber("8")) equalClicked = false }
            CalcKey.D9 -> { if (addNumber("9")) equalClicked = false }
            CalcKey.PLUS -> { if (addOperand("+")) equalClicked = false }
            CalcKey.MINUS -> { if (addOperand("-")) equalClicked = false }
            CalcKey.TIMES -> { if (addOperand("x")) equalClicked = false }
            CalcKey.DIV -> { if (addOperand("÷")) equalClicked = false }
            CalcKey.PERCENT -> { if (addOperand("%")) equalClicked = false }
            CalcKey.PARENS -> { if (addParenthesis()) equalClicked = false }
            CalcKey.DOT -> { if (addDot()) equalClicked = false }
            CalcKey.CLEAR -> {
                text = ""
                lastExpression = ""
                openParenthesis = 0
                dotUsed = false
                equalClicked = false
            }
            CalcKey.EQUALS -> { if (text != "") calculate(text) }
        }
    }

    private fun addDot(): Boolean {
        var done = false
        if (text.isEmpty()) {
            text = "0."
            dotUsed = true
            done = true
        } else if (dotUsed) {
            // already has a dot in the current number → ignore
        } else if (defineLastCharacter(text[text.length - 1].toString()) == IS_OPERAND) {
            text += "0."
            done = true
            dotUsed = true
        } else if (defineLastCharacter(text[text.length - 1].toString()) == IS_NUMBER) {
            text += "."
            done = true
            dotUsed = true
        }
        return done
    }

    private fun addParenthesis(): Boolean {
        var done = false
        val operationLength = text.length
        if (operationLength == 0) {
            text += "("
            dotUsed = false
            openParenthesis++
            done = true
        } else if (openParenthesis > 0 && operationLength > 0) {
            val lastInput = text[operationLength - 1].toString()
            when (defineLastCharacter(lastInput)) {
                IS_NUMBER -> {
                    text += ")"
                    done = true
                    openParenthesis--
                    dotUsed = false
                }
                IS_OPERAND -> {
                    text += "("
                    done = true
                    openParenthesis++
                    dotUsed = false
                }
                IS_OPEN_PARENTHESIS -> {
                    text += "("
                    done = true
                    openParenthesis++
                    dotUsed = false
                }
                IS_CLOSE_PARENTHESIS -> {
                    text += ")"
                    done = true
                    openParenthesis--
                    dotUsed = false
                }
            }
        } else if (openParenthesis == 0 && operationLength > 0) {
            val lastInput = text[operationLength - 1].toString()
            if (defineLastCharacter(lastInput) == IS_OPERAND) {
                text += "("
                done = true
                dotUsed = false
                openParenthesis++
            } else {
                text += "x("
                done = true
                dotUsed = false
                openParenthesis++
            }
        }
        return done
    }

    private fun addOperand(operand: String): Boolean {
        var done = false
        val operationLength = text.length
        if (operationLength > 0) {
            val lastInput = text[operationLength - 1].toString()
            if (lastInput == "+" || lastInput == "-" || lastInput == "*" || lastInput == "÷" || lastInput == "%") {
                // wrong format (operator after operator) → CalculatorActivity showed calc_wrong_format.
                _error.value = CalcError.WRONG_FORMAT
            } else if (operand == "%" && defineLastCharacter(lastInput) == IS_NUMBER) {
                text += operand
                dotUsed = false
                equalClicked = false
                lastExpression = ""
                done = true
            } else if (operand != "%") {
                text += operand
                dotUsed = false
                equalClicked = false
                lastExpression = ""
                done = true
            }
        } else {
            // operationLength == 0 → CalculatorActivity showed calc_wrong_format_operand.
            _error.value = CalcError.WRONG_FORMAT_OPERAND
        }
        return done
    }

    private fun addNumber(number: String): Boolean {
        var done = false
        val operationLength = text.length
        if (operationLength > 0) {
            val lastCharacter = text[operationLength - 1].toString()
            val lastCharacterState = defineLastCharacter(lastCharacter)
            if (operationLength == 1 && lastCharacterState == IS_NUMBER && lastCharacter == "0") {
                text = number
                done = true
            } else if (lastCharacterState == IS_OPEN_PARENTHESIS) {
                text += number
                done = true
            } else if (lastCharacterState == IS_CLOSE_PARENTHESIS || lastCharacter == "%") {
                text += "x$number"
                done = true
            } else if (lastCharacterState == IS_NUMBER || lastCharacterState == IS_OPERAND || lastCharacterState == IS_DOT) {
                text += number
                done = true
            }
        } else {
            text += number
            done = true
        }
        return done
    }

    private fun calculate(input: String) {
        val isOperation = checkIfOperation(input) || checkIfOperation(lastExpression)
        if (isPin(input) && !isOperation) {
            onUnlock()
            return
        }
        val temp = if (equalClicked) input + lastExpression else { saveLastExpression(input); input }
        val expr = temp
            .replace("%", "/100")
            .replace("x", "*")
            .replace(Regex("[^\\x00-\\x7F]"), "/")
        when (val result = evaluate(expr)) {
            is EvalResult.Ok -> {
                equalClicked = true
                text = result.value
            }
            // Both failures leave the display unchanged (as the old activity did) and raise the
            // matching CalcError for the composable to render via LocalStrings.
            EvalResult.DivByZero -> _error.value = CalcError.DIVISION_BY_ZERO
            EvalResult.Malformed -> _error.value = CalcError.WRONG_FORMAT
        }
    }

    private fun saveLastExpression(input: String) {
        if (input.isEmpty()) {
            lastExpression = ""
            return
        }
        val lastOfExpression = input[input.length - 1].toString()
        if (input.length > 1) {
            if (lastOfExpression == ")") {
                lastExpression = ")"
                var numberOfCloseParenthesis = 1
                for (i in input.length - 2 downTo 0) {
                    if (numberOfCloseParenthesis > 0) {
                        val last = input[i].toString()
                        if (last == ")") {
                            numberOfCloseParenthesis++
                        } else if (last == "(") {
                            numberOfCloseParenthesis--
                        }
                        lastExpression = last + lastExpression
                    } else if (defineLastCharacter(input[i].toString()) == IS_OPERAND) {
                        lastExpression = input[i].toString() + lastExpression
                        break
                    } else {
                        lastExpression = ""
                    }
                }
            } else if (defineLastCharacter(lastOfExpression) == IS_NUMBER) {
                lastExpression = lastOfExpression
                for (i in input.length - 2 downTo 0) {
                    val last = input[i].toString()
                    if (defineLastCharacter(last) == IS_NUMBER || defineLastCharacter(last) == IS_DOT) {
                        lastExpression = last + lastExpression
                    } else if (defineLastCharacter(last) == IS_OPERAND) {
                        lastExpression = last + lastExpression
                        break
                    }
                    if (i == 0) {
                        lastExpression = ""
                    }
                }
            }
        }
    }

    /** True if the input contains an arithmetic operator (+ - x ÷ %). Parentheses are not operators. */
    private fun checkIfOperation(input: String): Boolean = input.contains(Regex("[+\\-x÷%]"))

    private fun defineLastCharacter(lastCharacter: String): Int {
        if (lastCharacter.length == 1 && lastCharacter[0] in '0'..'9') return IS_NUMBER
        if (lastCharacter == "+" || lastCharacter == "-" || lastCharacter == "x" || lastCharacter == "÷" || lastCharacter == "%") return IS_OPERAND
        if (lastCharacter == "(") return IS_OPEN_PARENTHESIS
        if (lastCharacter == ")") return IS_CLOSE_PARENTHESIS
        return if (lastCharacter == ".") IS_DOT else EXCEPTION
    }

    private companion object {
        const val EXCEPTION = -1
        const val IS_NUMBER = 0
        const val IS_OPERAND = 1
        const val IS_OPEN_PARENTHESIS = 2
        const val IS_CLOSE_PARENTHESIS = 3
        const val IS_DOT = 4
    }
}
