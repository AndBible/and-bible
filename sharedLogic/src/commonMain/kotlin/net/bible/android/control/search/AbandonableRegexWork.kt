/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.control.search

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.search.StyledText
import org.crosswire.jsword.index.RegexSearchBudget
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/*
 * A regular-expression match cannot be interrupted portably: Android's ICU engine copies its input and matches it
 * natively, so a catastrophically backtracking pattern ignores the RegexSearchBudget for as long as it runs. The regex
 * work of a search therefore runs in workers that the search can stop waiting for. A worker that is abandoned keeps
 * its thread until the engine returns (it is counted in [abandonedRegexWorkRunning], and a new search is refused while
 * one runs), but it sees an exhausted budget from then on (so every engine that does poll
 * stops at once), and nothing it produces afterwards reaches the caller: it only returns values, or sends them into a
 * channel the caller has closed.
 */

/** How long a search waits beyond its time limit for a worker before abandoning it. */
internal val REGEX_ABANDON_GRACE: Duration = 500.milliseconds

/**
 * How many abandoned workers are still running their engine. Android's ICU engine cannot be interrupted, so a runaway
 * pattern keeps its thread at full CPU until the process dies: while this is not zero, a new regex search is refused
 * ([awaitNoAbandonedRegexWork]) instead of adding a second such thread.
 */
private val abandonedStillRunning = atomic(0)

/** The number of abandoned regex workers whose engine has not returned yet. */
internal val abandonedRegexWorkRunning: Int get() = abandonedStillRunning.value

/**
 * Whether no abandoned regex worker is running, waiting up to [patience] for one that is about to stop (a cancelled
 * search whose engine polls the budget returns within milliseconds; a runaway ICU match never does).
 */
internal suspend fun awaitNoAbandonedRegexWork(patience: Duration = 300.milliseconds): Boolean =
    withTimeoutOrNull(patience) {
        while (abandonedStillRunning.value > 0) delay(20.milliseconds)
        true
    } ?: false

/** One worker's life, so that a worker abandoned while it runs is counted until, and only until, it returns. */
private class WorkerTicket {
    private val state = atomic(RUNNING)

    /** The caller stopped waiting. Counted first: [finished] may run at once and must find the count already raised. */
    fun abandon() {
        abandonedStillRunning.incrementAndGet()
        if (!state.compareAndSet(RUNNING, ABANDONED)) abandonedStillRunning.decrementAndGet()
    }

    /** The engine returned (or threw). */
    fun finished() {
        if (!state.compareAndSet(RUNNING, FINISHED)) abandonedStillRunning.decrementAndGet()
    }

    private companion object {
        const val RUNNING = 0
        const val ABANDONED = 1
        const val FINISHED = 2
    }
}

/** Not a child of the caller: a cancelled search must not wait for an engine that cannot be cancelled. */
private val abandonableWork = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private const val TAG = "AbandonableRegexWork"

/** The finished scans of [scanWithinBudget] in item order; [timedOut] means it stopped at the time limit. */
internal class RegexScanOutcome<T, R>(val results: List<Pair<T, R>>, val timedOut: Boolean)

private class Box<R>(val value: R)

/**
 * Runs [scan] for each of [items] in turn, each on an abandonable worker, sharing one budget of [budgetDuration]. A scan
 * may return null to skip its item. When the budget is gone before an item starts, or a scan has not returned
 * [grace] after the deadline, the call returns at once with `timedOut = true` and the results so far; a stuck scan is
 * abandoned. Cancelling the caller abandons the running scan the same way and throws [CancellationException] promptly.
 * An exception thrown by a scan is rethrown here.
 */
internal suspend fun <T, R : Any> scanWithinBudget(
    items: List<T>,
    budgetDuration: Duration,
    grace: Duration = REGEX_ABANDON_GRACE,
    scan: (T, RegexSearchBudget) -> R?,
): RegexScanOutcome<T, R> {
    val caller = currentCoroutineContext()
    val abandoned = atomic(false)
    val deadline = TimeSource.Monotonic.markNow() + budgetDuration
    val budget = RegexSearchBudget(deadline) { abandoned.value || !caller.isActive }
    val results = ArrayList<Pair<T, R>>()
    for (item in items) {
        if (budget.exhausted) return RegexScanOutcome(results, timedOut = true)
        val ticket = WorkerTicket()
        val worker = abandonableWork.async { try { Box(scan(item, budget)) } finally { ticket.finished() } }
        val wait = (-deadline.elapsedNow()).coerceAtLeast(Duration.ZERO) + grace
        val done = try {
            withTimeoutOrNull(wait) { worker.await() }
        } catch (e: CancellationException) {
            abandoned.value = true
            ticket.abandon()
            throw e
        }
        if (done == null) {
            abandoned.value = true
            ticket.abandon()
            Log.w(TAG, "A regex scan did not stop at its time limit; abandoned it")
            return RegexScanOutcome(results, timedOut = true)
        }
        done.value?.let { results += item to it }
    }
    return RegexScanOutcome(results, timedOut = false)
}

/**
 * [texts] passed through [highlight] on one abandonable worker within [allowance]. Texts the worker has not finished
 * [grace] after the allowance (or when the caller is cancelled) are returned unchanged, as is a text whose [highlight]
 * throws; the search still completes with every row.
 */
internal suspend fun highlightWithinAllowance(
    texts: List<StyledText>,
    allowance: Duration,
    grace: Duration = REGEX_ABANDON_GRACE,
    highlight: (StyledText, RegexSearchBudget) -> StyledText,
): List<StyledText> {
    if (texts.isEmpty()) return texts
    val caller = currentCoroutineContext()
    val abandoned = atomic(false)
    val budget = RegexSearchBudget(TimeSource.Monotonic.markNow() + allowance) { abandoned.value || !caller.isActive }
    val finished = Channel<Pair<Int, StyledText>>(Channel.UNLIMITED)
    val ticket = WorkerTicket()
    abandonableWork.launch {
        try {
            for ((i, text) in texts.withIndex()) {
                if (budget.exhausted) break
                val out = try {
                    highlight(text, budget)
                } catch (e: Exception) {
                    Log.w(TAG, "Highlighting a regex result failed: ${e.message}")
                    text
                }
                if (finished.trySend(i to out).isFailure) break
            }
        } finally {
            finished.close()
            ticket.finished()
        }
    }
    val out = texts.toMutableList()
    val complete = try {
        withTimeoutOrNull(allowance + grace) { for ((i, text) in finished) out[i] = text; true }
    } catch (e: CancellationException) {
        abandoned.value = true
        ticket.abandon()
        finished.cancel()
        throw e
    }
    if (complete == null) {
        abandoned.value = true
        ticket.abandon()
        while (true) {
            val (i, text) = finished.tryReceive().getOrNull() ?: break
            out[i] = text
        }
        finished.cancel()
        Log.w(TAG, "Highlighting regex results did not finish in time; the rest are shown without highlights")
    }
    return out
}
