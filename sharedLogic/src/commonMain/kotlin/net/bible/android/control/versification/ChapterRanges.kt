package net.bible.android.control.versification

import net.bible.sharedcore.log.Log
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification

fun wholeChapter(currentVerse: Verse, showIntros: Boolean = true): VerseRange {
    Log.i("ChapterRanges", "getWholeChapter (Key) ${currentVerse.getOsisID()}")
    val versification = currentVerse.versification
    val book = currentVerse.book
    val chapter = currentVerse.chapter

    val startChapter = if(showIntros && chapter == 1) 0 else chapter
    val endChapter = if(showIntros && chapter == 0) 1 else chapter

    val targetChapterFirstVerse = Verse(versification, book, startChapter, 0)
    val targetChapterLastVerse = Verse(versification, book, endChapter, versification.getLastVerse(book, endChapter))

    // convert to full chapter before returning because bible view is for a full chapter
    return VerseRange(versification, targetChapterFirstVerse, targetChapterLastVerse)
}

fun wholeChapters(v11n: Versification, book: BibleBook, chapter1: Int, chapter2: Int): VerseRange {
    val targetChapterFirstVerse = Verse(v11n, book, chapter1, 0)
    val targetChapterLastVerse = Verse(v11n, book, chapter2, v11n.getLastVerse(book, chapter2))

    return VerseRange(v11n, targetChapterFirstVerse, targetChapterLastVerse)
}

