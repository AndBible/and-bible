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

import android.content.Intent
import android.util.Log

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.on
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.AndBibleActivity
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.database.WorkspaceEntities
import net.bible.sharedcore.reading.ReadingViewVisibility
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

class AddHistoryItem(val window: Window? = null)

class HistoryManager constructor(private val windowControl: WindowControl) {

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
                        windowId, it.createdAt, it.document.initials, it.key.osisID,
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
                if(k is RangedPassage) k[0] else k
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
        // register for BeforePageChangeEvent
        Log.i(TAG, "Registering HistoryManager with EventBus")
        ABEventBus.safelyRegister(this) {
            // allow current page to save any settings or data before being changed
            on<AddHistoryItem> { event -> addHistoryItem(event.window) }
        }
    }

    fun canGoBack(): Boolean {
        return getHistoryStack(windowControl.activeWindow.id).size > 0
    }

    /**
     * called when a verse is changed to allow current Activity to be saved in History list
     */
    fun addHistoryItem(window: Window?, intent: Intent? = null) {
        // if we cause the change by requesting Back then ignore it
        val activeWindow = window ?: windowControl.activeWindow
        if (!isGoingBack) {
            val item = createHistoryItem(activeWindow, intent)
            add(getHistoryStack(activeWindow.id), item)
        }
    }

    fun popHistoryItem() {
        getHistoryStack(windowControl.activeWindow.id).pop()
    }

    private fun createHistoryItem(window: Window, intent: Intent?): HistoryItem? {
        var historyItem: HistoryItem? = null

        val currentActivity = CurrentActivityHolder.currentActivity
        if (intent != null) {
            val title = intent.getStringExtra("description")?: "-"
            historyItem = IntentHistoryItem(title, intent, window)
        } else if (ReadingViewVisibility.isVisible) {
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

        } else if (currentActivity is AndBibleActivity) {
            val andBibleActivity = currentActivity as AndBibleActivity
            if (andBibleActivity.isIntegrateWithHistoryManager) {
                historyItem = IntentHistoryItem(currentActivity.title,
                    (currentActivity as AndBibleActivity).intentForHistoryList,
                    window)
            }
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

                    // finish current activity if not the Main screen
                    // Slice 7 / spec §5.3: the condition is now "the reading view is not what the
                    // user is looking at" (was `currentActivity !is MainBibleActivity`).
                    //
                    // These are NOT equivalent by construction — they agree only because
                    // `ReadingViewVisibility` is set true from `MainBibleActivity.onCreate`
                    // onwards, not merely from `onResume`. The state that needs the onCreate
                    // setter is this one: if the reading Activity was destroyed while a chooser
                    // was on top ("Don't keep activities" / low memory) and is then recreated,
                    // `onActivityResult`'s STD_REQUEST_CODE + RESULT_CANCELED path
                    // (`MainBibleActivity.onActivityResult`, guarded by `currentPage.key == null`)
                    // calls goBack() BEFORE onResume — and a flag that only went true at onResume
                    // would finish() the freshly recreated reading Activity, which the old class
                    // check never did.
                    //
                    // The remaining gap is the reverse one and is harmless: between onPause and
                    // the next Activity's onCreate the old check was still true while the flag is
                    // already false. goBack() is only reached from an Activity's back-press
                    // (`ActivityBase`/`MainBibleActivity.onBackPressed`) or from
                    // `onActivityResult`, and neither callback can run in that window.
                    //
                    // The finish() itself becomes popBackStack() in Task 7, once the secondary
                    // screens are destinations of one host and there is a host to pop.
                    val currentActivity = CurrentActivityHolder.currentActivity
                    if (!ReadingViewVisibility.isVisible) {
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

        const val MAX_HISTORY = 500

        private val TAG = "HistoryManager"
    }
}
