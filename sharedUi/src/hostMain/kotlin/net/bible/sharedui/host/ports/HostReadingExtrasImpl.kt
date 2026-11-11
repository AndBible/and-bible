package net.bible.sharedui.host.ports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.host.reading.HostReadingExtras
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedui.app.recommend.RecommendApp
import net.bible.sharedui.strings.Strings

/** Drawer actions share the existing presenters and read the current language on each invocation; "tellFriend" is the shared [RecommendApp] flow. */
internal class HostReadingExtrasImpl(
    private val helpDialogs: HelpDialogs,
    private val strings: () -> Strings,
    private val ask: suspend (AppDialogRequest) -> AppDialogResult,
    private val scope: CoroutineScope,
    private val textShare: TextShare,
    private val report: suspend (Throwable?) -> Unit,
) : HostReadingExtras {
    override val offeredRows = setOf("helpButton", "appLicence", "bugReport", "tellFriend")

    override fun runRow(id: String) {
        when (id) {
            "helpButton" -> helpDialogs.show(HelpTopic.HELP_AND_TIPS)
            "appLicence" -> helpDialogs.show(HelpTopic.LICENCE)
            "tellFriend" -> scope.launch { RecommendApp.run(strings(), ask, textShare::shareText) }
            "bugReport" -> scope.launch { report(null) }
        }
    }

    override fun reportJsError() {
        // Match AndroidLinkPlatform's diagnostic; the existing report flow retains consent and logs.
        scope.launch { report(Exception("Error in webview-js")) }
    }
}
