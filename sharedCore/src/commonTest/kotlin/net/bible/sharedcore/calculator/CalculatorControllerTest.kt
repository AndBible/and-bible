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
import kotlin.test.assertTrue

class CalculatorControllerTest {
    private var unlocked = false

    /**
     * Test controller. `evaluate` mimics the real host: transformed expression → result string
     * (only "2+2" is wired). `isPin` matches an exact PIN. Mirrors the real seams.
     */
    private fun controller(pin: String = "1234") = CalculatorController(
        evaluate = { expr -> if (expr == "2+2") "4" else null },
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
            evaluate = { null },
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
        // evaluate returns null (exception/Infinity in the host) → display untouched.
        val c = controller()
        c.onKey(CalcKey.D9); c.onKey(CalcKey.PLUS); c.onKey(CalcKey.D9); c.onKey(CalcKey.EQUALS)
        assertEquals("9+9", c.display.value)
    }
}
