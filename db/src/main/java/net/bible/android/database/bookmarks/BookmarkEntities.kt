/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.database.bookmarks

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification
import org.crosswire.jsword.versification.VersificationConverter
import org.crosswire.jsword.versification.system.SystemKJVA
import org.crosswire.jsword.versification.system.Versifications
import android.graphics.Color
import kotlinx.serialization.Serializable
import java.util.*

val KJVA = Versifications.instance().getVersification(SystemKJVA.V11N_NAME)
val converter = VersificationConverter()

/**
 * How to represent bookmarks
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
enum class BookmarkStyle(val backgroundColor: Int) {
    YELLOW_STAR(Color.argb(0, 255, 255, 255)),
    RED_HIGHLIGHT(Color.argb((255 * 0.28).toInt(), 213, 0, 0)),
    YELLOW_HIGHLIGHT(Color.argb((255 * 0.33).toInt(), 255, 255, 0)),
    GREEN_HIGHLIGHT(Color.argb((255 * 0.33).toInt(), 0, 255, 0)),
    BLUE_HIGHLIGHT(Color.argb((255 * 0.33).toInt(), 145, 167, 255)),
    ORANGE_HIGHLIGHT(Color.argb((255 * 0.33).toInt(), 255, 165, 0)),
    PURPLE_HIGHLIGHT(Color.argb((255 * 0.33).toInt(), 128, 0, 128)),
    UNDERLINE(0),

    // Special hard-coded style for Speak bookmarks. This must be last one here.
    // This is removed from the style lists.
    SPEAK(Color.argb(0, 255, 255, 255));
    val colorArray: List<Int> get() {
        val ar = ArrayList<Int>()
        ar.add(Color.red(backgroundColor))
        ar.add(Color.green(backgroundColor))
        ar.add(Color.blue(backgroundColor))
        ar.add(Color.alpha(backgroundColor))
        return ar
    }

}

enum class BookmarkSortOrder {
    BIBLE_ORDER, CREATED_AT;
    val sqlString get() = when(this) {
        BIBLE_ORDER -> "Bookmark.kjvStartOrdinal"
        CREATED_AT -> "Bookmark.createdAt"
    }
}

interface VerseRangeUser {
    val verseRange: VerseRange
}

class BookmarkEntities {
    @Serializable
    class TextRange(val start: Int, val end: Int) {
        val clientList get() = listOf(start, end)
    }

    @Entity(
        indices = [
            Index("kjvOrdinalStart"), Index("kjvOrdinalEnd")
        ]
    )
    data class Bookmark (
        // Verse range in KJV ordinals. For generic lookups, we must store verse ranges in a "standard"
        // versification. We store also verserange in original versification, as it conveys the more exact
        // versification-specific information.

        var kjvOrdinalStart: Int,
        var kjvOrdinalEnd: Int,

        var ordinalStart: Int,
        var ordinalEnd: Int,
        var v11n: Versification,

        var playbackSettings: PlaybackSettings?,

        @PrimaryKey(autoGenerate = true) var id: Long = 0,
        var createdAt: Date = Date(System.currentTimeMillis()),

        var book: Book? = null,
        val textRange: TextRange? = null,
    ): VerseRangeUser {
        constructor(verseRange: VerseRange, textRange: TextRange?,  book: Book?): this(
            converter.convert(verseRange.start, KJVA).ordinal,
            converter.convert(verseRange.end, KJVA).ordinal,
            verseRange.start.ordinal,
            verseRange.end.ordinal,
            verseRange.versification,
            null,
            book = book,
            textRange = textRange
        )

        constructor(id: Long, createdAt: Date, verseRange: VerseRange, textRange: TextRange?, book: Book?, playbackSettings: PlaybackSettings?): this(
            converter.convert(verseRange.start, KJVA).ordinal,
            converter.convert(verseRange.end, KJVA).ordinal,
            verseRange.start.ordinal,
            verseRange.end.ordinal,
            verseRange.versification,
            playbackSettings,
            id,
            createdAt,
            book,
            textRange,
        )

        override var verseRange: VerseRange
            get() {
                val begin = Verse(v11n, ordinalStart)
                val end = Verse(v11n, ordinalEnd)
                return VerseRange(v11n, begin, end)
            }
            set(value) {
                v11n = value.versification
                ordinalStart = value.start.ordinal
                ordinalEnd = value.end.ordinal
                kjvVerseRange = value
            }

        var kjvVerseRange: VerseRange
            get() {
                val begin = Verse(KJVA, kjvOrdinalStart)
                val end = Verse(KJVA, kjvOrdinalEnd)
                return VerseRange(KJVA, begin, end)
            }
            private set(value) {
                kjvOrdinalStart = converter.convert(value.start, KJVA).ordinal
                kjvOrdinalEnd = converter.convert(value.end, KJVA).ordinal
            }

        val speakBook: Book?
            get() = if (playbackSettings != null && playbackSettings!!.bookId != null) {
                Books.installed().getBook(playbackSettings!!.bookId)
            } else {
                null
            }
    }

    @Entity(
        primaryKeys = ["bookmarkId", "labelId"],
        foreignKeys = [
            ForeignKey(entity = Bookmark::class, parentColumns = ["id"], childColumns = ["bookmarkId"], onDelete = ForeignKey.CASCADE),
            ForeignKey(entity = Label::class, parentColumns = ["id"], childColumns = ["labelId"], onDelete = ForeignKey.CASCADE)
        ],
        indices = [
            Index("labelId")
        ]
    )
    data class BookmarkToLabel(
        val bookmarkId: Long,
        val labelId: Long
    )

    @Entity
    @Serializable
    data class Label(
        @PrimaryKey(autoGenerate = true) var id: Long = 0,
        var name: String = "",
        var bookmarkStyle: BookmarkStyle? = null,
    ) {
        override fun toString() = name
    }
}
