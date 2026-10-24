package net.bible.android.view.activity.search

import android.app.Activity
import android.content.Intent
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.sword.epub.isEpub
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.log.Log
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.index.IndexStatus

private const val TAG = "SearchControl"

/** if current document is indexed then go to search else go to download index page
 *
 * @return required Intent
 */
fun searchIntent(document: Book?, activity: Activity): Intent? {
    val indexStatus = document?.indexStatus
    Log.i(TAG, "Index status:$indexStatus")
    return if (indexStatus == IndexStatus.DONE) {
        Log.i(TAG, "Index status is DONE")
        if(document.isEpub) {
            ScreenLauncher.intentFor(activity, Screen.EpubSearch)
        } else
            ScreenLauncher.intentFor(activity, Screen.Search)
    } else if (document?.bookCategory == BookCategory.GENERAL_BOOK) {
        return null
    } else {
        Log.i(TAG, "Index status is NOT DONE")
        // nav-graph slice 5/Task 6: `Screen.SearchIndex`'s MIGRATED route is ARGUMENT-FREE,
        // and an argument-free index route means "index the book I am reading"
        // (NavHostComposeActivity falls back to the current page's document). Here we DO know
        // which document needs indexing -- it is the one this call was asked about, which is
        // not necessarily the active window's -- so the route has to name it. The two branches
        // above stay correct through `ScreenLauncher`: both `Screen.EpubSearch` and
        // `Screen.Search` have genuinely argument-free MIGRATED routes (an empty Find form,
        // and an EPUB form that reads its document from the current page).
        // `document` is NOT smart-cast non-null in this branch (only the DONE branch above is),
        // and a null document here is a real state: classic built a bare `Screen.SearchIndex`
        // intent with no SEARCH_DOCUMENT extra, i.e. exactly the argument-free route that
        // `NavRoutes.searchIndex(searchDocument = null)` produces. Equivalent, not a fallback.
        NavHostComposeActivity.intentFor(activity, NavRoutes.searchIndex(searchDocument = document?.initials))
    }
}

