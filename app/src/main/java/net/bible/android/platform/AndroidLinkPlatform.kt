package net.bible.android.platform

import net.bible.android.control.link.LinkPlatform
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.ReadingHostActivity

/** Android side of [LinkPlatform]: the foreground Activity decides where the search lands. */
class AndroidLinkPlatform : LinkPlatform {
    override fun searchStrongsInReadingView(ref: String, documentInitials: List<String>): Boolean {
        val activity = CurrentActivityHolder.currentActivity!!
        // T8b fix round 1 (I1): was `(activity as? MainBibleActivity)`, which is always null once
        // NavHostComposeActivity hosts the reading view -- "find all occurrences" had silently
        // stopped searching in place and left the reading view for the search cluster instead.
        return (activity as? ReadingHostActivity)?.readingCommands
            ?.composeSearchStrongsIfHosted(ref, documentInitials) == true
    }

    override fun openRoute(route: String) {
        val activity = CurrentActivityHolder.currentActivity!!
        activity.startActivity(NavHostComposeActivity.intentFor(activity, route))
    }
}
