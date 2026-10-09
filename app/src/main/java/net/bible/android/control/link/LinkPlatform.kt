package net.bible.android.control.link

import net.bible.sharedcore.nav.NavRoutes

/** The screen-host side of [LinkControl]: what a Strong's "find all occurrences" needs from the current screen. */
interface LinkPlatform {
    /**
     * Run the Strong's search inside the mounted reading view when there is one.
     * @return true when the reading view handled it (nothing more to do), false to fall through to [openRoute].
     */
    fun searchStrongsInReadingView(ref: String, documentInitials: List<String>): Boolean

    /** Open a navigation-graph route (one built by [NavRoutes]) from the current screen. */
    fun openRoute(route: String)
}
