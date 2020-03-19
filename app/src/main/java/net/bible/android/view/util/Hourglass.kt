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
package net.bible.android.view.util

import android.app.ProgressDialog
import android.util.Log
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.view.activity.base.CurrentActivityHolder

/** Helper class to show HourGlass
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class Hourglass {
    private var hourglass: ProgressDialog? = null
        private set

    fun show() {
        val activity = CurrentActivityHolder.getInstance().currentActivity
        activity?.runOnUiThread {
            val hourglass = ProgressDialog(activity)
            this.hourglass = hourglass
            hourglass.setMessage(application.getText(R.string.please_wait))
            hourglass.isIndeterminate = true
            hourglass.setCancelable(false)
            hourglass.show()
        }
    }

    fun dismiss() {
        try {
            if (hourglass != null) {
                val activity = CurrentActivityHolder.getInstance().currentActivity
                activity?.runOnUiThread {
                    hourglass!!.dismiss()
                    hourglass = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing hourglass", e)
        }
    }

    companion object {
        private const val TAG = "HourGlass"
    }
}
