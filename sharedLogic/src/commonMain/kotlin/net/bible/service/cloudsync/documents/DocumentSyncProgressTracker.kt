package net.bible.service.cloudsync.documents

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.sharedcore.cloud.DocumentSyncInterruptionReason
import net.bible.sharedcore.cloud.DocumentSyncOperation
import net.bible.sharedcore.cloud.DocumentSyncProgress
import net.bible.sharedcore.cloud.DocumentSyncProgressStatus
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.Subscription

/** Process-local queue state. Acceptance is serialized; callbacks always run outside the producer lock. */
class DocumentSyncProgressTracker {
    private class Observer(val callback: (DocumentSyncProgress) -> Unit) {
        val pending = ArrayDeque<DocumentSyncProgress>()
        var replayRequested = false
        var draining = false
        var cancelled = false
    }

    private val lock = SynchronizedObject()
    private var accepted = DocumentSyncProgress()
    private val mutableState = MutableStateFlow(accepted)
    private var publishedRevision = 0L
    internal val snapshot: DocumentSyncProgress get() = synchronized(lock) { accepted }
    private val source = EventSource<DocumentSyncProgress>()
    private val observers = mutableListOf<Observer>()
    private val emissions = ArrayDeque<() -> Unit>()
    private var emitting = false
    private var acceptanceDepth = 0
    private var currentOperationId: Long? = null
    private var lastOperationId = 0L
    private var nextSession = 0L
    private var refreshFailed = false
    val state: StateFlow<DocumentSyncProgress> = mutableState.asStateFlow()
    val changes: Events<DocumentSyncProgress> = source

    internal fun nextSessionId(): Long = synchronized(lock) { ++nextSession }

    /** Hosts mutate their queue in this same transaction, then drain ordered notifications after unlocking. */
    internal fun <T> accepting(block: () -> T): T {
        try {
            return synchronized(lock) {
                acceptanceDepth++
                try { block() } finally { acceptanceDepth-- }
            }
        } finally { drainEmissions() }
    }

    internal fun afterAcceptance(action: () -> Unit) = accepting { emissions.addLast(action) }

    internal class Observation(private val replayBlock: () -> Unit, private val cancelBlock: () -> Unit) : Subscription {
        fun replay() = replayBlock()
        override fun cancel() = cancelBlock()
    }

    fun observe(observer: (DocumentSyncProgress) -> Unit): Subscription = register(observer).also { it.replay() }

    /** Split registration/replay also lets common tests force the exact pre-replay interleaving. */
    internal fun register(observer: (DocumentSyncProgress) -> Unit): Observation {
        val entry = Observer(observer)
        synchronized(lock) {
            entry.pending.addLast(accepted)
            observers.add(entry)
        }
        return Observation({
            val start = synchronized(lock) {
                entry.replayRequested = true
                claimObserver(entry)
            }
            if (start) drainObserver(entry)
        }, {
            synchronized(lock) { entry.cancelled = true; observers.remove(entry); entry.pending.clear() }
        })
    }

    // Only revisions whose publication effect has run may cross this boundary. Otherwise a slow
    // observer could leap over queued side effects (notably closing #4031's error streak).
    private fun claimObserver(entry: Observer): Boolean {
        if (entry.cancelled || !entry.replayRequested || entry.draining ||
            entry.pending.firstOrNull()?.let { it.revision <= publishedRevision } != true) return false
        entry.draining = true
        return true
    }

    private fun drainObserver(entry: Observer) {
        while (true) {
            val item = synchronized(lock) {
                if (entry.cancelled || entry.pending.firstOrNull()?.let { it.revision <= publishedRevision } != true) {
                    entry.draining = false
                    null
                } else entry.pending.removeFirst()
            } ?: return
            invokeSafely { entry.callback(item) }
        }
    }

    private fun drainEmissions() {
        val start = synchronized(lock) {
            if (acceptanceDepth > 0 || emitting) false else { emitting = true; true }
        }
        if (!start) return
        while (true) {
            val item = synchronized(lock) {
                if (emissions.isEmpty()) { emitting = false; null } else emissions.removeFirst()
            } ?: return
            invokeSafely(item)
        }
    }

    private fun invokeSafely(action: () -> Unit) {
        try { action() } catch (e: Throwable) { e.printStackTrace() }
    }

    private fun update(transform: (DocumentSyncProgress) -> DocumentSyncProgress?) = accepting {
        val previous = accepted
        val next = transform(previous)?.copy(revision = previous.revision + 1)
        if (next != null) {
            accepted = next
            observers.forEach { it.pending.addLast(next) }
            emissions.addLast {
                // StateFlow may synchronously resume Unconfined/immediate consumers: never set it
                // inside acceptance. Keep a separate accepted snapshot for queue decisions/replay.
                synchronized(lock) { publishedRevision = next.revision }
                mutableState.value = next
                source.emit(next)
                val ready = synchronized(lock) { observers.filter(::claimObserver) }
                ready.forEach(::drainObserver)
            }
        }
    }

    internal fun begin(sessionId: Long, total: Int) = update { old ->
        if (total <= 0 || sessionId <= old.sessionId) null else {
            nextSession = maxOf(nextSession, sessionId)
            currentOperationId = null
            lastOperationId = 0
            refreshFailed = false
            DocumentSyncProgress(sessionId = sessionId, total = total, status = DocumentSyncProgressStatus.RUNNING)
        }
    }
    internal fun enqueue(sessionId: Long, added: Int) = update { p ->
        if (!p.accepts(sessionId) || added <= 0) null else p.copy(total = p.total + added)
    }
    internal fun operation(sessionId: Long, operationId: Long, kind: DocumentSyncOperation, initials: String) = update { p ->
        if (!p.accepts(sessionId) || operationId <= lastOperationId) null else {
            currentOperationId = operationId
            lastOperationId = operationId
            p.copy(currentOperation = kind, initials = initials, bytesTransferred = null, totalBytes = null)
        }
    }
    internal fun bytes(sessionId: Long, operationId: Long, transferred: Long, total: Long) = update { p ->
        if (!p.accepts(sessionId) || currentOperationId != operationId || p.currentOperation != DocumentSyncOperation.DOWNLOAD) null
        else p.copy(bytesTransferred = transferred.coerceAtLeast(0).let { if (total > 0) it.coerceAtMost(total) else it },
            totalBytes = total.takeIf { it > 0 })
    }
    internal fun outcome(sessionId: Long, operationId: Long, outcome: DocumentSyncOpOutcome) = update { p ->
        if (!p.accepts(sessionId) || currentOperationId != operationId) null else {
            currentOperationId = null
            p.copy(processed = p.processed + 1,
                failed = p.failed + if (outcome is DocumentSyncOpOutcome.Failed || outcome is DocumentSyncOpOutcome.UserFacing) 1 else 0,
                deferred = p.deferred + if (outcome is DocumentSyncOpOutcome.RetryLater) 1 else 0,
                currentOperation = null, initials = null, bytesTransferred = null, totalBytes = null)
        }
    }
    internal fun infrastructureFailure(sessionId: Long) = update { p ->
        if (!p.accepts(sessionId)) null else p.copy(failed = p.failed + 1)
    }
    internal fun refreshFailure(sessionId: Long) = update { p ->
        if (!p.accepts(sessionId)) null else { refreshFailed = true; p }
    }
    internal fun finish(sessionId: Long) = update { p ->
        if (!p.accepts(sessionId)) null else p.cleared().copy(status = when {
            p.failed > 0 -> DocumentSyncProgressStatus.FAILED
            p.deferred > 0 || refreshFailed -> DocumentSyncProgressStatus.INCOMPLETE
            else -> DocumentSyncProgressStatus.FINISHED
        })
    }
    internal fun interrupt(sessionId: Long, reason: DocumentSyncInterruptionReason) = update { p ->
        if (!p.accepts(sessionId)) null else p.cleared().copy(status = when {
            p.failed > 0 -> DocumentSyncProgressStatus.FAILED
            reason == DocumentSyncInterruptionReason.USER_STOP -> DocumentSyncProgressStatus.CANCELLED
            else -> DocumentSyncProgressStatus.INTERRUPTED
        }, interruptionReason = reason.takeIf { p.failed == 0 })
    }
    private fun DocumentSyncProgress.cleared() = copy(currentOperation = null, initials = null, bytesTransferred = null, totalBytes = null)
    private fun DocumentSyncProgress.accepts(id: Long) = sessionId == id && status == DocumentSyncProgressStatus.RUNNING
}
