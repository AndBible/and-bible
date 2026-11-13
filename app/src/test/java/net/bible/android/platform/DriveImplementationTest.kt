package net.bible.android.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveImplementationTest {
    @Test fun `selection table`() {
        // Stable and beta both default to the shared adapter; only an explicit "legacy" keeps the old one.
        val cases = listOf(
            null to true,
            DriveImplementation.SHARED to true,
            DriveImplementation.LEGACY to false,
            "garbage" to true,
            "" to true,
        )
        for ((override, expected) in cases) {
            assertEquals("override=$override", expected, DriveImplementation.useShared(override))
        }
    }

    @Test fun `constants are the documented pref values`() {
        assertEquals("google_drive_implementation", DriveImplementation.PREF_KEY)
        assertEquals("shared", DriveImplementation.SHARED)
        assertEquals("legacy", DriveImplementation.LEGACY)
    }
}
