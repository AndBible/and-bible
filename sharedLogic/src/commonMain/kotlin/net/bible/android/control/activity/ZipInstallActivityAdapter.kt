package net.bible.android.control.activity

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import net.bible.sharedcore.log.Log
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.InstallPhase

/**
 * Shows "install from zip" jobs (`InstallServiceController`, which reaches neither `JobManager` nor `DownloadControl`)
 * as INSTALL items in the [ActivityCenter] (#4007). The host feeds it the controller's job list ([onJobs]) and its
 * terminal events ([onTerminal]); it adds no UI of its own, so platform notifications stay as they are.
 *
 * Calls may come from any thread. A snapshot arriving after a job's terminal event is ignored, so a finished job
 * is never shown as running again. Every call is guarded: the activity list must never break an install.
 *
 * @param center resolved per call (the Koin graph can be replaced); null when there is no activity center
 * @param cancel cancels the install job with the given id
 */
class ZipInstallActivityAdapter(
    private val center: () -> ActivityCenter?,
    private val cancel: (String) -> Unit,
) {
    private val lock = SynchronizedObject()
    private val names = HashMap<String, String>()
    private val finished = HashSet<String>()

    /** One cancel action per job, so equal snapshots give equal items (the center's StateFlow then drops them). */
    private val cancelActions = HashMap<String, () -> Unit>()

    /**
     * The controller's current jobs; each unfinished one is started or updated. Ids of finished jobs that are no
     * longer in [jobs] are forgotten. The lock spans the check and the [ActivityCenter] call, so a terminal event
     * cannot slip in between and leave a RUNNING row behind.
     */
    fun onJobs(jobs: List<InstallJobState>) = guarded {
        val c = center() ?: return@guarded
        synchronized(lock) {
            finished.retainAll(jobs.map { it.jobId }.toSet())
            cancelActions.keys.retainAll(jobs.map { it.jobId }.toSet())
            for (job in jobs) {
                if (job.jobId in finished) continue
                names[job.jobId] = job.displayName ?: job.jobId
                val progress = when (val phase = job.phase) {
                    is InstallPhase.Acquiring -> phase.percent
                    is InstallPhase.Committing -> phase.percent
                    else -> null
                }
                c.startOrUpdate(itemId(job.jobId), ActivityKind.INSTALL, job.displayName ?: job.jobId, progress,
                    cancelActions.getOrPut(job.jobId) { { cancel(job.jobId) } })
            }
        }
    }

    /**
     * A job reached its terminal [phase]. [message] is the user-visible text for a failure (the same text the toast shows);
     * a failed row reads "<name>: <message>".
     */
    fun onTerminal(jobId: String, phase: InstallPhase, message: String) = guarded {
        val c = center() ?: return@guarded
        synchronized(lock) {
            finished += jobId
            cancelActions.remove(jobId)
            val name = names.remove(jobId) ?: jobId
            val id = itemId(jobId)
            when (phase) {
                is InstallPhase.Done -> c.succeed(id)
                is InstallPhase.Cancelled -> c.cancelled(id)
                is InstallPhase.Error -> {
                    c.startOrFail(id, ActivityKind.INSTALL, name, if (message.isBlank()) name else "$name: $message",
                        reportable = true, cause = phase.cause)
                }
                else -> Unit
            }
        }
    }

    private fun itemId(jobId: String) = "install-zip:$jobId"

    private inline fun guarded(block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.w("ZipInstallActivity", "Recording a zip install in the activity list failed", e) }
    }
}
