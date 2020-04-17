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
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.view.activity.base.CurrentActivityHolder

/** Helper class to show HourGlass
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class Hourglass(val context: Context) {
    private var hourglass: ProgressDialog? = null

    suspend fun show() {
        withContext(Dispatchers.Main) {
            val hourglass = ProgressDialog(context)
            this@Hourglass.hourglass = hourglass

            hourglass.setMessage(application.getText(R.string.please_wait))
            hourglass.isIndeterminate = true
            hourglass.setCancelable(false)
            hourglass.show()
        }
    }

    suspend fun dismiss() {
        withContext(Dispatchers.Main) {
            hourglass!!.dismiss()
            hourglass = null
        }
    }
}
