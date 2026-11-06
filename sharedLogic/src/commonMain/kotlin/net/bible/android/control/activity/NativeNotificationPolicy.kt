package net.bible.android.control.activity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppForeground
import net.bible.sharedcore.platform.NativeNotification
import net.bible.sharedcore.platform.NativeNotifier

/**
 * Turns [ActivityItem] changes into [NativeNotifier] calls (#4012), with no platform dependency.
 *
 * Rules: a FAILED item always notifies (urgent). A SUCCEEDED non-SYNC item that ran at least [LONG_JOB_MS] notifies
 * only when the app is not in the foreground. A RUNNING item shows a progress notification only when the notifier
 * supports it, the app is in the background and the item has run [LONG_JOB_MS]; updates are limited to one per
 * [PROGRESS_THROTTLE_MS] per item and only when the progress changed, the newest value being shown by a trailing update.
 * A vanished or CANCELLED item that had a notification (and was RUNNING or FAILED) removes it; a vanished SUCCEEDED
 * item leaves it to the OS.
 *
 * All state is confined to one collector coroutine in [scope]; every input (items, preference, foreground, a time
 * wake-up, close) is a flow combined into it, so no locks are needed and notifier calls run in that coroutine.
 * Time-based triggers (10 s reached, throttle window elapsed) are covered by a single one-shot wake-up job that is
 * scheduled after each evaluation only when something is pending, so an idle policy has no timer, it works with
 * virtual time and it dies with [close] or the scope.
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
    private val finishedText: () -> String,
) {
    private enum class Shown { PROGRESS, FINAL }

    private class ProgressShown(val progress: Int?, val at: Long)

    private val inForeground = MutableStateFlow(false)
    private val tick = MutableStateFlow(0L)
    private val closed = MutableStateFlow(false)
    private var job: Job? = null

    // Confined to the collector coroutine.
    private val shown = mutableMapOf<String, Shown>()
    private val lastSeen = mutableMapOf<String, ActivityState>()
    private val progressShown = mutableMapOf<String, ProgressShown>()
    private var wasEnabled = true
    private var needBaseline = false
    private var wakeJob: Job? = null
    private val loggedFailures = mutableSetOf<String>()

    /** Starts collecting. Idempotent. */
    fun start() {
        if (job != null) return
        inForeground.value = foreground.isForeground()
        job = scope.launch {
            combine(items, enabled, inForeground, tick, closed) { list, on, fg, _, isClosed ->
                Input(list, on, fg, isClosed)
            }.takeWhile { evaluate(it) }.collect { }
        }
    }

    /** Reports an app focus change; entering the foreground removes shown progress notifications. */
    fun onForegroundChanged(foreground: Boolean) { inForeground.value = foreground }

    /** Removes shown progress notifications and stops collecting (asynchronously, on the policy's scope). */
    fun close() { closed.value = true }

    /** The activity id behind a notification id made by this policy, or null for a foreign id. */
    fun activityIdOf(notificationId: String): String? =
        notificationId.takeIf { it.startsWith(ID_PREFIX) }?.removePrefix(ID_PREFIX)

    private class Input(val list: List<ActivityItem>, val on: Boolean, val fg: Boolean, val closed: Boolean)

    /** Returns false to end collection. */
    private fun evaluate(input: Input): Boolean {
        wakeJob?.cancel()
        wakeJob = null
        if (input.closed) {
            removeWhere { it == Shown.PROGRESS }
            return false
        }
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
            input.list.forEach { lastSeen[it.id] = it.state }
            needBaseline = false
        }
        val now = clock()
        if (input.fg) removeWhere { it == Shown.PROGRESS }
        var wakeAt = Long.MAX_VALUE
        val current = input.list.associateBy { it.id }

        lastSeen.keys.filter { it !in current }.forEach { id ->
            val last = lastSeen.remove(id)
            val kind = shown.remove(id)
            progressShown.remove(id)
            if (kind != null && (last == ActivityState.RUNNING || last == ActivityState.FAILED)) removeNotification(id)
        }
        for (item in input.list) {
            val prev = lastSeen[item.id]
            lastSeen[item.id] = item.state
            when (item.state) {
                ActivityState.FAILED -> if (prev != ActivityState.FAILED) {
                    showNotification(item, item.message ?: "", null, urgent = true, Shown.FINAL)
                }
                ActivityState.SUCCEEDED -> if (prev != ActivityState.SUCCEEDED) {
                    val finishedAt = item.finishedAt
                    val long = finishedAt != null && finishedAt - item.startedAt >= LONG_JOB_MS
                    if (item.kind != ActivityKind.SYNC && long && !input.fg) {
                        showNotification(item, finishedText(), null, urgent = false, Shown.FINAL)
                    } else if (shown[item.id] == Shown.PROGRESS) {
                        dropShown(item.id)
                    }
                }
                ActivityState.CANCELLED -> if (shown.containsKey(item.id)) dropShown(item.id)
                ActivityState.RUNNING -> if (notifier.supportsProgress && !input.fg) {
                    val due = item.startedAt + LONG_JOB_MS
                    val last = progressShown[item.id]
                    when {
                        now < due -> wakeAt = minOf(wakeAt, due)
                        last == null -> showProgress(item, now)
                        last.progress != item.progress -> {
                            val next = last.at + PROGRESS_THROTTLE_MS
                            if (now >= next) showProgress(item, now) else wakeAt = minOf(wakeAt, next)
                        }
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
        progressShown[item.id] = ProgressShown(p, now)
        showNotification(item, if (p != null) "$p %" else "", p, urgent = false, Shown.PROGRESS)
    }

    private fun showNotification(item: ActivityItem, body: String, progress: Int?, urgent: Boolean, kind: Shown) {
        shown[item.id] = kind
        if (kind == Shown.FINAL) progressShown.remove(item.id)
        guarded("show") {
            notifier.show(NativeNotification(ID_PREFIX + item.id, item.title, body, progress, progress == null && kind == Shown.PROGRESS, urgent))
        }
    }

    private fun dropShown(id: String) {
        shown.remove(id)
        progressShown.remove(id)
        removeNotification(id)
    }

    private fun removeWhere(predicate: (Shown) -> Boolean) {
        shown.filterValues(predicate).keys.forEach { dropShown(it) }
    }

    private fun removeNotification(id: String) = guarded("remove") { notifier.remove(ID_PREFIX + id) }

    /** Notifier calls must never break the policy; each kind of failure is logged once. */
    private inline fun guarded(kind: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (loggedFailures.add(kind)) Log.w(TAG, "Native notifier $kind failed; further failures of this kind are not logged", e)
        }
    }

    companion object {
        const val LONG_JOB_MS = 10_000L
        const val PROGRESS_THROTTLE_MS = 1_000L
        private const val ID_PREFIX = "andbible-"
        private const val TAG = "NativeNotificationPolicy"
    }
}
