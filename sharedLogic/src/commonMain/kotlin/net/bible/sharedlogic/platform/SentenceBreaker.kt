package net.bible.sharedlogic.platform

/**
 * Sentence boundaries with `java.text.BreakIterator` semantics: -1 for DONE, IllegalArgumentException
 * for an offset outside the text. iOS provides this from NSLinguisticTagger/ICU sentence breaking.
 */
interface SentenceBreaker {
    fun preceding(text: String, offset: Int): Int
    fun following(text: String, offset: Int): Int
}
