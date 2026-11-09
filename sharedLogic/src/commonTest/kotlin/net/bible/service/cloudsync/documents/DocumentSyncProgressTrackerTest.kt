package net.bible.service.cloudsync.documents

import net.bible.sharedcore.cloud.DocumentSyncInterruptionReason
import net.bible.sharedcore.cloud.DocumentSyncOperation
import net.bible.sharedcore.cloud.DocumentSyncProgressStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentSyncProgressTrackerTest {
    @Test fun backToBackTerminalAndStartAreBothObserved() {
        val tracker = DocumentSyncProgressTracker()
        val statuses = mutableListOf<DocumentSyncProgressStatus>()
        val subscription = tracker.observe { statuses += it.status }
        tracker.begin(1, 1)
        tracker.operation(1, 1, DocumentSyncOperation.PUSH, "A")
        tracker.outcome(1, 1, DocumentSyncOpOutcome.Done)
        tracker.finish(1)
        tracker.begin(2, 1)
        assertEquals(listOf(DocumentSyncProgressStatus.IDLE, DocumentSyncProgressStatus.RUNNING,
            DocumentSyncProgressStatus.RUNNING, DocumentSyncProgressStatus.RUNNING,
            DocumentSyncProgressStatus.FINISHED, DocumentSyncProgressStatus.RUNNING), statuses)
        subscription.cancel()
    }

    @Test fun staleOperationCannotAlterNewSession() {
        val tracker = DocumentSyncProgressTracker()
        tracker.begin(1, 1)
        tracker.operation(1, 1, DocumentSyncOperation.DOWNLOAD, "A")
        tracker.interrupt(1, DocumentSyncInterruptionReason.USER_STOP)
        tracker.begin(2, 1)
        tracker.operation(2, 2, DocumentSyncOperation.DOWNLOAD, "B")
        val before = tracker.state.value
        tracker.bytes(1, 1, 100, 100)
        tracker.outcome(1, 1, DocumentSyncOpOutcome.Failed)
        tracker.finish(1)
        assertEquals(before, tracker.state.value)
        assertNull(tracker.state.value.bytesTransferred)
    }

    @Test fun finishedThenNextRunningBetweenRegistrationAndReplayAreBothDelivered() {
        val tracker = DocumentSyncProgressTracker()
        tracker.begin(1, 1)
        val seen = mutableListOf<Pair<Long, DocumentSyncProgressStatus>>()
        val observation = tracker.register { seen += it.sessionId to it.status }
        tracker.finish(1)
        tracker.begin(2, 1)
        assertEquals(emptyList(), seen)
        observation.replay()
        assertEquals(listOf(1L to DocumentSyncProgressStatus.RUNNING,
            1L to DocumentSyncProgressStatus.FINISHED, 2L to DocumentSyncProgressStatus.RUNNING), seen)
        observation.cancel()
    }

    @Test fun observeDuringTransitionDoesNotReplayOlderState() {
        val tracker = DocumentSyncProgressTracker()
        val revisions = mutableListOf<Long>()
        var nested: net.bible.sharedcore.event.Subscription? = null
        val changes = tracker.changes.subscribe {
            if (nested == null) nested = tracker.observe { p -> revisions += p.revision }
        }
        tracker.begin(1, 1)
        tracker.finish(1)
        assertEquals(listOf(1L, 2L), revisions)
        changes.cancel()
        nested!!.cancel()
    }

    @Test fun replayBuffersReentrantTransitionsWithoutDuplicates() {
        val tracker = DocumentSyncProgressTracker()
        val revisions = mutableListOf<Long>()
        val subscription = tracker.observe {
            revisions += it.revision
            if (it.revision == 0L) { tracker.begin(1, 1); tracker.finish(1); tracker.begin(2, 1) }
        }
        assertEquals(listOf(0L, 1L, 2L, 3L), revisions)
        subscription.cancel()
    }

    @Test fun acceptanceBatchPublishesInOrderOutsideProducerLock() {
        val tracker = DocumentSyncProgressTracker()
        var accepting = false
        val revisions = mutableListOf<Long>()
        val subscription = tracker.observe {
            kotlin.test.assertFalse(accepting, "observer ran inside producer transaction")
            revisions += it.revision
        }
        tracker.accepting {
            accepting = true
            tracker.begin(1, 1)
            tracker.finish(1)
            tracker.begin(2, 1)
            accepting = false
        }
        assertEquals(listOf(0L, 1L, 2L, 3L), revisions)
        subscription.cancel()
    }

    @Test fun operationTokensAndTerminalTokensAreRejected() {
        val tracker = DocumentSyncProgressTracker()
        tracker.begin(1, 2)
        tracker.operation(1, 1, DocumentSyncOperation.DOWNLOAD, "A")
        tracker.outcome(1, 1, DocumentSyncOpOutcome.Done)
        tracker.operation(1, 2, DocumentSyncOperation.DOWNLOAD, "B")
        val before = tracker.state.value
        tracker.bytes(1, 1, 100, 100)
        tracker.outcome(1, 1, DocumentSyncOpOutcome.Failed)
        tracker.operation(1, 1, DocumentSyncOperation.PUSH, "stale")
        assertEquals(before, tracker.state.value)
        tracker.interrupt(1, DocumentSyncInterruptionReason.USER_STOP)
        val terminal = tracker.state.value
        tracker.finish(1)
        tracker.enqueue(1, 2)
        tracker.bytes(1, 2, 50, 100)
        tracker.outcome(1, 2, DocumentSyncOpOutcome.Done)
        assertEquals(terminal, tracker.state.value)
    }

    @Test fun failureDominatesDeferredAtFinish() {
        val tracker = DocumentSyncProgressTracker()
        tracker.begin(1, 2)
        tracker.operation(1, 1, DocumentSyncOperation.PUSH, "A")
        tracker.outcome(1, 1, DocumentSyncOpOutcome.RetryLater)
        tracker.operation(1, 2, DocumentSyncOperation.PUSH, "B")
        tracker.outcome(1, 2, DocumentSyncOpOutcome.Failed)
        tracker.finish(1)
        assertEquals(DocumentSyncProgressStatus.FAILED, tracker.state.value.status)
        assertEquals(2, tracker.state.value.processed)
        assertEquals(1, tracker.state.value.failed)
        assertEquals(1, tracker.state.value.deferred)
    }
}
