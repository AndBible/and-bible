package net.bible.sharedlogic.search

import fi.sykero.kdom.Element
import fi.sykero.kdom.Text
import net.bible.android.control.search.SearchTextHighlighter
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.search.strongsHighlightPatterns
import net.bible.sharedcore.search.termHighlightPattern

/**
 * Platform-neutral counterpart of Android's `SearchHighlighter`: same OSIS walk and same marked-up verse string, but
 * the bold ranges come from scanning its own `<b>...</b>` markers instead of `Html.fromHtml` (`android.text`).
 *
 * Modes (mutually exclusive, as on Android): word-term ([terms]) marks matches `highlight`; Strong's
 * ([lemmaTerms]) marks elements whose `lemma` attribute matches `bold`.
 * Note: unlike `Html.fromHtml`, no HTML entity decoding happens; kdom text is already decoded.
 */
object CommonSearchHighlighter : SearchTextHighlighter {
    private const val TAG = "CommonSearchHighlighter"
    private const val OPEN = "<b>"
    private const val CLOSE = "</b>"
    private val elementsToExclude = listOf("note", "reference")

    override fun highlight(osis: Element, terms: List<String>, lemmaTerms: List<String>): StyledText {
        val isStrongsSearch = lemmaTerms.isNotEmpty()
        val strongsPatterns = lemmaTerms.flatMap { strongsHighlightPatterns(it) }
            .map { Regex(it, RegexOption.IGNORE_CASE) }
        val verseString = StringBuilder()
        for (verse in osis.getChildren("verse")) {
            if (isStrongsSearch) {
                verseString.append(processElementChildrenWithLemmaHighlight(verse, strongsPatterns, false))
            } else {
                verseString.append(processElementChildren(verse))
            }
        }

        // Strip our own bold markers, recording the bold ranges in plain-text coordinates.
        val marked = verseString.toString()
        val plain = StringBuilder()
        val bold = ArrayList<Boolean>()
        var inBold = false
        var i = 0
        while (i < marked.length) {
            when {
                marked.startsWith(OPEN, i) -> { inBold = true; i += OPEN.length }
                marked.startsWith(CLOSE, i) -> { inBold = false; i += CLOSE.length }
                else -> { plain.append(marked[i]); bold.add(inBold); i++ }
            }
        }
        val text = plain.toString()

        val highlight = BooleanArray(text.length)
        if (!isStrongsSearch) {
            try {
                for (originalSearchWord in terms) {
                    val searchWord = termHighlightPattern(originalSearchWord) ?: continue
                    for (m in Regex(searchWord, RegexOption.IGNORE_CASE).findAll(text)) {
                        for (k in m.range) highlight[k] = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error highlighting search text", e)
            }
        }
        return buildStyledText(text, bold, highlight)
    }

    private fun buildStyledText(text: String, bold: List<Boolean>, highlight: BooleanArray): StyledText {
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
                is Element -> if (!elementsToExclude.contains(o.name)) {
                    verseString.append(if (o.children.isEmpty()) o.text else processElementChildren(o))
                }
                is Text -> verseString.append(o.text)
                else -> verseString.append(o.toString())
            }
        }
        return verseString.toString()
    }

    private fun processElementChildrenWithLemmaHighlight(
        parentElement: Element,
        strongsPatterns: List<Regex>,
        isBold: Boolean,
    ): String {
        val verseString = StringBuilder()
        for (o in parentElement.content) {
            when (o) {
                is Element -> if (!elementsToExclude.contains(o.name)) {
                    val currentIsBold = isBold || try {
                        val lemma = o.getAttributeValue("lemma")
                        lemma != null && strongsPatterns.any { it.containsMatchIn(lemma.trim()) }
                    } catch (e: Exception) {
                        false
                    }
                    if (o.children.isEmpty()) {
                        val text = o.text ?: ""
                        verseString.append(if (currentIsBold) "$OPEN$text$CLOSE" else text)
                    } else {
                        verseString.append(processElementChildrenWithLemmaHighlight(o, strongsPatterns, currentIsBold))
                    }
                }
                is Text -> verseString.append(if (isBold) "$OPEN${o.text}$CLOSE" else o.text)
                else -> verseString.append(o.toString())
            }
        }
        return verseString.toString()
    }
}
