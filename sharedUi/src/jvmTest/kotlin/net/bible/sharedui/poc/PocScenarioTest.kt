package net.bible.sharedui.poc

import kotlin.test.Test
import kotlin.test.assertEquals

class PocScenarioTest {
    @Test fun knownNames() {
        assertEquals(PocScenario.SPLIT3, PocScenario.fromName("split3"))
        assertEquals(PocScenario.HISTORY, PocScenario.fromName("history"))
    }
    @Test fun unknownScenarioFallsBackToSingle() {
        assertEquals(PocScenario.SINGLE, PocScenario.fromName("nope"))
        assertEquals(PocScenario.SINGLE, PocScenario.fromName(null))
        assertEquals(PocScenario.SINGLE, PocScenario.fromName(""))
    }
}
