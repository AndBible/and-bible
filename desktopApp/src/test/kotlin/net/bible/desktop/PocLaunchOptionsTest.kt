package net.bible.desktop

import net.bible.sharedui.poc.PocScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PocLaunchOptionsTest {
    private fun parse(vararg pairs: Pair<String, String>) = PocLaunchOptions.from(mapOf(*pairs)::get)

    @Test
    fun defaultsToTheTwoPaneLightPoc() {
        assertEquals(PocLaunchOptions(PocScenario.SPLIT2, dark = false), parse())
    }

    @Test
    fun readsScenarioByIdOrNameAndTheOtherSwitches() {
        val options = parse(
            "andbible.poc.scenario" to "split3", "andbible.poc.dark" to "true", "andbible.poc.spike" to "true",
            "andbible.poc.themeFlipMs" to "1500", "andbible.poc.overlay" to "Dialog",
        )
        assertEquals(PocLaunchOptions(PocScenario.SPLIT3, true, true, 1500, "dialog"), options)
        assertEquals(PocScenario.SINGLE, parse("andbible.poc.scenario" to "SINGLE").scenario)
        assertEquals(null, parse("andbible.poc.themeFlipMs" to "0").themeFlipMs)
    }

    @Test
    fun unknownValuesFailInsteadOfFallingBack() {
        assertFailsWith<IllegalArgumentException> { parse("andbible.poc.scenario" to "split4") }
        assertFailsWith<IllegalArgumentException> { parse("andbible.poc.overlay" to "toast") }
    }
}
