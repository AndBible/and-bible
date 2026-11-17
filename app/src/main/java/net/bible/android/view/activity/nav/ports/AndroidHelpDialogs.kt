package net.bible.android.view.activity.nav.ports

import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.docs.TutorialVideos
import net.bible.sharedui.app.ports.HelpAndTips
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic

/** Each lifting task adds its [HelpTopic] constants together with the matching `CommonUtils.showHelp*` call here. */
class AndroidHelpDialogs(private val activity: ActivityBase) : HelpDialogs {
    override fun tipText(key: String): String = activity.getString(CommonUtils.helpTipStringIds.getValue(key))

    override fun appLanguage(): String = CommonUtils.appLanguageTag(activity)

    override fun versionName(): String = CommonUtils.applicationVersionName

    override fun show(topic: HelpTopic) {
        when (topic) {
            HelpTopic.READING_PROGRESS -> CommonUtils.showHelpDialog(
                activity = activity,
                titleResId = R.string.help,
                messageResId = R.string.help_reading_progress_text,
                helpUrl = DocsLinks.page("reading_progress"),
                videoEpisode = TutorialVideos.Episode.TUTORIAL_15_READING_PLANS_AND_PROGRESS,
            )
            HelpTopic.DOCUMENT_SYNC -> CommonUtils.showHelpDialog(
                activity = activity,
                titleResId = R.string.help,
                messageResId = R.string.help_document_sync_text,
                helpUrl = DocsLinks.page("document_sync"),
                videoEpisode = TutorialVideos.Episode.TUTORIAL_18_SYNC_2_MORE_BACKENDS_AND_DOCUMENTS,
            )
            HelpTopic.AI_CONNECTION -> CommonUtils.showHelpDialog(
                activity = activity,
                titleResId = R.string.help,
                messageResId = R.string.help_ai_connection_text,
                helpUrl = DocsLinks.page("ai", "getting-started"),
            )
            HelpTopic.WORKSPACES -> CommonUtils.showTipDialog(HelpAndTips.items.first { it.titleKey == "help_workspaces_title" })
            HelpTopic.LICENCE -> CommonUtils.showLicence(activity)
        }
    }
}
