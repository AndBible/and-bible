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
package net.bible.android.control.page

import net.bible.android.common.toV11n
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.bookmarks.KJVA
import net.bible.service.common.CommonUtils
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.versification.Versification

class CurrentMyNotePage internal constructor(
	currentVerse: CurrentBibleVerse,
	bibleTraverser: BibleTraverser,
	swordDocumentFacade: SwordDocumentFacade,
    pageManager: CurrentPageManager
) : CurrentCommentaryPage(currentVerse, bibleTraverser, swordDocumentFacade, pageManager), CurrentPage
{
    override val currentPageContent: Document get() {
        val verseRange = CommonUtils.getWholeChapter(currentBibleVerse.verse, false)
        val bookmarksForChapter = pageManager.bookmarkControl.bookmarksForVerseRange(verseRange, withLabels = true)
        return MyNotesDocument(bookmarksForChapter, verseRange.toV11n(KJVA))
    }

    override fun next() {
        setKey(pageManager.currentBible.getKeyPlus(1))
    }

    override fun previous() {
        setKey(pageManager.currentBible.getKeyPlus(-1))
    }

    override val currentDocument: Book = FakeBookFactory.myNotesDocument

    /** can we enable the main menu search button
     */

	// TODO doesn't work currently - enable later
	override val isSearchable: Boolean = false

    /** can we enable the main menu Speak button
     */
	//TODO doesn't work currently - enable later
	override val isSpeakable: Boolean = false

    override val documentCategory = DocumentCategory.MYNOTE

    private val currentVersification: Versification
        get() = currentBibleVerse.versificationOfLastSelectedVerse


    /** Do not run VersePage implementation of localSetCurrentDocument
     * because that is not for notes
     */
    override fun localSetCurrentDocument(doc: Book?, isMyNote: Boolean) {
        localSetCurrentDocument(doc, true)
    }


    override val isSingleKey = false

    companion object {
        private const val TAG = "CurrentMyNotePage"
    }

}
