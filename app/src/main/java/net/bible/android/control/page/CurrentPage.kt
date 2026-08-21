/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import kotlinx.serialization.Serializable
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.sword.BookAndKey
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */

@Serializable
class OrdinalRange(val start: Int, val end: Int?): Comparable<Any> {
    constructor(v: IntRange): this(v.first, v.last)
    constructor(v: Int): this(v, null)

    val intRange: IntRange get() = start.. (end?:start)
    override fun compareTo(other: Any): Int = when(other) {
        is OrdinalRange -> start.compareTo(other.start)
        is Int -> start.compareTo(other)
        else -> throw UnsupportedOperationException()
    }
}


interface CurrentPage {
    val currentDocumentAbbreviation: String get () = currentDocument?.abbreviation?: ""
    val currentDocumentName: String get() = currentDocument?.name?:""

    val documentCategory: DocumentCategory
    val pageManager: CurrentPageManager
    val bookAndKey: BookAndKey? get() = singleKey?.let {BookAndKey(it, currentDocument, anchorOrdinal, htmlId)}

    fun startKeyChooser(context: ActivityBase)

    operator fun next()
    fun previous()
    /** get incremented key according to the type of page displayed - verse, chapter, ...
     */
    fun getKeyPlus(num: Int): Key

    /** add or subtract a number of pages from the current position and return Page
     */
    fun getPagePlus(num: Int): Key

    /** set key without updating screens  */
    fun doSetKey(key: Key?)

    /**
     * Whether [key] describes the location this page is already at.
     *
     * Not simply key equality: a commentary page is addressed by a single verse but displays a
     * whole entry, so every verse of that entry is the same location. Lets callers tell "the client
     * is reporting where we already are" from "the client has moved", without the side effects of
     * actually setting the key.
     */
    fun isAtSameLocationAs(key: Key): Boolean

    /**
     * Move the page to the location the BibleView reports having scrolled to, given as the
     * osisRef of the document that is now in view.
     *
     * @return true if this changed the page location, i.e. if listeners (title bar, window sync)
     * need to be notified. Scrolling within the location the page is already at returns false.
     */
    fun updateKeyFromScrolledOsisRef(osisRef: String): Boolean

    val isSingleKey: Boolean
    // bible and commentary share a key (verse)
    val isShareKeyBetweenDocs: Boolean

	var _key: Key?

    /** get current key */
    val key: Key?

    val displayKey: Key?

	/** set key and update screens  */
	fun setKey(key: Key, addHistoryItem: Boolean = true)

    /** get key for 1 verse instead of whole chapter if bible
     */
    val singleKey: Key?

    val currentDocument: Book?

	fun setCurrentDocument(doc: Book?)
    fun setCurrentDocumentAndKey(doc: Book, key: Key)

    fun checkCurrentDocumenInstalled(): Boolean
    /** get a page to display  */
    val currentPageContent: Document

    fun getPageContent(key: Key): Document

    var isInhibitChangeNotifications: Boolean
    val isSearchable: Boolean
    val isSpeakable: Boolean
    val isSyncable: Boolean

    var anchorOrdinal: OrdinalRange?
    var htmlId: String?
}
