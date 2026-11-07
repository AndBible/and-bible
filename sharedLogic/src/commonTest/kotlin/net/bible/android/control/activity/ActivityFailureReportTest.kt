package net.bible.android.control.activity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ActivityFailureReportTest {
    @Test
    fun failureReportMessageAndCause() {
        val original = IllegalStateException("network")
        val item = ActivityItem(
            id = "kjv", kind = ActivityKind.DOWNLOAD, title = "KJV", state = ActivityState.FAILED,
            progress = null, message = "Download of KJV failed", cancel = null,
            startedAt = 0, finishedAt = 1, read = false, reportable = true, cause = original,
        )
        val report = ActivityFailureReport(item)
        assertEquals("DOWNLOAD failed: KJV: Download of KJV failed", report.message)
        assertSame(original, report.cause)
    }
}
