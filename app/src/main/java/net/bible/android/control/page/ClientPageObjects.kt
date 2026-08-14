/*
 * Copyright (c) 2021-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.control.page

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.bible.android.common.toV11n
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.progress.ProgressControl
import net.bible.android.control.versification.toVerseRange
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.AI_DOC_LABEL_ID
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.json
import net.bible.android.database.mydocument.AiDocMarkerInfo
import net.bible.android.misc.OsisFragment
import net.bible.android.misc.sanitizeId
import net.bible.android.misc.uniqueId
import net.bible.android.misc.wrapString
import net.bible.service.common.CommonUtils
import net.bible.service.common.displayName
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.epubBackend
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.isMyDocument
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData.KEY_SOURCE_TYPE
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.Versification
import java.util.*
import java.util.UUID.randomUUID
import javax.inject.Inject
import kotlin.math.abs

/*
 * Serializable classes and utils that are used when transferring stuff to JS side
 */


fun mapToJson(map: Map<String, String>?): String =
    map?.map {(key, value) -> "'$key': $value"}?.joinToString(",", "{", "}")?:"null"

fun listToJson(list: List<String>) = list.joinToString(",", "[", "]")
val VerseRange.onlyNumber: String get() = if(cardinality > 1) "${start.verse}-${end.verse}" else "${start.verse}"
val VerseRange.abbreviated: String get() = synchronized(BookName::class.java) {
    Log.i("VerseRange", "BookName::class ${System.identityHashCode(BookName::class.java)}")
    val wasFullBookName = BookName.isFullBookName()
    BookName.setFullBookName(false)
    val shorter = name
    BookName.setFullBookName(wasFullBookName)
    return shorter
}

interface Document {
    val asJson: String get() {
        return asHashMap.map {(key, value) -> "'$key': $value"}.joinToString(",", "{", "}")
    }
    val asHashMap: Map<String, Any>
}

enum class ErrorSeverity {
    NORMAL, WARNING, ERROR
}

class ErrorDocument(private val errorMessage: String?, private val severity: ErrorSeverity): Document {
    init {
        if(severity == ErrorSeverity.ERROR) {
            Log.e("ErrorDocument", "ErrorDocument created: ${Log.getStackTraceString(Exception())}")
        }
    }
    override val asHashMap: Map<String, String> get() {
        if(severity == ErrorSeverity.ERROR) {
            Log.e("ErrorDocument", "ErrorDocument printed: ${Log.getStackTraceString(Exception())}")
        }
        return mapOf(
            "id" to wrapString(randomUUID().toString()),
            "type" to wrapString("error"),
            "errorMessage" to wrapString(errorMessage?:""),
            "severity" to wrapString(severity.name)
        )
    }
}

data class CommentaryRangeInfo(val startOsisRef: String, val endOsisRef: String, val name: String) {
    val asJson: String get() = mapToJson(mapOf(
        "startOsisRef" to wrapString(startOsisRef),
        "endOsisRef" to wrapString(endOsisRef),
        "name" to wrapString(name),
    ))
}

open class OsisDocument(
    val osisFragment: OsisFragment,
    val book: Book,
    val key: Key,
    val genericBookmarks: List<BookmarkEntities.GenericBookmarkWithNotes> = emptyList(),
    val highlightRange: IntRange? = null,
    val myDocumentPageId: String? = null,
    val sourcePromptId: String? = null,
    val sourcePromptName: String? = null,
    val sourceModelName: String? = null,
    open val aiDocMarkers: List<AiDocMarkerInfo> = emptyList(),
    val commentaryRange: CommentaryRangeInfo? = null,
): Document {
    override val asHashMap: Map<String, String> get () {
        val highlightedOrdinalRange =
            if(highlightRange == null) "null"
            else json.encodeToString(serializer(), listOf(highlightRange.first, highlightRange.last))
        val ordRange =
            if(book.bookCategory != BookCategory.BIBLE)
                SwordContentFacade.ordinalRangeFor(book, key)
            else null
        val ordinalRange =
            if (ordRange == null) "null"
            else json.encodeToString(serializer(), listOf(ordRange.first, ordRange.last))

        val readingProgress: String = when {
            book.isEpub -> book.epubBackend
                ?.let { ReadingProgressInfo.forEpub(it.fragmentOffset(key), it.bookOrdinalSpan, it.totalCharacters).asJson }
                ?: "null"
            book.bookCategory == BookCategory.COMMENTARY && book is SwordBook -> {
                val vr = when (val k = key) {
                    is VerseRange -> k
                    is Verse -> VerseRange(k.versification, k, k)
                    else -> null
                }
                vr?.let {
                    ReadingProgressInfo.forVerseKey(book.versification, it.toV11n(book.versification)).asJson
                } ?: "null"
            }
            else -> "null"
        }

        return mapOf(
            "id" to wrapString(sanitizeId("${book.initials}-${key.uniqueId}")),
            "type" to wrapString("osis"),
            "osisFragment" to mapToJson(osisFragment.toHashMap),
            "ordinalRange" to ordinalRange,
            "readingProgress" to readingProgress,
            "bookInitials" to wrapString(book.initials),
            "bookCategory" to wrapString(book.bookCategory.name),
            "bookAbbreviation" to wrapString(book.abbreviation),
            "bookName" to wrapString(book.name),
            "key" to wrapString(key.uniqueId),
            "annotateRef" to wrapString(osisFragment.annotateRef?.osisRef?: key.osisRef),
            "osisRef" to wrapString((osisFragment.annotateRef ?: key).osisRef),
            "v11n" to wrapString(if(book is SwordBook) book.versification.name else null),
            "genericBookmarks" to listToJson(genericBookmarks.map { ClientGenericBookmark(it).asJson }),
            "highlightedOrdinalRange" to highlightedOrdinalRange,
            "isNativeHtml" to json.encodeToString(serializer(), book.isEpub || book.isMyDocument),
            "isMyDocument" to json.encodeToString(serializer(), book.isMyDocument),
            "isAiDocument" to json.encodeToString(serializer(), book.initials == MyDocumentBookManager.AI_DOCUMENTS_INITIALS),
            "myDocumentPageId" to wrapString(myDocumentPageId),
            "sourcePromptId" to wrapString(sourcePromptId),
            "sourcePromptName" to wrapString(sourcePromptName),
            "sourceModelName" to wrapString(sourceModelName),
            "aiDocMarkers" to listToJson(aiDocMarkers.map { ClientAiDocMarker(it, (book as? SwordBook)?.versification).asJson }),
            "commentaryRange" to (commentaryRange?.asJson ?: "null"),
        )
    }
}

class BibleDocument(
    val bookmarks: List<BookmarkEntities.BibleBookmarkWithNotes>,
    val verseRange: VerseRange,
    osisFragment: OsisFragment,
    val swordBook: SwordBook,
    val originalKey: Key?,
    override val aiDocMarkers: List<AiDocMarkerInfo> = emptyList(),
): OsisDocument(osisFragment, swordBook, verseRange) {
    override val asHashMap: Map<String, String> get () {
        val bookmarks = bookmarks.map { ClientBibleBookmark(it, swordBook.versification).asJson }
        val vrInV11n = verseRange.toV11n(swordBook.versification)
        // Clicked link etc. had more specific reference
        val originalOrdinalRange = if(originalKey is RangedPassage) {
            val originalVerseRange = originalKey.toVerseRange.toV11n(swordBook.versification)
            json.encodeToString(serializer(), listOf(originalVerseRange.start.ordinal, originalVerseRange.end.ordinal))
        } else "null"
        val v11n = swordBook.versification
        val kjvRange = verseRange.toV11n(KJVA)
        val memorizedOrdinals = ProgressControl.getMemorizedOrdinalsInRange(kjvRange.start.ordinal, kjvRange.end.ordinal)
            .map { Verse(KJVA, it).toV11n(v11n).ordinal }
        val targetOrdinals = ProgressControl.getTargetOrdinalsInRange(kjvRange.start.ordinal, kjvRange.end.ordinal)
            .map { Verse(KJVA, it).toV11n(v11n).ordinal }
        return super.asHashMap.toMutableMap().apply {
            put("bookmarks", listToJson(bookmarks))
            put("type", wrapString("bible"))
            put("bibleBookName", wrapString(swordBook.versification.getPreferredNameInLocale(verseRange.start.book, Locale.getDefault())))
            put("ordinalRange", json.encodeToString(serializer(), listOf(vrInV11n.start.ordinal, vrInV11n.end.ordinal)))
            put("addChapter", json.encodeToString(serializer(), swordBook.getProperty(KEY_SOURCE_TYPE).toString()
                .lowercase(Locale.getDefault()) == "gbf" || !osisFragment.hasChapter))
            put("chapterNumber", json.encodeToString(serializer(), verseRange.start.chapter))
            put("originalOrdinalRange", originalOrdinalRange)
            put("v11n", wrapString(swordBook.versification.name))
            put("memorizedOrdinals", json.encodeToString(serializer(), memorizedOrdinals))
            put("targetOrdinals", json.encodeToString(serializer(), targetOrdinals))
            put("chapterReadCount", json.encodeToString(serializer(),
                ProgressControl.getChapterReadCount(swordBook.versification, verseRange.start.book, verseRange.start.chapter)))
            put("readingProgress", ReadingProgressInfo.forVerseKey(swordBook.versification, vrInV11n).asJson)
        }
    }
}

class MultiFragmentDocument(
    private val osisFragments: List<OsisFragment>,
    private val compare: Boolean = false,
    private val state: String? = null
): Document {
    private val contentType: String? get() {
        if (compare) return null
        val hasStrongs = osisFragments.any { it.isStrongsDictionary }
        val hasMorph = osisFragments.any { it.isMorphDictionary }
        return if (hasStrongs || hasMorph) "strongs" else null
    }

    override val asHashMap: Map<String, Any>
        get() = mapOf(
            "id" to wrapString(randomUUID().toString()),
            "type" to wrapString("multi"),
            "osisFragments" to listToJson(osisFragments.map { mapToJson(it.toHashMap) }),
            "compare" to json.encodeToString(serializer(), compare),
            "contentType" to wrapString(contentType),
            "state" to (state ?: "undefined"),
        )
}

class MemorizeDocument(
    private val title: String,
    private val texts: List<Pair<String, String>>,
    private val state: String?,
    val bookInitials: String? = null,
    private val v11nName: String? = null,
    private val osisRef: String? = null,
    private val startOrdinal: Int = 0,
    private val endOrdinal: Int = 0,
    private val memorizedOrdinals: List<Int> = emptyList(),
    private val targetOrdinals: List<Int> = emptyList(),
    private val readingProgressSettingsJson: String = "{}",
): Document {
    override val asHashMap: Map<String, Any>
        get() = mapOf(
            "id" to wrapString(randomUUID().toString()),
            "type" to wrapString("memorize"),
            "title" to wrapString(title),
            "texts" to listToJson(texts.map { "{ 'key': " + wrapString(it.first) + ", 'text':" + wrapString(it.second) + "}" }),
            "state" to (state ?: "undefined"),
            "bookInitials" to (if (bookInitials != null) wrapString(bookInitials) else "undefined"),
            "v11n" to (if (v11nName != null) wrapString(v11nName) else "undefined"),
            "osisRef" to (if (osisRef != null) wrapString(osisRef) else "undefined"),
            "startOrdinal" to startOrdinal,
            "endOrdinal" to endOrdinal,
            "memorizedOrdinals" to json.encodeToString(serializer(), memorizedOrdinals),
            "targetOrdinals" to json.encodeToString(serializer(), targetOrdinals),
            "readingProgressSettings" to readingProgressSettingsJson,
        )
}

class MyNotesDocument(val bookmarks: List<BookmarkEntities.BibleBookmarkWithNotes>,
                      val verseRange: VerseRange): Document
{
    override val asHashMap: Map<String, Any>
        get() {
            val bookmarks = bookmarks.map { ClientBibleBookmark(it, KJVA).asJson }
            return mapOf(
                "id" to wrapString(verseRange.uniqueId),
                "type" to wrapString("notes"),
                "bookmarks" to listToJson(bookmarks),
                "ordinalRange" to json.encodeToString(serializer(), listOf(verseRange.start.ordinal, verseRange.end.ordinal)),
                "verseRange" to wrapString(verseRange.name),
            )
        }
}

class StudyPadDocument(
    val label: BookmarkEntities.Label,
    val entryId: IdType?,
    val bookmarks: List<BookmarkEntities.BibleBookmarkWithNotes>,
    val genericBookmarks: List<BookmarkEntities.GenericBookmarkWithNotes>,
    private val bookmarkToLabels: List<BookmarkEntities.BibleBookmarkToLabel>,
    private val genericBookmarkToLabels: List<BookmarkEntities.GenericBookmarkToLabel>,
    private val studyPadTextEntries: List<BookmarkEntities.StudyPadTextEntryWithText>,
): Document {
    override val asHashMap: Map<String, Any>
        get() {
            val bookmarks = bookmarks.map { ClientBibleBookmark(it).asJson }
            val genericBookmarks = genericBookmarks.map { ClientGenericBookmark(it).asJson }
            val clientLabel = ClientBookmarkLabel(label)
            return mapOf(
                "id" to wrapString("journal_${label.id}"),
                "type" to wrapString("journal"),
                "bookmarks" to listToJson(bookmarks),
                "genericBookmarks" to listToJson(genericBookmarks),
                "bookmarkToLabels" to json.encodeToString(serializer(), bookmarkToLabels),
                "genericBookmarkToLabels" to json.encodeToString(serializer(), genericBookmarkToLabels),
                "journalTextEntries" to json.encodeToString(serializer(), studyPadTextEntries),
                "label" to json.encodeToString(serializer(), clientLabel),
            )
        }
}

class ClientBibleBookmark(val bookmark: BookmarkEntities.BibleBookmarkWithNotes, val v11n: Versification? = null): Document {
    @Inject lateinit var bookmarkControl: BookmarkControl

    init {
        CommonUtils.buildActivityComponent().inject(this)
    }

    override val asHashMap: Map<String, String> get() {
        val notes = if(bookmark.notes?.trim()?.isEmpty() == true) "null" else wrapString(bookmark.notes)
        return mapOf(
            "id" to wrapString(bookmark.id.toString()),
            "hashCode" to (abs(bookmark.id.hashCode())).toString(),
            "ordinalRange" to json.encodeToString(serializer(), listOf(bookmark.verseRange.toV11n(v11n).start.ordinal, bookmark.verseRange.toV11n(v11n).end.ordinal)),
            "originalOrdinalRange" to json.encodeToString(serializer(), listOf(bookmark.verseRange.start.ordinal, bookmark.verseRange.end.ordinal)),
            "offsetRange" to json.encodeToString(serializer(), if(bookmark.wholeVerse || bookmark.book == null) null else bookmark.textRange?.clientList),
            "labels" to json.encodeToString(serializer(), bookmark.labelIds!!.toMutableList().also {
                if(it.isEmpty()) it.add(bookmarkControl.labelUnlabelled.id)
            }),
            "bookInitials" to wrapString(bookmark.book?.initials),
            "bookName" to wrapString(bookmark.book?.name),
            "bookAbbreviation" to wrapString(bookmark.book?.abbreviation),
            "createdAt" to bookmark.createdAt.time.toString(),
            "lastUpdatedOn" to bookmark.lastUpdatedOn.time.toString(),
            "notes" to notes,
            "hasNote" to (notes != "null").toString(),
            "verseRange" to wrapString(bookmark.verseRange.name),
            "verseRangeOnlyNumber" to wrapString(bookmark.verseRange.onlyNumber),
            "verseRangeAbbreviated" to wrapString(bookmark.verseRange.abbreviated),
            "text" to wrapString(bookmark.text),
            "osisRef" to wrapString(bookmark.verseRange.osisRef),
            "v11n" to wrapString((bookmark.book?.versification?: KJVA).name),
            "fullText" to wrapString(bookmark.fullText),
            "bookmarkToLabels" to json.encodeToString(serializer(), bookmark.bookmarkToLabels),
            "osisFragment" to mapToJson(bookmark.osisFragment?.toHashMap),
            "type" to wrapString("bookmark"),
            "primaryLabelId" to wrapString(bookmark.primaryLabelId?.toString()),
            "wholeVerse" to (bookmark.wholeVerse || bookmark.book == null).toString(),
            "customIcon" to wrapString(bookmark.customIcon),
            "notesContentType" to wrapString(bookmark.notesContentType?.name),
            "editAction" to json.encodeToString(serializer(), bookmark.editAction ?: BookmarkEntities.EditAction()),
        )
    }
}

class ClientGenericBookmark(val bookmark: BookmarkEntities.GenericBookmarkWithNotes): Document {
    @Inject lateinit var bookmarkControl: BookmarkControl

    init {
        CommonUtils.buildActivityComponent().inject(this)
    }

    override val asHashMap: Map<String, String> get() {
        val notes = if(bookmark.notes?.trim()?.isEmpty() == true) "null" else wrapString(bookmark.notes)
        return mapOf(
            "id" to wrapString(bookmark.id.toString()),
            "key" to wrapString(bookmark.key),
            "keyName" to wrapString(bookmark.originalKey?.name?: bookmark.key),
            "hashCode" to (abs(bookmark.id.hashCode())).toString(),
            "ordinalRange" to json.encodeToString(serializer(), listOf(bookmark.ordinalStart, bookmark.ordinalEnd)),
            "offsetRange" to json.encodeToString(serializer(), if(bookmark.wholeVerse) null else bookmark.textRange?.clientList),
            "labels" to json.encodeToString(serializer(), bookmark.labelIds!!.toMutableList().also {
                if(it.isEmpty()) it.add(bookmarkControl.labelUnlabelled.id)
            }),
            "bookInitials" to wrapString(bookmark.bookInitials),
            "bookName" to wrapString(bookmark.book?.name?: bookmark.bookInitials),
            "bookAbbreviation" to wrapString(bookmark.book?.abbreviation ?: bookmark.bookInitials),
            "createdAt" to bookmark.createdAt.time.toString(),
            "lastUpdatedOn" to bookmark.lastUpdatedOn.time.toString(),
            "notes" to notes,
            "hasNote" to (notes != "null").toString(),
            "text" to wrapString(bookmark.text),
            "fullText" to wrapString(bookmark.fullText),
            "highlightedText" to wrapString(bookmark.highlightedText),
            "bookmarkToLabels" to json.encodeToString(serializer(), bookmark.bookmarkToLabels),
            "osisFragment" to mapToJson(bookmark.osisFragment?.toHashMap),
            "type" to wrapString("generic-bookmark"),
            "primaryLabelId" to wrapString(bookmark.primaryLabelId?.toString()),
            "wholeVerse" to bookmark.wholeVerse.toString(),
            "customIcon" to wrapString(bookmark.customIcon),
            "notesContentType" to wrapString(bookmark.notesContentType?.name),
            "editAction" to json.encodeToString(serializer(), bookmark.editAction ?: BookmarkEntities.EditAction()),
        )
    }
}

@Serializable
data class ClientBookmarkStyle(
    val color: Int,
    val isSpeak: Boolean,
    val isParagraphBreak: Boolean,
    val underline: Boolean,
    val underlineWholeVerse: Boolean,
    val markerStyle: Boolean,
    val markerStyleWholeVerse: Boolean,
    val hideStyle: Boolean,
    val hideStyleWholeVerse: Boolean,
    val customIcon: String?
)

@Serializable
data class ClientBookmarkLabel(
    val id: IdType,
    val name: String,
    val style: ClientBookmarkStyle,
    val isRealLabel: Boolean,
) {
    constructor(label: BookmarkEntities.Label): this(
        label.id,
        label.displayName.trim(),
        ClientBookmarkStyle(
            color = label.color,
            isSpeak = label.isSpeakLabel,
            underline = label.underlineStyle,
            underlineWholeVerse = label.underlineStyleWholeVerse,
            markerStyle = label.markerStyle,
            markerStyleWholeVerse = label.markerStyleWholeVerse,
            hideStyle = label.hideStyle,
            hideStyleWholeVerse = label.hideStyleWholeVerse,
            customIcon = label.customIcon,
            isParagraphBreak = label.isParagraphBreakLabel,
        ),
        !label.isSpecialLabel && !label.new,
    )
}

/**
 * Serializes an AI document page reference as a pseudo-bookmark for the Vue.js BibleView.
 * Contains all BaseBookmark fields so it flows through the same rendering pipeline as bookmarks.
 */
class ClientAiDocMarker(
    private val marker: AiDocMarkerInfo,
    private val v11n: Versification?
) : Document {
    override val asHashMap: Map<String, String> get() {
        val startOrdinal = if (v11n != null && marker.kjvOrdinalStart != null) {
            Verse(KJVA, marker.kjvOrdinalStart).toV11n(v11n).ordinal
        } else marker.kjvOrdinalStart
        val endOrdinal = if (v11n != null && marker.kjvOrdinalEnd != null) {
            Verse(KJVA, marker.kjvOrdinalEnd).toV11n(v11n).ordinal
        } else marker.kjvOrdinalEnd

        val verseRangeAbbreviated = if (startOrdinal != null && endOrdinal != null) {
            val targetV11n = v11n ?: KJVA
            VerseRange(targetV11n, Verse(targetV11n, startOrdinal), Verse(targetV11n, endOrdinal)).abbreviated
        } else ""

        val aiLabelId = AI_DOC_LABEL_ID
        return mapOf(
            "id" to wrapString(marker.pageId.toString()),
            "type" to wrapString("ai-doc-marker"),
            "hashCode" to kotlin.math.abs(marker.pageId.hashCode()).toString(),
            "ordinalRange" to if (startOrdinal != null && endOrdinal != null)
                json.encodeToString(serializer(), listOf(startOrdinal, endOrdinal)) else "null",
            "offsetRange" to "null",
            "labels" to json.encodeToString(serializer(), listOf(aiLabelId)),
            "primaryLabelId" to wrapString(aiLabelId),
            "bookmarkToLabels" to "[]",
            "bookInitials" to wrapString(marker.documentInitials),
            "bookName" to wrapString(marker.documentInitials),
            "bookAbbreviation" to wrapString(marker.documentInitials),
            "createdAt" to "0",
            "lastUpdatedOn" to "0",
            "text" to wrapString(marker.pageTitle),
            "fullText" to wrapString(marker.pageTitle),
            "notes" to "null",
            "notesContentType" to "null",
            "hasNote" to "false",
            "wholeVerse" to "true",
            "customIcon" to wrapString("robot"),
            "editAction" to json.encodeToString(serializer(), BookmarkEntities.EditAction()),
            "sourcePromptId" to wrapString(marker.sourcePromptId?.toString()),
            // AI doc marker specific fields
            "verseRangeAbbreviated" to wrapString(verseRangeAbbreviated),
            "title" to wrapString(marker.pageTitle),
            "documentInitials" to wrapString(marker.documentInitials),
            "pageKey" to wrapString(marker.pageKey),
            "sourceBookInitials" to wrapString(marker.sourceBookInitials),
            "sourceBookKey" to wrapString(marker.sourceBookKey),
        )
    }
}

