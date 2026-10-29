@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.host.ports

import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.nav.subscribeToBookmarkSync
import net.bible.service.sword.ContentSource
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedui.app.bookmark.BookmarkCsvHostFlow
import net.bible.sharedui.app.bookmark.BookmarkDocumentsRecreation
import net.bible.sharedui.app.bookmark.BookmarkHostPlatform
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.strings.keyed.StringTable
import okio.Sink

/** Bookmark CSV uses the host's picker, shared codec, and live UI-language provider. */
@kotlin.native.HiddenFromObjC
class IosBookmarkHostPlatform(
    bookmarkControl: () -> BookmarkControl,
    picker: DocumentPicker,
    settings: AppSettings,
    dialogs: AppDialogController,
    table: () -> StringTable,
    notifier: UserNotifier,
    contentSource: ContentSource,
    openSink: (DocumentRef) -> Sink?,
    timestamp: () -> String,
) : BookmarkHostPlatform {
    private val csv = BookmarkCsvHostFlow(bookmarkControl, picker, settings, dialogs, table, notifier,
        openSource = { contentSource.openSource(it.uri) }, openSink = openSink, timestamp = timestamp)

    override fun subscribeBookmarkSync(onChanged: () -> Unit): () -> Unit = subscribeToBookmarkSync(onChanged)
    override suspend fun exportBookmarksCsv(bookmarks: List<BookmarkEntities.BibleBookmarkWithNotes>) = csv.exportBookmarksCsv(bookmarks)
    override suspend fun importBookmarksCsv() = csv.importBookmarksCsv()
    override fun helpReadMoreUrl(studyPad: Boolean): String = if (studyPad)
        "https://www.youtube.com/playlist?list=PLD-W_Iw-N2MkMiGz7cjGASOYjElr1Q76m"
    else "https://www.youtube.com/playlist?list=PLD-W_Iw-N2Mnv8aYRK3QbZBjE3ZMmrJZ7"
    override fun documentsRecreation(): BookmarkDocumentsRecreation = BookmarkDocumentsRecreation()
}
