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

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import net.bible.sharedcore.log.Log
import net.bible.service.common.htmlToSpan
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.search.prepareSearchWord
import org.jdom2.Element
import org.jdom2.Text
import java.util.regex.Pattern

/**
 * Reusable OSIS/JDOM highlighting walk, extracted verbatim from the classic
 * [net.bible.android.view.activity.search.MultiSearchItemAdapter] highlighting methods but emitting the
 * portable [StyledText] (a list of [StyledRun]) instead of Android `Spannable` spans.
 *
 * Two mutually-exclusive modes, mirroring the original logic:
 *  - **word-term** ([terms] non-empty, [lemmaTerms] empty): the tree is flattened to plain text and every
 *    match of a search term is marked `highlight = true`.
 *  - **Strong's lemma** ([lemmaTerms] non-empty): the tree walk bolds any element whose `lemma` attribute
 *    matches one of the Strong's patterns, marked `bold = true`.
 *
 * Callers derive [terms]/[lemmaTerms] with the commonMain helpers (`prepareSearchTerms` / `splitSearchTerms`);
 * a Strong's search (search string contains `strong:`) supplies [lemmaTerms] and no [terms].
 */
object SearchHighlighter {
    private const val TAG = "SearchHighlighter"
    private val elementsToExclude = listOf("note", "reference")

    fun highlight(osis: Element, terms: List<String>, lemmaTerms: List<String>): StyledText {
        val isStrongsSearch = lemmaTerms.isNotEmpty()
        val strongsPatterns = lemmaTerms.map { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }
        val verseString = StringBuilder()

        val verses = osis.getChildren("verse")
        for (verse in verses) {
            if (isStrongsSearch) {
                verseString.append(processElementChildrenWithLemmaHighlight(verse, strongsPatterns, false))
            } else {
                verseString.append(processElementChildren(verse))
            }
        }

        val spanned: Spanned = htmlToSpan(verseString.toString())
        val text = spanned.toString()
        val length = text.length

        // Lemma matches arrive as <b> tags → BOLD StyleSpans after htmlToSpan.
        val bold = BooleanArray(length)
        for (span in spanned.getSpans(0, length, StyleSpan::class.java)) {
            if (span.style == Typeface.BOLD || span.style == Typeface.BOLD_ITALIC) {
                val start = spanned.getSpanStart(span).coerceIn(0, length)
                val end = spanned.getSpanEnd(span).coerceIn(0, length)
                for (i in start until end) bold[i] = true
            }
        }

        val highlight = BooleanArray(length)
        if (!isStrongsSearch) {
            try {
                for (originalSearchWord in terms) {
                    var searchWord = prepareSearchWord(originalSearchWord)
                    searchWord = if (originalSearchWord.contains("*")) {
                        "\\b$searchWord[\\w'\\-]*\\b"
                    } else {
                        "\\b$searchWord\\b"
                    }
                    val m = Pattern.compile(searchWord, Pattern.CASE_INSENSITIVE).matcher(text)
                    while (m.find()) {
                        for (i in m.start() until m.end()) highlight[i] = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error highlighting search text", e)
            }
        }

        return buildStyledText(text, bold, highlight)
    }

    /** Coalesce per-character (bold, highlight) flags into contiguous [StyledRun]s. */
    private fun buildStyledText(text: String, bold: BooleanArray, highlight: BooleanArray): StyledText {
        val runs = mutableListOf<StyledRun>()
        var i = 0
        while (i < text.length) {
            val b = bold[i]
            val h = highlight[i]
            var j = i + 1
            while (j < text.length && bold[j] == b && highlight[j] == h) j++
            runs.add(StyledRun(text.substring(i, j), bold = b, highlight = h))
            i = j
        }
        return StyledText(runs)
    }

    private fun processElementChildren(parentElement: Element): String {
        val verseString = StringBuilder()
        for (o in parentElement.content) {
            when (o) {
                is Element -> {
                    if (!elementsToExclude.contains(o.name)) {
                        if (o.children.isEmpty()) {
                            verseString.append(o.text)
                        } else {
                            verseString.append(processElementChildren(o))
                        }
                    }
                }
                is Text -> {
                    verseString.append(o.text)
                }
                else -> {
                    verseString.append(o.toString())
                }
            }
        }
        return verseString.toString()
    }

    /**
     * Process element children for Strong's searches, checking lemma attributes to determine
     * which words should be bolded. Returns HTML string with <b> tags for matched words.
     */
    private fun processElementChildrenWithLemmaHighlight(
        parentElement: Element,
        strongsPatterns: List<Pattern>,
        isBold: Boolean
    ): String {
        val verseString = StringBuilder()
        for (o in parentElement.content) {
            when (o) {
                is Element -> {
                    if (!elementsToExclude.contains(o.name)) {
                        val currentIsBold = isBold || try {
                            val lemma = o.getAttributeValue("lemma")
                            lemma != null && strongsPatterns.any { it.matcher(lemma.trim()).find() }
                        } catch (e: Exception) {
                            false
                        }
                        if (o.children.isEmpty()) {
                            val text = o.text ?: ""
                            verseString.append(if (currentIsBold) "<b>$text</b>" else text)
                        } else {
                            verseString.append(processElementChildrenWithLemmaHighlight(o, strongsPatterns, currentIsBold))
                        }
                    }
                }
                is Text -> {
                    verseString.append(if (isBold) "<b>${o.text}</b>" else o.text)
                }
                else -> {
                    verseString.append(o.toString())
                }
            }
        }
        return verseString.toString()
    }
}
