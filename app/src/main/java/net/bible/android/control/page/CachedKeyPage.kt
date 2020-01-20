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
import net.bible.android.activity.R
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.SwordDocumentFacade
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Key
import java.util.*

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class CachedKeyPage internal constructor(
	shareKeyBetweenDocs: Boolean,
	swordContentFacade: SwordContentFacade,
	swordDocumentFacade: SwordDocumentFacade,
    pageManager: CurrentPageManager
) : CurrentPageBase(shareKeyBetweenDocs, swordContentFacade, swordDocumentFacade, pageManager) {
    private var mCachedGlobalKeyList: MutableList<Key>? = null


	override fun setCurrentDocument(doc: Book?) {
		if (doc != null && doc != currentDocument) {
			mCachedGlobalKeyList = null
		}
		super.setCurrentDocument(doc)
	}

    //TODO remove this and do binary search of globalkeylist// root key has no name and can be ignored but also check for any other keys with no name// this cache is cleared in setCurrentDoc
    /** make dictionary key lookup much faster
     *
     * @return
     */
    val cachedGlobalKeyList: List<Key>?
        get() {
			var keylist = mCachedGlobalKeyList
			if (currentDocument != null && keylist == null) {
                try {
                    Log.d(TAG, "Start to create cached key list for $currentDocument")
                    // this cache is cleared in setCurrentDoc
                    keylist = ArrayList()
                    for (key in currentDocument!!.globalKeyList) { // root key has no name and can be ignored but also check for any other keys with no name
                        if (!StringUtils.isEmpty(key.name)) {
                            keylist.add(key)
                        }
                    }
                } catch (oom: OutOfMemoryError) {
                    keylist = null
                    System.gc()
                    Log.e(TAG, "out of memory", oom)
                    throw oom
                } catch (e: Exception) {
                    keylist = null
                    System.gc()
                    Log.e(TAG, "Error getting keys for $currentDocument", e)
                    Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
                }
                Log.d(TAG, "Finished creating cached key list len:" + keylist!!.size)
            }
			mCachedGlobalKeyList = keylist
            return mCachedGlobalKeyList
        }

    /** add or subtract a number of pages from the current position and return Verse
     */
    override fun getKeyPlus(num: Int): Key {
        val currentKey = key
        val keyPos = findIndexOf(currentKey)
        // move forward or backward to new posn
        var newKeyPos = keyPos + num
        // check bounds
        newKeyPos = Math.min(newKeyPos, cachedGlobalKeyList!!.size - 1)
        newKeyPos = Math.max(newKeyPos, 0)
        // get the actual key at that posn
        return cachedGlobalKeyList!![newKeyPos]
    }

    /** find index of key in cached key list but cater for TreeKeys too
     */
    protected fun findIndexOf(key: Key?): Int {
        return cachedGlobalKeyList!!.indexOf(key)
    }

    companion object {
        private const val TAG = "CachedKeyPage"
    }
}
