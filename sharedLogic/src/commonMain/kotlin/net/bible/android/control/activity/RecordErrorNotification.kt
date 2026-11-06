package net.bible.android.control.activity

import net.bible.android.control.event.UserMessage
import net.bible.sharedcore.log.Log

/**
 * Records [message] as a failed row of its own [UserMessage.ErrorNotification.kind] in the activity list (#4007), for
 * hosts that keep their own system notifications (Android) and so never reroute. It records whatever the activity menu
 * preference says, so turning the menu on later shows earlier failures. [textOf] resolves a resource-id-only message.
 * A [source] answering null (no activity center) records nothing; a failure here, including resolving the source, is logged and never propagates, so the list
 * cannot break error reporting.
 */
fun recordErrorNotification(source: () -> SyncActivitySource?, message: UserMessage.ErrorNotification, textOf: (Int?) -> String) {
    try {
        val target = source() ?: return
        target.reportFailure(message.kind, message.text ?: textOf(message.textId))
    } catch (e: Exception) {
        Log.w("ActivityList", "Recording an error in the activity list failed", e)
    }
}
