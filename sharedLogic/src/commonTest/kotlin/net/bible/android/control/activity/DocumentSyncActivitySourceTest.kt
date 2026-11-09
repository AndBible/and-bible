package net.bible.android.control.activity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.documents.DocumentSyncOpOutcome
import net.bible.service.cloudsync.documents.DocumentSyncProgressTracker
import net.bible.sharedcore.cloud.DocumentSyncInterruptionReason
import net.bible.sharedcore.cloud.DocumentSyncOperation
import net.bible.sharedcore.cloud.DocumentSyncProgress
import net.bible.sharedcore.platform.CoreStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentSyncActivitySourceTest {
    private fun strings(): CoreStrings = ActivityTestStrings()

    @Test fun replayUpdatesAndClose() = runTest {
        val tracker = DocumentSyncProgressTracker()
        tracker.begin(1, 1)
        tracker.operation(1, 1, DocumentSyncOperation.DOWNLOAD, "PRIVATE")
        val center = ActivityCenter(backgroundScope) { currentTime }
        val source = DocumentSyncActivitySource(center, tracker, ::strings)
        advanceTimeBy(1000); runCurrent()
        tracker.bytes(1, 1, 50, 100)
        val item = center.items.value.single()
        assertEquals(50, item.progress)
        assertTrue(item.message!!.contains("PRIVATE"))
        assertFalse(item.notificationMessage!!.contains("PRIVATE"))
        assertEquals(0, center.unreadFailures.value)
        source.close()
        tracker.finish(1)
        assertTrue(center.isRunning("document-sync:1"))
    }

    @Test fun unknownDownloadTotalDoesNotProducePercentage() = runTest {
        val tracker = DocumentSyncProgressTracker()
        val center = ActivityCenter(backgroundScope) { currentTime }
        DocumentSyncActivitySource(center, tracker, ::strings)
        tracker.begin(1, 1)
        tracker.operation(1, 1, DocumentSyncOperation.DOWNLOAD, "KJV")
        advanceTimeBy(1000); runCurrent()
        tracker.bytes(1, 1, 25, 0)
        val item = center.items.value.single()
        assertEquals(null, item.progress)
        assertTrue(item.message!!.contains("25"))
    }

    @Test fun shortFinishedThenNewRunRetainsSuccess() = runTest {
        val tracker = DocumentSyncProgressTracker()
        val center = ActivityCenter(backgroundScope) { currentTime }
        DocumentSyncActivitySource(center, tracker, ::strings)
        tracker.begin(1, 1); tracker.finish(1); tracker.begin(2, 1)
        assertEquals(ActivityState.SUCCEEDED, center.items.value.single().state)
        assertTrue(center.isRunning("document-sync:2"))
        advanceTimeBy(1000); runCurrent()
        assertEquals(2, center.items.value.size)
        advanceTimeBy(59_000); runCurrent()
        assertEquals(listOf("document-sync:2"), center.items.value.map { it.id })
    }

    @Test fun terminalSnapshotIsReplayedToLateSource() = runTest {
        val tracker = DocumentSyncProgressTracker()
        tracker.begin(1, 1); tracker.finish(1)
        val center = ActivityCenter(backgroundScope) { currentTime }
        DocumentSyncActivitySource(center, tracker, ::strings)
        assertEquals(ActivityState.SUCCEEDED, center.items.value.single().state)
    }

    @Test fun interruptedRowsRespectFinishedCap() = runTest {
        val tracker = DocumentSyncProgressTracker()
        val center = ActivityCenter(backgroundScope) { currentTime }
        DocumentSyncActivitySource(center, tracker, ::strings)
        repeat(55) { n ->
            val session = n.toLong() + 1
            tracker.begin(session, 1)
            tracker.interrupt(session, DocumentSyncInterruptionReason.SCOPE_CANCELLED)
        }
        assertEquals(ActivityCenter.MAX_FINISHED, center.items.value.size)
        assertFalse(center.items.value.any { it.id == "document-sync:1" })
    }

    @Test fun explicitFailureRemovesQueueRow() = runTest {
        val tracker = DocumentSyncProgressTracker()
        val center = ActivityCenter(backgroundScope) { currentTime }
        DocumentSyncActivitySource(center, tracker, ::strings)
        tracker.begin(1, 1); tracker.infrastructureFailure(1); tracker.finish(1)
        assertTrue(center.items.value.isEmpty())
        assertEquals(0, center.unreadFailures.value)
    }

    @Test fun cancellationExpirationAndDeferredAreNeutral() = runTest {
        val tracker = DocumentSyncProgressTracker()
        val center = ActivityCenter(backgroundScope) { currentTime }
        DocumentSyncActivitySource(center, tracker, ::strings)
        tracker.begin(1, 1); tracker.interrupt(1, DocumentSyncInterruptionReason.USER_STOP)
        tracker.begin(2, 1); tracker.interrupt(2, DocumentSyncInterruptionReason.BACKGROUND_EXPIRED)
        tracker.begin(3, 1); tracker.operation(3, 1, DocumentSyncOperation.PUSH, "X")
        tracker.outcome(3, 1, DocumentSyncOpOutcome.RetryLater); tracker.finish(3)
        val items = center.items.value.associateBy { it.id }
        assertEquals(3, items.size)
        assertTrue(items.values.all { it.state == ActivityState.INTERRUPTED })
        assertFalse(items.getValue("document-sync:1").notifyInterruption)
        assertTrue(items.getValue("document-sync:2").notifyInterruption)
        assertTrue(items.getValue("document-sync:3").notifyInterruption)
        assertEquals(0, center.unreadFailures.value)
    }
}
