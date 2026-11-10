package net.bible.android.control.activity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.concurrent.Volatile
import net.bible.sharedcore.cloud.DocumentSyncProgress
import net.bible.sharedcore.cloud.DocumentSyncProgressStatus
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppForeground
import net.bible.sharedcore.platform.NativeNotification
import net.bible.sharedcore.platform.NativeNotifier

/**
 * Turns [ActivityItem] changes into [NativeNotifier] calls (#4012), with no platform dependency.
 *
 * Rules: a FAILED item notifies urgently unless it is a foreground user-started failure. A SUCCEEDED non-SYNC item
 * that ran at least [LONG_JOB_MS] notifies only when the app is not in the foreground. A RUNNING item shows progress
 * only when supported, in the background, and seen RUNNING for [LONG_JOB_MS] on this policy's [clock]. Progress or
 * message changes are throttled to [PROGRESS_THROTTLE_MS] with a trailing update; unchanged progress is refreshed
 * every [PROGRESS_REFRESH_MS] so finite-lived native notifications remain visible.
 * A vanished or CANCELLED item that had a notification (and was RUNNING or FAILED) removes it; a vanished SUCCEEDED
 * item leaves it to the OS.
 *
 * Policy state is confined to one collector coroutine in [scope]. The default policy combines state flows. The iOS
 * document-lifecycle opt-in instead serializes queue revisions, focus edges, preference changes and timer wake-ups
 * through a loss-free channel; byte-only updates are filtered at ingress. [closeLock] serializes delivery with [close].
 * Time-based triggers (10 s reached, throttle window elapsed) are covered by a single one-shot wake-up job that is
 * scheduled after each evaluation only when something is pending, so an idle policy has no timer, it works with
 * virtual time and it dies with [close] or the scope.
 *
 * [clock] may be any monotonic-ish millisecond clock; it is only compared with itself. The finished check uses
 * `finishedAt - startedAt`, which share ActivityCenter's time base.
 * The inputs are conflated state flows, so a FAILED, RUNNING, FAILED sequence between two collections can miss the
 * second failure; that is accepted.
 *
 * Items already present when [start] is called are treated as new. When the preference goes false everything shown
 * is removed; when it comes back true the current list becomes the baseline (no replay of past transitions).
 */
class NativeNotificationPolicy(
    private val items: StateFlow<List<ActivityItem>>,
    private val notifier: NativeNotifier,
    private val foreground: AppForeground,
    private val enabled: StateFlow<Boolean>,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val attentionText: (() -> String)?,
    private val finishedText: () -> String,
    private val documentSyncLifecycleNotices: Boolean = false,
    private val documentSyncNoticeText: ((DocumentSyncProgress) -> String)? = null,
    private val documentSyncNoticeTitle: (() -> String)? = null,
) {
    constructor(items: StateFlow<List<ActivityItem>>, notifier: NativeNotifier, foreground: AppForeground,
                enabled: StateFlow<Boolean>, clock: () -> Long, scope: CoroutineScope, finishedText: () -> String) :
        this(items, notifier, foreground, enabled, clock, scope, null, finishedText)

    private enum class Shown { PROGRESS, ATTENTION, FINAL }

    private class ProgressShown(val progress: Int?, val message: String?, val at: Long)

    private val inForeground = MutableStateFlow(false)
    private val tick = MutableStateFlow(0L)
    private val closed = MutableStateFlow(false)
    @Volatile private var job: Job? = null

    /** Transient notification ids, published so [close] can remove them on any thread. */
    @Volatile private var progressIds: Set<String> = emptySet()
    @Volatile private var isClosed = false

    /**
     * Makes "check isClosed, show, publish progressIds" (collector) and "set isClosed, snapshot, remove" ([close])
     * mutually exclusive, so a show can never land after close() removed its id. Notifier calls run under it, which is
     * acceptable because the [NativeNotifier] port is non-blocking.
     */
    private val closeLock = SynchronizedObject()

    // Confined to the collector coroutine.
    private val shown = mutableMapOf<String, Shown>()
    private val lastSeen = mutableMapOf<String, ActivityItem>()
    private val progressShown = mutableMapOf<String, ProgressShown>()
    private val firstSeenRunningAt = mutableMapOf<String, Long>()
    private var wasEnabled = true
    private var needBaseline = false
    private var previousForeground = false
    @Volatile private var wakeJob: Job? = null
    private val loggedFailures = mutableSetOf<String>()

    private sealed interface LifecycleInput {
        data class Queue(val progress: DocumentSyncProgress, val at: Long) : LifecycleInput
        data class Focus(val foreground: Boolean, val at: Long) : LifecycleInput
        data class Preference(val enabled: Boolean, val at: Long) : LifecycleInput
        data class ListChanged(val items: List<ActivityItem>) : LifecycleInput
        data class Expired(val generation: Long) : LifecycleInput
        data object Refresh : LifecycleInput
    }

    private val lifecycleInputs = Channel<LifecycleInput>(Channel.UNLIMITED)
    // Ingress filtering is independent of notifier/close locking: a producer never waits on delivery.
    private val inputLock = SynchronizedObject()
    private var acceptedQueue: DocumentSyncProgress? = null
    private var queue: DocumentSyncProgress? = null
    private var queueTerminal = false
    private var queueStartIssued = false
    private var lifecycleForeground = false
    private var lifecycleEnabled = true
    private var backgroundSince: Long? = null
    private var lifecycleGeneration = 0L
    @Volatile private var lifecycleTimer: Job? = null
    /** Separate classification: lifecycle starts are neither persistent progress nor attention. */
    @Volatile private var lifecycleStartId: String? = null

    private fun acceptQueue(progress: DocumentSyncProgress, at: Long) {
        val previous = queue
        if (previous != null && (progress.sessionId < previous.sessionId || progress.revision <= previous.revision)) return
        val newSession = previous?.sessionId != progress.sessionId
        if (newSession) {
            removeLifecycleStart()
            queueStartIssued = false
            queueTerminal = false
        } else if (queueTerminal) return
        queue = progress
        if (progress.status == DocumentSyncProgressStatus.RUNNING) {
            if (newSession) resetLifecycleTimer(at)
            return
        }
        queueTerminal = true
        resetLifecycleTimer(at)
        val notifyTerminal = queueStartIssued && lifecycleEnabled && !lifecycleForeground &&
            progress.status in setOf(DocumentSyncProgressStatus.FINISHED,
                DocumentSyncProgressStatus.INTERRUPTED, DocumentSyncProgressStatus.INCOMPLETE)
        if (notifyTerminal) showLifecycle(progress, terminal = true) else removeLifecycleStart()
    }

    private fun resetLifecycleTimer(at: Long) {
        lifecycleTimer?.cancel()
        lifecycleTimer = null
        val generation = ++lifecycleGeneration
        backgroundSince = at.takeIf { !lifecycleForeground && lifecycleEnabled &&
            queue?.status == DocumentSyncProgressStatus.RUNNING && !queueStartIssued && !queueTerminal }
        val since = backgroundSince ?: return
        lifecycleTimer = scope.launch {
            delay((since + LONG_JOB_MS - clock()).coerceAtLeast(0))
            lifecycleInputs.send(LifecycleInput.Expired(generation))
        }
    }

    private fun showLifecycleStart() {
        val progress = queue ?: return
        val since = backgroundSince ?: return
        if (documentSyncNoticeText == null || queueTerminal || queueStartIssued || !lifecycleEnabled || lifecycleForeground ||
            progress.status != DocumentSyncProgressStatus.RUNNING || clock() - since < LONG_JOB_MS) return
        queueStartIssued = true
        showLifecycle(progress, terminal = false)
    }

    private fun showLifecycle(progress: DocumentSyncProgress, terminal: Boolean) {
        val text = documentSyncNoticeText ?: return
        val id = "document-sync:${progress.sessionId}"
        synchronized(closeLock) { // L1-common-ok: atomicfu lock
            if (isClosed) return
            lifecycleStartId = if (terminal) null else id
            guarded("show") {
                notifier.show(NativeNotification(ID_PREFIX + id, documentSyncNoticeTitle?.invoke() ?: "",
                    text(progress), null, false, false))
            }
        }
    }

    private fun removeLifecycleStart() {
        synchronized(closeLock) { // L1-common-ok: atomicfu lock
            val id = lifecycleStartId ?: return
            lifecycleStartId = null
            removeNotification(id)
        }
    }

    /** Starts collecting. Idempotent, but call it from one thread (it is not an atomic check-and-set). */
    fun start() {
        if (job != null) return
        if (isClosed) return
        inForeground.value = foreground.isForeground()
        if (documentSyncLifecycleNotices) {
            lifecycleForeground = inForeground.value
            lifecycleEnabled = enabled.value
            job = scope.launch {
                launch { items.collect { lifecycleInputs.send(LifecycleInput.ListChanged(it)) } }
                launch { enabled.collect { lifecycleInputs.send(LifecycleInput.Preference(it, clock())) } }
                launch { notifier.progressCapabilityChanges.collect { lifecycleInputs.send(LifecycleInput.Refresh) } }
                launch { tick.collect { lifecycleInputs.send(LifecycleInput.Refresh) } }
                var list = items.value
                for (input in lifecycleInputs) {
                    if (isClosed) break
                    when (input) {
                        is LifecycleInput.ListChanged -> list = input.items
                        is LifecycleInput.Queue -> acceptQueue(input.progress, input.at)
                        is LifecycleInput.Focus -> if (lifecycleForeground != input.foreground) {
                            lifecycleForeground = input.foreground
                            resetLifecycleTimer(input.at)
                            if (input.foreground) removeLifecycleStart()
                        }
                        is LifecycleInput.Preference -> {
                            if (lifecycleEnabled != input.enabled) {
                                lifecycleEnabled = input.enabled
                                resetLifecycleTimer(input.at)
                                if (!input.enabled) removeLifecycleStart()
                            }
                        }
                        is LifecycleInput.Expired -> if (input.generation == lifecycleGeneration) showLifecycleStart()
                        LifecycleInput.Refresh -> Unit
                    }
                    evaluate(Input(list, lifecycleEnabled, lifecycleForeground, false))
                }
            }
        } else {
            job = scope.launch {
                launch { notifier.progressCapabilityChanges.collect { tick.update { it + 1 } } }
                combine(items, enabled, inForeground, tick, closed) { list, on, fg, _, closeRequested ->
                    Input(list, on, fg, closeRequested)
                }.takeWhile { evaluate(it) }.collect { }
            }
        }
    }

    /** Queues lifecycle revisions losslessly; byte-only ticks never enter the notification channel. */
    fun onDocumentSyncProgress(progress: DocumentSyncProgress) {
        if (!documentSyncLifecycleNotices || isClosed) return
        synchronized(inputLock) { // L1-common-ok: atomicfu lock
            if (isClosed) return
            val previous = acceptedQueue
            if (previous != null && (progress.revision <= previous.revision || progress.sessionId < previous.sessionId)) return
            val sanitized = progress.copy(currentOperation = null, initials = null, bytesTransferred = null, totalBytes = null)
            acceptedQueue = sanitized
            if (previous?.copy(revision = sanitized.revision) == sanitized) return
            lifecycleInputs.trySend(LifecycleInput.Queue(sanitized, clock()))
        }
    }

    /** Reports every app focus edge; consecutive background time resets even between consumer turns. */
    fun onForegroundChanged(foreground: Boolean) {
        inForeground.value = foreground
        if (documentSyncLifecycleNotices && !isClosed) lifecycleInputs.trySend(LifecycleInput.Focus(foreground, clock()))
    }

    /**
     * Stops collecting and removes progress and attention notifications synchronously on the calling thread, so it is
     * reliable when the scope is cancelled or the JVM exits right after. Notifier calls are non-blocking by the port
     * contract, but do not call this on the AWT/Compose thread. Idempotent.
     */
    fun close() {
        synchronized(closeLock) { // L1-common-ok: atomicfu lock
            if (isClosed) return
            isClosed = true
            closed.value = true
            job?.cancel()
            wakeJob?.cancel()
            lifecycleTimer?.cancel()
            lifecycleInputs.cancel()
            removeLifecycleStart()
            val ids = progressIds
            progressIds = emptySet()
            ids.forEach { removeNotification(it) }
        }
    }

    /** The activity id behind a notification id made by this policy, or null for a foreign id. */
    fun activityIdOf(notificationId: String): String? =
        notificationId.takeIf { it.startsWith(ID_PREFIX) }?.removePrefix(ID_PREFIX)

    private class Input(val list: List<ActivityItem>, val on: Boolean, val fg: Boolean, val closed: Boolean)

    /** Returns false to end collection. */
    private fun evaluate(input: Input): Boolean {
        if (isClosed || input.closed) return false
        wakeJob?.cancel()
        wakeJob = null
        val backgroundTransition = previousForeground && !input.fg
        previousForeground = input.fg
        if (!input.on) {
            if (wasEnabled) {
                removeWhere { true }
                lastSeen.clear()
                progressShown.clear()
            }
            wasEnabled = false
            needBaseline = true
            return true
        }
        wasEnabled = true
        if (needBaseline) {
            lastSeen.clear()
            input.list.forEach { lastSeen[it.id] = it }
            needBaseline = false
        }
        val now = clock()
        if (input.fg) removeWhere { it == Shown.PROGRESS }
        var wakeAt = Long.MAX_VALUE
        val current = input.list.associateBy { it.id }
        firstSeenRunningAt.keys.retainAll { current[it]?.state == ActivityState.RUNNING }

        lastSeen.keys.filter { it !in current }.forEach { id ->
            val last = lastSeen.remove(id)
            val kind = shown.remove(id)
            progressShown.remove(id)
            publishProgressIds()
            if (kind != null && (last?.state == ActivityState.RUNNING || last?.state == ActivityState.FAILED)) removeNotification(id)
        }
        for (item in input.list) {
            // Document queues use a separate lifecycle policy; never leak their detailed in-app message here.
            if (item.kind == ActivityKind.DOCUMENT_SYNC) continue
            val prev = lastSeen[item.id]
            lastSeen[item.id] = item
            if (item.state == ActivityState.RUNNING) firstSeenRunningAt.getOrPut(item.id) { now }
            if (item.state == ActivityState.RUNNING && !item.attention && shown[item.id] == Shown.ATTENTION) {
                dropShown(item.id)
                firstSeenRunningAt[item.id] = now
            }
            when (item.state) {
                ActivityState.FAILED -> if (prev?.state != ActivityState.FAILED || prev.finishedAt != item.finishedAt || prev.failureOccurrence != item.failureOccurrence || (prev.read && !item.read)) {
                    if (!input.fg || item.backgroundFailure) {
                        val body = item.message?.let { if (it == item.title) "" else it.removePrefix("${item.title}: ") } ?: ""
                        showNotification(item, body, null, urgent = true, Shown.FINAL)
                    } else if (shown[item.id] == Shown.ATTENTION) dropShown(item.id)
                }
                ActivityState.SUCCEEDED -> if (prev?.state != ActivityState.SUCCEEDED) {
                    val finishedAt = item.finishedAt
                    val long = finishedAt != null && finishedAt - item.startedAt >= LONG_JOB_MS
                    if (item.kind != ActivityKind.SYNC && long && !input.fg) {
                        showNotification(item, finishedText(), null, urgent = false, Shown.FINAL)
                    } else if (shown[item.id] == Shown.PROGRESS || shown[item.id] == Shown.ATTENTION) {
                        dropShown(item.id)
                    }
                }
                ActivityState.CANCELLED, ActivityState.INTERRUPTED -> if (shown.containsKey(item.id)) dropShown(item.id)
                ActivityState.RUNNING -> if (item.attention) {
                    if (input.fg && shown[item.id] == Shown.PROGRESS) dropShown(item.id)
                    if (!input.fg && (prev?.attention != true || prev.state != ActivityState.RUNNING || backgroundTransition) && shown[item.id] != Shown.ATTENTION) {
                        showNotification(item, item.message ?: "", null, urgent = true, Shown.ATTENTION)
                    }
                } else if (notifier.supportsProgress && !input.fg) {
                    val due = firstSeenRunningAt.getValue(item.id) + LONG_JOB_MS
                    val last = progressShown[item.id]
                    when {
                        now < due -> wakeAt = minOf(wakeAt, due)
                        last == null -> {
                            showProgress(item, now)
                            wakeAt = minOf(wakeAt, now + PROGRESS_REFRESH_MS)
                        }
                        last.progress != item.progress || last.message != item.message -> {
                            val next = last.at + PROGRESS_THROTTLE_MS
                            if (now >= next) {
                                showProgress(item, now)
                                wakeAt = minOf(wakeAt, now + PROGRESS_REFRESH_MS)
                            } else wakeAt = minOf(wakeAt, next)
                        }
                        now >= last.at + PROGRESS_REFRESH_MS -> {
                            showProgress(item, now)
                            wakeAt = minOf(wakeAt, now + PROGRESS_REFRESH_MS)
                        }
                        else -> wakeAt = minOf(wakeAt, last.at + PROGRESS_REFRESH_MS)
                    }
                }
            }
        }
        if (wakeAt != Long.MAX_VALUE) {
            val delayMs = (wakeAt - clock()).coerceAtLeast(0)
            wakeJob = scope.launch {
                delay(delayMs)
                tick.update { it + 1 }
            }
        }
        return true
    }

    private fun showProgress(item: ActivityItem, now: Long) {
        val p = item.progress
        progressShown[item.id] = ProgressShown(p, item.message, now)
        showNotification(item, item.message ?: "", p, urgent = false, Shown.PROGRESS)
    }

    private fun showNotification(item: ActivityItem, body: String, progress: Int?, urgent: Boolean, kind: Shown) {
        synchronized(closeLock) { // L1-common-ok: atomicfu lock
            if (isClosed) return
            shown[item.id] = kind
            if (kind != Shown.PROGRESS) progressShown.remove(item.id)
            publishProgressIds()
            guarded("show") {
                val title = if (kind == Shown.ATTENTION) attentionText?.invoke() ?: item.title else item.title
                notifier.show(NativeNotification(ID_PREFIX + item.id, title, body, progress, progress == null && kind == Shown.PROGRESS, urgent))
            }
        }
    }

    private fun dropShown(id: String) {
        shown.remove(id)
        progressShown.remove(id)
        publishProgressIds()
        removeNotification(id)
    }

    private fun publishProgressIds() { progressIds = shown.filterValues { it == Shown.PROGRESS || it == Shown.ATTENTION }.keys.toSet() }

    private fun removeWhere(predicate: (Shown) -> Boolean) {
        shown.filterValues(predicate).keys.forEach { dropShown(it) }
    }

    private fun removeNotification(id: String) = guarded("remove") { notifier.remove(ID_PREFIX + id) }

    /** Notifier calls must never break the policy; each kind of failure is logged once. */
    private inline fun guarded(kind: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            synchronized(closeLock) { // L1-common-ok: atomicfu lock
                if (loggedFailures.add(kind)) Log.w(TAG, "Native notifier $kind failed (${e.message}); further failures of this kind are not logged")
            }
        }
    }

    companion object {
        const val LONG_JOB_MS = 10_000L
        const val PROGRESS_THROTTLE_MS = 1_000L
        const val PROGRESS_REFRESH_MS = 60_000L
        private const val ID_PREFIX = "andbible-"
        private const val TAG = "NativeNotificationPolicy"
    }
}
