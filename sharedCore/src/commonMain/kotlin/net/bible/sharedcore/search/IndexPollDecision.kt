/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.sharedcore.search

/** What the caller should do after asking once whether the index has reached DONE. */
enum class PollOutcome { Done, KeepPolling, GaveUp }

/**
 * The bookkeeping half of classic's post-indexing wait, with no timing in it.
 *
 * JSword's `Progress` declares itself finished *before* the document's `indexStatus` flips to
 * `DONE`, so `SearchIndexProgressComposeActivity.jobFinished` (:132-161) re-read the status up to
 * six times at 2 s intervals — at most ~12 s — and only then treated the index as invalid. That
 * cap is preserved here; what is dropped is the blocking `pause(2)` loop, so the caller owns the
 * delay (a coroutine `delay`, a timer, or a test's nothing at all) and this class stays pure.
 *
 * One instance per index build: the counter is not reset.
 */
class IndexPollDecision(private val maxPolls: Int = 6) {
    private var polls = 0

    /**
     * Records one status read. [indexDone] short-circuits to [PollOutcome.Done] regardless of how
     * many polls have been spent (classic checked the condition before each pause, so an index that
     * was already DONE never waited), and the [maxPolls]th not-done read is the last one:
     * [PollOutcome.GaveUp].
     */
    fun onPoll(indexDone: Boolean): PollOutcome = when {
        indexDone -> PollOutcome.Done
        ++polls >= maxPolls -> PollOutcome.GaveUp
        else -> PollOutcome.KeepPolling
    }
}
