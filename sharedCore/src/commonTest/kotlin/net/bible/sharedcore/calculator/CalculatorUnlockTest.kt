package net.bible.sharedcore.calculator

import kotlin.test.Test
import kotlin.test.assertEquals

class CalculatorUnlockTest {
    @Test
    fun followsAndroidPinRules() {
        val cases = listOf(
            Triple("1234", null, true), Triple("4321", null, false),
            Triple("1234", "01234", true), Triple("01234", "01234", false),
            Triple("7", "", true), Triple("0", "000", true),
            Triple("", "000", false), Triple("12", "0012", true),
            Triple("000", "0", false), Triple("1234", "4321", false),
        )
        for ((entered, stored, expected) in cases) {
            assertEquals(expected, CalculatorUnlock.allows(entered, stored), "entered=$entered stored=$stored")
        }
    }

    @Test
    fun controllerDoesNotUnlockArithmeticEvenWithEmptyPin() {
        var unlocked = false
        val controller = CalculatorController(CalculatorEvaluator::evaluate, { CalculatorUnlock.allows(it, "") }, { unlocked = true })
        listOf(CalcKey.D1, CalcKey.PLUS, CalcKey.D2, CalcKey.EQUALS).forEach(controller::onKey)
        assertEquals(false, unlocked)
        assertEquals("3", controller.display.value)
        controller.onKey(CalcKey.EQUALS)
        assertEquals(false, unlocked)
    }
}
