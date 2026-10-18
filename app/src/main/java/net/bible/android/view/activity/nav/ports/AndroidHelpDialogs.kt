package net.bible.android.view.activity.nav.ports

import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic

/** Each lifting task adds its [HelpTopic] constants together with the matching `CommonUtils.showHelp*` call here. */
class AndroidHelpDialogs(private val activity: ActivityBase) : HelpDialogs {
    override fun show(topic: HelpTopic) {
        when (topic) {
            HelpTopic.READING_PROGRESS -> CommonUtils.showHelpDialog(
                activity = activity,
                titleResId = R.string.help,
                messageResId = R.string.help_reading_progress_text,
                helpUrl = DocsLinks.page("reading_progress"),
            )
            HelpTopic.DOCUMENT_SYNC -> CommonUtils.showHelpDialog(
                activity = activity,
                titleResId = R.string.help,
                messageResId = R.string.help_document_sync_text,
                helpUrl = DocsLinks.page("document_sync"),
            )
            HelpTopic.AI_CONNECTION -> CommonUtils.showHelpDialog(
                activity = activity,
                titleResId = R.string.help,
                messageResId = R.string.help_ai_connection_text,
                helpUrl = DocsLinks.page("ai", "getting-started"),
            )
            HelpTopic.WORKSPACES -> CommonUtils.showHelp(activity, listOf(R.string.help_workspaces_title))
        }
    }
}
