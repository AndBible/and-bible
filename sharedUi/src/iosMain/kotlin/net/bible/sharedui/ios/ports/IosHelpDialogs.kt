@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package net.bible.sharedui.ios.ports

import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.app.ports.DocumentDialogsCore
import net.bible.sharedui.app.ports.HelpAndTips
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.strings.keyed.StringTable
import platform.Foundation.*

@kotlin.native.HiddenFromObjC
class IosHelpDialogs(
    private val dialogs: AppDialogController,
    private val table: () -> StringTable,
    private val bundleHtml: (String) -> String = ::bundledHelpHtml,
) : HelpDialogs {
    override fun show(topic: HelpTopic) {
        val core = DocumentDialogsCore(dialogs, table)
        dialogs.post(AppDialogRequest.Localized {
            val t = table()
            when (topic) {
                HelpTopic.WORKSPACES -> core.helpNotice(listOf(DocumentDialogsCore.HelpItem(
                    t.text("help_workspaces_title"), t.text("help_workspaces_text"),
                    "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mmiq_X6G-vDhoAIq9sDnrIQ", DocsLinks.page("workspaces"),
                )))
                HelpTopic.HELP_AND_TIPS -> core.helpNotice(
                    HelpAndTips.items.map { item ->
                        DocumentDialogsCore.HelpItem(t.text(item.titleKey), t.text(item.textKey), item.videoLink, helpPage(item.docPage))
                    },
                    versionMsg = t.fmt("version_text", NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""),
                    includeSponsor = false,
                )
                HelpTopic.LICENCE -> AppDialogRequest.Message(
                    title = t.text("app_licence_title"), message = bundleHtml("license"),
                    confirmText = t.text("okay"), cancellable = true,
                )
                HelpTopic.READING_PROGRESS -> core.helpDialog(t.text("help"), t.text("help_reading_progress_text"), DocsLinks.page("reading_progress"))
                HelpTopic.DOCUMENT_SYNC -> core.helpDialog(t.text("help"), t.text("help_document_sync_text"), DocsLinks.page("document_sync"))
                HelpTopic.AI_CONNECTION -> core.helpDialog(t.text("help"), t.text("help_ai_connection_text"), DocsLinks.page("ai", "getting-started"))
            }
        })
    }
}

// Literal calls keep the app-to-documentation link scanner authoritative.
private fun helpPage(page: String?): String? = when (page) {
    null -> null
    "navigation" -> DocsLinks.page("navigation")
    "windows" -> DocsLinks.page("windows")
    "bookmarks" -> DocsLinks.page("bookmarks")
    "study_pads" -> DocsLinks.page("study_pads")
    "search" -> DocsLinks.page("search")
    "workspaces" -> DocsLinks.page("workspaces")
    else -> error("Unknown help page: $page")
}

private fun bundledHelpHtml(name: String): String {
    val path = checkNotNull(NSBundle.mainBundle.pathForResource(name, "html")) { "Missing bundled HTML: $name" }
    return checkNotNull(NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)) { "Unreadable bundled HTML: $name" }
}
