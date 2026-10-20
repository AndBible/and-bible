@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.ios.ports

import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedui.app.ports.DocumentDialogsCore
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.strings.keyed.StringTable

@kotlin.native.HiddenFromObjC
class IosHelpDialogs(private val dialogs: AppDialogController, private val table: () -> StringTable) : HelpDialogs {
    override fun show(topic: HelpTopic) {
        val core = DocumentDialogsCore(dialogs, table)
        dialogs.post(AppDialogRequest.Localized {
            val t = table()
            when (topic) {
                HelpTopic.WORKSPACES -> core.helpNotice(listOf(DocumentDialogsCore.HelpItem(
                    t.text("help_workspaces_title"), t.text("help_workspaces_text"),
                    "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mmiq_X6G-vDhoAIq9sDnrIQ", DocsLinks.page("workspaces"),
                )))
                else -> {
                    val (key, page) = when (topic) {
                        HelpTopic.READING_PROGRESS -> "help_reading_progress_text" to "reading_progress"
                        HelpTopic.DOCUMENT_SYNC -> "help_document_sync_text" to "document_sync"
                        HelpTopic.AI_CONNECTION -> "help_ai_connection_text" to "ai"
                        else -> error("Handled workspace help")
                    }
                    core.helpDialog(t.text("help"), t.text(key), if (topic == HelpTopic.AI_CONNECTION) DocsLinks.page(page, "getting-started") else DocsLinks.page(page))
                }
            }
        })
    }
}
