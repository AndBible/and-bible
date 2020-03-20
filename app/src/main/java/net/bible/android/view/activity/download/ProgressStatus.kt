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
package net.bible.android.view.activity.download

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ProgressActivityBase

/**Show all Progress status
 * see BibleDesktop JobsProgressBar for example use
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class ProgressStatus : ProgressActivityBase() {
    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying $TAG view")
        setContentView(R.layout.progress_status)
        super.buildActivityComponent().inject(this)
        Log.d(TAG, "Finished displaying Search Index view")
    }

    fun onOkay(v: View?) {
        Log.i(TAG, "CLICKED")
        val resultIntent = Intent(this, ProgressStatus::class.java)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        private const val TAG = "ProgressStatus"
    }
}
