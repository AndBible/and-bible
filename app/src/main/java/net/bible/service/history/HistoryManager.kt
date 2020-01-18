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


import java.util.ArrayList
import java.util.Collections
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
class HistoryManager @Inject
constructor(private val windowControl: WindowControl) {

    private val windowHistoryStackMap = HashMap<Long, Stack<HistoryItem>>()

    private var isGoingBack = false

    // reverse so most recent items are at top rather than end
    val history: List<HistoryItem>
        get() {
            val allHistory = ArrayList(historyStack)
            Collections.reverse(allHistory)
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


    fun getEntities(windowId: Long) =
        windowHistoryStackMap[windowId]?.mapNotNull {
            if(it is KeyHistoryItem) {
                WorkspaceEntities.HistoryItem(
                    windowId, it.createdAt, it.document.initials, it.key.osisID,
                    if(it.yOffsetRatio.isNaN()) null else it.yOffsetRatio
                )
            } else null
        } ?: emptyList()

    fun restoreFrom(window: Window, historyItems: List<WorkspaceEntities.HistoryItem>) {
        val stack = Stack<HistoryItem>()
        for(entity in historyItems) {
            val doc = Books.installed().getBook(entity.document) ?: continue
            val key = try {doc.getKey(entity.key) } catch (e: NoSuchKeyException) {
                Log.e(TAG, "Could not load key ${entity.key} from ${entity.document}")
                continue
            }
            stack.add(KeyHistoryItem(doc, key, entity.yOffsetRatio ?: Float.NaN, window))
        }
        windowHistoryStackMap[window.id] = stack
    }

    fun clear() {
        windowHistoryStackMap.clear()
    }

    init {
        // register for BeforePageChangeEvent
        Log.i(TAG, "Registering HistoryManager with EventBus")
        ABEventBus.getDefault().safelyRegister(this)
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

        val currentActivity = CurrentActivityHolder.getInstance().currentActivity
        if (currentActivity is MainBibleActivity) {
            val currentPage = windowControl.activeWindowPageManager.currentPage
            val doc = currentPage.currentDocument
            if (currentPage.key == null) {
                return null
            }

            val key = currentPage.singleKey
            val yOffsetRatio = currentPage.currentYOffsetRatio
            if(doc == null) return null
            historyItem = if(key != null) KeyHistoryItem(doc, key, yOffsetRatio, windowControl.activeWindow) else null
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
                Log.d(TAG, "History size:" + historyStack.size)
                isGoingBack = true

                // pop the previous item
                val previousItem = historyStack.pop()

                if (previousItem != null) {
                    Log.d(TAG, "Going back to:$previousItem")
                    previousItem.revertTo()

                    // finish current activity if not the Main screen
                    val currentActivity = CurrentActivityHolder.getInstance().currentActivity
                    if (currentActivity !is MainBibleActivity) {
                        currentActivity.finish()
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
                Log.d(TAG, "Adding $item to history")
                Log.d(TAG, "Stack size:" + stack.size)

                stack.push(item)

                while (stack.size > MAX_HISTORY) {
                    Log.d(TAG, "Shrinking large stack")
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
