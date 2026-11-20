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

/**
 * [text] with every non-empty match of [regex] in its plain text highlighted. Runs are split at the match
 * boundaries; `bold` is kept. Zero-length matches are skipped, so a pattern such as `x*` neither loops nor
 * highlights anything.
 */
fun highlightRegexMatches(text: StyledText, regex: Regex): StyledText {
    val plain = text.plainText()
    val spans = regex.findAll(plain).map { it.range }.filter { !it.isEmpty() }.toList()
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
