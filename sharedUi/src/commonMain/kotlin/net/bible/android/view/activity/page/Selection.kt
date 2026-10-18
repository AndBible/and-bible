/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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


@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.android.view.activity.page

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.llm.agent.NoteEditorEntityType
import net.bible.sharedlogic.sync.bookNameMonitor
import net.bible.sharedlogic.sync.withMonitor
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BookName
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@kotlin.native.HiddenFromObjC
@Serializable
class Selection(
    override val bookInitials: String?,
    override val startOrdinal: Int,
    override val startOffset: Int?,
    override val endOrdinal: Int,
    override val endOffset: Int?,
    val bookmarks: List<IdType>,
    override val notes: String? = null,
    override val text: String = "",
    override val osisRef: String? = null,
    override val noteEditorEntityType: NoteEditorEntityType? = null,
    /** Note editor entity ID (bookmark UUID, studypad entry UUID, or MyDocument page ID) */
    override val noteEditorEntityId: String? = null,
    /** Current text content in the note editor */
    override val noteEditorContent: String? = null,
    /** Content type of the editor: "MARKDOWN" or "HTML" */
    override val noteEditorContentType: String? = null,
) : KoinComponent, net.bible.service.sword.ShareSelection, net.bible.service.llm.agent.LlmSelection
{
    constructor(bookmark: BookmarkEntities.BibleBookmarkWithNotes):
        this(
            bookInitials = bookmark.book?.initials,
            startOrdinal = bookmark.ordinalStart,
            startOffset = bookmark.startOffset,
            endOrdinal = bookmark.ordinalEnd,
            endOffset = bookmark.endOffset,
            bookmarks = emptyList(),
            notes = bookmark.notes
        )

    constructor(bookInitials: String, startOrdinal: Int, endOrdinal: Int?, text: String = ""):
        this(
            bookInitials = bookInitials,
            startOrdinal = startOrdinal,
            startOffset = 0,
            endOrdinal = endOrdinal?: startOrdinal,
            endOffset = null,
            bookmarks = emptyList(),
            text = text,
        )
    constructor(bookInitials: String, osisRef: String, startOrdinal: Int, endOrdinal: Int?, text: String = ""):
        this(
            bookInitials = bookInitials,
            osisRef = osisRef,
            startOrdinal = startOrdinal,
            startOffset = 0,
            endOrdinal = endOrdinal?: startOrdinal,
            endOffset = null,
            bookmarks = emptyList(),
            text = text,
        )

    @Transient val windowControl: WindowControl by inject()



    val hasRange get() = startOffset != null && endOffset != null

    val book: Book? get() = Books.installed().getBook(bookInitials)
    val swordBook: SwordBook? get() =
        if(book is SwordBook)
            book as SwordBook? ?: windowControl.defaultBibleDoc(false)
        else null
    override val verseRange: VerseRange? get() {
        swordBook?: return null
        val v11n = swordBook?.versification ?: KJVA
        return VerseRange(v11n, Verse(v11n, startOrdinal), Verse(v11n, endOrdinal))
    }
}

/**
 * How [BibleJavascriptInterface.refChooserDialog] renders the chosen verse for the JS side: SHORT
 * book-name form, whatever `BookName.isFullBookName()` happens to be globally, and that global
 * restored afterwards. Verbatim from the version that read the chooser Activity's result — the
 * format is part of the JS contract, not incidental — extracted only so it can be tested without a
 * WebView (`RefChooserSheetTest`).
 *
 * `null` (a dismissed chooser) renders as the empty string, which is what the JS promise resolves
 * to when the user picks nothing.
 *
 * Public only because `:app` tests call it; not API for other callers.
 */
@kotlin.native.HiddenFromObjC
fun refChooserVerseName(verseStr: String?): String {
    val verse = if(verseStr == null) null else VerseFactory.fromString(KJVA, verseStr)

    return withMonitor(bookNameMonitor) {
        val oldValue = BookName.isFullBookName()
        BookName.setFullBookName(false)
        try {
            verse?.getName() ?: ""
        } finally {
            BookName.setFullBookName(oldValue)
        }
    }
}

/** JS passes a negative ordinal for "no end ordinal"; maps it to `null`. Public for `:app` tests only. */
@kotlin.native.HiddenFromObjC
fun positiveOrNull(value: Int): Int? {
    if(value < 0) return null
    return value
}
