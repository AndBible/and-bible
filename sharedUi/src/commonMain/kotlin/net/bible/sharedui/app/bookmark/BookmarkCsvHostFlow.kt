@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
package net.bible.sharedui.app.bookmark

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkCsvUtils
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.sharedcore.ui.dialog.plainTextToHtml
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.strings.keyed.StringTable
import okio.Sink
import okio.Source
import okio.buffer
import okio.use

/** Picker-based CSV interaction; Android retains its Activity/SAF flow and drawable inventory. */
@kotlin.native.HiddenFromObjC
class BookmarkCsvHostFlow(
    private val bookmarkControl: () -> BookmarkControl,
    private val picker: DocumentPicker,
    private val settings: AppSettings,
    private val dialogs: AppDialogController,
    private val table: () -> StringTable,
    private val notifier: UserNotifier,
    private val openSource: (DocumentRef) -> Source?,
    private val openSink: (DocumentRef) -> Sink?,
    private val timestamp: () -> String,
) {
    suspend fun exportBookmarksCsv(bookmarks: List<BibleBookmarkWithNotes>) = safely("csv_export_failed") {
        if (bookmarks.isEmpty()) {
            notifier.showInfo(table().text("no_bookmarks_to_export"))
            return@safely
        }
        val columns = BookmarkCsvUtils.availableColumns
        val unchecked = settings.getStringSet("csv_export_unchecked_columns", emptySet())
        val result = dialogs.await(AppDialogRequest.Localized {
            val strings = table()
            AppDialogRequest.MultiChoice(
                title = strings.text("csv_column_selection_title"),
                options = columns.map { SettingsItem.Choice(it.key, strings.text(columnTitleKey(it.key))) },
                selectedIds = columns.filterNot { it.key in unchecked }.map { it.key },
                confirmText = strings.text("okay"), dismissText = strings.text("cancel"),
                selectAllText = strings.text("select_all"), selectNoneText = strings.text("select_none"),
            )
        })
        val ids = (result as? AppDialogResult.SelectedMany)?.ids.orEmpty()
        val selected = columns.map { it.key }.filter { it in ids }
        // Classic persists the inverse even when the chooser was cancelled.
        settings.setStringSet("csv_export_unchecked_columns", columns.map { it.key }.filterNot { it in selected }.toSet())
        if (selected.isEmpty()) return@safely
        val ref = picker.createDocument("bible_bookmarks_${timestamp()}.csv", "text/csv") ?: return@safely
        withContext(Dispatchers.IO) {
            val sink = openSink(ref) ?: error("Could not open output stream for URI: ${ref.uri}")
            sink.buffer().use { BookmarkCsvUtils.exportBookmarksToCsv(it, bookmarks, bookmarkControl(), selected) }
        }
        notifier.showInfo(table().fmt("csv_export_success", bookmarks.size))
    }

    suspend fun importBookmarksCsv() = safely("csv_import_failed") {
        val ref = picker.openDocuments(listOf("text/csv", "text/plain", "text/comma-separated-values"), false)
            .firstOrNull() ?: return@safely
        val result = withContext(Dispatchers.IO) {
            val source = openSource(ref) ?: error("Could not open input stream for URI: ${ref.uri}")
            source.buffer().use { BookmarkCsvUtils.importBookmarksFromCsv(it, bookmarkControl()) }
        }
        if (result.errors > 0) {
            dialogs.post(AppDialogRequest.Localized {
                val strings = table()
                val message = strings.fmt("csv_import_errors", result.created, result.updated, result.errors) +
                    "\n\n" + result.errorMessages.take(5).joinToString("\n") +
                    if (result.errorMessages.size > 5) "\n..." else ""
                AppDialogRequest.Message(strings.fmt("import_items", "CSV"), plainTextToHtml(message),
                    strings.text("okay"), cancellable = true)
            })
        } else notifier.showInfo(table().fmt("csv_import_success", result.created, result.updated))
    }

    private suspend fun safely(errorKey: String, action: suspend () -> Unit) {
        try { action() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { notifier.showError(table().fmt(errorKey, e.message), e) }
    }

    private fun columnTitleKey(key: String): String = when (key) {
        "osisRef" -> "osis_reference"
        "bibleRef" -> "bible_reference"
        "chapterStart" -> "chapter_start"
        "verseStart" -> "verse_start"
        "chapterEnd" -> "chapter_end"
        "verseEnd" -> "verse_end"
        "ordinalStart" -> "ordinal_start"
        "ordinalEnd" -> "ordinal_end"
        "createdAt" -> "created_at"
        "lastUpdatedOn" -> "last_updated_at"
        "startOffset" -> "start_offset"
        "endOffset" -> "end_offset"
        "notes" -> "bookmark_notes"
        "customIcon" -> "custom_icon"
        else -> key // document, book, id, labels have matching string keys.
    }
}
