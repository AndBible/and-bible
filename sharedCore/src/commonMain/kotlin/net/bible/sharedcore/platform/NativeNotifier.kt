package net.bible.sharedcore.platform

/**
 * One operating-system notification (#4012). [id] is stable per background activity so a later [NativeNotifier.show]
 * with the same id updates the notification in place. [progress] is 0..100 when known; [indeterminate] means running
 * with no known fraction. [urgent] is for failures that should not be missed.
 */
data class NativeNotification(
    val id: String,
    val title: String,
    val body: String,
    val progress: Int?,
    val indeterminate: Boolean,
    val urgent: Boolean,
)

/**
 * Port to the platform's native notification service. Implementations are bound only where one exists (desktop Linux);
 * all calls are non-blocking and never throw: a missing or broken service degrades to a no-op.
 */
interface NativeNotifier {
    /** True when the service can show a progress bar, so running items may be shown as notifications. */
    val supportsProgress: Boolean

    /** Shows [n], replacing in place any notification already shown with the same id. */
    fun show(n: NativeNotification)

    /** Closes the notification with [id], if shown. */
    fun remove(id: String)

    /** Registers the handler called with the id of a notification the user activated (clicked). */
    fun setOnActivated(handler: (id: String) -> Unit)
}
