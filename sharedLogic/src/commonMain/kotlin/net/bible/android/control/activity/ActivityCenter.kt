package net.bible.android.control.activity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Registry of long-running background work (downloads, installs, indexing, sync) for the activity center UI.
 *
 * Rules: a running item is hidden for its first [DEBOUNCE_MS] so short jobs never flash; a failure is
 * shown at once and kept until dismissed; a success is kept [SUCCESS_KEEP_MS] (SYNC successes are not
 * kept); a cancelled item is dropped immediately; at most [MAX_FINISHED] finished items are kept.
 *
 * Intended to be called from one thread (the main thread); [scope] must run on that thread too. It hosts
 * one coroutine that wakes at the next debounce end / success expiry, using [delay], so a test
 * scheduler's virtual time works as long as [clock] follows it.
 */
class ActivityCenter(scope: CoroutineScope, private val clock: () -> Long) {

    private class Rec(val seq: Long, val item: ActivityItem)

    private class Model(
        val recs: Map<String, Rec>,
        val items: List<ActivityItem>,
        val unread: Int,
        /** Next time visibility changes by itself, or null. */
        val nextDeadline: Long?,
    )

    private val model = MutableStateFlow(build(0L, emptyMap()))
    private val _items = MutableStateFlow<List<ActivityItem>>(emptyList())
    private val _unread = MutableStateFlow(0)

    /**
     * Visible items: running first, then finished; each group newest first.
     * Separate flow from [unreadFailures]; the two are updated one after the other, not atomically.
     */
    val items: StateFlow<List<ActivityItem>> = _items.asStateFlow()

    /** Number of failed items the user has not acknowledged with [markAllRead]. Updated after [items], not atomically with it. */
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

    fun start(id: String, kind: ActivityKind, title: String, cancel: (() -> Unit)? = null) = change { recs ->
        val now = clock()
        recs + (id to Rec(nextSeq(), ActivityItem(id, kind, title, ActivityState.RUNNING, null, null, cancel, now, null, true)))
    }

    /** Ignored unless the item is still RUNNING, so a late progress event cannot change a finished row. */
    fun update(id: String, progress: Int?) = modify(id) {
        if (it.state == ActivityState.RUNNING) it.copy(progress = progress) else it
    }

    fun succeed(id: String) = change { recs ->
        val rec = recs[id] ?: return@change recs
        val now = clock()
        if (rec.item.kind == ActivityKind.SYNC || now - rec.item.startedAt < DEBOUNCE_MS) {
            recs - id // never shown, or not worth keeping
        } else {
            recs + (id to Rec(rec.seq, rec.item.copy(state = ActivityState.SUCCEEDED, progress = 100, finishedAt = now, cancel = null)))
        }
    }

    fun fail(id: String, message: String) = modify(id) {
        it.copy(state = ActivityState.FAILED, message = message, finishedAt = clock(), cancel = null, read = false)
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

    private fun change(f: (Map<String, Rec>) -> Map<String, Rec>) {
        val m = build(clock(), f(model.value.recs))
        model.value = m
        _items.value = m.items
        _unread.value = m.unread
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
                recs.values.count { it.item.state == ActivityState.FAILED && !it.item.read },
                deadlines.minOrNull(),
            )
        }
    }
}
