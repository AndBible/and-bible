/*
 * Copyright (c) 2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchIndexProgressController
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.search.SearchIndexProgressScreen
import net.bible.sharedui.strings.LocalStrings
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener

/**
 * Compose host for the generic job-progress screen — the new-path twin of [ProgressStatus].
 * Launched from a notification ([net.bible.service.device.ProgressNotificationManager]), not from
 * [net.bible.android.view.ScreenLauncher] directly (which only picks the target class).
 *
 * Reuses Batch-1's [SearchIndexProgressScreen] + [SearchIndexProgressController] (progress list +
 * a single dismiss button), but with classic [ProgressStatus]'s own copy (task-kill warning / "OK")
 * instead of the search-index wording, and — unlike [net.bible.android.view.activity.search.SearchIndexProgressComposeActivity]
 * — with NO `jobFinished` navigation branching: [ProgressStatus] is a plain multi-job viewer with an
 * OK button, not tied to a single document's index status.
 */
class ProgressStatusComposeActivity : ActivityBase() {
    private var workListener: WorkListener? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private val controller by lazy { SearchIndexProgressController(onHide = ::onOkay) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying $TAG view")
        setContent {
            AbAppTheme {
                    val strings = LocalStrings.current
                    val jobs by controller.jobs.collectAsState()
                    val noTasks by controller.noTasks.collectAsState()
                    val error by controller.error.collectAsState()
                    SearchIndexProgressScreen(
                        title = getString(R.string.progress_status),
                        jobs = jobs,
                        noTasks = noTasks,
                        error = error,
                        onHide = controller::hide,
                        onDismissError = controller::dismissError,
                        message = strings.taskKillWarning,
                        buttonLabel = strings.okay,
                    )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { CommonUtils.requestNotificationPermission(this@ProgressStatusComposeActivity) }
        refreshJobs()
        workListener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) = onWorkEvent()
            override fun workStateChanged(ev: WorkEvent) = onWorkEvent()
        }
        JobManager.addWorkListener(workListener)
        // Classic parity: ProgressActivityBase delays the "no tasks running" line ~4s in case a
        // just-launched job hasn't registered yet.
        uiHandler.postDelayed({ controller.revealNoTasksIfIdle() }, 4000)
    }

    override fun onPause() {
        super.onPause()
        JobManager.removeWorkListener(workListener)
    }

    private fun onWorkEvent() {
        uiHandler.post { refreshJobs() }
    }

    private fun refreshJobs() {
        val snapshot = ArrayList<ProgressJob>()
        val it = JobManager.iterator()
        while (it.hasNext()) {
            val job = it.next()
            snapshot.add(
                ProgressJob(
                    id = System.identityHashCode(job).toString(),
                    label = job.jobName,
                    percent = job.work,
                    indeterminate = job.work == 0,
                )
            )
        }
        controller.setJobs(snapshot)
    }

    /** className parity with classic [ProgressStatus.onOkay]: RESULT_OK + Intent(ProgressStatus) + finish. */
    private fun onOkay() {
        Log.i(TAG, "CLICKED")
        setResult(Activity.RESULT_OK, Intent(this, ProgressStatus::class.java))
        finish()
    }

    companion object { private const val TAG = "ProgressStatusCompose" }
}
