package net.bible.android.control.activity

import net.bible.service.cloudsync.documents.DocumentSyncProgressTracker
import net.bible.sharedcore.cloud.DocumentSyncProgress
import net.bible.sharedcore.cloud.DocumentSyncProgressStatus
import net.bible.sharedcore.platform.CoreStrings

/** Projects the ordered process-local queue lifecycle independently of screen subscriptions. */
class DocumentSyncActivitySource(
    private val center: ActivityCenter,
    tracker: DocumentSyncProgressTracker,
    private val strings: () -> CoreStrings,
) {
    private var runningId: String? = null
    private val subscription = tracker.observe(::update)

    private fun update(progress: DocumentSyncProgress) {
        if (progress.status == DocumentSyncProgressStatus.IDLE) return
        val id = "document-sync:${progress.sessionId}"
        if (runningId != null && runningId != id) center.cancelled(runningId!!)
        val text = strings()
        if (!center.isRunning(id)) center.start(id, ActivityKind.DOCUMENT_SYNC, text.documentSyncActivityTitle)
        runningId = id
        center.setTitle(id, text.documentSyncActivityTitle)
        center.setMessage(id, text.documentSyncStatus(progress, progress.initials))
        center.setNotificationMessage(id, text.documentSyncNotificationStatus(progress))
        val bytes = progress.bytesTransferred
        val total = progress.totalBytes
        val percent = if (bytes != null && total != null && total > 0) {
            ((bytes.coerceIn(0, total).toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else null
        center.update(id, percent)
        when (progress.status) {
            DocumentSyncProgressStatus.RUNNING -> Unit
            DocumentSyncProgressStatus.FINISHED -> center.succeed(id)
            DocumentSyncProgressStatus.FAILED -> center.cancelled(id)
            DocumentSyncProgressStatus.CANCELLED -> center.interrupt(id, text.documentSyncStatus(progress, null), false)
            DocumentSyncProgressStatus.INCOMPLETE, DocumentSyncProgressStatus.INTERRUPTED ->
                center.interrupt(id, text.documentSyncStatus(progress, null), true)
            DocumentSyncProgressStatus.IDLE -> Unit
        }
        if (progress.status != DocumentSyncProgressStatus.RUNNING) runningId = null
    }

    fun close() = subscription.cancel()
}
