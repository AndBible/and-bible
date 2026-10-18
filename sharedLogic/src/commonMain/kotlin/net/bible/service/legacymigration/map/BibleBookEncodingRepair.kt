package net.bible.service.legacymigration.map

import net.bible.service.legacymigration.read.LegacyRow
import net.bible.service.legacymigration.read.LegacyValue

/** `PageManager.jswordBibleBookEncoding` of the Swift app: the marker that the stored book is a JSword `BibleBook.ordinal()`. */
internal const val JSWORD_BIBLE_BOOK_ENCODING = "jswordOrdinal"

/** Android's `CurrentBibleVerse` default (JSword's KJV versification, Genesis 1:1; `BibleBook.GEN.ordinal` is 2). */
internal const val ANDROID_DEFAULT_VERSIFICATION = "KJV"
internal const val ANDROID_DEFAULT_BIBLE_BOOK = 2L
internal const val ANDROID_DEFAULT_CHAPTER = 1L
internal const val ANDROID_DEFAULT_VERSE = 1L

/**
 * Port of `PageManagerBibleBookEncodingMigrationService` / `PageManager.normalizeBibleBookEncodingToJSwordOrdinal()`
 * of the Swift app, for one legacy `PageManager` row. Returns the row's property values with the Bible location fixed:
 *
 * - A row is left unchanged when its `bibleBookEncoding` is [JSWORD_BIBLE_BOOK_ENCODING] and versification, book,
 *   chapter and verse are all present.
 * - Any other row (no marker, which includes every row of a 1.0.1 store that predates the `bibleBookEncoding`
 *   column, an unknown marker, or an incomplete tuple) is reset to Android's default Genesis 1:1 and marked canonical.
 *   Like the Swift code this neither looks at installed modules nor reinterprets the old integer, because an unmarked
 *   book number is an index into a module's own book list and cannot be resolved without that module. The selected
 *   Bible document and every other category stay as they were.
 */
fun repairBibleBookEncoding(pageManager: LegacyRow): Map<String, LegacyValue?> {
    val v = pageManager.values
    val canonical = (v["bibleBookEncoding"] as? LegacyValue.TextV)?.value == JSWORD_BIBLE_BOOK_ENCODING &&
        v["bibleVersification"] != null && v["bibleBibleBook"] != null &&
        v["bibleChapterNo"] != null && v["bibleVerseNo"] != null
    if (canonical) return v
    return v + mapOf(
        "bibleVersification" to LegacyValue.TextV(ANDROID_DEFAULT_VERSIFICATION),
        "bibleBibleBook" to LegacyValue.IntV(ANDROID_DEFAULT_BIBLE_BOOK),
        "bibleChapterNo" to LegacyValue.IntV(ANDROID_DEFAULT_CHAPTER),
        "bibleVerseNo" to LegacyValue.IntV(ANDROID_DEFAULT_VERSE),
        "bibleBookEncoding" to LegacyValue.TextV(JSWORD_BIBLE_BOOK_ENCODING),
    )
}
