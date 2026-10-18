package net.bible.android.view.activity.nav.ports

import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.bookmark.BookmarkCsvFlow
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.nav.subscribeToBookmarkSync
import net.bible.service.common.labelsAndBookmarksPlaylist
import net.bible.service.common.studyPadsVideo
import net.bible.sharedui.app.bookmark.BookmarkDocumentsRecreation
import net.bible.sharedui.app.bookmark.BookmarkHostPlatform
import org.koin.java.KoinJavaComponent.get

/**
 * [BookmarkHostPlatform] over the nav host: the bookmark cluster's Activity-bound and `:app`-only seams, moved here
 * unchanged from `NavHostComposeActivity`'s bookmark baggage.
 */
class AndroidBookmarkHostPlatform(private val activity: NavHostComposeActivity) : BookmarkHostPlatform {
    private val bookmarkControl: BookmarkControl get() = get(BookmarkControl::class.java)

    override fun subscribeBookmarkSync(onChanged: () -> Unit): () -> Unit = subscribeToBookmarkSync(onChanged)

    override suspend fun exportBookmarksCsv(bookmarks: List<BookmarkEntities.BibleBookmarkWithNotes>) {
        BookmarkCsvFlow(bookmarkControl).exportBookmarksToCSV(activity, bookmarks)
    }

    override suspend fun importBookmarksCsv() {
        BookmarkCsvFlow(bookmarkControl).importBookmarksFromCSV(activity)
    }

    override fun helpReadMoreUrl(studyPad: Boolean): String =
        if (studyPad) studyPadsVideo else labelsAndBookmarksPlaylist

    override fun documentsRecreation(): BookmarkDocumentsRecreation = activity.bookmarkDocumentsRecreation()
}
