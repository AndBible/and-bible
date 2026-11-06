package net.bible.android.control.activity

/**
 * Sends background errors to the activity list instead of a modal dialog (#4007), on platforms that have the list
 * as the place to look (desktop, iOS). Android keeps its dialogs and only [record]s.
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
     * Records [message] as a failed item (always, so turning the preference on later still shows earlier failures)
     * and returns the toast text to show, or null when the preference is off and the caller must keep today's dialog.
     */
    fun reroute(message: String): String? {
        sync.reportFailure(message)
        return if (enabled()) toastText(message) else null
    }

    /**
     * Android: records [message] for the list when the preference is on, and shows nothing itself; Android's
     * dialogs and system notifications stay as they are.
     */
    fun record(message: String) {
        if (enabled()) sync.reportFailure(message)
    }
}

/**
 * [BackgroundErrorRouter.reroute] for presenters: the toast text when the error went to the list, null when the
 * dialog must be shown. A failing router is logged and counts as "not rerouted", so an error is never lost.
 */
fun ((String) -> String?).rerouteOrNull(message: String): String? =
    try {
        this(message)
    } catch (e: Exception) {
        net.bible.sharedcore.log.Log.w("BackgroundErrorRouter", "Rerouting an error to the activity list failed", e)
        null
    }
