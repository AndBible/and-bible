package net.bible.service.cloudsync.host

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import net.bible.android.control.event.UserMessages
import net.bible.service.cloudsync.CloudSyncProvider
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.SyncErrorReporter
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncActions
import net.bible.service.cloudsync.documents.DocumentSyncOp
import net.bible.service.cloudsync.documents.DocumentSyncOpOutcome
import net.bible.service.cloudsync.documents.DocumentSyncProgressTracker
import net.bible.service.cloudsync.documents.asActions
import net.bible.service.cloudsync.documents.buildDocumentSyncOps
import net.bible.service.cloudsync.documents.runDocumentSyncOp
import net.bible.sharedcore.cloud.DocumentSyncInterruptionReason
import net.bible.sharedcore.cloud.DocumentSyncOperation
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.log.Log
import kotlin.coroutines.coroutineContext

private const val TAG = "HostDocumentSyncStarter"

/** Services needed by a drain, without constructing the document and cloud services in queue tests. */
internal interface DocumentSyncDriver {
    val actions: DocumentSyncActions
    fun notifyRunning(running: Boolean)
    suspend fun refreshCache()
    fun dropAdapterForReconnect()
}

/**
 * Single-consumer host queue. Queue mutations and progress acceptance share the tracker's lock;
 * ordered effects/observers run after unlocking. A detached session may unwind, but cannot write to
 * the new session, report stale errors, refresh its cache or drop its adapter.
 *
 * Errors retain #4031's coalescing through [UserMessages]. Counts represent returned queue actions,
 * not verified installed books. UIKit expiration ends the task synchronously even for blocking work.
 */
class HostDocumentSyncStarter private constructor(
    private val scope: CoroutineScope,
    private val driver: () -> DocumentSyncDriver,
    private val syncErrorMessage: () -> String,
    private val begin: (() -> Unit) -> ULong,
    private val end: (ULong) -> Unit,
    private val invalidTask: ULong,
    private val tracker: DocumentSyncProgressTracker,
) : DocumentSyncStarter {
    constructor(
        scope: CoroutineScope,
        documents: DocumentSyncProvider,
        cloudSync: CloudSyncProvider,
        syncErrorMessage: () -> String,
        actions: (DocumentSync) -> DocumentSyncActions = { it.asActions() },
        begin: (() -> Unit) -> ULong,
        end: (ULong) -> Unit,
        invalidTask: ULong,
        tracker: DocumentSyncProgressTracker,
    ) : this(scope, {
        val sync = documents.get()
        object : DocumentSyncDriver {
            override val actions = actions(sync)
            override fun notifyRunning(running: Boolean) = sync.notifyRunning(running)
            override suspend fun refreshCache() = sync.refreshCache()
            override fun dropAdapterForReconnect() = cloudSync.get().dropAdapterForReconnect()
        }
    }, syncErrorMessage, begin, end, invalidTask, tracker)

    companion object {
        internal fun forDriver(
            scope: CoroutineScope,
            driver: DocumentSyncDriver,
            syncErrorMessage: () -> String,
            begin: (() -> Unit) -> ULong,
            end: (ULong) -> Unit,
            invalidTask: ULong = ULong.MAX_VALUE,
            tracker: DocumentSyncProgressTracker = DocumentSyncProgressTracker(),
        ) = HostDocumentSyncStarter(scope, { driver }, syncErrorMessage, begin, end, invalidTask, tracker)

        internal fun forDriver(
            scope: CoroutineScope,
            driver: () -> DocumentSyncDriver,
            syncErrorMessage: () -> String,
            begin: (() -> Unit) -> ULong,
            end: (ULong) -> Unit,
            tracker: DocumentSyncProgressTracker,
        ) = HostDocumentSyncStarter(scope, driver, syncErrorMessage, begin, end, ULong.MAX_VALUE, tracker)
    }

    private class Session(val id: Long, val run: SyncErrorReporter.Run) {
        var job: Job? = null
        var operation = 0L
    }
    // Protected by tracker.accepting, not a separate queue lock with a conflicting lock order.
    private val queue = ArrayDeque<DocumentSyncOp>()
    private var active: Session? = null
    private var lastJob: Job? = null
    private val errorReporter = SyncErrorReporter()

    override fun pushDocuments(initials: List<String>) = start(push = initials, download = emptyList())

    override fun start(
        push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>,
    ) {
        val ops = buildDocumentSyncOps(push, download, remove, purge, uninstall)
        if (ops.isEmpty()) return
        val job = tracker.accepting {
            queue.addAll(ops)
            val current = active
            if (current != null) {
                tracker.enqueue(current.id, ops.size)
                null
            } else {
                val session = Session(tracker.nextSessionId(), errorReporter.beginRun())
                active = session
                tracker.begin(session.id, ops.size)
                log(session, "start")
                // LAZY prevents an immediate dispatcher running arbitrary begin/driver code under the lock.
                scope.launch(start = CoroutineStart.LAZY) { drain(session) }.also {
                    session.job = it
                    lastJob = it
                    // Also closes a session whose parent was cancelled before a LAZY body could run.
                    it.invokeOnCompletion { interrupt(session, DocumentSyncInterruptionReason.SCOPE_CANCELLED) }
                }
            }
        }
        job?.start()
    }

    override fun stop() {
        val job = tracker.accepting {
            active?.let { session ->
                close(session, DocumentSyncInterruptionReason.USER_STOP)
                session.job
            }
        }
        job?.cancel()
    }

    internal suspend fun awaitIdle() {
        while (true) {
            val job = tracker.accepting { lastJob } ?: return
            if (job.isCompleted) return
            job.join()
        }
    }

    private fun interrupt(session: Session, reason: DocumentSyncInterruptionReason) {
        val job = tracker.accepting {
            if (active !== session) null else {
                close(session, reason)
                session.job
            }
        }
        job?.cancel()
    }

    /** Called only inside acceptance. The terminal and error effects are queued before a new start. */
    private fun close(session: Session, reason: DocumentSyncInterruptionReason? = null) {
        if (active !== session) return
        // Close the streak before releasing active; external producers may start while delivery is
        // blocked. Only the error consumer call is deferred, before ordered terminal observations.
        session.run.prepareEnd(completed = reason == null)?.let { tracker.afterAcceptance(it) }
        if (reason == null) tracker.finish(session.id) else tracker.interrupt(session.id, reason)
        queue.clear()
        active = null
        log(session, "end", reason)
    }

    private fun log(session: Session, event: String, reason: DocumentSyncInterruptionReason? = null) {
        val p = tracker.snapshot
        val text = "Queue $event session=${session.id} processed=${p.processed} total=${p.total} failed=${p.failed} deferred=${p.deferred} status=${p.status} reason=$reason"
        tracker.afterAcceptance { Log.i(TAG, text) }
    }

    private suspend fun drain(session: Session) {
        val self = coroutineContext[Job]
        val task = BackgroundTask()
        var d: DocumentSyncDriver? = null
        var sentStopped = false
        try {
            coroutineContext.ensureActive()
            if (!tracker.accepting { active === session }) return
            task.attach(begin {
                task.expire {
                    interrupt(session, DocumentSyncInterruptionReason.BACKGROUND_EXPIRED)
                    self?.cancel()
                }
            }, end, invalidTask)
            coroutineContext.ensureActive()
            if (!tracker.accepting { active === session }) return
            val services = driver()
            d = services
            tracker.accepting {
                if (active === session) tracker.afterAcceptance {
                    if (tracker.accepting { active === session }) services.notifyRunning(true)
                }
            }
            while (true) {
                coroutineContext.ensureActive()
                val next = tracker.accepting {
                    if (active !== session) null else queue.removeFirstOrNull()?.let { op ->
                        val token = ++session.operation
                        tracker.operation(session.id, token, op.kind, op.initials)
                        tracker.afterAcceptance {
                            if (tracker.accepting { active === session }) services.notifyRunning(true)
                            Log.i(TAG, "Operation start session=${session.id} operation=$token kind=${op.kind}")
                        }
                        token to op
                    }
                }
                if (next == null) {
                    if (!tracker.accepting { active === session }) break
                    try {
                        services.refreshCache()
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) {
                        tracker.accepting {
                            if (active === session) {
                                tracker.refreshFailure(session.id)
                                session.run.noteSilentFailure()
                                tracker.afterAcceptance { Log.w(TAG, "Cache refresh failed session=${session.id}") }
                            }
                        }
                    }
                    val done = tracker.accepting {
                        if (active !== session) true
                        else if (queue.isNotEmpty()) false
                        else {
                            close(session)
                            sentStopped = true
                            tracker.afterAcceptance { services.notifyRunning(false) }
                            true
                        }
                    }
                    if (done) break else continue
                }
                val (token, op) = next
                coroutineContext.ensureActive()
                if (!tracker.accepting { active === session }) break
                val outcome = runDocumentSyncOp(op, services.actions) { bytes, total ->
                    tracker.accepting {
                        if (active === session && session.operation == token) tracker.bytes(session.id, token, bytes, total)
                    }
                }
                tracker.accepting {
                    if (active === session && session.operation == token) {
                        tracker.outcome(session.id, token, outcome)
                        tracker.afterAcceptance {
                            val name = when (outcome) {
                                DocumentSyncOpOutcome.Done -> "done"
                                DocumentSyncOpOutcome.RetryLater -> "deferred"
                                DocumentSyncOpOutcome.Failed -> "failed"
                                is DocumentSyncOpOutcome.UserFacing -> "user-facing"
                            }
                            Log.i(TAG, "Operation end session=${session.id} operation=$token kind=${op.kind} outcome=$name")
                        }
                        when (outcome) {
                            DocumentSyncOpOutcome.Done -> Unit
                            DocumentSyncOpOutcome.RetryLater -> session.run.noteSilentFailure()
                            is DocumentSyncOpOutcome.UserFacing -> {
                                session.run.report(outcome.message, showReportButton = false, background = false)
                                queue.clear()
                                if (outcome.requiresReconnect) tracker.afterAcceptance {
                                    // A callback earlier in the emission drain may have stopped/restarted us.
                                    if (tracker.accepting { active === session }) services.dropAdapterForReconnect()
                                }
                            }
                            DocumentSyncOpOutcome.Failed -> session.run.report(syncErrorMessage(), background = false)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            interrupt(session, DocumentSyncInterruptionReason.SCOPE_CANCELLED)
        } catch (_: Exception) {
            tracker.accepting {
                if (active === session) {
                    tracker.infrastructureFailure(session.id)
                    session.run.report(syncErrorMessage(), background = false)
                    close(session)
                }
            }
        } finally {
            tracker.accepting {
                if (!sentStopped && active == null && tracker.snapshot.sessionId == session.id) d?.let { services ->
                    tracker.afterAcceptance {
                        if (tracker.accepting { active == null && tracker.snapshot.sessionId == session.id }) {
                            services.notifyRunning(false)
                        }
                    }
                }
            }
            task.endOnce()
        }
    }
}

private val DocumentSyncOp.kind: DocumentSyncOperation get() = when (this) {
    is DocumentSyncOp.Push -> DocumentSyncOperation.PUSH
    is DocumentSyncOp.Download -> DocumentSyncOperation.DOWNLOAD
    is DocumentSyncOp.Remove -> DocumentSyncOperation.REMOVE
    is DocumentSyncOp.Purge -> DocumentSyncOperation.PURGE
    is DocumentSyncOp.Uninstall -> DocumentSyncOperation.UNINSTALL
}

/** An expiration may precede attachment; an attached valid task is always ended exactly once. */
private class BackgroundTask {
    private val lock = SynchronizedObject()
    private var id: ULong? = null
    private var end: ((ULong) -> Unit)? = null
    private var expired = false
    private var ended = false

    fun attach(id: ULong, end: (ULong) -> Unit, invalidTask: ULong) {
        if (id == invalidTask) {
            synchronized(lock) { ended = true }
            return
        }
        val endNow = synchronized(lock) { this.id = id; this.end = end; expired }
        if (endNow) endOnce()
    }

    fun expire(cancel: () -> Unit) {
        synchronized(lock) { expired = true }
        try { cancel() } finally { endOnce() }
    }

    fun endOnce() {
        val (taskId, endFn) = synchronized(lock) {
            val i = id; val e = end
            if (ended || i == null || e == null) return
            ended = true
            i to e
        }
        endFn(taskId)
    }
}
