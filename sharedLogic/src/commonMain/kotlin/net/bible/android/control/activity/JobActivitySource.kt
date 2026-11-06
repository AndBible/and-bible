package net.bible.android.control.activity

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import net.bible.service.sword.epub.EPUB_INDEX_JOB_PREFIX
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.ProgressMode
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener

/**
 * True for the JSword jobs that build or download a search index (SWORD, downloaded index, EPUB). The single
 * rule: sharedUi's `Progress.isSearchIndexJob()` delegates here.
 */
fun Progress.isIndexJob(): Boolean =
    jobID.startsWith(Progress.CREATE_INDEX.substringBefore("%s")) ||
        jobID.startsWith(Progress.DOWNLOAD_SEARCH_INDEX.substringBefore("%s")) ||
        jobID.startsWith(EPUB_INDEX_JOB_PREFIX)

/** True for the `INSTALL_BOOK-<docId>` jobs that `DownloadQueue`/`DownloadManager` create; [DownloadActivitySource] owns those. */
fun Progress.isDownloadInstallJob(): Boolean = jobID.startsWith(Progress.INSTALL_BOOK.substringBefore("%s"))

/**
 * Shows JSword [JobManager] jobs (search indexing, other long jobs) in the [ActivityCenter] as `job:<jobID>`.
 *
 * Jobs for which [isDownloadJob] is true are skipped (a download must not show twice). Cancel is offered only
 * while the job is cancelable (re-checked on every progress event). JSword reports a cancel like a normal finish, so
 * a cancel requested through the row is remembered and recorded as cancelled; a cancel from elsewhere looks
 * like a success. Listener callbacks may come from any thread; [ActivityCenter] is thread-safe.
 */
class JobActivitySource(
    private val center: ActivityCenter,
    private val isDownloadJob: (Progress) -> Boolean = Progress::isDownloadInstallJob,
    private val kindOf: (Progress) -> ActivityKind = { if (it.isIndexJob()) ActivityKind.INDEX else ActivityKind.OTHER },
) {
    private val lock = SynchronizedObject()
    private val cancelRequested = HashSet<String>()

    /** The latest [Progress] per item id, read by the cached cancel action at call time. */
    private val jobs = HashMap<String, Progress>()

    /**
     * One cancel action per item id, so successive progress events carry an equal [ActivityItem] and the
     * center's StateFlow drops the emission (a fresh lambda per event made every event look like a change).
     */
    private val cancelActions = HashMap<String, () -> Unit>()

    private val listener = object : WorkListener {
        override fun workProgressed(ev: WorkEvent) = onJob(ev.job)
        override fun workStateChanged(ev: WorkEvent) = onJob(ev.job)
    }

    /** Starts listening and picks up jobs that are already running. */
    fun attach() {
        JobManager.addWorkListener(listener)
        val it = JobManager.iterator()
        while (it.hasNext()) onJob(it.next())
    }

    fun detach() = JobManager.removeWorkListener(listener)

    private fun onJob(job: Progress) {
        if (isDownloadJob(job)) return
        val id = "job:${job.jobID}"
        if (job.isFinished) {
            val cancelled = synchronized(lock) {
                jobs.remove(id)
                cancelActions.remove(id)
                cancelRequested.remove(id)
            }
            if (cancelled) center.cancelled(id) else if (center.isRunning(id)) center.succeed(id)
            return
        }
        val percent = if (job.progressMode == ProgressMode.UNKNOWN) null else job.work.coerceIn(0, 100)
        // Re-read cancelability on every event (JobManager listeners see progress events, not state changes).
        val cancel = if (job.isCancelable) cancelActionFor(id, job) else null
        center.startOrUpdate(id, kindOf(job), job.jobName, percent, cancel)
    }

    private fun cancelActionFor(id: String, job: Progress): () -> Unit = synchronized(lock) {
        jobs[id] = job
        cancelActions.getOrPut(id) {
            {
                val current = synchronized(lock) { jobs[id] }
                if (current != null && !current.isFinished) {
                    synchronized(lock) { cancelRequested.add(id) }
                    current.cancel()
                    // A cancel that did not end the job was ineffective: a later normal finish is a success.
                    if (!current.isFinished) synchronized(lock) { cancelRequested.remove(id) }
                }
            }
        }
    }
}
