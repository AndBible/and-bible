package net.bible.sharedui.ios.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.ios.reading.IosReadingExtras
import net.bible.sharedui.strings.keyed.StringTable

/** Drawer actions share the existing presenters and read the current language on each invocation. */
internal class IosReadingExtrasImpl(
    private val helpDialogs: HelpDialogs,
    private val table: () -> StringTable,
    private val scope: CoroutineScope,
    private val textShare: TextShare,
    private val report: suspend (Throwable?) -> Unit,
) : IosReadingExtras {
    override val offeredRows = setOf("helpButton", "appLicence", "bugReport", "tellFriend")

    override fun runRow(id: String) {
        when (id) {
            "helpButton" -> helpDialogs.show(HelpTopic.HELP_AND_TIPS)
            "appLicence" -> helpDialogs.show(HelpTopic.LICENCE)
            "tellFriend" -> table().let { textShare.shareText(IosTellAFriend.message(it), it.text("tell_friend_title")) }
            "bugReport" -> scope.launch { report(null) }
        }
    }

    override fun reportJsError() {
        // Match AndroidLinkPlatform's diagnostic; the existing report flow retains consent and logs.
        scope.launch { report(Exception("Error in webview-js")) }
    }
}
