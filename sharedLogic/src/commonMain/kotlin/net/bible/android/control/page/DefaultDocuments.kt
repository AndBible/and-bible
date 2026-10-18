package net.bible.android.control.page

import net.bible.android.common.toV11n
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory

fun defaultBible(): SwordBook = Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }[0] as SwordBook
fun defaultVerse(): VerseRange {
    val (otVerse, ntVerse, psVerse) =
        listOf("Gen.1.1-Gen.1.3", "Joh.3.16-Joh.3.18", "Ps.1.1-Ps.1.3")
            .map {
                VerseRangeFactory.fromString(KJVA, it).toV11n(defaultBible().versification)
            }
    return when {
        defaultBible().contains(ntVerse.start) -> ntVerse
        defaultBible().contains(otVerse.start) -> otVerse
        else -> psVerse
    }
}

