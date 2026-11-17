@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.host.ports

import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.app.ports.DocumentDialogsCore
import net.bible.sharedui.app.ports.HelpAndTips
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.strings.keyed.StringTable

@kotlin.native.HiddenFromObjC
class HostHelpDialogs(
    private val dialogs: AppDialogController,
    private val table: () -> StringTable,
    private val bundleHtml: (String) -> String,
    private val versionName: () -> String,
) : HelpDialogs {
    override fun tipText(key: String): String = table().text(key)

    override fun versionName(): String = versionName.invoke()

    override fun show(topic: HelpTopic) {
        val core = DocumentDialogsCore(dialogs, table)
        dialogs.post(AppDialogRequest.Localized {
            val t = table()
            when (topic) {
                HelpTopic.WORKSPACES -> core.helpNotice(listOf(DocumentDialogsCore.HelpItem(
                    t.text("help_workspaces_title"), t.text("help_workspaces_text"),
                    TutorialVideos.url(TutorialVideos.Episode.TUTORIAL_07_WORKSPACES), DocsLinks.page("workspaces"),
                )))
                HelpTopic.HELP_AND_TIPS -> core.helpNotice(
                    HelpAndTips.items.map { item ->
                        DocumentDialogsCore.HelpItem(t.text(item.titleKey), t.text(item.textKey), HelpAndTips.videoUrl(item), HelpAndTips.docUrl(item.docPage))
                    },
                    versionMsg = t.fmt("version_text", versionName()),
                    includeSponsor = false,
                )
                HelpTopic.LICENCE -> AppDialogRequest.Message(
                    title = t.text("app_licence_title"), message = bundleHtml("license"),
                    confirmText = t.text("okay"), cancellable = true,
                )
                HelpTopic.READING_PROGRESS -> core.helpDialog(t.text("help"), t.text("help_reading_progress_text"), DocsLinks.page("reading_progress"),
                    TutorialVideos.url(TutorialVideos.Episode.TUTORIAL_15_READING_PLANS_AND_PROGRESS))
                HelpTopic.DOCUMENT_SYNC -> core.helpDialog(t.text("help"), t.text("help_document_sync_text"), DocsLinks.page("document_sync"),
                    TutorialVideos.url(TutorialVideos.Episode.TUTORIAL_18_SYNC_2_MORE_BACKENDS_AND_DOCUMENTS))
                HelpTopic.AI_CONNECTION -> core.helpDialog(t.text("help"), t.text("help_ai_connection_text"), DocsLinks.page("ai", "getting-started"))
            }
        })
    }
}
