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
package net.bible.android.view.activity.download

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
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
        val btn: Button = findViewById(R.id.okButton)
        btn.setOnClickListener { onOkay() }
        super.buildActivityComponent().inject(this)
        Log.i(TAG, "Finished displaying Search Index view")
    }

    fun onOkay() {
        Log.i(TAG, "CLICKED")
        val resultIntent = Intent(this, ProgressStatus::class.java)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        private const val TAG = "ProgressStatus"
    }
}
