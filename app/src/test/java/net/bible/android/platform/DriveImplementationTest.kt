package net.bible.android.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveImplementationTest {
    @Test fun `selection table`() {
        val cases = listOf(
            Triple(true, null, true),
            Triple(false, null, false),
            Triple(false, DriveImplementation.SHARED, true),
            Triple(true, DriveImplementation.LEGACY, false),
            Triple(true, "garbage", true),
            Triple(false, "garbage", false),
            Triple(false, "", false),
        )
        for ((beta, override, expected) in cases) {
            assertEquals("beta=$beta override=$override", expected, DriveImplementation.useShared(beta, override))
        }
    }

    @Test fun `constants are the documented pref values`() {
        assertEquals("google_drive_implementation", DriveImplementation.PREF_KEY)
        assertEquals("shared", DriveImplementation.SHARED)
        assertEquals("legacy", DriveImplementation.LEGACY)
    }
}
