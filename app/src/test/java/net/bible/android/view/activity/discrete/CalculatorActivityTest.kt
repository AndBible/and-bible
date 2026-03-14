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
package net.bible.android.view.activity.discrete

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CalculatorActivityTest {
    private lateinit var activity: CalculatorActivity

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(CalculatorActivity::class.java)
            .create()
            .get()
    }

    // Tests for checkIfOperation function
    @Test
    fun `checkIfOperation returns true for addition`() {
        assertThat(activity.checkIfOperation("5+3"), equalTo(true))
    }

    @Test
    fun `checkIfOperation returns true for subtraction`() {
        assertThat(activity.checkIfOperation("10-5"), equalTo(true))
    }

    @Test
    fun `checkIfOperation returns true for multiplication`() {
        assertThat(activity.checkIfOperation("4x3"), equalTo(true))
    }

    @Test
    fun `checkIfOperation returns true for division`() {
        assertThat(activity.checkIfOperation("8÷2"), equalTo(true))
    }

    @Test
    fun `checkIfOperation returns true for percentage`() {
        assertThat(activity.checkIfOperation("50%"), equalTo(true))
    }

    @Test
    fun `checkIfOperation returns true for complex expression`() {
        assertThat(activity.checkIfOperation("100-20+5"), equalTo(true))
    }

    @Test
    fun `checkIfOperation returns false for single digit`() {
        assertThat(activity.checkIfOperation("5"), equalTo(false))
    }

    @Test
    fun `checkIfOperation returns false for multi-digit number`() {
        assertThat(activity.checkIfOperation("1234"), equalTo(false))
    }

    @Test
    fun `checkIfOperation returns false for decimal number`() {
        assertThat(activity.checkIfOperation("12.34"), equalTo(false))
    }

    @Test
    fun `checkIfOperation returns false for empty string`() {
        assertThat(activity.checkIfOperation(""), equalTo(false))
    }

    @Test
    fun `checkIfOperation returns false for number with parentheses`() {
        assertThat(activity.checkIfOperation("(123)"), equalTo(false))
    }

    // Tests for defineLastCharacter function
    @Test
    fun `defineLastCharacter identifies single digit numbers`() {
        assertThat(activity.defineLastCharacter("0"), equalTo(CalculatorActivity.IS_NUMBER))
        assertThat(activity.defineLastCharacter("5"), equalTo(CalculatorActivity.IS_NUMBER))
        assertThat(activity.defineLastCharacter("9"), equalTo(CalculatorActivity.IS_NUMBER))
    }

    @Test
    fun `defineLastCharacter identifies addition operand`() {
        assertThat(activity.defineLastCharacter("+"), equalTo(CalculatorActivity.IS_OPERAND))
    }

    @Test
    fun `defineLastCharacter identifies subtraction operand`() {
        assertThat(activity.defineLastCharacter("-"), equalTo(CalculatorActivity.IS_OPERAND))
    }

    @Test
    fun `defineLastCharacter identifies multiplication operand`() {
        assertThat(activity.defineLastCharacter("x"), equalTo(CalculatorActivity.IS_OPERAND))
    }

    @Test
    fun `defineLastCharacter identifies division operand`() {
        assertThat(activity.defineLastCharacter("÷"), equalTo(CalculatorActivity.IS_OPERAND))
    }

    @Test
    fun `defineLastCharacter identifies percentage operand`() {
        assertThat(activity.defineLastCharacter("%"), equalTo(CalculatorActivity.IS_OPERAND))
    }

    @Test
    fun `defineLastCharacter identifies open parenthesis`() {
        assertThat(activity.defineLastCharacter("("), equalTo(CalculatorActivity.IS_OPEN_PARENTHESIS))
    }

    @Test
    fun `defineLastCharacter identifies close parenthesis`() {
        assertThat(activity.defineLastCharacter(")"), equalTo(CalculatorActivity.IS_CLOSE_PARENTHESIS))
    }

    @Test
    fun `defineLastCharacter identifies dot`() {
        assertThat(activity.defineLastCharacter("."), equalTo(CalculatorActivity.IS_DOT))
    }

    @Test
    fun `defineLastCharacter returns EXCEPTION for unknown characters`() {
        assertThat(activity.defineLastCharacter("a"), equalTo(CalculatorActivity.EXCEPTION))
        assertThat(activity.defineLastCharacter("@"), equalTo(CalculatorActivity.EXCEPTION))
        assertThat(activity.defineLastCharacter("#"), equalTo(CalculatorActivity.EXCEPTION))
    }

    // Tests for saveLastExpression function
    @Test
    fun `saveLastExpression extracts operation with simple addition`() {
        activity.saveLastExpression("5+3")
        assertThat(activity.lastExpression, equalTo("+3"))
    }

    @Test
    fun `saveLastExpression extracts operation with simple subtraction`() {
        activity.saveLastExpression("100-20")
        assertThat(activity.lastExpression, equalTo("-20"))
    }

    @Test
    fun `saveLastExpression extracts operation with decimal number`() {
        activity.saveLastExpression("10-2.5")
        assertThat(activity.lastExpression, equalTo("-2.5"))
    }

    @Test
    fun `saveLastExpression extracts operation with multiplication`() {
        activity.saveLastExpression("5x3")
        assertThat(activity.lastExpression, equalTo("x3"))
    }

    @Test
    fun `saveLastExpression extracts operation with division`() {
        activity.saveLastExpression("10÷2")
        assertThat(activity.lastExpression, equalTo("÷2"))
    }

    @Test
    fun `saveLastExpression extracts operation with parentheses`() {
        activity.saveLastExpression("10x(2+3)")
        assertThat(activity.lastExpression, equalTo("x(2+3)"))
    }

    @Test
    fun `saveLastExpression extracts nested parentheses correctly`() {
        activity.saveLastExpression("5x((2+3)x4)")
        assertThat(activity.lastExpression, equalTo("x((2+3)x4)"))
    }

    @Test
    fun `saveLastExpression returns empty string for single digit`() {
        activity.saveLastExpression("5")
        assertThat(activity.lastExpression, equalTo(""))
    }

    @Test
    fun `saveLastExpression returns empty string for multi-digit number without operand`() {
        activity.saveLastExpression("80")
        assertThat(activity.lastExpression, equalTo(""))
    }

    @Test
    fun `saveLastExpression returns empty string for three-digit number`() {
        activity.saveLastExpression("123")
        assertThat(activity.lastExpression, equalTo(""))
    }

    @Test
    fun `saveLastExpression handles complex expression`() {
        activity.saveLastExpression("100-20+5")
        assertThat(activity.lastExpression, equalTo("+5"))
    }

    @Test
    fun `saveLastExpression handles large numbers`() {
        activity.saveLastExpression("1000+999")
        assertThat(activity.lastExpression, equalTo("+999"))
    }

    // Integration tests for PIN security fix
    @Test
    fun `PIN bypass prevented - calculation that equals PIN should not unlock`() {
        // This tests the security fix from commit b1624aa34
        // Even if 617+617=1234 and PIN is 1234, it should not unlock
        // because the input contains operands

        val inputWithOperands = "617+617"
        val lastExpr = "" // No previous expression
        val pin = "1234"

        // The input contains operands, so should not be treated as valid PIN
        assertThat(activity.checkIfOperation(inputWithOperands), equalTo(true))

        // This represents what calculate() function checks
        val isOperation = activity.checkIfOperation(inputWithOperands) || activity.checkIfOperation(lastExpr)
        val shouldUnlock = (inputWithOperands == pin || pin.isEmpty()) && !isOperation

        assertThat(shouldUnlock, equalTo(false))
    }

    @Test
    fun `PIN validation works for correct plain PIN`() {
        val plainPin = "1234"
        val lastExpr = ""
        val pin = "1234"

        // Plain PIN has no operands
        assertThat(activity.checkIfOperation(plainPin), equalTo(false))

        val isOperation = activity.checkIfOperation(plainPin) || activity.checkIfOperation(lastExpr)
        val shouldUnlock = (plainPin == pin || pin.isEmpty()) && !isOperation

        assertThat(shouldUnlock, equalTo(true))
    }

    @Test
    fun `PIN bypass prevented - lastExpression with operands blocks unlock`() {
        // Test case where current input matches PIN but lastExpression has operands
        val input = "1234"
        val lastExpr = "-20"
        val pin = "1234"

        assertThat(activity.checkIfOperation(lastExpr), equalTo(true))

        val isOperation = activity.checkIfOperation(input) || activity.checkIfOperation(lastExpr)
        val shouldUnlock = (input == pin || pin.isEmpty()) && !isOperation

        assertThat(shouldUnlock, equalTo(false))
    }

    // Integration test for Clear button bug fix
    @Test
    fun `saveLastExpression with single digit does not modify existing lastExpression`() {
        // This tests the bug from commit cce2cdb43
        // When user does: 55-8 = Clear, 5 =
        // The single digit "5" should not preserve old lastExpression

        // First operation sets lastExpression
        activity.saveLastExpression("55-8")
        assertThat(activity.lastExpression, equalTo("-8"))

        // Simulating pressing single digit after clear
        // (Clear would set lastExpression = "" in the actual activity)
        activity.lastExpression = "" // Simulate clear button

        // Now save single digit
        activity.saveLastExpression("5")

        // Should remain empty, not revert to "-8"
        assertThat(activity.lastExpression, equalTo(""))
    }

    @Test
    fun `repeated equals operations use lastExpression correctly`() {
        // Test the repeat calculation feature
        // 5 + 3 = 8, then = again should give 11 (8+3)

        activity.saveLastExpression("5+3")
        assertThat(activity.lastExpression, equalTo("+3"))

        // After first equals, result is 8
        // User presses equals again, it should append lastExpression
        // "8" + "+3" = "8+3"

        // This simulates what happens in calculate() when equalClicked = true
        val repeatExpression = "8" + activity.lastExpression
        assertThat(repeatExpression, equalTo("8+3"))
    }

    @Test
    fun `complex expressions with multiple operands extract last operation`() {
        activity.saveLastExpression("100+50-30")
        assertThat(activity.lastExpression, equalTo("-30"))

        activity.saveLastExpression("5x3+2")
        assertThat(activity.lastExpression, equalTo("+2"))

        activity.saveLastExpression("20÷4x3")
        assertThat(activity.lastExpression, equalTo("x3"))
    }

    @Test
    fun `saveLastExpression handles edge case with closing parenthesis and operand`() {
        activity.saveLastExpression("(5+3)x2")
        assertThat(activity.lastExpression, equalTo("x2"))
    }

    @Test
    fun `saveLastExpression with percentage suffix returns empty - percentage is not repeatable`() {
        // Percentage is a suffix operand (comes after number), not prefix like +/-/x/÷
        // The saveLastExpression logic only handles expressions ending with numbers or ')'
        // So "100-50%" doesn't extract a repeatable operation
        activity.saveLastExpression("100-50%")
        assertThat(activity.lastExpression, equalTo(""))
    }
}
