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
) {
    private val subscription: Subscription = progress.subscribe(::onStatus)

    fun close() = subscription.cancel()

    private fun onStatus(status: DocumentStatus) {
        val id = "download:${status.id}"
        when (status.documentInstallStatus) {
            DocumentInstallStatus.BEING_INSTALLED ->
                if (center.isRunning(id)) center.update(id, status.percentDone)
                else {
                    center.start(id, ActivityKind.DOWNLOAD, titleOf(status.id), cancel = { JobManager.findJob(Progress.INSTALL_BOOK.replace("%s", status.id))?.cancel() })
                    center.update(id, status.percentDone)
                }
            DocumentInstallStatus.INSTALLED -> center.succeed(id)
            DocumentInstallStatus.ERROR_DOWNLOADING -> {
                val title = titleOf(status.id)
                if (!center.isRunning(id)) center.start(id, ActivityKind.DOWNLOAD, title)
                center.fail(id, failedText(title))
            }
            DocumentInstallStatus.INSTALL_CANCELLED -> center.cancelled(id)
            DocumentInstallStatus.NOT_INSTALLED, DocumentInstallStatus.UPGRADE_AVAILABLE -> Unit
        }
    }
}
