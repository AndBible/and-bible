package net.bible.android.control.search

import net.bible.sharedcore.search.StyledText
import fi.sykero.kdom.Element

/** Converts OSIS search previews into portable styled runs using platform text styling. */
fun interface SearchTextHighlighter {
    fun highlight(osis: Element, terms: List<String>, lemmaTerms: List<String>): StyledText
}
