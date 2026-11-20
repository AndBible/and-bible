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

import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import org.crosswire.jsword.index.RegexSearchBudget

/**
 * [text] with every non-empty match of [regex] in its plain text highlighted. Runs are split at the match
 * boundaries; `bold` is kept. Zero-length matches are skipped, so a pattern such as `x*` neither loops nor
 * highlights anything.
 *
 * With a [budget], an engine that reads its input through `get` (the JVM's, Kotlin/Native's) stops once the budget is
 * exhausted, and [text] is returned unhighlighted; so is it when the match overflows the stack. Android's ICU engine
 * cannot be stopped this way: run the highlighting through [highlightWithinAllowance].
 */
fun highlightRegexMatches(text: StyledText, regex: Regex, budget: RegexSearchBudget? = null): StyledText {
    val plain = text.plainText()
    val input: CharSequence = if (budget == null) plain else BudgetPollingText(plain, budget)
    val spans = try {
        regex.findAll(input).map { it.range }.filter { !it.isEmpty() }.toList()
    } catch (_: HighlightBudgetExhausted) {
        return text
    } catch (e: Throwable) {
        if (e::class.simpleName != "StackOverflowError") throw e
        return text
    }
    if (spans.isEmpty()) return text

    val out = ArrayList<StyledRun>()
    var runStart = 0
    var spanIndex = 0
    for (run in text.runs) {
        val runEnd = runStart + run.text.length
        var pos = runStart
        while (pos < runEnd) {
            while (spanIndex < spans.size && spans[spanIndex].last < pos) spanIndex++
            val span = spans.getOrNull(spanIndex)
            val inside = span != null && pos >= span.first
            val end = when {
                span == null -> runEnd
                inside -> minOf(runEnd, span.last + 1)
                else -> minOf(runEnd, span.first)
            }
            val piece = StyledRun(run.text.substring(pos - runStart, end - runStart), bold = run.bold, highlight = run.highlight || inside)
            val last = out.lastOrNull()
            if (last != null && last.bold == piece.bold && last.highlight == piece.highlight) {
                out[out.size - 1] = last.copy(text = last.text + piece.text)
            } else {
                out.add(piece)
            }
            pos = end
        }
        runStart = runEnd
    }
    return StyledText(out)
}

private class HighlightBudgetExhausted : RuntimeException()

/** [text] that checks [budget] on the first and then every [CHECK_INTERVAL]th character read. */
private class BudgetPollingText(private val text: String, private val budget: RegexSearchBudget) : CharSequence {
    private var reads = 0
    override val length: Int get() = text.length
    override fun get(index: Int): Char {
        if (reads++ % CHECK_INTERVAL == 0 && budget.exhausted) throw HighlightBudgetExhausted()
        return text[index]
    }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)
    override fun toString(): String = text

    private companion object { const val CHECK_INTERVAL = 1024 }
}
