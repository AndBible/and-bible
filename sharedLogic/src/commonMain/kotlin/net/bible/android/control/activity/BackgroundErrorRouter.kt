package net.bible.android.control.activity

import net.bible.android.control.event.UserMessage

/**
 * Decides where an error notification goes (#4007): every failure is recorded in the activity list, and a failure of
 * background work is additionally rerouted from the modal dialog to a toast on platforms that have the list as the
 * place to look (desktop, iOS). A foreground failure (something the user just asked for) keeps its dialog. Android
 * keeps its system notifications and only calls [route] for the recording.
 *
 * @param enabled whether the activity menu preference is on; read per error, so toggling takes effect at once
 * @param toastText the short toast shown instead of the dialog (for example "<message> - see menu")
 */
class BackgroundErrorRouter(
    private val enabled: () -> Boolean,
    private val sync: SyncActivitySource,
    private val toastText: (String) -> String,
) {
    /**
     * Records [message] as a failed row of [kind], always (so turning the preference on later still shows earlier
     * failures). Returns the toast text when [background] is true and the preference is on; null means the caller must
     * show the dialog.
     */
    fun route(message: String, background: Boolean, kind: ActivityKind, reportable: Boolean = false, cause: Throwable? = null): String? {
        sync.reportFailure(kind, message, background, reportable, cause.takeIf { reportable })
        return if (background && enabled()) toastText(message) else null
    }
}

/**
 * [BackgroundErrorRouter.route] for presenters: the toast text when the error went to the list only, null when the
 * dialog must be shown. A failing router is logged and counts as "not rerouted", so an error is never lost.
 */
fun ((UserMessage.ErrorNotification) -> String?).rerouteOrNull(message: UserMessage.ErrorNotification): String? =
    try {
        this(message)
    } catch (e: Exception) {
        net.bible.sharedcore.log.Log.w("BackgroundErrorRouter", "Rerouting an error to the activity list failed", e)
        null
    }
