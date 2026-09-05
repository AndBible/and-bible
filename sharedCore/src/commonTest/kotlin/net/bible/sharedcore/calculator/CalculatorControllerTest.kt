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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalculatorControllerTest {
    private var unlocked = false

    /**
     * Test controller. `evaluate` mimics the real host: transformed expression → [EvalResult]
     * (only "2+2" is wired to Ok; everything else is Malformed). `isPin` matches an exact PIN.
     * Mirrors the real seams.
     */
    private fun controller(pin: String = "1234") = CalculatorController(
        evaluate = { expr -> if (expr == "2+2") EvalResult.Ok("4") else EvalResult.Malformed },
        isPin = { it == pin },
        onUnlock = { unlocked = true },
    )

    // --- Brief's pinned contract ---

    @Test fun digits_append_to_display() {
        val c = controller()
        c.onKey(CalcKey.D2); c.onKey(CalcKey.PLUS); c.onKey(CalcKey.D2)
        assertEquals("2+2", c.display.value)
    }

    @Test fun equals_evaluates() {
        val c = controller()
        c.onKey(CalcKey.D2); c.onKey(CalcKey.PLUS); c.onKey(CalcKey.D2); c.onKey(CalcKey.EQUALS)
        assertEquals("4", c.display.value)
    }

    @Test fun clear_resets_display() {
        val c = controller()
        c.onKey(CalcKey.D2); c.onKey(CalcKey.CLEAR)
        assertEquals("", c.display.value)
    }

    @Test fun typing_the_pin_and_equals_unlocks() {
        unlocked = false
        val c = controller("1234")
        listOf(CalcKey.D1, CalcKey.D2, CalcKey.D3, CalcKey.D4).forEach { c.onKey(it) }
        c.onKey(CalcKey.EQUALS)
        assertEquals(true, unlocked)
    }

    // --- Extra behaviour ported from CalculatorActivity ---

    @Test fun pin_with_operator_evaluates_instead_of_unlocking() {
        // "12+34" contains an operator → checkIfOperation is true → NOT a PIN unlock, evaluate instead.
        unlocked = false
        val c = controller("1234")
        c.onKey(CalcKey.D1); c.onKey(CalcKey.D2); c.onKey(CalcKey.PLUS); c.onKey(CalcKey.D3); c.onKey(CalcKey.D4)
        c.onKey(CalcKey.EQUALS)
        assertFalse(unlocked)
    }

    @Test fun leading_zero_is_replaced_by_next_digit() {
        // Old addNumber: a lone "0" is overwritten by the next digit (no "05").
        val c = controller()
        c.onKey(CalcKey.D0); c.onKey(CalcKey.D5)
        assertEquals("5", c.display.value)
    }

    @Test fun dot_on_empty_display_prefixes_zero() {
        val c = controller()
        c.onKey(CalcKey.DOT)
        assertEquals("0.", c.display.value)
    }

    @Test fun second_dot_in_same_number_is_ignored() {
        val c = controller()
        c.onKey(CalcKey.D1); c.onKey(CalcKey.DOT); c.onKey(CalcKey.D5); c.onKey(CalcKey.DOT)
        assertEquals("1.5", c.display.value)
    }

    @Test fun multiply_and_divide_use_internal_glyphs() {
        val c = controller()
        c.onKey(CalcKey.D3); c.onKey(CalcKey.TIMES); c.onKey(CalcKey.D2); c.onKey(CalcKey.DIV); c.onKey(CalcKey.D4)
        assertEquals("3x2÷4", c.display.value)
    }

    @Test fun operator_on_empty_display_is_ignored() {
        val c = controller()
        c.onKey(CalcKey.PLUS)
        assertEquals("", c.display.value)
    }

    @Test fun operator_after_operator_is_ignored() {
        val c = controller()
        c.onKey(CalcKey.D2); c.onKey(CalcKey.PLUS); c.onKey(CalcKey.MINUS)
        assertEquals("2+", c.display.value)
    }

    @Test fun parenthesis_on_empty_opens() {
        val c = controller()
        c.onKey(CalcKey.PARENS)
        assertEquals("(", c.display.value)
    }

    @Test fun parenthesis_after_number_multiplies_and_opens() {
        // Old addParenthesis: number then "( )" with no open paren → "x(".
        val c = controller()
        c.onKey(CalcKey.D2); c.onKey(CalcKey.PARENS)
        assertEquals("2x(", c.display.value)
    }

    @Test fun parenthesis_closes_after_number_inside_open() {
        val c = controller()
        c.onKey(CalcKey.PARENS); c.onKey(CalcKey.D2); c.onKey(CalcKey.PARENS)
        assertEquals("(2)", c.display.value)
    }

    @Test fun percent_after_number_appends() {
        val c = controller()
        c.onKey(CalcKey.D5); c.onKey(CalcKey.D0); c.onKey(CalcKey.PERCENT)
        assertEquals("50%", c.display.value)
    }

    @Test fun digit_after_close_paren_multiplies() {
        val c = controller()
        c.onKey(CalcKey.PARENS); c.onKey(CalcKey.D2); c.onKey(CalcKey.PARENS); c.onKey(CalcKey.D3)
        assertEquals("(2)x3", c.display.value)
    }

    @Test fun empty_pin_unlocks_on_non_operation() {
        unlocked = false
        val c = CalculatorController(
            evaluate = { EvalResult.Malformed },
            isPin = { true }, // empty-pin semantics: any non-operation input unlocks
            onUnlock = { unlocked = true },
        )
        c.onKey(CalcKey.D9); c.onKey(CalcKey.EQUALS)
        assertTrue(unlocked)
    }

    @Test fun equals_on_empty_display_does_nothing() {
        unlocked = false
        val c = controller()
        c.onKey(CalcKey.EQUALS)
        assertEquals("", c.display.value)
        assertFalse(unlocked)
    }

    @Test fun failed_evaluation_leaves_display_unchanged() {
        // evaluate returns Malformed (exception in the host) → display untouched.
        val c = controller()
        c.onKey(CalcKey.D9); c.onKey(CalcKey.PLUS); c.onKey(CalcKey.D9); c.onKey(CalcKey.EQUALS)
        assertEquals("9+9", c.display.value)
    }

    // --- Error surfacing (restores the classic activity's error feedback via LocalStrings) ---

    @Test fun no_error_initially() {
        assertNull(controller().error.value)
    }

    @Test fun malformed_expression_sets_wrong_format() {
        // CalculatorActivity.calculate() catch(Exception) → getString(calc_wrong_format).
        val c = controller()
        c.onKey(CalcKey.D9); c.onKey(CalcKey.PLUS); c.onKey(CalcKey.D9); c.onKey(CalcKey.EQUALS)
        assertEquals(CalcError.WRONG_FORMAT, c.error.value)
    }

    @Test fun operator_on_empty_display_sets_wrong_format_operand() {
        // CalculatorActivity.addOperand() else-branch (operationLength == 0) → calc_wrong_format_operand.
        val c = controller()
        c.onKey(CalcKey.PLUS)
        assertEquals(CalcError.WRONG_FORMAT_OPERAND, c.error.value)
    }

    @Test fun operator_after_operator_sets_wrong_format() {
        // CalculatorActivity.addOperand() first-branch (operator after operator) → calc_wrong_format.
        val c = controller()
        c.onKey(CalcKey.D2); c.onKey(CalcKey.PLUS); c.onKey(CalcKey.MINUS)
        assertEquals(CalcError.WRONG_FORMAT, c.error.value)
    }

    @Test fun division_by_zero_sets_division_by_zero_error() {
        // Host maps Infinity/ArithmeticException → EvalResult.DivByZero → calc_division_by_zero.
        val c = CalculatorController(
            evaluate = { EvalResult.DivByZero },
            isPin = { false },
            onUnlock = { unlocked = true },
        )
        c.onKey(CalcKey.D1); c.onKey(CalcKey.D0); c.onKey(CalcKey.DIV); c.onKey(CalcKey.D0)
        c.onKey(CalcKey.EQUALS)
        assertEquals(CalcError.DIVISION_BY_ZERO, c.error.value)
        assertEquals("10÷0", c.display.value) // display unchanged, mirroring the old activity
    }

    @Test fun next_keypress_clears_error() {
        val c = controller()
        c.onKey(CalcKey.PLUS) // WRONG_FORMAT_OPERAND
        assertEquals(CalcError.WRONG_FORMAT_OPERAND, c.error.value)
        c.onKey(CalcKey.D1) // a fresh entry hides the old error
        assertNull(c.error.value)
        assertEquals("1", c.display.value)
    }

    // --- saveLastExpression, ported from the deleted CalculatorActivityTest (S15) ---
    //
    // The classic suite called activity.saveLastExpression(input) directly and read
    // activity.lastExpression. Both are private in CalculatorController, so these drive the same
    // algorithm through the only public path that uses it: the repeat-equals branch of calculate()
    // (CalculatorController.kt:257), where a second EQUALS evaluates `display + lastExpression`.
    //
    // The recording controller returns a fixed "0" from evaluate, so the SECOND recorded expression
    // is exactly "0" + lastExpression — with calculate()'s own transforms applied to it
    // (CalculatorController.kt:258-261: "x" -> "*", any non-ASCII -> "/"). That is why the expected
    // strings below read "0*3" and "0/2" rather than "0x3" and "0÷2".

    private fun recording(): Pair<CalculatorController, MutableList<String>> {
        val seen = mutableListOf<String>()
        val c = CalculatorController(
            evaluate = { expr -> seen += expr; EvalResult.Ok("0") },
            isPin = { false },
            onUnlock = {},
        )
        return c to seen
    }

    /** Types [expression] on the keypad, asserting the display matches before pressing EQUALS. */
    private fun CalculatorController.type(expression: String) {
        expression.forEach { ch ->
            onKey(
                when (ch) {
                    in '0'..'9' -> CalcKey.entries[ch - '0']   // D0..D9 are the first ten entries
                    '+' -> CalcKey.PLUS
                    '-' -> CalcKey.MINUS
                    'x' -> CalcKey.TIMES
                    '÷' -> CalcKey.DIV
                    '%' -> CalcKey.PERCENT
                    '.' -> CalcKey.DOT
                    '(', ')' -> CalcKey.PARENS
                    else -> error("no key for '$ch'")
                }
            )
        }
        assertEquals(expression, display.value, "keypad did not produce the expression under test")
    }

    /** Presses EQUALS twice and returns what the second press handed to evaluate, minus the "0". */
    private fun lastExpressionAfter(expression: String): String {
        val (c, seen) = recording()
        c.type(expression)
        c.onKey(CalcKey.EQUALS)
        c.onKey(CalcKey.EQUALS)
        assertEquals(2, seen.size, "expected exactly two evaluations")
        assertEquals("0", c.display.value)
        assertTrue(seen[1].startsWith("0"), "second evaluation was ${seen[1]}")
        return seen[1].removePrefix("0")
    }

    @Test fun lastExpression_extracts_simple_addition() =
        assertEquals("+3", lastExpressionAfter("5+3"))

    @Test fun lastExpression_extracts_simple_subtraction() =
        assertEquals("-20", lastExpressionAfter("100-20"))

    @Test fun lastExpression_extracts_decimal_number() =
        assertEquals("-2.5", lastExpressionAfter("10-2.5"))

    @Test fun lastExpression_extracts_multiplication() =
        assertEquals("*3", lastExpressionAfter("5x3"))          // "x3", transformed by calculate()

    @Test fun lastExpression_extracts_division() =
        assertEquals("/2", lastExpressionAfter("10÷2"))         // "÷2", transformed by calculate()

    @Test fun lastExpression_extracts_parenthesised_group() =
        assertEquals("*(2+3)", lastExpressionAfter("10x(2+3)"))

    @Test fun lastExpression_extracts_nested_parentheses() =
        assertEquals("*((2+3)*4)", lastExpressionAfter("5x((2+3)x4)"))

    @Test fun lastExpression_is_empty_for_a_single_digit() =
        assertEquals("", lastExpressionAfter("5"))

    @Test fun lastExpression_is_empty_for_a_two_digit_number() =
        assertEquals("", lastExpressionAfter("80"))

    @Test fun lastExpression_is_empty_for_a_three_digit_number() =
        assertEquals("", lastExpressionAfter("123"))

    @Test fun lastExpression_takes_only_the_final_operation() =
        assertEquals("+5", lastExpressionAfter("100-20+5"))

    @Test fun lastExpression_handles_large_numbers() =
        assertEquals("+999", lastExpressionAfter("1000+999"))
}
