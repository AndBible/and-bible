package net.bible.sharedcore.cloud

enum class DocumentSyncProgressStatus { IDLE, RUNNING, FINISHED, FAILED, INCOMPLETE, CANCELLED, INTERRUPTED }
enum class DocumentSyncOperation { PUSH, DOWNLOAD, REMOVE, PURGE, UNINSTALL }
enum class DocumentSyncInterruptionReason { USER_STOP, BACKGROUND_EXPIRED, SCOPE_CANCELLED }

data class DocumentSyncProgress(
    val sessionId: Long = 0,
    val revision: Long = 0,
    val status: DocumentSyncProgressStatus = DocumentSyncProgressStatus.IDLE,
    val currentOperation: DocumentSyncOperation? = null,
    val initials: String? = null,
    val processed: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
    val deferred: Int = 0,
    val bytesTransferred: Long? = null,
    val totalBytes: Long? = null,
    val interruptionReason: DocumentSyncInterruptionReason? = null,
)
