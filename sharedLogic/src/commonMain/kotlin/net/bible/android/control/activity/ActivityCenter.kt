package net.bible.android.control.activity

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * A monotonic clock for [ActivityCenter]: milliseconds elapsed since this call. Unlike the wall clock it never
 * jumps (NTP, manual change, DST), which would hide or expire items early against the monotonic `delay`. The values
 * are only meaningful relative to each other ([ActivityItem.startedAt]/[ActivityItem.finishedAt] are not wall-clock times).
 */
fun monotonicActivityClock(): () -> Long {
    val mark = TimeSource.Monotonic.markNow()
    return { mark.elapsedNow().inWholeMilliseconds }
}

data class ActivityBadgeCounts(val failures: Int = 0, val attention: Int = 0) {
    val total: Int get() = failures + attention
}

/**
 * Registry of long-running background work (downloads, installs, indexing, sync) for the activity center UI.
 *
 * Rules: a running item is hidden for its first [DEBOUNCE_MS] so short jobs never flash; a failure is
 * shown at once and kept until dismissed; a success is kept [SUCCESS_KEEP_MS] (SYNC successes are not
 * kept); a cancelled item is dropped immediately; at most [MAX_FINISHED] finished items are kept.
 *
 * Thread-safe: every mutation is serialised by one lock, so the source adapters may call it from whatever
 * thread JSword jobs, downloads or sync happen to report on. The registered [scope] hosts
 * one coroutine that wakes at the next debounce end / success expiry, using [delay], so a test
 * scheduler's virtual time works as long as [clock] follows it. Production passes [monotonicActivityClock].
 */
class ActivityCenter(scope: CoroutineScope, private val clock: () -> Long) {

    private class Rec(val seq: Long, val item: ActivityItem)

    private class Model(
        val recs: Map<String, Rec>,
        val items: List<ActivityItem>,
        val badge: ActivityBadgeCounts,
        /** Next time visibility changes by itself, or null. */
        val nextDeadline: Long?,
    )

    private val lock = SynchronizedObject()
    private val model = MutableStateFlow(build(0L, emptyMap()))
    private val _items = MutableStateFlow<List<ActivityItem>>(emptyList())
    private val _unread = MutableStateFlow(0)
    private val _badge = MutableStateFlow(ActivityBadgeCounts())
    val badge: StateFlow<ActivityBadgeCounts> = _badge.asStateFlow()

    /**
     * Visible items: running first, then finished; each group newest first.
     * Separate flow from [unreadFailures]; the two are updated one after the other, not atomically.
     */
    val items: StateFlow<List<ActivityItem>> = _items.asStateFlow()

    /** Unread failures plus running items requiring attention. Updated after [items], not atomically with it. */
    val unreadFailures: StateFlow<Int> = _unread.asStateFlow()

    init {
        scope.launch {
            model.collectLatest { m ->
                val deadline = m.nextDeadline ?: return@collectLatest
                delay((deadline - clock()).coerceAtLeast(0))
                change { it } // re-evaluate visibility at the new time
            }
        }
    }

    /** True while [id] is a RUNNING item, shown or still inside its debounce window. */
    fun isRunning(id: String): Boolean = model.value.recs[id]?.item?.state == ActivityState.RUNNING

    /**
     * Atomically: if [id] is RUNNING, set its [progress] and [cancel] (the work may become cancelable or stop
     * being so); otherwise start it with them. One lock hold, so a concurrent [cancelled]/[dismiss] cannot
     * leave a RUNNING row behind that nobody will finish.
     */
    fun startOrUpdate(id: String, kind: ActivityKind, title: String, progress: Int?, cancel: (() -> Unit)?) = change { recs ->
        val rec = recs[id]
        if (rec != null && rec.item.state == ActivityState.RUNNING) {
            recs + (id to Rec(rec.seq, rec.item.copy(progress = progress, cancel = cancel)))
        } else {
            recs + (id to Rec(nextSeq(), ActivityItem(id, kind, title, ActivityState.RUNNING, progress, null, cancel, clock(), null, true)))
        }
    }

    /**
     * Atomically: if [id] is RUNNING, fail it with [message]; otherwise (absent, failed or succeeded) record it as a
     * fresh failed, unread row. One lock hold, so there is no intermediate hidden RUNNING row: [items] and
     * [unreadFailures] never lose an existing failed row while it is refreshed, and a concurrent [dismiss] cannot
     * make the failure vanish.
     */
    fun startOrFail(id: String, kind: ActivityKind, title: String, message: String, backgroundFailure: Boolean = true, reportable: Boolean = false, cause: Throwable? = null) = change { recs ->
        val rec = recs[id]
        val now = clock()
        val occurrence = nextSeq()
        if (rec != null && rec.item.state == ActivityState.RUNNING) {
            recs + (id to Rec(rec.seq, rec.item.copy(state = ActivityState.FAILED, message = message, finishedAt = now, cancel = null, read = false, backgroundFailure = backgroundFailure, failureOccurrence = occurrence, reportable = reportable, cause = cause, attention = false)))
        } else {
            recs + (id to Rec(occurrence, ActivityItem(id, kind, title, ActivityState.FAILED, null, message, null, now, now, false, backgroundFailure, occurrence, reportable, cause)))
        }
    }

    fun start(id: String, kind: ActivityKind, title: String, cancel: (() -> Unit)? = null) = change { recs ->
        val now = clock()
        recs + (id to Rec(nextSeq(), ActivityItem(id, kind, title, ActivityState.RUNNING, null, null, cancel, now, null, true)))
    }

    /** Ignored unless the item is still RUNNING, so a late progress event cannot change a finished row. */
    fun update(id: String, progress: Int?) = modify(id) {
        if (it.state == ActivityState.RUNNING) it.copy(progress = progress) else it
    }

    internal fun setTitle(id: String, title: String) = modify(id) {
        if (it.state == ActivityState.RUNNING) it.copy(title = title) else it
    }

    fun setMessage(id: String, message: String?) = modify(id) {
        if (it.state == ActivityState.RUNNING) it.copy(message = message) else it
    }

    fun setAttention(id: String, attention: Boolean) = modify(id) {
        if (it.state == ActivityState.RUNNING) it.copy(attention = attention) else it
    }

    fun succeed(id: String) = change { recs ->
        val rec = recs[id] ?: return@change recs
        val now = clock()
        if (rec.item.kind == ActivityKind.SYNC || (rec.item.kind != ActivityKind.DOCUMENT_SYNC && now - rec.item.startedAt < DEBOUNCE_MS)) {
            recs - id // never shown, or not worth keeping
        } else {
            recs + (id to Rec(rec.seq, rec.item.copy(state = ActivityState.SUCCEEDED, progress = 100, finishedAt = now, cancel = null, attention = false)))
        }
    }

    fun fail(id: String, message: String, reportable: Boolean = false, cause: Throwable? = null) = modify(id) {
        it.copy(state = ActivityState.FAILED, message = message, finishedAt = clock(), cancel = null, read = false, reportable = reportable, cause = cause, attention = false)
    }

    fun setNotificationMessage(id: String, message: String?) = modify(id) {
        if (it.state == ActivityState.RUNNING) it.copy(notificationMessage = message) else it
    }

    fun interrupt(id: String, message: String, notify: Boolean) = modify(id) {
        if (it.state == ActivityState.RUNNING) it.copy(state = ActivityState.INTERRUPTED, message = message,
            finishedAt = clock(), cancel = null, attention = false, notifyInterruption = notify) else it
    }

    fun cancelled(id: String) = change { it - id }

    fun dismiss(id: String) = change { it - id }

    /** Removes every finished item (running ones stay). */
    fun clearFinished() = change { recs -> recs.filterValues { it.item.state == ActivityState.RUNNING } }

    fun markAllRead() = change { recs -> recs.mapValues { (_, r) -> Rec(r.seq, r.item.copy(read = true)) } }

    private fun modify(id: String, f: (ActivityItem) -> ActivityItem) = change { recs ->
        val rec = recs[id] ?: return@change recs
        recs + (id to Rec(rec.seq, f(rec.item)))
    }

    private var seqCounter = 0L
    private fun nextSeq() = ++seqCounter

    private fun change(f: (Map<String, Rec>) -> Map<String, Rec>) = synchronized(lock) {
        val m = build(clock(), f(model.value.recs))
        model.value = m
        _items.value = m.items
        _unread.value = m.badge.total
        _badge.value = m.badge
    }

    companion object {
        const val DEBOUNCE_MS = 1000L
        const val SUCCESS_KEEP_MS = 60_000L
        const val MAX_FINISHED = 50

        private fun build(now: Long, input: Map<String, Rec>): Model {
            // Drop expired successes, then cap the finished items to the newest MAX_FINISHED.
            var recs = input.filterValues {
                !(it.item.state == ActivityState.SUCCEEDED && now >= it.item.finishedAt!! + SUCCESS_KEEP_MS)
            }
            val finishedOrder = compareByDescending<Rec> { it.item.finishedAt }.thenByDescending { it.seq }
            val finished = recs.values.filter { it.item.state != ActivityState.RUNNING }.sortedWith(finishedOrder)
            if (finished.size > MAX_FINISHED) {
                val drop = finished.drop(MAX_FINISHED).map { it.item.id }.toSet()
                recs = recs.filterKeys { it !in drop }
            }
            val running = recs.values.filter { it.item.state == ActivityState.RUNNING }
            val visibleRunning = running.filter { now - it.item.startedAt >= DEBOUNCE_MS }
                .sortedWith(compareByDescending<Rec> { it.item.startedAt }.thenByDescending { it.seq })
            val visibleFinished = recs.values.filter { it.item.state != ActivityState.RUNNING }.sortedWith(finishedOrder)
            val deadlines = running.filter { now - it.item.startedAt < DEBOUNCE_MS }.map { it.item.startedAt + DEBOUNCE_MS } +
                recs.values.filter { it.item.state == ActivityState.SUCCEEDED }.map { it.item.finishedAt!! + SUCCESS_KEEP_MS }
            return Model(
                recs,
                (visibleRunning + visibleFinished).map { it.item },
                ActivityBadgeCounts(
                    failures = recs.values.count { it.item.state == ActivityState.FAILED && !it.item.read },
                    attention = recs.values.count { it.item.state == ActivityState.RUNNING && it.item.attention },
                ),
                deadlines.minOrNull(),
            )
        }
    }
}
