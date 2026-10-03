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

import android.util.Log
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.sword.mydocument.MyDocumentUpdatedEvent
import net.bible.service.sword.mydocument.isMyDocument
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key
import java.util.*

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class CachedKeyPage internal constructor(
	shareKeyBetweenDocs: Boolean,
    pageManager: CurrentPageManager
) : CurrentPageBase(shareKeyBetweenDocs, pageManager) {
    private var mCachedGlobalKeyList: MutableList<Key>? = null

    init {
        ABEventBus.register(this)
    }

    /**
     * Called when a MyDocument is updated (pages added/removed).
     * Clears the cache if the current document matches.
     */
    fun onEvent(event: MyDocumentUpdatedEvent) {
        val doc = currentDocument
        if (doc != null && doc.isMyDocument && doc.initials == event.initials) {
            Log.d(TAG, "Clearing cached key list for updated MyDocument: ${event.initials}")
            mCachedGlobalKeyList = null
        }
    }


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
            val doc = currentDocument
            if (doc != null && keylist == null) {
                try {
                    Log.i(TAG, "Start to create cached key list for $doc")
                    // this cache is cleared in setCurrentDoc
                    keylist = ArrayList()
                    for (key in doc.globalKeyList) { // root key has no name and can be ignored but also check for any other keys with no name
                        if (!StringUtils.isEmpty(key.name)) {
                            keylist.add(key)
                        }
                    }
                } catch (oom: OutOfMemoryError) {
                    System.gc()
                    Log.e(TAG, "out of memory", oom)
                    throw oom
                } catch (e: Exception) {
                    keylist = null
                    System.gc()
                    Log.e(TAG, "Error getting keys for $doc", e)
                    Dialogs.showErrorMsg(R.string.error_occurred, e)
                }
                Log.i(TAG, "Finished creating cached key list len:" + (keylist?.size ?: 0))
            }
			mCachedGlobalKeyList = keylist
            return keylist
        }

    /** add or subtract a number of pages from the current position and return Verse
     */
    override fun getKeyPlus(num: Int): Key {
        return getKeyPlus(key, num)
    }

    fun getKeyPlus(currentKey: Key?, num: Int): Key {
        // Guard before indexing: the key list can be null or empty (the underlying
        // document was deactivated concurrently, building it failed, or the
        // document genuinely has no keys). Indexing an empty list at [0] below
        // would otherwise throw. Fall back to the current key.
        val keyList = cachedGlobalKeyList?.takeIf { it.isNotEmpty() } ?: return currentKey ?: DefaultLeafKeyList("")
        val keyPos = keyList.indexOf(currentKey)
        // move forward or backward to new posn
        var newKeyPos = keyPos + num
        // check bounds
        newKeyPos = Math.min(newKeyPos, keyList.size - 1)
        newKeyPos = Math.max(newKeyPos, 0)
        // get the actual key at that posn
        return keyList[newKeyPos]
    }

    companion object {
        private const val TAG = "CachedKeyPage"
    }
}
