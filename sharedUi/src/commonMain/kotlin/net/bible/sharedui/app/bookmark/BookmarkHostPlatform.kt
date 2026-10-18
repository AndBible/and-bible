package net.bible.sharedui.app.bookmark

import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.bookmark.CloudFiltersValue
import net.bible.android.view.activity.bookmark.MyDocumentPagesValue
import net.bible.android.view.activity.bookmark.MyDocumentsSearchValue

/**
 * The parts of [net.bible.android.view.activity.bookmark.BookmarkRecreationState] that belong to the My Documents
 * screens. Their sessions live in `MyDocumentsHost`; the Android platform reads them from there.
 * A null part means "not currently on screen"; the host then keeps what the saved state carried.
 */
class BookmarkDocumentsRecreation(
    val documentsSearch: MyDocumentsSearchValue? = null,
    val pages: MyDocumentPagesValue? = null,
    val cloudFilters: CloudFiltersValue? = null,
)

/**
 * The platform half of the bookmark cluster: what [BookmarkHost] cannot do in common code because it needs the
 * Activity or `:app`-only types. A host-scoped port, built on first use (see `HostPorts.bookmarkPlatform`).
 */
interface BookmarkHostPlatform {
    /** Calls [onChanged] on main when bookmarks are synced in; the returned function unsubscribes. */
    fun subscribeBookmarkSync(onChanged: () -> Unit): () -> Unit

    /** Classic `exportBookmarksToCSV`: the CSV chooser wants an Activity. */
    suspend fun exportBookmarksCsv(bookmarks: List<BookmarkEntities.BibleBookmarkWithNotes>)

    /** Classic `importBookmarksFromCSV`. */
    suspend fun importBookmarksCsv()

    /**
     * Opens InstallZip in-graph and runs [onResult] once with its answer. Temporary: it goes through the activity
     * until Task 12 lifts the install-zip members.
     */
    fun openInstallZip(onResult: () -> Unit)

    /** The "read more" link of the label manager's help dialog: the StudyPad playlist or the labels one. */
    fun helpReadMoreUrl(studyPad: Boolean): String

    /** The My Documents screens' share of the recreation snapshot. */
    fun documentsRecreation(): BookmarkDocumentsRecreation
}
