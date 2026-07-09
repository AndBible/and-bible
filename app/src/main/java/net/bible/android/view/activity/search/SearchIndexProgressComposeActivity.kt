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
package net.bible.android.view.activity.search

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.android.activity.R
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.pause
import net.bible.service.device.ScreenSettings
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.isEpub
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchIndexProgressController
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.search.SearchIndexProgressScreen
import net.bible.sharedui.theme.AbTheme
import org.apache.commons.lang3.StringUtils
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.index.IndexStatus

/** Compose host for the search-index progress screen — the new-path twin of [SearchIndexProgressStatus]. */
class SearchIndexProgressComposeActivity : ActivityBase() {
    private var documentBeingIndexed: Book? = null
    private var workListener: WorkListener? = null
    private val finishedJobs = HashSet<Progress>()
    private val uiHandler = Handler(Looper.getMainLooper())

    private val controller by lazy { SearchIndexProgressController(onHide = { finish() }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val docInitials = intent.getStringExtra(SearchControl.SEARCH_DOCUMENT)
        documentBeingIndexed = SwordDocumentFacade.getDocumentByInitials(docInitials)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val jobs by controller.jobs.collectAsState()
                    val noTasks by controller.noTasks.collectAsState()
                    val error by controller.error.collectAsState()
                    SearchIndexProgressScreen(
                        title = getString(R.string.search),
                        jobs = jobs,
                        noTasks = noTasks,
                        error = error,
                        onHide = controller::hide,
                        onDismissError = controller::dismissError,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshJobs()
        workListener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) = onWorkEvent(ev)
            override fun workStateChanged(ev: WorkEvent) = onWorkEvent(ev)
        }
        JobManager.addWorkListener(workListener)
    }

    override fun onPause() {
        super.onPause()
        JobManager.removeWorkListener(workListener)
    }

    private fun onWorkEvent(ev: WorkEvent) {
        uiHandler.post {
            refreshJobs()
            val prog = ev.job
            if (prog.isFinished && finishedJobs.add(prog)) {
                jobFinished(prog)
            }
        }
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

    private val isAllJobsFinished: Boolean
        get() {
            val it = JobManager.iterator()
            while (it.hasNext()) if (!it.next().isFinished) return false
            return true
        }

    private fun jobFinished(jobJustFinished: Progress) {
        // give the document up to 12 secs to reload - the Progress declares itself finished before the index status has been changed
        var attempts = 0
        while ((documentBeingIndexed == null || IndexStatus.DONE != documentBeingIndexed?.indexStatus) && attempts++ < 6) {
            pause(2)
        }
        if (IndexStatus.DONE == documentBeingIndexed?.indexStatus) {
            Log.i(TAG, "Index created")
            val newIntent: Intent
            if (StringUtils.isNotEmpty(intent.getStringExtra(SearchControl.SEARCH_TEXT))) {
                newIntent = if (documentBeingIndexed?.isEpub == true)
                    Intent(this, EpubSearchResults::class.java)
                else
                    Intent(this, SearchResults::class.java)
                newIntent.putExtras(intent.extras!!)
            } else {
                newIntent = if (documentBeingIndexed?.isEpub == true)
                    Intent(this, EpubSearch::class.java)
                else
                    Intent(this, Search::class.java)
            }
            startActivity(newIntent)
            finish()
        } else {
            if (isAllJobsFinished) {
                Log.e(TAG, "Index finished but document's index is invalid")
                controller.showError()
            }
        }
    }

    companion object { private const val TAG = "SearchIndexProgCompose" }
}
