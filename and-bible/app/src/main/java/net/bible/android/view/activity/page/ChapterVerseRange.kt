package net.bible.android.view.activity.page

import net.bible.android.control.page.ChapterVerse
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification

/**
 * Handle verse selection logic.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
data class ChapterVerseRange(private val v11n: Versification, private val bibleBook: BibleBook, val start: ChapterVerse, val end: ChapterVerse) {

    fun toggleVerse(verse: ChapterVerse): ChapterVerseRange {
        var newStart = start
        var newEnd = end
        if (verse.after(end)) {
            newEnd = verse
        } else if (verse.before(start)) {
            newStart = verse
        } else if (verse.after(start)) {
            // inc/dec are tricky when we don't know how many verses in chapters
            newEnd =
                    if (verse.verse > 1) {
                        ChapterVerse(verse.chapter, verse.verse - 1)
                    } else {
                        verse
                    }
        } else if (verse == start && start == end) {
            newStart = ChapterVerse.NOT_SET
            newEnd = ChapterVerse.NOT_SET
        } else if (verse == start) {
            // Inc/dec are tricky when we don't know how many verses in chapters.
            // So there is a flaw in that the first verse cannot be deselected if selection spans multiple chapters
            if (start.sameChapter(end) && start.sameChapter(verse)) {
                newStart = ChapterVerse(verse.chapter, verse.verse + 1)
            }
        }

        return ChapterVerseRange(v11n, bibleBook, newStart, newEnd)
    }

    fun getExtrasIn(other: ChapterVerseRange): Set<ChapterVerse> {
        val verseRange = createVerseRange()
        val otherVerseRange = other.createVerseRange()
        val otherVerses = otherVerseRange.toVerseArray()

        return otherVerses
                .filterNot { verseRange.contains(it) }
                .map { ChapterVerse(it.chapter, it.verse) }
                .toSet()
    }

    operator fun contains(verse: ChapterVerse) =
            verse == start ||
            verse == end ||
            (verse.after(start) && verse.before(end))

    private fun createVerseRange() =
            VerseRange(
                    v11n,
                    Verse(v11n, bibleBook, start.chapter, start.verse),
                    Verse(v11n, bibleBook, end.chapter, end.verse)
            )

    fun isEmpty() = !ChapterVerse.isSet(start) || !ChapterVerse.isSet(end)
}
