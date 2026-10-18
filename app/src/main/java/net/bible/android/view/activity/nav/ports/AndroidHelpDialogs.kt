package net.bible.android.view.activity.nav.ports

import android.app.Activity
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic

/** Each lifting task adds its [HelpTopic] constants together with the matching `CommonUtils.showHelp*` call here. */
class AndroidHelpDialogs(@Suppress("unused") private val activity: Activity) : HelpDialogs {
    override fun show(topic: HelpTopic) {
        // No topic exists yet, so this cannot be reached; the first lifting task replaces it with a `when (topic)`.
        error("No help topic is lifted yet: $topic")
    }
}
