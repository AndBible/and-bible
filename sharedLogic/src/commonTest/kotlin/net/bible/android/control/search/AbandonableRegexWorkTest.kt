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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import org.crosswire.jsword.index.RegexSearchBudget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The regex work of a search stops being waited for at its time limit even when the engine ignores the budget (Android's
 * ICU engine cannot be interrupted): simulated here by workers that spin until the test releases them.
 */
class AbandonableRegexWorkTest {
    /**
     * Spins, ignoring every budget, until [release] is set (or, as a safety net for a broken implementation, 4 s have
     * passed); then reports whether the budget it was given had run out.
     */
    private class Blocker {
        val release = atomic(false)
        val started = atomic(false)
        val sawExhaustedBudget = atomic<Boolean?>(null)
        fun block(budget: RegexSearchBudget) {
            started.value = true
            val since = TimeSource.Monotonic.markNow()
            while (!release.value && since.elapsedNow() < 4.seconds) { /* an uninterruptible match */ }
            sawExhaustedBudget.value = budget.exhausted
        }
    }

    // Real time: the workers run on real threads, so virtual time must not let the timeouts fire early.
    private fun realTime(block: suspend CoroutineScope.() -> Unit) = runTest { withContext(Dispatchers.Default) { block() } }

    @Test fun aStuckScanEndsTheSearchAtBudgetPlusGraceAndKeepsEarlierBooks() = realTime {
        val blocker = Blocker()
        val ranAfterStuck = atomic(false)
        try {
            val start = TimeSource.Monotonic.markNow()
            val outcome = scanWithinBudget(listOf("A", "B", "C"), 300.milliseconds, grace = 200.milliseconds) { item, budget ->
                when (item) {
                    "A" -> "hits of A"
                    "B" -> { blocker.block(budget); "hits of B" }
                    else -> { ranAfterStuck.value = true; "hits of C" }
                }
            }
            val took = start.elapsedNow()
            assertTrue(took < 1500.milliseconds, "took $took")
            assertTrue(took >= 300.milliseconds, "returned before the budget: $took")
            assertTrue(outcome.timedOut)
            assertEquals(listOf("A" to "hits of A"), outcome.results)
            assertTrue(!ranAfterStuck.value, "no book is scanned after the budget ran out")
        } finally {
            blocker.release.value = true
        }
        // The abandoned worker sees an exhausted budget once it gets control back, so a polling engine stops at once.
        withTimeoutOrNull(5.seconds) { while (blocker.sawExhaustedBudget.value == null) delay(10) }
        assertEquals(true, blocker.sawExhaustedBudget.value)
    }

    @Test fun skippedBooksAndFinishedBooksAreNotTimeouts() = realTime {
        val outcome = scanWithinBudget(listOf("A", "skip", "C"), 1.hours) { item, _ -> if (item == "skip") null else item.lowercase() }
        assertEquals(listOf("A" to "a", "C" to "c"), outcome.results)
        assertTrue(!outcome.timedOut)
    }

    @Test fun anExhaustedBudgetScansNothing() = realTime {
        val outcome = scanWithinBudget(listOf("A"), 0.milliseconds) { _, _ -> error("must not run") }
        assertTrue(outcome.timedOut)
        assertEquals(emptyList(), outcome.results)
    }

    @Test fun cancellingTheCallerReturnsPromptlyFromAStuckScan() = realTime {
        val blocker = Blocker()
        try {
            val job = async {
                scanWithinBudget(listOf("A"), 1.hours) { _, budget -> blocker.block(budget); "never" }
            }
            withTimeoutOrNull(5.seconds) { while (!blocker.started.value) delay(10) }
            assertTrue(blocker.started.value)
            val returned = withTimeoutOrNull(2.seconds) { job.cancelAndJoin(); true }
            assertEquals(true, returned, "cancelling must not wait for the stuck worker")
        } finally {
            blocker.release.value = true
        }
        withTimeoutOrNull(5.seconds) { while (blocker.sawExhaustedBudget.value == null) delay(10) }
        assertEquals(true, blocker.sawExhaustedBudget.value, "a cancelled caller exhausts the worker's budget")
    }

    private fun marked(t: StyledText) = StyledText(t.runs.map { it.copy(highlight = true) })

    @Test fun aStuckHighlightReturnsTheRemainingPreviewsUnhighlighted() = realTime {
        val blocker = Blocker()
        val texts = (0 until 5).map { StyledText.plain("verse $it") }
        try {
            val start = TimeSource.Monotonic.markNow()
            val out = highlightWithinAllowance(texts, 300.milliseconds, grace = 200.milliseconds) { t, budget ->
                if (t == texts[2]) blocker.block(budget)
                marked(t)
            }
            val took = start.elapsedNow()
            assertTrue(took < 1500.milliseconds, "took $took")
            assertEquals(listOf(marked(texts[0]), marked(texts[1])) + texts.drop(2), out)
        } finally {
            blocker.release.value = true
        }
    }

    @Test fun highlightingWithinTheAllowanceHighlightsEverything() = realTime {
        val texts = (0 until 50).map { StyledText.plain("verse $it") }
        assertEquals(texts.map { marked(it) }, highlightWithinAllowance(texts, 5.seconds) { t, _ -> marked(t) })
    }

    @Test fun aFailingHighlightKeepsThatPreviewPlain() = realTime {
        val texts = listOf(StyledText.plain("a"), StyledText.plain("b"))
        val out = highlightWithinAllowance(texts, 5.seconds) { t, _ -> if (t == texts[0]) error("boom") else marked(t) }
        assertEquals(listOf(texts[0], marked(texts[1])), out)
        assertEquals(listOf(StyledRun("b", highlight = true)), out[1].runs)
    }

    @Test fun cancellingTheCallerReturnsPromptlyFromAStuckHighlight() = realTime {
        val blocker = Blocker()
        try {
            val job = launch {
                highlightWithinAllowance(listOf(StyledText.plain("a")), 1.hours) { t, budget -> blocker.block(budget); t }
            }
            withTimeoutOrNull(5.seconds) { while (!blocker.started.value) delay(10) }
            assertTrue(blocker.started.value)
            assertEquals(true, withTimeoutOrNull(2.seconds) { job.cancelAndJoin(); true })
        } finally {
            blocker.release.value = true
        }
    }
}
