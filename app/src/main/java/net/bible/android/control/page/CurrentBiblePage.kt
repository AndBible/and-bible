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

import android.content.Intent
import android.util.Log
import net.bible.android.common.toV11n
import net.bible.android.control.page.window.Window
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.ActivityBase.Companion.STD_REQUEST_CODE
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.service.common.CommonUtils.getWholeChapter
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.doesNotExist
import net.bible.service.sword.SwordDocumentFacade
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.Versification

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentBiblePage(
    currentBibleVerse: CurrentBibleVerse,
    bibleTraverser: BibleTraverser,
    pageManager: CurrentPageManager
) : VersePage(true, currentBibleVerse, bibleTraverser, pageManager), CurrentPage {

    override val documentCategory = DocumentCategory.BIBLE

    override fun startKeyChooser(context: ActivityBase) = context.startActivityForResult(
        Intent(context, GridChoosePassageBook::class.java).apply { putExtra("isScripture", true) }, STD_REQUEST_CODE)

    override fun next() {
        Log.i(TAG, "Next")
        nextChapter()
    }

    override fun previous() {
        Log.i(TAG, "Previous")
        previousChapter()
    }

    fun getDocumentForChapter(chapter: Int): Document {
        val verseForFragment = Verse(versification, verseSelected.book, chapter, 1)
        val wholeChapter = getWholeChapter(verseForFragment, showIntros)
        return getPageContent(wholeChapter)
    }

    override fun getPageContent(key: Key): Document {
        annotateKey = null
        val verseRange = key as VerseRange
        val doc = super.getPageContent(verseRange)
        return if(doc is OsisDocument) {
            val bookmarksForChapter = pageManager.bookmarkControl.bookmarksForVerseRange(verseRange, withLabels = true)
            BibleDocument(
                osisFragment = doc.osisFragment, swordBook = doc.book as SwordBook,
                bookmarks = bookmarksForChapter, verseRange = verseRange, originalKey = originalKey
            )
        } else doc
    }

    private fun nextChapter() {
        setKey(getKeyPlus(+1))
    }

    private fun previousChapter() {
        setKey(getKeyPlus(-1))
    }

    /** add or subtract a number of pages from the current position and return Verse
     */
    override fun getKeyPlus(num: Int): Verse {
        var num = num
        val currVer = verseSelected
        return try {
            var nextVer = currVer
            if (num >= 0) { // move to next book if required
                for (i in 0 until num) {
                    nextVer = bibleTraverser.getNextChapter(currentPassageBook, nextVer)
                }
            } else { // move to prev book if required
                     // allow standard loop structure by changing num to positive
                num = -num
                for (i in 0 until num) {
                    nextVer = bibleTraverser.getPrevChapter(currentPassageBook, nextVer)
                }
            }
            nextVer
        } catch (nsve: Exception) {
            Log.e(TAG, "Incorrect verse", nsve)
            currVer
        }
    }

    /** add or subtract a number of pages from the current position and return Page
     */
    override fun getPagePlus(num: Int): Key {
        val targetChapterVerse1 = getKeyPlus(num)
        // convert to full chapter before returning because bible view is for a full chapter
        return getWholeChapter(targetChapterVerse1, showIntros)
    }


    fun setKey(keyText: String) {
        Log.i(TAG, "key text:$keyText")
        try {
            val key = currentDocument!!.getKey(keyText)
            setKey(key)
        } catch (nske: NoSuchKeyException) {
            Log.e(TAG, "Invalid verse reference:$keyText")
        }
    }
    var originalKey: Key? = null
    /** set key without notification **/

    override fun doSetKey(key: Key?) {
        originalKey = key
        val verse = KeyUtil.getVerse(key)
		//TODO av11n should this be the verse Versification or the Module/doc's Versification
		currentBibleVerse.setVerseSelected(versification, verse)
	}

    // TODO: for intros, could add new setting. Now using same setting as for section titles
    private val showIntros get() = pageManager.actualTextDisplaySettings.showSectionTitles == true

    private fun doGetKey(requireSingleKey: Boolean): Key {
        val verse = verseSelected
        val key: Key = if (!requireSingleKey) {
            // display whole page of bible so return whole chapter key - not just the single verse even if a single verse was set in verseKey
            // if verseNo is required too then use getVerseRange()
            getWholeChapter(verse, showIntros)
        } else {
            verse
        }
        return key
    }

	override val singleKey: Verse get() {
		val key = doGetKey(true)
		// it is already a Verse but this avoids a downcast
		return KeyUtil.getVerse(key)
	}

	override val key: Key get() = doGetKey(false)

    private val verseSelected: Verse get() = currentBibleVerse.getVerseSelected(versification)

    override val isSingleKey = false

    val entity get() =
        WorkspaceEntities.BiblePage(currentDocument?.initials, currentBibleVerse.entity)

    fun restoreFrom(entity: WorkspaceEntities.BiblePage) {
        originalKey = null
        val document = entity.document
        Log.i(TAG, "State document:$document")
        val book = SwordDocumentFacade.getDocumentByInitials(document) ?: if(document!= null) FakeBookFactory.giveDoesNotExist(document) else null
        Log.i(TAG, "Restored document:" + book?.name)
        // bypass setter to avoid automatic notifications
        localSetCurrentDocument(book)
        currentBibleVerse.restoreFrom(entity.verse)
    }

    @Deprecated("Used by test only!!! use setCurrentVerseOrdinal instead")
    var currentChapterVerse
        get() = currentBibleVerse.chapterVerse
        set(chapterVerse) {
            val oldChapterVerse = currentBibleVerse.chapterVerse
            if(chapterVerse != oldChapterVerse) {
                currentBibleVerse.chapterVerse = chapterVerse
            }
    }

    fun setCurrentVerseOrdinal(value: Int, versification: Versification, window: Window) {
        val old = currentBibleVerse.verse.ordinal
        val newVerse = Verse(versification, value).toV11n(currentBibleVerse.versificationOfLastSelectedVerse)
        if(newVerse.ordinal != old) {
            currentBibleVerse.setVerseSelected(versification, newVerse)
            onVerseChange(window)
        }
    }

    //TODO allow japanese search - japanese bibles use smartcn which is not available
    /** can we enable the main menu search button
     */
    override val isSearchable: Boolean
        get() = try { //TODO allow japanese search - japanese bibles use smartcn which is not available
            !currentDocument!!.doesNotExist && "ja" != currentDocument!!.language.code
        } catch (e: Exception) {
            Log.w(TAG, "Missing language code", e)
            true
        }

    companion object {
        private const val TAG = "CurrentBiblePage"
    }
}
