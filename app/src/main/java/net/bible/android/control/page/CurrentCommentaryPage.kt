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
import net.bible.service.db.workspaces.WorkspaceEntities
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.KeyUtil
import org.crosswire.jsword.passage.Verse
import org.json.JSONException
import org.json.JSONObject

/** Reference to current passage shown by viewer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class CurrentCommentaryPage  /* default */
internal constructor(
	currentBibleVerse: CurrentBibleVerse,
	bibleTraverser: BibleTraverser,
	swordContentFacade: SwordContentFacade,
	swordDocumentFacade: SwordDocumentFacade
) : VersePage(true, currentBibleVerse, bibleTraverser, swordContentFacade, swordDocumentFacade), CurrentPage
{

    override val bookCategory = BookCategory.COMMENTARY

    override val keyChooserActivity: Class<out Activity?>?
        get() = GridChoosePassageBook::class.java

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#next()
	 */
    override fun next() {
        Log.d(TAG, "Next")
        nextVerse()
    }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#previous()
	 */
    override fun previous() {
        Log.d(TAG, "Previous")
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

    /** set key without notification
     *
     * @param key
     */
    override fun doSetKey(key: Key?) {
        if(key != null) {
            val verse = KeyUtil.getVerse(key)
            currentBibleVerse.setVerseSelected(versification, verse)
        }
    }

    /* (non-Javadoc)
	 * @see net.bible.android.control.CurrentPage#getKey()
	 */
    override val key: Key get() = currentBibleVerse.getVerseSelected(versification)

    open val numberOfVersesDisplayed: Int
        get() = 1

    override val isSingleKey = true

    /** can we enable the main menu search button
     */
    override val isSearchable = true

    val entity get() = WorkspaceEntities.CommentaryPage(currentDocument!!.initials)

    /** called during app close down to save state
     */
    @get:Throws(JSONException::class)
    override val stateJson: JSONObject
        get() {
            val `object` = JSONObject()
            if (currentDocument != null && currentBibleVerse != null && currentBibleVerse.getVerseSelected(versification) != null) {
                Log.d(TAG, "Saving Commentary state for 1 window")
                `object`.put("document", currentDocument!!.initials)
                // allow Bible page to save shared verse
            }
            return `object`
        }

    /** called during app start-up to restore previous state
     *
     * @param jsonObject
     */
    @Throws(JSONException::class)
    override fun restoreState(jsonObject: JSONObject?) {
        if (jsonObject != null) {
            Log.d(TAG, "Restoring Commentary page state")
            if (jsonObject.has("document")) {
                val document = jsonObject.getString("document")
                if (StringUtils.isNotEmpty(document)) {
                    val book = swordDocumentFacade.getDocumentByInitials(document)
                    if (book != null) {
                        Log.d(TAG, "Restored document:" + book.name)
                        // bypass setter to avoid automatic notifications
                        localSetCurrentDocument(book)
                        // allow Bible page to restore shared verse
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "CurrentCommentaryPage"
    }
}
