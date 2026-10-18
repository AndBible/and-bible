@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

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
@kotlin.native.HiddenFromObjC
class BookmarkDocumentsRecreation(
    val documentsSearch: MyDocumentsSearchValue? = null,
    val pages: MyDocumentPagesValue? = null,
    val cloudFilters: CloudFiltersValue? = null,
)

/**
 * The platform half of the bookmark cluster: what [BookmarkHost] cannot do in common code because it needs the
 * Activity or `:app`-only types. A host-scoped port, built on first use (see `HostPorts.bookmarkPlatform`).
 */
@kotlin.native.HiddenFromObjC
interface BookmarkHostPlatform {
    /** Calls [onChanged] on main when bookmarks are synced in; the returned function unsubscribes. */
    fun subscribeBookmarkSync(onChanged: () -> Unit): () -> Unit

    /** Classic `exportBookmarksToCSV`: the CSV chooser wants an Activity. */
    suspend fun exportBookmarksCsv(bookmarks: List<BookmarkEntities.BibleBookmarkWithNotes>)

    /** Classic `importBookmarksFromCSV`. */
    suspend fun importBookmarksCsv()

    /** The "read more" link of the label manager's help dialog: the StudyPad playlist or the labels one. */
    fun helpReadMoreUrl(studyPad: Boolean): String

    /** The My Documents screens' share of the recreation snapshot. */
    fun documentsRecreation(): BookmarkDocumentsRecreation
}
