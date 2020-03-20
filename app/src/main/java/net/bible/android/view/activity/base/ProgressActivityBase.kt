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
package net.bible.android.view.activity.base

import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ProgressActivityBase
import org.apache.commons.lang3.StringUtils
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Base class for any screen that shows job progress indicators
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
open class ProgressActivityBase : CustomTitlebarActivityBase() {
    private val progressMap: MutableMap<Progress, ProgressUIControl> = HashMap()
    private var progressControlContainer: LinearLayout? = null
    private var workListener: WorkListener? = null
    private val progressNotificationQueue: Queue<Progress> = ConcurrentLinkedQueue()
    private var taskKillWarningView: TextView? = null
    private var noTasksMessageView: TextView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
    }

    /** Wait until subclass has setContentView before looking for controls.  */
    public override fun onResume() {
        super.onResume()
        Log.i(TAG, "Displaying $TAG view")
        progressControlContainer = findViewById<View>(R.id.progressControlContainer) as LinearLayout
        initialiseView()
    }

    private fun initialiseView() {
        // prepare to show no tasks msg
        noTasksMessageView = findViewById<View>(R.id.noTasksRunning) as TextView
        taskKillWarningView = findViewById<View>(R.id.progressStatusMessage) as TextView
        val jobsIterator = JobManager.iterator()
        while (jobsIterator.hasNext()) {
            val job = jobsIterator.next()
            findOrCreateUIControl(job)
        }

        // allow call back and continuation in the ui thread after JSword has been initialised
        val uiHandler = Handler()
        val uiUpdaterRunnable = Runnable {
            val prog = progressNotificationQueue.poll()
            prog?.let { updateProgress(it) }
        }

        // listen for Progress changes and call the above Runnable to update the ui
        workListener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) {
                callUiThreadUpdateHandler(ev)
            }

            override fun workStateChanged(ev: WorkEvent) {
                callUiThreadUpdateHandler(ev)
            }

            private fun callUiThreadUpdateHandler(ev: WorkEvent) {
                val prog = ev.job
                progressNotificationQueue.offer(prog)
                // switch back to ui thread to continue
                uiHandler.post(uiUpdaterRunnable)
            }
        }
        JobManager.addWorkListener(workListener)

        // give new jobs a chance to start then show 'No Job' msg if nothing running
        uiHandler.postDelayed(
            {
                if (!JobManager.iterator().hasNext()) {
                    showNoTaskMsg(true)
                }
            }, 4000)
    }

    /** virtual method called on ui thread to update progress.  Can be overridden for subclass specific ui updates br make sure this method is called to update progres controls
     */
    protected fun updateProgress(prog: Progress) {
        // if this is called then ensure the no tasks msg is not also displayed
        showNoTaskMsg(false)
        val done = prog.work
        val status = getStatusDesc(prog)
        val progressUIControl = findOrCreateUIControl(prog)
        progressUIControl.showMsg(status)
        progressUIControl.showPercent(done)
        if (prog.isFinished && !progressUIControl.isFinishNotified) {
            Log.i(TAG, "Job finished:" + prog.jobName)
            progressUIControl.isFinishNotified = true
            jobFinished(prog)
        }
    }

    protected open fun jobFinished(job: Progress?) {
        // do nothing by default
    }

    /** helper method that returns true if alll jobs are finished
     *
     * @return true if all jobs finished or no jobs
     */
    protected val isAllJobsFinished: Boolean
        protected get() {
            val jobsIterator = JobManager.iterator()
            while (jobsIterator.hasNext()) {
                val job = jobsIterator.next()
                if (!job.isFinished) {
                    return false
                }
            }
            return true
        }

    /** format a descriptive string from a Progress object
     *
     * @param prog
     * @return
     */
    protected fun getStatusDesc(prog: Progress): String {
        // compose a descriptive string showing job name and current section if relevant
        var status = prog.jobName + SharedConstants.LINE_SEPARATOR
        if (!StringUtils.isEmpty(prog.sectionName) && !prog.sectionName.equals(prog.jobName, ignoreCase = true)) {
            status += prog.sectionName
        }
        return status
    }

    protected fun hideButtons() {
        val buttonPanel = findViewById<View>(R.id.button_panel)
        if (buttonPanel != null) {
            buttonPanel.visibility = View.INVISIBLE
        }
    }

    protected fun setMainText(text: String?) {
        (findViewById<View>(R.id.progressStatusMessage) as TextView).text = text
    }

    private fun showNoTaskMsg(bShow: Boolean) {
        if (noTasksMessageView != null && taskKillWarningView != null) {
            if (bShow) {
                noTasksMessageView!!.visibility = View.VISIBLE
                // if the no-tasks msg is show then hide the warning relating to running tasks
                taskKillWarningView!!.visibility = View.INVISIBLE
            } else {
                noTasksMessageView!!.visibility = View.GONE
                taskKillWarningView!!.visibility = View.VISIBLE
            }
        }
    }

    /** get a UI control for the current prog from the previously created controls, or create one
     *
     * @param prog
     * @return
     */
    protected fun findOrCreateUIControl(prog: Progress): ProgressUIControl {
        var uiControl = progressMap[prog]
        if (uiControl == null) {
            uiControl = ProgressUIControl()
            progressMap[prog] = uiControl
            progressControlContainer!!.addView(uiControl.parent)
            uiControl.showMsg(prog.jobName)
            uiControl.showPercent(prog.work)
        }
        return uiControl
    }

    override fun onPause() {
        super.onPause()
        JobManager.removeWorkListener(workListener)
    }

    /** contains a TextView desc and ProgressBar for a single Job
     */
    inner class ProgressUIControl {
        var parent = LinearLayout(this@ProgressActivityBase)
        var status = TextView(this@ProgressActivityBase)
        var progressBar = ProgressBar(this@ProgressActivityBase, null, android.R.attr.progressBarStyleHorizontal)
        var isFinishNotified = false
        fun showMsg(msg: String?) {
            status.text = msg
        }

        fun showPercent(percent: Int) {
            progressBar.isIndeterminate = percent == 0
            progressBar.progress = percent
        }

        init {
            parent.orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            parent.addView(status, lp)
            parent.addView(progressBar, lp)
            progressBar.max = 100
            showMsg("Starting...")
        }
    }

    companion object {
        private const val TAG = "ProgressActivityBase"
    }
}
