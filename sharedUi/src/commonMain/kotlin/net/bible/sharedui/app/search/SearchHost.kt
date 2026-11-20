@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedui.app.HostShell
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener

/**
 * Host baggage of the Progress status screen: it lists the JSword jobs (index builds, downloads) that
 * `JobManager` is running. The full-screen search routes that used to share this host (the index prompt, its
 * progress screen and the EPUB search form, removed in #4170) are gone; the reading view's search sheet owns
 * index building and EPUB search now.
 */
@kotlin.native.HiddenFromObjC
class SearchHost(
    private val shell: HostShell,
) {
    /** The unfinished jobs `JobManager` knows, as the rows the Progress status screen shows. */
    private fun jobSnapshot(): List<ProgressJob> {
        val snapshot = ArrayList<ProgressJob>()
        val it = JobManager.iterator()
        while (it.hasNext()) {
            val job = it.next()
            if (job.isFinished) continue
            snapshot.add(
                ProgressJob(
                    id = job.hashCode().toString(),
                    label = job.jobName,
                    percent = job.work,
                    indeterminate = job.work == 0,
                )
            )
        }
        return snapshot
    }

    /**
     * `PROGRESS_STATUS_PATTERN`'s `observeJobs`: classic `ProgressStatusComposeActivity`'s `onResume`/`onPause`
     * PAIR (`:82-98`) as one call: the initial refresh, then `JobManager.addWorkListener`, and the returned
     * lambda is `JobManager.removeWorkListener`. There is no "finished" callback, only a job-list refresh.
     */
    fun observeProgressStatusJobs(onJobs: (List<ProgressJob>) -> Unit): () -> Unit {
        onJobs(jobSnapshot())
        val listener = object : WorkListener {
            override fun workProgressed(ev: WorkEvent) {
                shell.hostScope.launch(Dispatchers.Main) { onJobs(jobSnapshot()) }
            }
            override fun workStateChanged(ev: WorkEvent) {
                shell.hostScope.launch(Dispatchers.Main) { onJobs(jobSnapshot()) }
            }
        }
        JobManager.addWorkListener(listener)
        return { JobManager.removeWorkListener(listener) }
    }
}
