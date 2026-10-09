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

package net.bible.service.history

import net.bible.sharedcore.log.Log

import androidx.annotation.VisibleForTesting
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.RangedPassage
import java.lang.Exception


import java.util.ArrayList
import java.util.HashMap
import java.util.Stack


/**
 * Application managed History List.
 * The HistoryManager keeps a different history list for each window.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */

class HistoryManager constructor(
    private val windowControl: WindowControl,
    private val platform: HistoryPlatform,
) {

    private val windowHistoryStackMap = HashMap<IdType, Stack<HistoryItem>>()

    private var isGoingBack = false

    // reverse so most recent items are at top rather than end
    fun getHistory(windowId: IdType): List<HistoryItem> {
        val allHistory = ArrayList(getHistoryStack(windowId))
        allHistory.reverse()
        return allHistory
    }

    private fun getHistoryStack(windowNo: IdType): Stack<HistoryItem> {
        var historyStack = windowHistoryStackMap[windowNo]
        if (historyStack == null) {
            synchronized(windowHistoryStackMap) {
                historyStack = windowHistoryStackMap[windowNo]
                if (historyStack == null) {
                    historyStack = Stack()
                    windowHistoryStackMap[windowNo] = historyStack!!
                }
            }
        }
        return historyStack!!
    }

    fun getEntities(windowId: IdType): List<WorkspaceEntities.HistoryItem> {
        var lastItem: KeyHistoryItem? = null
        return windowHistoryStackMap[windowId]?.mapNotNull {
            if (it is KeyHistoryItem) {
                if(it.document == lastItem?.document && it.key == lastItem?.key) {
                    null
                } else {
                    lastItem = it
                    WorkspaceEntities.HistoryItem(
                        windowId, it.createdAt, it.document.initials, it.key.getOsisID(),
                        it.anchorOrdinal?.start
                    )
                }
            } else null
        } ?: emptyList()
    }

    fun restoreFrom(window: Window, historyItems: List<WorkspaceEntities.HistoryItem>) {
        val stack = Stack<HistoryItem>()
        for(entity in historyItems) {
            val doc = Books.installed().getBook(entity.document) ?: continue
            val key = try {
                val k = doc.getKey(entity.key)
                if(k is RangedPassage) k.get(0)!! else k
            } catch (e: NoSuchKeyException) {
                Log.e(TAG, "Could not load key ${entity.key} from ${entity.document}")
                continue
            } catch (e: Exception) {
                Log.e(TAG, "Could not load key ${entity.key} from ${entity.document}")
                continue
            }
            stack.add(KeyHistoryItem(doc, key, entity.anchorOrdinal?.let { OrdinalRange(it) }, window, entity.createdAt))
        }
        windowHistoryStackMap[window.id] = stack
    }

    fun clear() {
        windowHistoryStackMap.clear()
    }

    init {
        // The newest constructed manager is live; do not force the lazy Koin singleton.
        instance = this
    }

    fun canGoBack(): Boolean {
        return getHistoryStack(windowControl.activeWindow.id).size > 0
    }

    /**
     * called when a verse is changed to allow current Activity to be saved in History list.
     * [screenToken] is an opaque platform screen token (on Android the Intent a screen is
     * leaving with); when given, the platform turns it into the item.
     */
    fun addHistoryItem(window: Window?, screenToken: Any? = null) {
        // if we cause the change by requesting Back then ignore it
        val activeWindow = window ?: windowControl.activeWindow
        if (!isGoingBack) {
            val item = createHistoryItem(activeWindow, screenToken)
            add(getHistoryStack(activeWindow.id), item)
        }
    }

    fun popHistoryItem() {
        getHistoryStack(windowControl.activeWindow.id).pop()
    }

    private fun createHistoryItem(window: Window, screenToken: Any?): HistoryItem? {
        var historyItem: HistoryItem? = null

        if (screenToken != null) {
            historyItem = platform.screenHistoryItem(window, screenToken)
        } else if (platform.isOnReadingScreen()) {
            // Slice 7 / spec §5.1: was `currentActivity is MainBibleActivity`. This is the ONLY
            // branch that produces a KeyHistoryItem — the verse back-stack and the only item type
            // getEntities()/restoreFrom() persist — so it must be anchored on "the reading view is
            // what the user is looking at" rather than on which Activity class is on top.
            val currentPage = window.pageManager.currentPage
            val doc = currentPage.currentDocument
            if (currentPage.key == null) {
                return null
            }

            val key = currentPage.singleKey
            val anchorOrdinal = currentPage.anchorOrdinal
            if(doc == null) return null
            historyItem =
                if(key != null) KeyHistoryItem(doc, key, anchorOrdinal, window)
                else null

        } else {
            historyItem = platform.screenHistoryItem(window, null)
        }
        return historyItem
    }

    fun goBack() {
        val historyStack = getHistoryStack(windowControl.activeWindow.id)
        if (historyStack.size > 0) {
            try {
                Log.i(TAG, "History size:" + historyStack.size)
                isGoingBack = true

                // pop the previous item
                val previousItem = historyStack.pop()

                if (previousItem != null) {
                    Log.i(TAG, "Going back to:$previousItem")
                    previousItem.revertTo()

                    // Leave the screen on top when it is not the reading view. Both questions are asked
                    // of the platform: leaving is a HOST operation (a classic Activity finishes, the
                    // nav host pops its back stack -- finishing it would close the app, finding M4),
                    // and isOnReadingScreen is the same predicate createHistoryItem records on
                    // (Android: ReadingViewVisibility, keyed by host and gated on ReadingHostPresence
                    // (R7b), so a destination composed under a backgrounded host does not count).
                    if (!platform.isOnReadingScreen()) {
                        platform.leaveCurrentScreen()
                    }
                }
            } finally {
                isGoingBack = false
            }
        }
    }

    /**
     * Add item and check size of stack
     */
    @Synchronized
    private fun add(stack: Stack<HistoryItem>, item: HistoryItem?) {
        if (item != null) {
            if (stack.isEmpty() || item != stack.peek()) {
                Log.i(TAG, "Adding $item to history")
                Log.i(TAG, "Stack size:" + stack.size)

                stack.push(item)

                while (stack.size > MAX_HISTORY) {
                    Log.i(TAG, "Shrinking large stack")
                    stack.removeAt(0)
                }
            }
        }
    }

    companion object {
        @Volatile private var instance: HistoryManager? = null

        /**
         * Records the old position in the live manager (null: active window), if one exists.
         * Logs failures instead of interrupting navigation, as the former bus delivery did.
         */
        fun recordIfCreated(window: Window?) {
            val manager = instance ?: return
            try {
                manager.addHistoryItem(window)
            } catch (e: Throwable) {
                Log.e(TAG, "addHistoryItem failed", e)
            }
        }

        @VisibleForTesting
        fun resetInstanceForTest() { instance = null }

        const val MAX_HISTORY = 500

        private val TAG = "HistoryManager"
    }
}
