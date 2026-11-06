package net.bible.android.control.activity

import net.bible.android.control.download.DocumentStatus
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.Subscription
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress

/**
 * Feeds document downloads and installs into the [ActivityCenter]; the canonical source for them
 * (the matching `INSTALL_BOOK-` JSword jobs are skipped by [JobActivitySource]).
 *
 * Item id is `download:<docId>`. Events arrive on whatever thread `DownloadControl` emits on; that is fine,
 * [ActivityCenter] is thread-safe. A new BEING_INSTALLED after a failure replaces the failed row (a retry).
 *
 * @param titleOf the user-visible document name for a repository document id
 * @param failedText the failure message for a document title
 */
class DownloadActivitySource(
    private val center: ActivityCenter,
    progress: Events<DocumentStatus>,
    private val titleOf: (docId: String) -> String,
    private val failedText: (title: String) -> String,
    /** Takes the download progress feed (it only flows while something monitors it); undone by [close]. */
    private val startMonitoring: () -> Unit = {},
    private val stopMonitoring: () -> Unit = {},
    /** Offers cancel for a document only while JSword still allows it. */
    private val cancelFor: (docId: String) -> (() -> Unit)? = ::jswordCancelFor,
) {
    private val subscription: Subscription
    private var closed = false

    /** One cancel action per document, so repeated progress events compare equal (see [JobActivitySource]). */
    private val cancelActions = HashMap<String, () -> Unit>()

    init {
        startMonitoring()
        subscription = progress.subscribe(::onStatus)
    }

    /** Stops listening and releases the monitoring reference taken at construction. Idempotent. */
    fun close() {
        if (closed) return
        closed = true
        subscription.cancel()
        stopMonitoring()
    }

    /** The cached action while [cancelFor] still offers cancel (cancelability can change), else `null`. */
    private fun stableCancel(docId: String): (() -> Unit)? {
        if (cancelFor(docId) == null) return null
        return cancelActions.getOrPut(docId) { { cancelFor(docId)?.invoke() } }
    }

    private fun onStatus(status: DocumentStatus) {
        val id = "download:${status.id}"
        when (status.documentInstallStatus) {
            DocumentInstallStatus.BEING_INSTALLED ->
                center.startOrUpdate(id, ActivityKind.DOWNLOAD, titleOf(status.id), status.percentDone, stableCancel(status.id))
            DocumentInstallStatus.INSTALLED -> { cancelActions.remove(status.id); center.succeed(id) }
            DocumentInstallStatus.ERROR_DOWNLOADING -> {
                cancelActions.remove(status.id)
                val title = titleOf(status.id)
                if (!center.isRunning(id)) center.start(id, ActivityKind.DOWNLOAD, title)
                center.fail(id, failedText(title))
            }
            DocumentInstallStatus.INSTALL_CANCELLED -> { cancelActions.remove(status.id); center.cancelled(id) }
            DocumentInstallStatus.NOT_INSTALLED, DocumentInstallStatus.UPGRADE_AVAILABLE -> Unit
        }
    }
}

internal fun jswordCancelFor(docId: String): (() -> Unit)? {
    val job = JobManager.findJob(Progress.INSTALL_BOOK.replace("%s", docId)) ?: return null
    return if (job.isCancelable) ({ JobManager.findJob(Progress.INSTALL_BOOK.replace("%s", docId))?.cancel() }) else null
}
