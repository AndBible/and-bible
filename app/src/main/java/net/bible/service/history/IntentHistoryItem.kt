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

import android.app.Activity
import android.content.Intent
import android.util.Log

import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.base.CurrentActivityHolder

/**
 * Any item in the History list that is not related to the main bible activity view e.g. search results etc
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class IntentHistoryItem(override val description: CharSequence, private val intent: Intent, window: Window)// prevent re-add of intent to history if reverted to
//		intent.putExtra(HISTORY_INTENT, true);
    : HistoryItemBase(window) {

    override fun equals(o: Any?): Boolean {
        if (o == null || o !is IntentHistoryItem) {
            return false
        }
        if (o === this) {
            return true
        }

        val oihs = o as IntentHistoryItem?
        // assumes intent exists
        return intent == oihs!!.intent
    }

    override fun revertTo() {
        Log.d(TAG, "Revert to history item:$description")
        // need to get current activity and call startActivity on that
        val currentActivity = CurrentActivityHolder.getInstance().currentActivity

        // start activity chosen from activity
        currentActivity.startActivity(intent)
    }

    companion object {

        private val TAG = "IntentHistoryItem"
    }
}
