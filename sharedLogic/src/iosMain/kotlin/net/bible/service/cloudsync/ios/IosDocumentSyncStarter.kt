package net.bible.service.cloudsync.ios

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.bible.service.cloudsync.CloudSyncProvider
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.DocumentSyncActions
import net.bible.service.cloudsync.documents.DocumentSyncOp
import net.bible.service.cloudsync.documents.DocumentSyncOpOutcome
import net.bible.service.cloudsync.documents.asActions
import net.bible.service.cloudsync.documents.buildDocumentSyncOps
import net.bible.service.cloudsync.documents.runDocumentSyncOp
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.UserNotifier
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.coroutines.coroutineContext

private const val TAG = "IosDocumentSyncStarter"

/**
 * What one drain session needs from the document-sync / cloud-sync services. Production binds it to
 * [DocumentSync] and [net.bible.service.cloudsync.CloudSync]; tests fake it (both are final classes
 * with heavy constructors).
 */
internal interface DocumentSyncDriver {
    val actions: DocumentSyncActions
    fun notifyRunning(running: Boolean)
    suspend fun refreshCache()
    fun dropAdapterForReconnect()
}

/**
 * iOS counterpart of Android's `DocumentSyncService`: a single consumer coroutine on [scope] drains a
 * queue of [DocumentSyncOp]s. Each drain session is wrapped in a UIKit background task ([begin]/[end])
 * so a started transfer can finish after the app is backgrounded; when iOS expires the task the drain
 * is cancelled (unfinished ops are retried on the next sync, like on Android).
 *
 * Queue semantics mirror the Android service: UserFacing -> notify, drop the adapter if a reconnect
 * is needed, abandon the queue; Failed -> generic sync error, continue; RetryLater -> silent.
 * `notifyRunning(false)` is always emitted when the drain ends, including cancellation.
 */
class IosDocumentSyncStarter private constructor(
    private val scope: CoroutineScope,
    private val driver: () -> DocumentSyncDriver,
    private val notifier: UserNotifier,
    private val syncErrorMessage: () -> String,
    private val begin: (() -> Unit) -> ULong,
    private val end: (ULong) -> Unit,
) : DocumentSyncStarter {

    constructor(
        scope: CoroutineScope,
        documents: DocumentSyncProvider,
        cloudSync: CloudSyncProvider,
        notifier: UserNotifier,
        syncErrorMessage: () -> String,
        actions: (DocumentSync) -> DocumentSyncActions = { it.asActions() },
        begin: (() -> Unit) -> ULong = { UIApplication.sharedApplication.beginBackgroundTaskWithName("document-sync", it) },
        end: (ULong) -> Unit = { UIApplication.sharedApplication.endBackgroundTask(it) },
    ) : this(
        scope,
        {
            val sync = documents.get()
            object : DocumentSyncDriver {
                override val actions = actions(sync)
                override fun notifyRunning(running: Boolean) = sync.notifyRunning(running)
                override suspend fun refreshCache() = sync.refreshCache()
                override fun dropAdapterForReconnect() = cloudSync.get().dropAdapterForReconnect()
            }
        },
        notifier, syncErrorMessage, begin, end,
    )

    companion object {
        /** Test seam: a starter over a fake [DocumentSyncDriver]. */
        internal fun forDriver(
            scope: CoroutineScope,
            driver: DocumentSyncDriver,
            notifier: UserNotifier,
            syncErrorMessage: () -> String,
            begin: (() -> Unit) -> ULong,
            end: (ULong) -> Unit,
        ) = IosDocumentSyncStarter(scope, { driver }, notifier, syncErrorMessage, begin, end)
    }

    private val lock = SynchronizedObject()
    private val queue = ArrayDeque<DocumentSyncOp>()
    private var active = false
    private var generation = 0
    private var drainJob: Job? = null
    private var lastJob: Job? = null

    override fun pushDocuments(initials: List<String>) = start(push = initials, download = emptyList())

    override fun start(
        push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>,
    ) {
        val ops = buildDocumentSyncOps(push, download, remove, purge, uninstall)
        if (ops.isEmpty()) return
        synchronized(lock) {
            queue.addAll(ops)
            if (active) return
            active = true
            val gen = ++generation
            val job = scope.launch(start = CoroutineStart.ATOMIC) { drain(gen) }
            drainJob = job
            lastJob = job
        }
    }

    /**
     * Cancels the running drain (its `finally` ends the background task) and abandons the queue.
     *
     * A later [start] may begin a new session while the cancelled one is still unwinding (an op that does
     * not cooperate with cancellation can outlive this call). The old session is fenced off by a
     * generation counter: it can no longer poll, clear the queue, drop the adapter or flip the running flag
     * of the new session. Its own background task is still ended.
     */
    override fun stop() {
        val job = synchronized(lock) {
            queue.clear()
            active = false
            generation++
            drainJob.also { drainJob = null }
        }
        job?.cancel()
    }

    /** Test hook: suspends until the current drain session (if any) has finished. */
    internal suspend fun awaitIdle() {
        while (true) {
            val job = synchronized(lock) { lastJob } ?: return
            if (job.isCompleted) return
            job.join()
        }
    }

    private fun poll(gen: Int): DocumentSyncOp? = synchronized(lock) {
        if (gen != generation) null else queue.removeFirstOrNull()
    }

    private suspend fun drain(gen: Int) {
        val self = coroutineContext[Job]
        val task = BackgroundTask()
        // UIKit requires endBackgroundTask before the expiration handler returns, and cancellation is
        // asynchronous (a blocking op may outlive the deadline), so the handler ends the task itself.
        task.attach(begin { task.expire { self?.cancel() } }, end)
        var finishedNormally = false
        try {
            val d = driver()
            d.notifyRunning(true)
            try {
                while (true) {
                    val op = poll(gen)
                    if (op == null) {
                        // Refresh the cloud-listing cache, then relinquish only if no late op arrived meanwhile.
                        try { d.refreshCache() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                            Log.e(TAG, "Cache refresh failed", e)
                        }
                        val relinquished = synchronized(lock) {
                            if (gen != generation) true
                            else if (queue.isEmpty()) { active = false; drainJob = null; true }
                            else false
                        }
                        if (relinquished) break else continue
                    }
                    // Re-assert running per op (see Android's drain): late subscribers learn of the drain.
                    d.notifyRunning(true)
                    when (val outcome = runDocumentSyncOp(op, d.actions) { _, _ -> }) {
                        DocumentSyncOpOutcome.Done, DocumentSyncOpOutcome.RetryLater -> {}
                        is DocumentSyncOpOutcome.UserFacing -> {
                            notifier.showError(outcome.message)
                            // Fenced: a stale session (stop() + start()) must not touch the new one.
                            val current = synchronized(lock) { (gen == generation).also { if (it) queue.clear() } }
                            if (current && outcome.requiresReconnect) d.dropAdapterForReconnect()
                        }
                        DocumentSyncOpOutcome.Failed -> notifier.showError(syncErrorMessage())
                    }
                }
                finishedNormally = true
            } finally {
                val emit = synchronized(lock) { gen == generation || !active }
                if (emit) d.notifyRunning(false)
            }
        } finally {
            if (!finishedNormally) synchronized(lock) {
                // Cancelled or crashed: drop the session unless stop()/a newer session already did.
                if (gen == generation) { queue.clear(); active = false; drainJob = null }
            }
            task.endOnce()
        }
    }
}

/**
 * One UIKit background task, ended exactly once whichever of the expiration handler and the drain's
 * `finally` gets there first. The handler can fire before `begin` has returned the identifier, in which
 * case the task is ended as soon as [attach] receives it.
 */
private class BackgroundTask {
    private val lock = SynchronizedObject()
    private var id: ULong? = null
    private var end: ((ULong) -> Unit)? = null
    private var expired = false
    private var ended = false

    fun attach(id: ULong, end: (ULong) -> Unit) {
        // UIBackgroundTaskInvalid means the system refused the task: there is nothing to end
        // (mirrors IosSyncScheduler's guard).
        if (id == UIBackgroundTaskInvalid) {
            synchronized(lock) { ended = true }
            return
        }
        val endNow = synchronized(lock) { this.id = id; this.end = end; expired }
        if (endNow) endOnce()
    }

    /** Expiration handler body: cancel the drain, then end the task synchronously. */
    fun expire(cancel: () -> Unit) {
        synchronized(lock) { expired = true }
        cancel()
        endOnce()
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
