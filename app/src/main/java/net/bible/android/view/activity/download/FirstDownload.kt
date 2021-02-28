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

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import net.bible.android.activity.R
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener

/**
 * Only allow progress into the main app once a Bible has been downloaded
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class FirstDownload : DownloadActivity() {
    private var okayButton: Button? = null
    private var okayButtonEnabled = false
    private val downloadCompletionListener: WorkListener
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        okayButton = findViewById<View>(R.id.okayButton) as Button
    }

    override fun onStart() {
        super.onStart()
        enableOkayButtonIfBibles()
        JobManager.addWorkListener(downloadCompletionListener)
    }

    override fun onStop() {
        super.onStop()
        JobManager.removeWorkListener(downloadCompletionListener)
    }

    private fun enableOkayButtonIfBibles() {
        if (!okayButtonEnabled) {
            val enable = swordDocumentFacade.bibles.size > 0
            okayButtonEnabled = enable
            runOnUiThread { okayButton!!.isEnabled = enable }
        }
    }

    fun onOkay(v: View?) {
        val resultIntent = Intent(this, FirstDownload::class.java)
        setResult(DOWNLOAD_FINISH, resultIntent)
        finish()
    }

    init {
        // Normal document screen but with an added OK button to facilitate forward like flow to main screen
        setShowOkButtonBar(visible = true)
        downloadCompletionListener = object : WorkListener {
            override fun workProgressed(workEvent: WorkEvent) {
                if (workEvent.job.isFinished) {
                    enableOkayButtonIfBibles()
                }
            }

            override fun workStateChanged(workEvent: WorkEvent) {
                // TODO this is never called so have to do it all in workProgressed
            }
        }
    }
}
