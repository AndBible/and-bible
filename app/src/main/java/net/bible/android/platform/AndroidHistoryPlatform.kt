package net.bible.android.platform

import android.content.Intent
import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.base.AndBibleActivity
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.sharedcore.nav.NavRoutes
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryPlatform
import net.bible.service.history.IntentHistoryItem
import net.bible.sharedcore.reading.ReadingViewVisibility

/** Android side of [HistoryPlatform]: the Activity / Intent decisions of the history list. */
class AndroidHistoryPlatform : HistoryPlatform {
    override fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem? {
        if (screenToken != null) {
            val intent = (screenToken as? Intent) ?: error("screenToken must be an Intent")
            val target = routeIntentFor(intent) ?: intent
            return IntentHistoryItem(intent.getStringExtra("description") ?: "-", target, window)
        }
        val currentActivity = CurrentActivityHolder.currentActivity
        if (currentActivity is AndBibleActivity && currentActivity.isIntegrateWithHistoryManager) {
            return IntentHistoryItem(currentActivity.title, currentActivity.intentForHistoryList, window)
        }
        return null
    }

    /**
     * The nav-host intent that re-opens the screen a component-less RESULT [intent] was picked from, or null when
     * [intent] already names a component or is not one this platform knows.
     *
     * A bookmark pick records its result intent as the history entry (classic `BookmarksComposeActivity` built it
     * with `Intent(this, Bookmarks::class.java)`, so Back re-opened the list, issue #3012). The Compose port
     * dropped the component, so the entry resolved to nothing and `revertTo` threw `ActivityNotFoundException`.
     * Screens now are routes of [NavHostComposeActivity], which is also how reading-plan and search entries are
     * recorded (`intentForHistoryList`), so the entry is mapped to the bookmarks list route (same label filter).
     * The entry's title is read from the original intent by the caller. The result intent itself is left untouched: it is still delivered to the reading view.
     */
    private fun routeIntentFor(intent: Intent): Intent? {
        if (intent.component != null) return null
        if (intent.getStringExtra(ActivityResultKind.EXTRA) != ActivityResultKind.Bookmarks.name) return null
        val labelNo = intent.getIntExtra(BookmarkControl.LABEL_NO_EXTRA, -1).takeIf { it >= 0 }
        val context = CurrentActivityHolder.currentActivity ?: return null
        return NavHostComposeActivity.intentFor(context, NavRoutes.bookmarks(labelNo))
    }

    override fun isOnReadingScreen(): Boolean = ReadingViewVisibility.isVisible

    override fun leaveCurrentScreen() {
        CurrentActivityHolder.currentActivity?.leaveCurrentScreen()
    }
}
