@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.help

import androidx.navigation.NavHostController
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.HelpAndTips
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.help.nav.HelpNavDeps
import net.bible.sharedui.strings.Strings

/** Builds the Help & tips destination's deps. Construction touches no database; everything resolves on use. */
@kotlin.native.HiddenFromObjC
class HelpHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val helpDialogs: HelpDialogs,
    private val links: ExternalLinks,
    private val dialogs: AppDialogController,
) {
    @Suppress("UNUSED_PARAMETER")
    fun navDeps(navController: NavHostController): HelpNavDeps = HelpNavDeps(
        exitHost = { shell.exitHost() },
        setWindowTitle = { shell.setWindowTitle(it) },
        tips = { HelpAndTips.toUi(strings, helpDialogs::tipText) },
        versionText = { strings.versionText(helpDialogs.versionName()) },
        onOpenUrl = { links.confirmAndOpen(it, dialogs) { strings } },
    )
}
