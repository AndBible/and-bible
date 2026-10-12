package net.bible.android.control.search

import net.bible.sharedcore.search.StyledText
import org.jdom2.Element

/** Converts OSIS search previews into portable styled runs using platform text styling. */
fun interface SearchTextHighlighter {
    fun highlight(osis: Element, terms: List<String>, lemmaTerms: List<String>): StyledText
}
