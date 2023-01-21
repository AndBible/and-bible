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

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.BeforeCurrentPageChangeEvent


/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class HistoryTraversal(val historyManager: HistoryManager, var isIntegrateWithHistoryManager: Boolean) {

    /**
     * about to change activity so tell the HistoryManager so it can register the old activity in its list
     */
    fun beforeStartActivity() {
        if (isIntegrateWithHistoryManager) {
            ABEventBus.post(BeforeCurrentPageChangeEvent())
        }
    }

    fun goBack(): Boolean {
        if (isIntegrateWithHistoryManager && historyManager.canGoBack()) {
            Log.i(TAG, "Go back")
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
