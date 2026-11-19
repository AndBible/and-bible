@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.host.ports

import net.bible.sharedcore.docs.DocsLink
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.app.ports.DocumentDialogsCore
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

    override fun guideTitle(key: String): String = table().text(key)

    override fun appLanguage(): String = table().tag

    override fun versionName(): String = versionName.invoke()

    override fun show(topic: HelpTopic) {
        val core = DocumentDialogsCore(dialogs, table)
        dialogs.post(AppDialogRequest.Localized {
            val t = table()
            when (topic) {
                HelpTopic.WORKSPACES -> core.helpNotice(listOf(DocumentDialogsCore.HelpItem(
                    t.text("help_workspaces_title"), t.text("help_workspaces_text"), DocsLink.WORKSPACES,
                )))
                HelpTopic.LICENCE -> AppDialogRequest.Message(
                    title = t.text("app_licence_title"), message = bundleHtml("license"),
                    confirmText = t.text("okay"), cancellable = true,
                )
                HelpTopic.READING_PROGRESS -> core.helpDialog(t.text("help"), t.text("help_reading_progress_text"), DocsLink.READING_PROGRESS)
                HelpTopic.DOCUMENT_SYNC -> core.helpDialog(t.text("help"), t.text("help_document_sync_text"), DocsLink.DOCUMENT_SYNC)
                HelpTopic.AI_CONNECTION -> core.helpDialog(t.text("help"), t.text("help_ai_connection_text"), DocsLink.AI_GETTING_STARTED)
            }
        })
    }
}
