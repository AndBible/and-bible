package net.bible.sharedui.ios.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosBugReportTest {
    @Test fun stagingFailureIsContainedAndRecoveryCanReportAgain() = kotlinx.coroutines.runBlocking {
        val failure = IllegalStateException("staging failed")
        val received = mutableListOf<Exception>()
        reportSafely({ received += it }) { throw failure }
        var retried = false
        reportSafely({ received += it }) { retried = true }
        assertEquals(listOf<Exception>(failure), received)
        assertTrue(retried)
    }
    @Test fun cancellationIsRethrownWithoutFailureFeedback() = kotlinx.coroutines.runBlocking {
        val cancelled = kotlinx.coroutines.CancellationException("cancelled")
        val caught = kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> {
            reportSafely({ error("No feedback on cancellation") }) { throw cancelled }
        }
        kotlin.test.assertSame(cancelled, caught)
    }

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
