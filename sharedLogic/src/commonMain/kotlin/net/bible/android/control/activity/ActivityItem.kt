package net.bible.android.control.activity

/** What kind of background work an [ActivityItem] represents. */
enum class ActivityKind { DOWNLOAD, INSTALL, INDEX, SYNC, OTHER, AI_AGENT }

/** Lifecycle state of an [ActivityItem]. [CANCELLED] items are never shown; they are dropped at once. */
enum class ActivityState { RUNNING, SUCCEEDED, FAILED, CANCELLED }

/**
 * One row of the activity center.
 *
 * @param progress percent 0..100, or null when indeterminate
 * @param cancel action to cancel the work, or null when it cannot be cancelled
 * @param startedAt clock value (ms) when the work started
 * @param finishedAt clock value (ms) when it finished, null while running
 * @param read false for a failure the user has not yet acknowledged
 */
data class ActivityItem(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val state: ActivityState,
    val progress: Int?,
    val message: String?,
    val cancel: (() -> Unit)?,
    val startedAt: Long,
    val finishedAt: Long?,
    val read: Boolean,
    val backgroundFailure: Boolean = true,
    val failureOccurrence: Long = 0,
    val reportable: Boolean = false,
    val cause: Throwable? = null,
    val attention: Boolean = false,
)
