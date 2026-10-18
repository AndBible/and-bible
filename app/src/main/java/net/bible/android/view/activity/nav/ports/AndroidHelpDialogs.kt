package net.bible.android.view.activity.nav.ports

import android.app.Activity
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic

/** Each lifting task adds its [HelpTopic] constants together with the matching `CommonUtils.showHelp*` call here. */
class AndroidHelpDialogs(private val activity: Activity) : HelpDialogs {
    override fun show(topic: HelpTopic) {
        when (topic) {
            HelpTopic.READING_PROGRESS -> CommonUtils.showHelpDialog(
                activity = activity,
                titleResId = R.string.help,
                messageResId = R.string.help_reading_progress_text,
                helpUrl = DocsLinks.page("reading_progress"),
            )
        }
    }
}
