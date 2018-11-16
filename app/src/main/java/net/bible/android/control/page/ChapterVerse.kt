package net.bible.android.control.page

import net.bible.android.SharedConstants

import org.crosswire.jsword.passage.Verse

/**
 * Represent a chapter and verse
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
data class ChapterVerse(val chapter: Int, val verse: Int) {

    /**
     * The format used for ids in html
     */
    fun toHtmlId(): String = chapter.toString() + "." + verse
    fun toChapterHtmlId(): String = chapter.toString()

    fun after(other: ChapterVerse): Boolean =
            chapter > other.chapter || (chapter == other.chapter && verse > other.verse)

    fun before(other: ChapterVerse): Boolean =
            chapter < other.chapter || (chapter == other.chapter && verse < other.verse)

    fun sameChapter(other: ChapterVerse): Boolean =
            chapter == other.chapter

    companion object {

        val NOT_SET = ChapterVerse(SharedConstants.NO_VALUE, SharedConstants.NO_VALUE)

        @JvmStatic fun isSet(chapterVerse: ChapterVerse?) = chapterVerse!=null && chapterVerse!=NOT_SET

        @JvmStatic fun fromHtmlId(chapterDotVerse: String): ChapterVerse {
            val strings = chapterDotVerse.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val chapter = Integer.parseInt(strings[0])
            val verse = Integer.parseInt(strings[1])
            return ChapterVerse(chapter, verse)
        }

        @JvmStatic fun fromVerse(pVerse: Verse): ChapterVerse {
            val chapter = pVerse.chapter
            val verse = pVerse.verse
            return ChapterVerse(chapter, verse)
        }
    }
}
