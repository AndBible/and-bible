package net.bible.android.control.search

import net.bible.sharedcore.search.StyledText
import fi.sykero.kdom.Element

/**
 * Converts OSIS search previews into portable styled runs using platform text styling.
 *
 * The text is that of the fragment's `<verse>` elements, or of the whole fragment when it has none (a commentary
 * entry as `readOsisFragment` returns it). With [excerptLength], a word-term preview is cut by `searchExcerpt` to that
 * many characters around the first match before it is styled (long commentary entries, #4092).
 */
fun interface SearchTextHighlighter {
    fun highlight(osis: Element, terms: List<String>, lemmaTerms: List<String>, excerptLength: Int?): StyledText

    fun highlight(osis: Element, terms: List<String>, lemmaTerms: List<String>): StyledText =
        highlight(osis, terms, lemmaTerms, null)
}
