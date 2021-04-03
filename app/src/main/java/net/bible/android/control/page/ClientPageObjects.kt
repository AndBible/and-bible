/*
 * Copyright (c) 2021 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.control.page

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.common.toV11n
import net.bible.android.control.bookmark.LABEL_UNLABELED_ID
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.json
import net.bible.android.misc.OsisFragment
import net.bible.android.misc.uniqueId
import net.bible.android.misc.wrapString
import net.bible.service.common.displayName
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData.KEY_SOURCE_TYPE
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.Versification
import java.util.*
import java.util.UUID.randomUUID

/*
 * Serializable classes and utils that are used when transferring stuff to JS side
 */


fun mapToJson(map: Map<String, String>?): String =
    map?.map {(key, value) -> "'$key': $value"}?.joinToString(",", "{", "}")?:"null"

fun listToJson(list: List<String>) = list.joinToString(",", "[", "]")
val VerseRange.onlyNumber: String get() = if(cardinality > 1) "${start.verse}-${end.verse}" else "${start.verse}"
val VerseRange.abbreviated: String get() {
    synchronized(BookName::class) {
        val wasFullBookName = BookName.isFullBookName()
        BookName.setFullBookName(false)
        val shorter = name
        BookName.setFullBookName(wasFullBookName)
        return shorter
    }
}

interface DocumentWithBookmarks

interface Document {
    val asJson: String get() {
        return asHashMap.map {(key, value) -> "'$key': $value"}.joinToString(",", "{", "}")
    }
    val asHashMap: Map<String, Any>
}

class ErrorDocument(private val errorMessage: String?): Document {
    override val asHashMap: Map<String, String> get() =
        mapOf(
            "id" to wrapString(randomUUID().toString()),
            "type" to wrapString("error"),
            "errorMessage" to wrapString(errorMessage?:"")
        )
}

open class OsisDocument(
    val osisFragment: OsisFragment,
    val book: Book,
    val key: Key,
): Document {
    override val asHashMap: Map<String, String> get () = mapOf(
        "id" to wrapString("${book.initials}-${key.uniqueId}"),
        "type" to wrapString("osis"),
        "osisFragment" to mapToJson(osisFragment.toHashMap),
        "bookInitials" to wrapString(book.initials),
        "bookCategory" to wrapString(book.bookCategory.name),
        "bookAbbreviation" to wrapString(book.abbreviation),
        "bookName" to wrapString(book.name),
        "key" to wrapString(key.uniqueId),
        "v11n" to wrapString(if(book is SwordBook) book.versification.name else null),
    )
}

class BibleDocument(
    val bookmarks: List<BookmarkEntities.Bookmark>,
    val verseRange: VerseRange,
    osisFragment: OsisFragment,
    val swordBook: SwordBook,
    val originalKey: Key?,
): OsisDocument(osisFragment, swordBook, verseRange), DocumentWithBookmarks {
    override val asHashMap: Map<String, String> get () {
        val bookmarks = bookmarks.map { ClientBookmark(it, swordBook.versification).asJson }
        val vrInV11n = verseRange.toV11n(swordBook.versification)
        // Clicked link etc. had more specific reference
        val originalOrdinalRange = if(originalKey is RangedPassage) {
            val originalVerseRange = VerseRangeFactory.fromString(originalKey.versification, originalKey.osisRef).toV11n(swordBook.versification)
            json.encodeToString(serializer(), listOf(originalVerseRange.start.ordinal, originalVerseRange.end.ordinal))
        } else "null"
        return super.asHashMap.toMutableMap().apply {
            put("bookmarks", listToJson(bookmarks))
            put("type", wrapString("bible"))
            put("ordinalRange", json.encodeToString(serializer(), listOf(vrInV11n.start.ordinal, vrInV11n.end.ordinal)))
            put("addChapter", json.encodeToString(serializer(), swordBook.getProperty(KEY_SOURCE_TYPE).toString().toLowerCase(Locale.getDefault()) == "gbf"))
            put("chapterNumber", json.encodeToString(serializer(), verseRange.start.chapter))
            put("originalOrdinalRange", originalOrdinalRange)
            put("v11n", wrapString(swordBook.versification.name))
        }
    }
}

class MultiFragmentDocument(private val osisFragments: List<OsisFragment>, private val compare: Boolean=false): Document {
    override val asHashMap: Map<String, Any>
        get() = mapOf(
            "id" to wrapString(randomUUID().toString()),
            "type" to wrapString("multi"),
            "osisFragments" to listToJson(osisFragments.map { mapToJson(it.toHashMap) }),
            "compare" to json.encodeToString(serializer(), compare),
        )
}


class MyNotesDocument(val bookmarks: List<BookmarkEntities.Bookmark>,
                      val verseRange: VerseRange): Document, DocumentWithBookmarks
{
    override val asHashMap: Map<String, Any>
        get() {
            val bookmarks = bookmarks.map { ClientBookmark(it, KJVA).asJson }
            return mapOf(
                "id" to wrapString(verseRange.uniqueId),
                "type" to wrapString("notes"),
                "bookmarks" to listToJson(bookmarks),
                "verseRange" to wrapString(verseRange.name),
            )
        }
}

class StudyPadDocument(
    val label: BookmarkEntities.Label,
    val bookmarks: List<BookmarkEntities.Bookmark>,
    val bookmarkToLabels: List<BookmarkEntities.BookmarkToLabel>,
    val studyPadTextEntries: List<BookmarkEntities.StudyPadTextEntry>,
): Document, DocumentWithBookmarks {
    override val asHashMap: Map<String, Any>
        get() {
            val bookmarks = bookmarks.map { ClientBookmark(it).asJson }
            val clientLabel = ClientBookmarkLabel(label)
            return mapOf(
                "id" to wrapString("journal_${label.id}"),
                "type" to wrapString("journal"),
                "bookmarks" to listToJson(bookmarks),
                "bookmarkToLabels" to json.encodeToString(serializer(), bookmarkToLabels),
                "journalTextEntries" to json.encodeToString(serializer(), studyPadTextEntries),
                "label" to json.encodeToString(serializer(), clientLabel),
            )
        }
}

class ClientBookmark(val bookmark: BookmarkEntities.Bookmark, val v11n: Versification? = null): Document {
    override val asHashMap: Map<String, String> get() = mapOf(
        "id" to bookmark.id.toString(),
        "ordinalRange" to json.encodeToString(serializer(), listOf(bookmark.verseRange.toV11n(v11n).start.ordinal, bookmark.verseRange.toV11n(v11n).end.ordinal)),
        "originalOrdinalRange" to json.encodeToString(serializer(), listOf(bookmark.verseRange.start.ordinal, bookmark.verseRange.end.ordinal)),
        "offsetRange" to json.encodeToString(serializer(), bookmark.textRange?.clientList),
        "labels" to json.encodeToString(serializer(), bookmark.labelIds!!.toMutableList().also {
            if(it.isEmpty()) it.add(LABEL_UNLABELED_ID)
        }),
        "bookInitials" to wrapString(bookmark.book?.initials),
        "bookName" to wrapString(bookmark.book?.name),
        "bookAbbreviation" to wrapString(bookmark.book?.abbreviation),
        "createdAt" to bookmark.createdAt.time.toString(),
        "lastUpdatedOn" to bookmark.lastUpdatedOn.time.toString(),
        "notes" to if(bookmark.notes?.trim()?.isEmpty() == true) "null" else wrapString(bookmark.notes),
        "verseRange" to wrapString(bookmark.verseRange.name),
        "verseRangeOnlyNumber" to wrapString(bookmark.verseRange.onlyNumber),
        "verseRangeAbbreviated" to wrapString(bookmark.verseRange.abbreviated),
        "text" to wrapString(bookmark.text),
        "fullText" to wrapString(bookmark.fullText),
        "bibleUrl" to wrapString(getUrl(bookmark)),
        "bookmarkToLabels" to json.encodeToString(serializer(), bookmark.bookmarkToLabels),
        "osisFragment" to mapToJson(bookmark.osisFragment?.toHashMap),
        "type" to wrapString("bookmark")
    )

    companion object{
        fun getUrl(bookmark: BookmarkEntities.Bookmark): String {
            val ref = bookmark.verseRange.osisRef
            val v11n = bookmark.book?.versification?.name
            return "osis://?osis=$ref" + if(v11n !== null) "&v11n=$v11n" else ""
        }
    }
}

@Serializable
data class ClientBookmarkStyle(val color: Int, val icon: String?, val noHighlight: Boolean)

@Serializable
data class ClientBookmarkLabel(val id: Long, val name: String, val style: ClientBookmarkStyle, val editPosition: Int) {
    constructor(label: BookmarkEntities.Label): this(
        label.id, label.displayName,
        label.color.let {v ->
            ClientBookmarkStyle(v, if(label.isSpeakLabel) "headphones" else null, label.isSpeakLabel)
        },
        label.editPosition,
    )
}

