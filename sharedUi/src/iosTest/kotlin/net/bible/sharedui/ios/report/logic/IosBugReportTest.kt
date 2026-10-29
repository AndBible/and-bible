package net.bible.sharedui.ios.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bible.sharedui.host.report.Attachment
import net.bible.sharedui.host.report.ReportDevice
import net.bible.sharedui.host.report.ReportTransport
import net.bible.sharedui.host.report.aiReportEligible
import net.bible.sharedui.host.report.reportAttachments
import net.bible.sharedui.host.report.reportDetails
import net.bible.sharedui.host.report.reportSafely
import net.bible.sharedui.host.report.reportTransport

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
    @Test fun extraAttachmentsFollowTheAppLog() {
        val extra = Attachment("legacy_migration_report.json", "application/json", byteArrayOf(1, 2))
        assertEquals(listOf("app_log.txt.gz"), reportAttachments(byteArrayOf(9), emptyList()).map { it.name })
        val both = reportAttachments(byteArrayOf(9), listOf(extra))
        assertEquals(listOf("app_log.txt.gz", "legacy_migration_report.json"), both.map { it.name })
        assertEquals(extra, both[1])
    }
}
