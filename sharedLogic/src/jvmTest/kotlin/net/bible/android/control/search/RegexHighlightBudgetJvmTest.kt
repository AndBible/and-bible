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

import net.bible.sharedcore.search.StyledText
import org.crosswire.jsword.index.RegexSearchBudget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** java.util.regex reads its input through `get`, so on the JVM the highlighter's budget interrupts the engine itself. */
class RegexHighlightBudgetJvmTest {
    @Test fun catastrophicHighlightStopsAtTheBudgetAndLeavesTheTextPlain() {
        val text = StyledText.plain("a".repeat(60) + "c")
        val start = TimeSource.Monotonic.markNow()
        val out = highlightRegexMatches(text, Regex("(a*)*\\1b"), RegexSearchBudget(start + 200.milliseconds))
        assertTrue(start.elapsedNow() < 3.seconds, "took ${start.elapsedNow()}")
        assertEquals(text, out)
    }

    @Test fun anOpenBudgetHighlightsAsBefore() {
        val out = highlightRegexMatches(StyledText.plain("x said: y"), Regex("said:"), RegexSearchBudget(TimeSource.Monotonic.markNow() + 1.hours))
        assertEquals(listOf(false, true, false), out.runs.map { it.highlight })
    }

    @Test fun aStackOverflowInTheHighlightLeavesTheTextPlain() {
        val text = StyledText.plain("ab".repeat(100_000))
        var out: StyledText? = null
        val t = Thread(null, { out = highlightRegexMatches(text, Regex("(a|b)*c"), RegexSearchBudget(TimeSource.Monotonic.markNow() + 1.hours)) }, "deep", 512L * 1024)
        t.start(); t.join(60_000)
        assertEquals(text, out)
    }
}
