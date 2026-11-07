package net.bible.sharedui.ios.report

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class CrashEnvelopeFixtureSyncTest {
    @Test fun kotlinCopyEqualsTheFileTheSwiftTestPins() {
        val file = File(System.getProperty("repoRoot") ?: ".", "iosApp/test-fixtures/crash-envelope.json")
        assertEquals(file.readText(), CRASH_ENVELOPE_FIXTURE)
    }
}
