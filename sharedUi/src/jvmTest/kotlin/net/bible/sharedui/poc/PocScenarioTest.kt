package net.bible.sharedui.poc

import kotlin.test.Test
import kotlin.test.assertEquals

class PocScenarioTest {
    @Test fun knownNames() {
        assertEquals(PocScenario.SPLIT3, PocScenario.fromName("split3"))
        assertEquals(PocScenario.HISTORY, PocScenario.fromName("history"))
    }
    @Test fun probeHasSafeReadingFallback() {
        val probe = PocScenario.fromName("probe")
        assertEquals("probe", probe.id)
        assertEquals("reading", probe.startRoute)
        assertEquals(1, probe.windowCount)
    }

    @Test fun probeEntryRequiresDebugBinaryForBothRoutes() {
        for (debug in listOf(false, true)) {
            for (flag in listOf(null, "0", "1")) {
                for (scenario in listOf(PocScenario.SINGLE, PocScenario.fromName("probe"))) {
                    assertEquals(
                        debug && (flag == "1" || scenario.id == "probe"),
                        scenario.shouldShowPlatformProbe(debug, flag),
                        "debug=$debug flag=$flag scenario=$scenario",
                    )
                }
            }
        }
    }

    @Test fun unknownScenarioFallsBackToSingle() {
        assertEquals(PocScenario.SINGLE, PocScenario.fromName("nope"))
        assertEquals(PocScenario.SINGLE, PocScenario.fromName(null))
        assertEquals(PocScenario.SINGLE, PocScenario.fromName(""))
    }
}
