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

package net.bible.service.history

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

import net.bible.android.control.ApplicationScope
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent
import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.base.AndBibleActivity
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils.JSON_CONFIG
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books


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

    private val screenHistoryStackMap = HashMap<Int, Stack<HistoryItem>>()

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
            val windowNo = windowControl.activeWindow.screenNo
            var historyStack = screenHistoryStackMap[windowNo]
            if (historyStack == null) {
                synchronized(screenHistoryStackMap) {
                    historyStack = screenHistoryStackMap[windowNo]
                    if (historyStack == null) {
                        historyStack = Stack()
                        screenHistoryStackMap[windowNo] = historyStack!!
                    }
                }
            }
            return historyStack!!
        }


    @Serializable
    class SerializableHistoryItem(
        val document: String,
        val key: String,
        val yOffsetRatio: Float
    )

    @Serializable
	class HistorySerializer(val map: HashMap<Int, ArrayList<SerializableHistoryItem>>)

	var dumpString: String
        get() {
            val map = HashMap<Int, ArrayList<SerializableHistoryItem>>()
            for((windowId, historyStack) in screenHistoryStackMap) {
                val historyItems = arrayListOf<SerializableHistoryItem>()
                for(itm in historyStack) {
                    if(itm is KeyHistoryItem) {
                        historyItems.add(
                            SerializableHistoryItem(itm.document.initials, itm.key.osisID, itm.yOffsetRatio)
                        )
                    }
                }

                map[windowId] = historyItems
            }

            val s = HistorySerializer(map)
            return Json(JSON_CONFIG).stringify(HistorySerializer.serializer(), s)
        }
        set(newValue) {
            screenHistoryStackMap.clear()
            if(newValue.isEmpty()) return

            val map = Json(JSON_CONFIG).parse(HistorySerializer.serializer(), newValue).map
            for((windowId, historyItems) in map) {
                val window = windowControl.windowRepository.getWindow(windowId)
                if(window != null) {
                    val stack = Stack<HistoryItem>()
                    for (itm in historyItems) {
                        val doc = Books.installed().getBook(itm.document) ?: continue
                        val key = doc.getKey(itm.key)
                        stack.add(KeyHistoryItem(doc, key, itm.yOffsetRatio, window))
                    }
                    screenHistoryStackMap[windowId] = stack
                }
            }
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
    fun addHistoryItem() {
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
            historyItem = KeyHistoryItem(doc, key, yOffsetRatio, windowControl.activeWindow)
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

        private val MAX_HISTORY = 80

        private val TAG = "HistoryManager"
    }
}
