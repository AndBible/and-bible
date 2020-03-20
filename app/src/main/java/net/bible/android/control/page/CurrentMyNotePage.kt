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
package net.bible.android.control.page

import android.util.Log
import net.bible.android.control.mynote.MyNoteDAO
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.control.versification.ConvertibleVerseRange
import net.bible.service.download.FakeSwordBookFactory
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification
import java.io.IOException

/** Provide information for My Note page
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentMyNotePage internal constructor(
	currentVerse: CurrentBibleVerse,
	bibleTraverser: BibleTraverser,
	swordContentFacade: SwordContentFacade,
	swordDocumentFacade: SwordDocumentFacade,
	private val myNoteDAO: MyNoteDAO,
    pageManager: CurrentPageManager
) : CurrentCommentaryPage(currentVerse, bibleTraverser, swordContentFacade, swordDocumentFacade, pageManager), CurrentPage
{
    private var currentNoteVerseRange: ConvertibleVerseRange? = null
    // just one fake book for every note
    private var fakeMyNoteBook: Book? = null
    private var fakeMyNoteBookVersification: Versification? = null
    override val currentPageContent: String get() = myNoteDAO.getMyNoteTextByKey(key)

    override val currentDocument: Book?
		get () {
			try {
				if (fakeMyNoteBook == null || fakeMyNoteBookVersification == null || fakeMyNoteBookVersification != currentVersification) {
					val v11n = currentVersification
					fakeMyNoteBook = FakeSwordBookFactory.createFakeRepoBook("My Note", MY_NOTE_DUMMY_CONF + v11n.name, "")
					fakeMyNoteBookVersification = v11n
				}
			} catch (e: IOException) {
				Log.e(TAG, "Error creating fake MyNote book", e)
			} catch (e: BookException) {
				Log.e(TAG, "Error creating fake MyNote book", e)
			}
			return fakeMyNoteBook
		}

    /** can we enable the main menu search button
     */

	// TODO doesn't work currently - enable later
	override val isSearchable: Boolean = false

    /** can we enable the main menu Speak button
     */
	//TODO doesn't work currently - enable later
	override val isSpeakable: Boolean = false

    override val bookCategory = BookCategory.OTHER

    private val currentVersification: Versification
        get() = currentBibleVerse.versificationOfLastSelectedVerse

    /**
     * Set key without notification
     */
    override fun doSetKey(key: Key?) {
        if (key != null) {
            val verse = KeyUtil.getVerse(key)
            val verseRange: VerseRange
            verseRange = if (key is VerseRange) {
                key
            } else {
                VerseRange(verse.versification, verse)
            }
            currentNoteVerseRange = ConvertibleVerseRange(verseRange)
            currentBibleVerse.setVerseSelected(versification, verse)
        }
    }

    /** Do not run VersePage implementation of localSetCurrentDocument
     * because that is not for notes
     */
    override fun localSetCurrentDocument(doc: Book?, isMyNote: Boolean) {
        localSetCurrentDocument(doc, true)
    }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
    override val key: Key
        get() = if (currentNoteVerseRange != null) {
            currentNoteVerseRange!!.getVerseRange(versification)
        } else {
            currentBibleVerse.getVerseSelected(versification)
        }

	override fun setKey(key: Key) {
		_key = key
        doSetKey(key)
	}

    override val numberOfVersesDisplayed: Int
        get() = if (currentNoteVerseRange != null) currentNoteVerseRange!!.verseRange.cardinality else 1

    override val isSingleKey: Boolean get()  {
        return currentNoteVerseRange == null || currentNoteVerseRange!!.verseRange.cardinality == 1
    }

    companion object {
        private const val MY_NOTE_DUMMY_CONF = """[MyNote]
Description=My Note
Category=OTHER
ModDrv=zCom
BlockType=CHAPTER
Lang=en
Encoding=UTF-8
LCSH=Bible--Commentaries.
DataPath=./modules/comments/zcom/mynote/
About=
Versification="""
        private const val TAG = "CurrentMyNotePage"
    }

}
