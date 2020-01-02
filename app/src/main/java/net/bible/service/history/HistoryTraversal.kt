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

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent


/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class HistoryTraversal(private val historyManager: HistoryManager, var isIntegrateWithHistoryManager: Boolean) {

    private var lastBackNavTime: Long = 0

    /**
     * about to change activity so tell the HistoryManager so it can register the old activity in its list
     */
    fun beforeStartActivity() {
        if (isIntegrateWithHistoryManager) {
            ABEventBus.getDefault().post(BeforeCurrentPageChangeEvent())
        }
    }

    fun goBack(): Boolean {
        val prevBackNavTime = lastBackNavTime
        lastBackNavTime = System.currentTimeMillis()
        if (lastBackNavTime - prevBackNavTime < MIN_BACK_SEPERATION_MILLIS) {
            // swallow back key if it seems like a phantom repeat to prevent history item jumping
            return true
        } else if (isIntegrateWithHistoryManager && historyManager.canGoBack()) {
            Log.d(TAG, "Go back")
            historyManager.goBack()
            return true
        } else {
            return false
        }
    }

    companion object {

        private val MIN_BACK_SEPERATION_MILLIS: Long = 500

        private val TAG = "HistoryTraversal"
    }
}
