package net.bible.service.cloudsync.drivebrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveAuthChoiceTest {
    @Test fun allEightCombinations() {
        val B = DriveAuthChoice.BROWSER
        val P = DriveAuthChoice.PLAY
        // (playServicesAvailable, browserTokenStored, appLinkAvailable) -> choice
        val table = mapOf(
            Triple(true, true, true) to B, Triple(true, true, false) to B, // sticky
            Triple(false, true, true) to B, Triple(false, true, false) to B, // sticky
            Triple(true, false, true) to P, Triple(true, false, false) to P, // Play services win
            Triple(false, false, true) to B, // runtime fallback
            Triple(false, false, false) to P, // discrete without Play services: as before
        )
        assertEquals(8, table.size)
        for ((input, expected) in table) {
            assertEquals(input.toString(), expected, chooseDriveAuth(input.first, input.second, input.third))
        }
    }
}
