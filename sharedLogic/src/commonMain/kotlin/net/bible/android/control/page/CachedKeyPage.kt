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

import net.bible.sharedcore.log.Log
import androidx.annotation.VisibleForTesting
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.service.sword.mydocument.MyDocumentChange
import net.bible.sharedcore.event.Subscription
import net.bible.service.sword.mydocument.isMyDocument
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Key
import net.bible.sharedlogic.util.requestGarbageCollection

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
abstract class CachedKeyPage internal constructor(
	shareKeyBetweenDocs: Boolean,
    pageManager: CurrentPageManager
) : CurrentPageBase(shareKeyBetweenDocs, pageManager) {
    /** A key list and the book it was built from: a list is only ever used for its own book (#3975). */
    private class KeyListCache(val document: Book, val keys: List<Key>)

    /** Written from the thread that builds the list (often IO) and cleared on main; read whole, never field by field. */
    @kotlin.concurrent.Volatile private var keyListCache: KeyListCache? = null

    @VisibleForTesting fun hasCachedKeyListForTest() = keyListCache != null

    private val documentUpdates: Subscription = MyDocumentBookManager.changes.subscribe { change ->
        // Clears the key cache when pages in the current MyDocument change.
        if (change !is MyDocumentChange.DocumentUpdated) return@subscribe
        val doc = currentDocument
        if (doc != null && doc.isMyDocument && doc.initials == change.initials) {
            Log.d(TAG, "Clearing cached key list for updated MyDocument: ${change.initials}")
            keyListCache = null
        }
    }

    /** Releases the subscription when the owning window is destroyed. */
    internal fun destroy() = documentUpdates.cancel()


	override fun setCurrentDocument(doc: Book?) {
		if (doc != null && doc != currentDocument) {
			keyListCache = null
		}
		super.setCurrentDocument(doc)
	}

    /** The current document's key list (named keys only), built on first use; makes dictionary key lookup faster. */
    val cachedGlobalKeyList: List<Key>?
        get() = currentDocument?.let { keyListOf(it) }

    /**
     * [doc]'s key list. The cached list is used only if it was built for [doc]; a list built here is published as the
     * cache only while [doc] is still the page's document, so a read of an outgoing book that finishes after the page
     * switched (off main, [getKeyPlus] with a captured book) never becomes the next book's list (#3975).
     */
    fun keyListOf(doc: Book): List<Key>? {
        keyListCache?.takeIf { it.document == doc }?.let { return it.keys }
        val keylist: List<Key>? = try {
            Log.i(TAG, "Start to create cached key list for $doc")
            // root key has no name and can be ignored but also check for any other keys with no name
            doc.getGlobalKeyList().filter { !it.getName().isNullOrEmpty() }
        } catch (oom: OutOfMemoryError) { // L1-common-ok: OutOfMemoryError compiles in commonMain
            requestGarbageCollection()
            Log.e(TAG, "out of memory", oom)
            throw oom
        } catch (e: Exception) {
            requestGarbageCollection()
            Log.e(TAG, "Error getting keys for $doc", e)
            notifier.showError(strings.errorOccurred, e)
            null
        }
        Log.i(TAG, "Finished creating cached key list len:" + (keylist?.size ?: 0))
        if (keylist != null && currentDocument == doc) keyListCache = KeyListCache(doc, keylist)
        return keylist
    }

    /** add or subtract a number of pages from the current position and return Verse
     */
    override fun getKeyPlus(num: Int): Key {
        return getKeyPlus(key, num)
    }

    /** [num] keys after (before, if negative) [currentKey] in the current document; see the three-argument form. */
    fun getKeyPlus(currentKey: Key?, num: Int): Key {
        val doc = currentDocument ?: return currentKey ?: DefaultLeafKeyList("")
        return getKeyPlus(currentKey, num, doc)
    }

    /**
     * [num] keys after (before, if negative) [currentKey] in [document]'s key list, whatever the page shows now: infinite
     * scroll reads the next entry of the book its page came from (#3975).
     */
    fun getKeyPlus(currentKey: Key?, num: Int, document: Book): Key {
        // Guard before indexing: the key list can be null or empty (the underlying
        // document was deactivated concurrently, building it failed, or the
        // document genuinely has no keys). Indexing an empty list at [0] below
        // would otherwise throw. Fall back to the current key.
        val keyList = keyListOf(document)?.takeIf { it.isNotEmpty() } ?: return currentKey ?: DefaultLeafKeyList("")
        val keyPos = keyList.indexOf(currentKey)
        // move forward or backward to new posn
        var newKeyPos = keyPos + num
        // check bounds
        newKeyPos = kotlin.math.min(newKeyPos, keyList.size - 1)
        newKeyPos = kotlin.math.max(newKeyPos, 0)
        // get the actual key at that posn
        return keyList[newKeyPos]
    }

    companion object {
        private const val TAG = "CachedKeyPage"
    }
}
