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

import android.util.Log

import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.AndBibleActivity
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.database.WorkspaceEntities
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.NoSuchKeyException
import org.crosswire.jsword.passage.RangedPassage


import java.util.ArrayList
import java.util.HashMap
import java.util.Stack

import javax.inject.Inject

/**
 * Application managed History List.
 * The HistoryManager keeps a different history list for each window.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class HistoryManager @Inject constructor(private val windowControl: WindowControl) {

    private val windowHistoryStackMap = HashMap<Long, Stack<HistoryItem>>()

    private var isGoingBack = false

    // reverse so most recent items are at top rather than end
    val history: List<HistoryItem>
        get() {
            val allHistory = ArrayList(historyStack)
            allHistory.reverse()
            return allHistory
        }

    private val historyStack: Stack<HistoryItem>
        get() {
            val windowNo = windowControl.activeWindow.id
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


    fun getEntities(windowId: Long): List<WorkspaceEntities.HistoryItem> {
        var lastItem: KeyHistoryItem? = null
        return windowHistoryStackMap[windowId]?.mapNotNull {
            if (it is KeyHistoryItem) {
                if(it.document == lastItem?.document && it.key == lastItem?.key) {
                    null
                } else {
                    lastItem = it
                    WorkspaceEntities.HistoryItem(
                        windowId, it.createdAt, it.document.initials, it.key.osisID,
                        it.anchorOrdinal
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
                if(k is RangedPassage) k[0] else k
            } catch (e: NoSuchKeyException) {
                Log.e(TAG, "Could not load key ${entity.key} from ${entity.document}")
                continue
            }
            stack.add(KeyHistoryItem(doc, key, entity.anchorOrdinal, window, entity.createdAt))
        }
        windowHistoryStackMap[window.id] = stack
    }

    fun clear() {
        windowHistoryStackMap.clear()
    }

    init {
        // register for BeforePageChangeEvent
        Log.i(TAG, "Registering HistoryManager with EventBus")
        ABEventBus.safelyRegister(this)
    }

    /** allow current page to save any settings or data before being changed
     */
    fun onEvent(event: BeforeCurrentPageChangeEvent) {
        if (event.updateHistory) {
            addHistoryItem()
        }
    }

    fun canGoBack(): Boolean {
        return historyStack.size > 0
    }

    /**
     * called when a verse is changed to allow current Activity to be saved in History list
     */
    private fun addHistoryItem() {
        // if we cause the change by requesting Back then ignore it
        if (!isGoingBack) {
            val item = createHistoryItem()
            add(historyStack, item)
        }
    }

    private fun createHistoryItem(): HistoryItem? {
        var historyItem: HistoryItem? = null

        val currentActivity = CurrentActivityHolder.currentActivity
        if (currentActivity is MainBibleActivity) {
            val currentPage = windowControl.activeWindowPageManager.currentPage
            val doc = currentPage.currentDocument
            if (currentPage.key == null) {
                return null
            }

            val key = currentPage.singleKey
            val anchorOrdinal = currentPage.anchorOrdinal
            if(doc == null) return null
            historyItem =
                if(key != null) KeyHistoryItem(doc, key, anchorOrdinal, windowControl.activeWindow)
                else null

        } else if (currentActivity is AndBibleActivity) {
            val andBibleActivity = currentActivity as AndBibleActivity
            if (andBibleActivity.isIntegrateWithHistoryManager) {
                historyItem = IntentHistoryItem(currentActivity.title,
                    (currentActivity as AndBibleActivity).intentForHistoryList,
                    windowControl.activeWindow)
            }
        }
        return historyItem
    }

    fun goBack() {
        if (historyStack.size > 0) {
            try {
                Log.i(TAG, "History size:" + historyStack.size)
                isGoingBack = true

                // pop the previous item
                val previousItem = historyStack.pop()

                if (previousItem != null) {
                    Log.i(TAG, "Going back to:$previousItem")
                    previousItem.revertTo()

                    // finish current activity if not the Main screen
                    val currentActivity = CurrentActivityHolder.currentActivity
                    if (currentActivity !is MainBibleActivity) {
                        currentActivity?.finish()
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

        private const val MAX_HISTORY = 500

        private val TAG = "HistoryManager"
    }
}
