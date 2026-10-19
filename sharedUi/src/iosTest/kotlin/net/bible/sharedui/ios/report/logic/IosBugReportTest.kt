package net.bible.sharedui.ios.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosBugReportTest {
    @Test fun fallsBackToShareWithoutMail() {
        assertEquals(ReportTransport.SHARE, reportTransport(false))
        assertEquals(ReportTransport.MAIL, reportTransport(true))
    }
    @Test fun aiReportSkipsEmptyLog() {
        assertFalse(aiReportEligible(true, byteArrayOf()))
        assertFalse(aiReportEligible(false, byteArrayOf(1)))
        assertTrue(aiReportEligible(true, byteArrayOf(1)))
    }
    @Test fun reportIncludesLastCrashStack() {
        val body = reportDetails(ReportDevice("org.test", "1.2", "iOS 18", "iPad"), 123L, "crash\nat actual.call", IllegalStateException("current"))
        listOf("org.test", "1.2", "iOS 18", "iPad", "123", "crash\nat actual.call", "current").forEach { assertTrue(it in body, "Missing $it") }
    }
    @Test fun recoveryRetainsStackAfterCrashTimeReset() {
        assertTrue("saved stack" in reportDetails(ReportDevice("id", "v", "os", "device"), 0, "saved stack", null))
    }
}
