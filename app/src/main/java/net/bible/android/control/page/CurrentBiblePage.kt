/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import android.app.Activity
import android.util.Log
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.view.activity.navigation.GridChoosePassageBook
import net.bible.service.common.CommonUtils.getWholeChapter
import net.bible.service.db.workspaces.WorkspaceEntities
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.json.JSONException
import org.json.JSONObject

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class CurrentBiblePage(
	currentBibleVerse: CurrentBibleVerse,
	bibleTraverser: BibleTraverser,
	swordContentFacade: SwordContentFacade,
	swordDocumentFacade: SwordDocumentFacade
) : VersePage(true, currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade), CurrentPage {

    override val bookCategory = BookCategory.BIBLE

    override val keyChooserActivity: Class<out Activity?>?
        get() = GridChoosePassageBook::class.java

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#next()
	 */
    override fun next() {
        Log.d(TAG, "Next")
        nextChapter()
    }

    /* go to prev verse quietly without updates
	 */
    fun doPreviousVerse() {
        Log.d(TAG, "Previous verse")
        val versification = versification
        val verse = currentBibleVerse.getVerseSelected(versification)
        currentBibleVerse.setVerseSelected(versification, bibleTraverser.getPrevVerse(currentPassageBook, verse))
    }

    /* go to next verse quietly without updates
	 */
    fun doNextVerse() {
        Log.d(TAG, "Next verse")
        val versification = versification
        val verse = currentBibleVerse.getVerseSelected(versification)
        currentBibleVerse.setVerseSelected(versification, bibleTraverser.getNextVerse(currentPassageBook, verse))
    }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#previous()
	 */
    override fun previous() {
        Log.d(TAG, "Previous")
        previousChapter()
    }

    /**
     * Get a fragment for specified chapter of Bible to be inserted at top of bottom of original text
     */
    fun getFragmentForChapter(chapter: Int): String? {
        val verseForFragment = Verse(versification, verseSelected.book, chapter, 1)
        val wholeChapter = getWholeChapter(verseForFragment)
        return getPageContent(wholeChapter, true)
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
        return getWholeChapter(targetChapterVerse1)
    }


    fun setKey(keyText: String) {
        Log.d(TAG, "key text:$keyText")
        try {
            val key = currentDocument!!.getKey(keyText)
            setKey(key)
        } catch (nske: NoSuchKeyException) {
            Log.e(TAG, "Invalid verse reference:$keyText")
        }
    }

    /** set key without notification **/

    override fun doSetKey(key: Key?) {
		val verse = KeyUtil.getVerse(key)
		//TODO av11n should this be the verse Versification or the Module/doc's Versification
		currentBibleVerse.setVerseSelected(versification, verse)
	}

    private fun doGetKey(requireSingleKey: Boolean): Key {
        val verse = verseSelected
        return run {
            val key: Key
            key = if (!requireSingleKey) {
				// display whole page of bible so return whole chapter key - not just the single verse even if a single verse was set in verseKey
				// if verseNo is required too then use getVerseRange()
                getWholeChapter(verse)
            } else {
                verse
            }
            key
        }
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
        WorkspaceEntities.BiblePage(currentDocument!!.initials, currentBibleVerse.entity)

    fun restoreFrom(entity: WorkspaceEntities.BiblePage) {
        val document = entity.document
        if (StringUtils.isNotEmpty(document)) {
            Log.d(TAG, "State document:$document")
            val book = swordDocumentFacade.getDocumentByInitials(document)
            if (book != null) {
                Log.d(TAG, "Restored document:" + book.name)
                // bypass setter to avoid automatic notifications
                localSetCurrentDocument(book)
                currentBibleVerse.restoreFrom(entity.verse)
            }
        }
    }

    var currentChapterVerse: ChapterVerse
        get() = currentBibleVerse.chapterVerse
        set(chapterVerse) {
            currentBibleVerse.chapterVerse = chapterVerse
            onVerseChange()
        }

    /** called during app close down to save state
     */
    @get:Throws(JSONException::class)
    override val stateJson: JSONObject
        get() {
            val `object` = JSONObject()
            if (currentDocument != null && currentBibleVerse != null && currentBibleVerse.getVerseSelected(versification) != null) {
                Log.d(TAG, "Saving Bible state for 1 window")
                `object`.put("document", currentDocument!!.initials)
                `object`.put("verse", currentBibleVerse.stateJson)
            }
            return `object`
        }

    /** called during app start-up to restore previous state
     */
    @Throws(JSONException::class)
    override fun restoreState(jsonObject: JSONObject?) {
        if (jsonObject != null) {
            Log.d(TAG, "Restoring Bible page state")
            if (jsonObject.has("document")) {
                val document = jsonObject.getString("document")
                if (StringUtils.isNotEmpty(document)) {
                    Log.d(TAG, "State document:$document")
                    val book = swordDocumentFacade.getDocumentByInitials(document)
                    if (book != null) {
                        Log.d(TAG, "Restored document:" + book.name)
                        // bypass setter to avoid automatic notifications
                        localSetCurrentDocument(book)
                        currentBibleVerse.restoreState(jsonObject.getJSONObject("verse"))
                    }
                }
            }
        }
    }//TODO allow japanese search - japanese bibles use smartcn which is not available

    /** can we enable the main menu search button
     */
    override val isSearchable: Boolean
        get() = try { //TODO allow japanese search - japanese bibles use smartcn which is not available
            "ja" != currentDocument!!.language.code
        } catch (e: Exception) {
            Log.w(TAG, "Missing language code", e)
            true
        }

    companion object {
        private const val TAG = "CurrentBiblePage"
    }
}
