@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.files

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.sharedui.app.ports.DocumentRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosIncomingFilesTest {
    @Test fun offerBeforeDrainIsDelivered() = withQueue { queue ->
        queue.offer(A)
        val delivered = mutableListOf<List<DocumentRef>>()
        queue.bind({ refs, _ -> delivered += refs }, {})
        assertEquals(listOf(listOf(DocumentRef(A))), delivered)
    }

    @Test fun nonFileUrlIgnored() = withQueue { queue ->
        val delivered = mutableListOf<List<DocumentRef>>()
        queue.bind({ refs, _ -> delivered += refs }, { error("Invalid URLs must not enter staging") })
        listOf("https://example.org/a.zip", "andbible://a.zip", "not a URL", "file:relative.zip").forEach(queue::offer)
        assertTrue(delivered.isEmpty())
        queue.offer(A)
        assertEquals(listOf(listOf(DocumentRef(A))), delivered)
    }

    @Test fun twoFilesKeepOrderAndWaitForResult() = withQueue { queue ->
        queue.offer(A)
        queue.offer(B)
        val deliveries = Deliveries()
        queue.bind(deliveries::open, {})
        assertEquals(listOf(A), deliveries.uris)
        deliveries.complete[0]()
        assertEquals(listOf(A, B), deliveries.uris)
    }

    @Test fun duplicateUrlsAreDistinctArrivalsButDuplicateAckIsIgnored() = withQueue { queue ->
        repeat(3) { queue.offer(A) }
        val deliveries = Deliveries()
        queue.bind(deliveries::open, {})
        deliveries.complete[0]()
        deliveries.complete[0]()
        assertEquals(listOf(A, A), deliveries.uris)
        deliveries.complete[1]()
        assertEquals(listOf(A, A, A), deliveries.uris)
    }

    @Test fun reentrantOfferStaysBehindAlreadyWaitingFile() = withQueue { queue ->
        queue.offer(A)
        queue.offer(B)
        val deliveries = Deliveries()
        queue.bind({ refs, done ->
            deliveries.open(refs, done)
            if (refs.single().uri == A) queue.offer(C)
        }, {})
        deliveries.complete[0]()
        deliveries.complete[1]()
        assertEquals(listOf(A, B, C), deliveries.uris)
    }

    @Test fun synchronousCompletionDoesNotLoseReentrantOffers() = withQueue { queue ->
        queue.offer(A)
        queue.offer(B)
        val seen = mutableListOf<String>()
        queue.bind({ refs, done ->
            seen += refs.single().uri
            if (refs.single().uri == A) queue.offer(C)
            done()
        }, {})
        assertEquals(listOf(A, B, C), seen)
    }

    @Test fun detachBuffersForNextHostInsteadOfCallingDeadHost() = withQueue { queue ->
        val old = Deliveries()
        val owner = queue.bind(old::open, {})
        queue.detach(owner)
        queue.offer(A)
        assertTrue(old.uris.isEmpty())
        val next = Deliveries()
        queue.bind(next::open, {})
        assertEquals(listOf(A), next.uris)
    }

    @Test fun replacedHostKeepsUnansweredFileAndIgnoresStaleDisposeAndAck() = withQueue { queue ->
        queue.offer(A)
        queue.offer(B)
        val old = Deliveries()
        val oldOwner = queue.bind(old::open, {})
        val next = Deliveries()
        queue.bind(next::open, {})
        assertEquals(listOf(A), next.uris, "An unconfirmed file belongs to the replacement host")
        queue.detach(oldOwner)
        old.complete.single()()
        assertEquals(listOf(A), next.uris, "The old answer cannot consume the new owner's delivery")
        next.complete[0]()
        assertEquals(listOf(A, B), next.uris)
        queue.offer(C)
        next.complete[1]()
        assertEquals(listOf(A, B, C), next.uris)
    }

    @Test fun detachedActiveFileIsRedeliveredWithoutRestaging() = withQueueStage { scope ->
        var copies = 0
        val queue = queue(scope) { copies++; DocumentRef(it) }
        queue.offer(A)
        val old = Deliveries()
        val owner = queue.bind(old::open, {})
        queue.detach(owner)
        val next = Deliveries()
        queue.bind(next::open, {})
        assertEquals(listOf(A), next.uris)
        assertEquals(1, copies)
    }

    @Test fun slowFirstCopyCannotBeOvertakenByLaterOffer() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val first = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            val started = mutableListOf<String>()
            val queue = queue(scope) {
                started += it
                if (it == A) first.await()
                DocumentRef(it)
            }
            val deliveries = Deliveries()
            queue.bind({ refs, done -> deliveries.open(refs, done); if (refs.single().uri == B) finished.complete(Unit) }, {})
            queue.offer(A)
            queue.offer(B)
            assertTrue(deliveries.uris.isEmpty())
            first.complete(Unit)
            assertEquals(listOf(A), deliveries.uris)
            deliveries.complete[0]()
            withTimeout(5_000) { finished.await() }
            assertEquals(listOf(A, B), deliveries.uris)
            assertEquals(listOf(A, B), started)
        } finally { scope.cancel() }
    }

    @Test fun copyFailureBeforeHostIsReportedOnceAndNextFileStillOpens() = withQueueStage { scope ->
        val queue = queue(scope) { if (it == A) error("Unreadable external file") else DocumentRef(it) }
        queue.offer(A)
        queue.offer(B)
        var failures = 0
        val deliveries = Deliveries()
        val owner = queue.bind(deliveries::open, { failures++ })
        assertEquals(1, failures)
        assertEquals(listOf(B), deliveries.uris)
        queue.detach(owner)
        queue.bind({ _, _ -> }, { failures++ })
        assertEquals(1, failures, "A reported copy failure must not reappear on host recreation")
    }

    @Test fun externalAccessIsRetainedAtOfferBeforeDelayedCopyStarts() = withQueueStage { scope ->
        val blocked = CompletableDeferred<Unit>()
        val retained = mutableListOf<String>()
        val queue = IosIncomingFileQueue(scope, prepare = { url ->
            retained += url
            suspend { if (url == A) blocked.await(); DocumentRef(url) }
        })
        queue.offer(A)
        queue.offer(B)
        assertEquals(listOf(A, B), retained, "Waiting inputs must acquire external access before the callback returns")
        blocked.complete(Unit)
    }

    private class Deliveries {
        val uris = mutableListOf<String>()
        val complete = mutableListOf<() -> Unit>()
        fun open(refs: List<DocumentRef>, done: () -> Unit) {
            uris += refs.single().uri
            complete += done
        }
    }

    private fun queue(scope: CoroutineScope, stage: suspend (String) -> DocumentRef = { DocumentRef(it) }) =
        IosIncomingFileQueue(scope, prepare = { url -> suspend { stage(url) } })

    private fun withQueue(block: (IosIncomingFileQueue) -> Unit) = withQueueStage { scope -> block(queue(scope)) }

    private fun withQueueStage(block: (CoroutineScope) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try { block(scope) } finally { scope.cancel() }
    }

    private companion object {
        const val A = "file:///tmp/first.zip"
        const val B = "file:///tmp/second.epub"
        const val C = "file:///tmp/third.ttf"
    }
}
