package net.bible.sharedui.ios.ports

import net.bible.sharedlogic.platform.ios.IosLocalCalendar
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedui.host.ports.HostReadingPlanHostPlatform

class IosReadingPlanHostPlatformTest {
    @Test fun orientationReadsTheLiveWindowRatherThanConstructionTime() {
        var portrait = true
        val port = HostReadingPlanHostPlatform(IosLocalCalendar()) { portrait }
        assertEquals(true, port.isPortrait)
        portrait = false
        assertEquals(false, port.isPortrait)
    }
}
