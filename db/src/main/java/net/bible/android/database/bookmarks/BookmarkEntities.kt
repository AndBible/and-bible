/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
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
import org.crosswire.jsword.versification.system.SystemKJVA
import org.crosswire.jsword.versification.system.Versifications
import android.graphics.Color
import androidx.room.ColumnInfo
import androidx.room.Ignore
import kotlinx.serialization.Serializable
import net.bible.android.common.toV11n
import net.bible.android.misc.OsisFragment
import org.crosswire.jsword.book.basic.AbstractPassageBook
import java.util.*

val KJVA = Versifications.instance().getVersification(SystemKJVA.V11N_NAME)

const val SPEAK_LABEL_NAME = "__SPEAK_LABEL__"
const val UNLABELED_NAME = "__UNLABELED__"

/**
 * How to represent bookmarks
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */


fun intToColorArray(colorInt: Int): ArrayList<Int> {
    val ar = ArrayList<Int>()
    ar.add(Color.red(colorInt))
    ar.add(Color.green(colorInt))
    ar.add(Color.blue(colorInt))
    ar.add(Color.alpha(colorInt))
    return ar
}

enum class BookmarkStyle(val backgroundColor: Int) {
    YELLOW_STAR(Color.argb(255, 255, 255, 0)),
    RED_HIGHLIGHT(Color.argb(255, 213, 0, 0)),
    YELLOW_HIGHLIGHT(Color.argb(255, 255, 255, 0)),
    GREEN_HIGHLIGHT(Color.argb(255, 0, 255, 0)),
    BLUE_HIGHLIGHT(Color.argb(255, 145, 167, 255)),
    ORANGE_HIGHLIGHT(Color.argb(255, 255, 165, 0)),
    PURPLE_HIGHLIGHT(Color.argb(255, 128, 0, 128)),
    UNDERLINE(Color.argb(255, 128, 99, 128)),

    // Special hard-coded style for Speak bookmarks. This must be last one here.
    // This is removed from the style lists.
    SPEAK(Color.argb(255, 255, 0, 0));
    val colorArray: List<Int> get() = intToColorArray(backgroundColor)
}

val defaultLabelColor = BookmarkStyle.BLUE_HIGHLIGHT.backgroundColor

enum class BookmarkSortOrder {
    BIBLE_ORDER, CREATED_AT, CREATED_AT_DESC, LAST_UPDATED, ORDER_NUMBER;
}

interface VerseRangeUser {
    val verseRange: VerseRange
}

enum class LabelType {HIGHLIGHT, EXAMPLE}
enum class BookmarkType {EXAMPLE}

class BookmarkEntities {
    @Serializable
    class TextRange(val start: Int, val end: Int) {
        val clientList get() = listOf(start, end)
    }

    @Entity(
        indices = [
            Index("kjvOrdinalStart"), Index("kjvOrdinalEnd"), Index("primaryLabelId"),
        ],
        foreignKeys = [
            ForeignKey(entity = Label::class, parentColumns = ["id"], childColumns = ["primaryLabelId"], onDelete = ForeignKey.SET_NULL),
        ],
    )
    data class Bookmark(
        // Verse range in KJV ordinals. For generic lookups, we must store verse ranges in a "standard"
        // versification. We store also verserange in original versification, as it conveys the more exact
        // versification-specific information.

        var kjvOrdinalStart: Int,
        var kjvOrdinalEnd: Int,

        var ordinalStart: Int,
        var ordinalEnd: Int,

        var v11n: Versification,

        var playbackSettings: PlaybackSettings?,

        @PrimaryKey var id: String = UUID.randomUUID().toString(),

        var createdAt: Date = Date(System.currentTimeMillis()),

        var book: AbstractPassageBook? = null,

        var startOffset: Int?,
        var endOffset: Int?,

        @ColumnInfo(defaultValue = "NULL") var primaryLabelId: String? = null,

        @ColumnInfo(defaultValue = "NULL") var notes: String? = null,
        @ColumnInfo(defaultValue = "0") var lastUpdatedOn: Date = Date(System.currentTimeMillis()),
        @ColumnInfo(defaultValue = "0") var wholeVerse: Boolean = false,
        @ColumnInfo(defaultValue = "NULL") var type: BookmarkType? = null,
        @Ignore var new: Boolean = false,
        ): VerseRangeUser {

        constructor(
            kjvOrdinalStart: Int,
            kjvOrdinalEnd: Int,
            ordinalStart: Int,
            ordinalEnd: Int,
            v11n: Versification,
            playbackSettings: PlaybackSettings?,
            id: String,
            createdAt: Date,
            book: AbstractPassageBook?,
            startOffset: Int?,
            endOffset: Int?,
            primaryLabelId: String?,
            notes: String?,
            lastUpdatedOn: Date,
            wholeVerse: Boolean,
            type: BookmarkType?,
        ): this(
            kjvOrdinalStart = kjvOrdinalStart,
            kjvOrdinalEnd = kjvOrdinalEnd,
            ordinalStart = ordinalStart,
            ordinalEnd = ordinalEnd,
            v11n = v11n,
            playbackSettings = playbackSettings,
            id = id,
            createdAt = createdAt,
            book = book,
            startOffset = startOffset,
            endOffset = endOffset,
            primaryLabelId = primaryLabelId,
            notes = notes,
            lastUpdatedOn = lastUpdatedOn,
            wholeVerse = wholeVerse,
            type = type,
            new = false,
        )

        constructor(verseRange: VerseRange, textRange: TextRange?, wholeVerse: Boolean, book: AbstractPassageBook?): this(
            kjvOrdinalStart = verseRange.toV11n(KJVA).start.ordinal,
            kjvOrdinalEnd = verseRange.toV11n(KJVA).end.ordinal,
            ordinalStart = verseRange.start.ordinal,
            ordinalEnd = verseRange.end.ordinal,
            v11n = verseRange.versification,
            playbackSettings = null,
            book = book,
            startOffset = textRange?.start,
            endOffset = textRange?.end,
            wholeVerse = wholeVerse,
            new = true,
        )

        constructor(id: String, createdAt: Date, verseRange: VerseRange, textRange: TextRange?, wholeVerse: Boolean, book: AbstractPassageBook?, playbackSettings: PlaybackSettings?): this(
            kjvOrdinalStart = verseRange.toV11n(KJVA).start.ordinal,
            kjvOrdinalEnd = verseRange.toV11n(KJVA).end.ordinal,
            ordinalStart = verseRange.start.ordinal,
            ordinalEnd = verseRange.end.ordinal,
            v11n = verseRange.versification,
            playbackSettings = playbackSettings,
            id = id,
            createdAt = createdAt,
            book = book,
            startOffset = textRange?.start,
            endOffset = textRange?.end,
            wholeVerse = wholeVerse,
            new = true,
        )

        var textRange: TextRange?
            get() = if(startOffset != null) {
                TextRange(startOffset!!, endOffset!!)
            } else null
            set(value) {
                if(value == null) {
                    startOffset = null
                    endOffset = null
                } else {
                    startOffset = value.start
                    endOffset = value.end
                }
            }

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
                kjvOrdinalStart = value.toV11n(KJVA).start.ordinal
                kjvOrdinalEnd = value.toV11n(KJVA).end.ordinal
            }

        val speakBook: Book?
            get() = if (playbackSettings != null && playbackSettings!!.bookId != null) {
                Books.installed().getBook(playbackSettings!!.bookId)
            } else {
                null
            }
        @Ignore var labelIds: List<String>? = null
        @Ignore var bookmarkToLabels: List<BookmarkToLabel>? = null
        @Ignore var text: String? = null

        val highlightedText: String get() {
            return "$startText<b>$text</b>$endText"
        }

        @Ignore var startText: String? = null
        @Ignore var endText: String? = null

        @Ignore var fullText: String? = null
        @Ignore var osisFragment: OsisFragment? = null
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
    @Serializable
    data class BookmarkToLabel(
        val bookmarkId: String,
        val labelId: String,

        // Journal display variables
        @ColumnInfo(defaultValue = "-1") var orderNumber: Int = -1,
        @ColumnInfo(defaultValue = "0") var indentLevel: Int = 0,
        @ColumnInfo(defaultValue = "0") var expandContent: Boolean = true,
    )

    @Entity(
        foreignKeys = [
            ForeignKey(entity = Label::class, parentColumns = ["id"], childColumns = ["labelId"], onDelete = ForeignKey.CASCADE)
        ],
        indices = [
            Index("labelId")
        ]
    )
    @Serializable
    data class StudyPadTextEntry(
        @PrimaryKey var id: String = UUID.randomUUID().toString(),
        val labelId: String,
        val text: String = "",
        var orderNumber: Int,
        var indentLevel: Int = 0,
    ) {
        @Ignore val type: String = "journal"
    }

    @Entity
    @Serializable
    data class Label(
        @PrimaryKey var id: String = UUID.randomUUID().toString(),
        var name: String = "",
        @ColumnInfo(defaultValue = "0") var color: Int = defaultLabelColor,
        @ColumnInfo(defaultValue = "0") var markerStyle: Boolean = false,
        @ColumnInfo(defaultValue = "0") var markerStyleWholeVerse: Boolean = false,
        @ColumnInfo(defaultValue = "0") var underlineStyle: Boolean = false,
        @ColumnInfo(defaultValue = "0") var underlineStyleWholeVerse: Boolean = true,
        @ColumnInfo(defaultValue = "NULL") var type: LabelType? = null,
        @Ignore var new: Boolean = false
    ) {
        override fun toString() = name
        val isSpeakLabel get() = name == SPEAK_LABEL_NAME
        val isUnlabeledLabel get() = name == UNLABELED_NAME
        val isSpecialLabel get() = isSpeakLabel || isUnlabeledLabel
    }
}
