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

import android.content.Context
import android.content.Intent
import android.util.Log
import net.bible.android.common.toV11n
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.android.database.WorkspaceEntities
import net.bible.android.misc.OsisFragment
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.OsisError
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.BookFilters
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

open class CurrentCommentaryPage internal constructor(
    currentBibleVerse: CurrentBibleVerse,
    bibleTraverser: BibleTraverser,
    swordDocumentFacade: SwordDocumentFacade,
    pageManager: CurrentPageManager
) : VersePage(true, currentBibleVerse, bibleTraverser, swordDocumentFacade, pageManager), CurrentPage
{

    override val documentCategory = DocumentCategory.COMMENTARY

    override fun startKeyChooser(context: ActivityBase) =
        context.startActivityForResult(Intent(context, GridChoosePassageBook::class.java).apply { putExtra("isScripture", true) }, STD_REQUEST_CODE)

    private val isSpecialDoc: Boolean get() = currentDocument == FakeBookFactory.compareDocument

    override val currentPageContent: Document
        get() {
            return if(currentDocument == FakeBookFactory.compareDocument) {
                val key: VerseRange = when(val origKey = originalKey ?: singleKey) {
                    is VerseRange -> origKey
                    is Verse -> VerseRange(origKey.versification, origKey, origKey)
                    else -> throw RuntimeException("Invalid type")
                }

                val frags = Books.installed().getBooks(BookFilters.getBibles()).map {
                    try {
                        OsisFragment(SwordContentFacade.readOsisFragment(it, key.toV11n((it as SwordBook).versification)), key, it)
                    } catch (e: OsisError) {
                        null
                    }
                }.filterNotNull()
                MultiFragmentDocument(frags, compare=true)
            } else super.currentPageContent
        }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#next()
	 */
    override fun next() {
        Log.i(TAG, "Next")
        nextVerse()
    }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#previous()
	 */
    override fun previous() {
        Log.i(TAG, "Previous")
        previousVerse()
    }

    private fun nextVerse() {
        setKey(getKeyPlus(1))
    }

    private fun previousVerse() {
        setKey(getKeyPlus(-1))
    }

    /** add or subtract a number of pages from the current position and return Verse
     */
    override fun getKeyPlus(num: Int): Verse {
        var num = num
        val v11n = versification
        val currVer = currentBibleVerse.getVerseSelected(v11n)
        return try {
            var nextVer = currVer
            if (num >= 0) { // move to next book or chapter if required
                for (i in 0 until num) {
                    nextVer = bibleTraverser.getNextVerse(currentPassageBook, nextVer)
                }
            } else { // move to next book if required
                     // allow standard loop structure by changing num to positive
                num = -num
                for (i in 0 until num) {
                    nextVer = bibleTraverser.getPrevVerse(currentPassageBook, nextVer)
                }
            }
            nextVer
        } catch (nsve: Exception) {
            Log.e(TAG, "Incorrect verse", nsve)
            currVer
        }
    }
    override val isSpeakable: Boolean get() = !isSpecialDoc

    var originalKey: Key? = null

    override fun doSetKey(key: Key?) {
        originalKey = key
        if(key != null) {
            val verse = KeyUtil.getVerse(key)
            currentBibleVerse.setVerseSelected(versification, verse)
        }
    }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
    override val key: Key get() = currentBibleVerse.getVerseSelected(versification)

    override val isSingleKey = true

    /** can we enable the main menu search button
     */
    override val isSearchable get() = !isSpecialDoc

    val entity get() =
        WorkspaceEntities.CommentaryPage(currentDocument?.initials, anchorOrdinal)

    fun restoreFrom(entity: WorkspaceEntities.CommentaryPage?) {
        if(entity == null) return
        val document = entity.document
        val book = when(document) {
            FakeBookFactory.compareDocument.initials -> FakeBookFactory.compareDocument
            else -> swordDocumentFacade.getDocumentByInitials(document) ?: if(document != null) FakeBookFactory.giveDoesNotExist(document) else null
        }
        if(book != null) {
            Log.i(TAG, "Restored document:" + book.name)
            // bypass setter to avoid automatic notifications.
            // Also let's not use localSetCurrentDocument, because we don't want to set the verse.
            // It is already set correctly when CurrentBiblePage is restored.
            // Otherwise versification will be messed up!
            onlySetCurrentDocument(book)
            anchorOrdinal = entity.anchorOrdinal
        }
    }

    companion object {
        private const val TAG = "CurrentCommentaryPage"
    }
}
