package net.bible.sharedcore.cloud

/** Formats queue state through platform resource lookup, without exposing document labels to notices. */
fun documentSyncText(p: DocumentSyncProgress, label: String?, format: (String, List<Any>) -> String): String {
    fun text(key: String, vararg args: Any) = format("document_sync_$key", args.toList())
    val counts = text("counts", p.processed, p.total)
    if (p.status != DocumentSyncProgressStatus.RUNNING) return text(when (p.status) {
        DocumentSyncProgressStatus.IDLE -> "idle"
        DocumentSyncProgressStatus.FINISHED -> "finished"
        DocumentSyncProgressStatus.FAILED -> "failed"
        DocumentSyncProgressStatus.INCOMPLETE -> "incomplete"
        DocumentSyncProgressStatus.CANCELLED -> "cancelled"
        DocumentSyncProgressStatus.INTERRUPTED -> "interrupted"
        DocumentSyncProgressStatus.RUNNING -> error("unreachable")
    }, counts)
    val operation = p.currentOperation ?: return text("running", counts)
    val verb = text(when (operation) {
        DocumentSyncOperation.PUSH -> "operation_push"
        DocumentSyncOperation.DOWNLOAD -> "operation_download"
        DocumentSyncOperation.REMOVE -> "operation_remove"
        DocumentSyncOperation.PURGE -> "operation_purge"
        DocumentSyncOperation.UNINSTALL -> "operation_uninstall"
    })
    val detail = if (label == null) verb else text("operation_label", verb, label)
    val bytes = p.bytesTransferred
    return when {
        bytes == null -> text("running_detail", counts, detail)
        p.totalBytes != null && bytes >= p.totalBytes -> text("finishing", counts, detail)
        p.totalBytes != null -> text("running_bytes_total", counts, detail, bytes, p.totalBytes)
        else -> text("running_bytes", counts, detail, bytes)
    }
}

fun documentSyncNotificationText(p: DocumentSyncProgress, format: (String, List<Any>) -> String): String =
    documentSyncText(p.copy(currentOperation = null, initials = null, bytesTransferred = null, totalBytes = null), null, format)
