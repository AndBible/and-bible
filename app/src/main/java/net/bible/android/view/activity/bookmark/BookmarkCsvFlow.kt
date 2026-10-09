/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.bookmark

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkCsvUtils
import net.bible.android.control.bookmark.BookmarkCsvUtils.CsvColumn
import net.bible.android.control.bookmark.CsvColumnTitles
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.plainTextToHtml
import org.koin.java.KoinJavaComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Each CSV column's title in the export column chooser: its string resource. */
class AndroidCsvColumnTitles(private val context: Context) : CsvColumnTitles {
    override fun title(column: CsvColumn): String = context.getString(when (column.key) {
        BookmarkCsvUtils.HEADER_OSIS_REF -> R.string.osis_reference
        BookmarkCsvUtils.HEADER_BIBLE_REF -> R.string.bible_reference
        BookmarkCsvUtils.HEADER_DOCUMENT -> R.string.document
        BookmarkCsvUtils.HEADER_BOOK -> R.string.book
        BookmarkCsvUtils.HEADER_CHAPTER_START -> R.string.chapter_start
        BookmarkCsvUtils.HEADER_VERSE_START -> R.string.verse_start
        BookmarkCsvUtils.HEADER_CHAPTER_END -> R.string.chapter_end
        BookmarkCsvUtils.HEADER_VERSE_END -> R.string.verse_end
        BookmarkCsvUtils.HEADER_ID -> R.string.id
        BookmarkCsvUtils.HEADER_ORDINAL_START -> R.string.ordinal_start
        BookmarkCsvUtils.HEADER_ORDINAL_END -> R.string.ordinal_end
        BookmarkCsvUtils.HEADER_CREATED_AT -> R.string.created_at
        BookmarkCsvUtils.HEADER_LAST_UPDATED -> R.string.last_updated_at
        BookmarkCsvUtils.HEADER_START_OFFSET -> R.string.start_offset
        BookmarkCsvUtils.HEADER_END_OFFSET -> R.string.end_offset
        BookmarkCsvUtils.HEADER_LABELS -> R.string.labels
        BookmarkCsvUtils.HEADER_NOTES -> R.string.bookmark_notes
        BookmarkCsvUtils.HEADER_CUSTOM_ICON -> R.string.custom_icon
        else -> throw IllegalArgumentException("No title for CSV column ${column.key}")
    })
}

/**
 * The Android side of bookmark CSV export/import: the system file pickers (SAF intents), the
 * column chooser, toasts and the error/summary dialogs. The CSV itself is read and written by the
 * domain's [BookmarkCsvUtils] against [bookmarkControl].
 */
class BookmarkCsvFlow(private val bookmarkControl: BookmarkControl) {

    suspend fun exportBookmarksToCSV(context: ActivityBase, exportBookmarks: List<BibleBookmarkWithNotes>) = context.run {
        try {
            if (exportBookmarks.isEmpty()) {
                Toast.makeText(context, getString(R.string.no_bookmarks_to_export), Toast.LENGTH_SHORT)
                    .show()
                return
            }

            // Show column selection dialog
            val selectedColumns = showColumnSelectionDialog(context)
            if (selectedColumns.isEmpty()) return // User cancelled or selected no columns

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                putExtra(Intent.EXTRA_TITLE, "bible_bookmarks_$timestamp.csv")
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { exportToUri(context, it, exportBookmarks, selectedColumns) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CSV export", e)
            ErrorReportControl.showErrorDialog(
                context,
                getString(R.string.csv_export_failed, e.message),
                exception = e
            )
        }
    }

    suspend fun importBookmarksFromCSV(context: ActivityBase) = context.run {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "text/comma-separated-values"))
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { importFromUri(context, it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting CSV import", e)
            ErrorReportControl.showErrorDialog(
                context,
                getString(R.string.csv_import_failed, e.message),
                exception = e
            )
        }
    }


    private suspend fun showColumnSelectionDialog(context: ActivityBase): List<String> {
        val columns = BookmarkCsvUtils.availableColumns
        val titles = AndroidCsvColumnTitles(context)

        // Load previously unchecked columns from settings
        val uncheckedColumns = CommonUtils.settings.getStringSet("csv_export_unchecked_columns", emptySet())

        // Pre-select columns (all columns except those that were previously unchecked)
        val selectedColumns = Dialogs.multiselect(
            context,
            context.getString(R.string.csv_column_selection_title),
            columns,
            itemToString = { column -> titles.title(column) },
            preSelected = { column -> !uncheckedColumns.contains(column.key) }
        )

        // Save the inverse selection (unchecked items) to settings
        val selectedKeys = selectedColumns.map { it.key }.toSet()
        val newUncheckedColumns = columns.map { it.key }.filter { !selectedKeys.contains(it) }.toSet()
        CommonUtils.settings.setStringSet("csv_export_unchecked_columns", newUncheckedColumns)

        return selectedColumns.map { it.key }
    }

    private suspend fun exportToUri(context: Context, uri: Uri, bookmarks: List<BibleBookmarkWithNotes>, selectedColumns: List<String>) = context.run {
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                BookmarkCsvUtils.exportBookmarksToCsv(outputStream, bookmarks, bookmarkControl, selectedColumns)
            } ?: throw IllegalArgumentException("Could not open output stream for URI: $uri")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    getString(R.string.csv_export_success, bookmarks.size),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * `internal`, not `private`: exercised directly by `BookmarkCsvFlowTest` (the import summary
     * dialog) rather than through the full `importBookmarksFromCSV` SAF round trip.
     */
    internal suspend fun importFromUri(context: Context, uri: Uri) = context.run {
        withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

                withContext(Dispatchers.Main) {
                    if (result.errors > 0) {
                        // Show detailed error dialog. I2 fix: AppDialogRequest.Message is always
                        // parsed as HTML (parseHtmlRuns) -- a plain "\n"-joined summary collapses
                        // onto one line, and any "<...>" an exception's own message happens to
                        // contain is silently dropped as an unknown tag. plainTextToHtml keeps both.
                        val plainMessage =
                            getString(R.string.csv_import_errors, result.created, result.updated, result.errors) +
                                "\n\n" + result.errorMessages.take(5).joinToString("\n") +
                                if (result.errorMessages.size > 5) "\n..." else ""

                        KoinJavaComponent.get<AppDialogController>(AppDialogController::class.java).post(
                            AppDialogRequest.Message(
                                title = getString(R.string.import_items, "CSV"),
                                message = plainTextToHtml(plainMessage),
                                confirmText = getString(R.string.okay),
                                cancellable = true,
                            ),
                        )
                    } else {
                        Toast.makeText(
                            context,
                            getString(R.string.csv_import_success, result.created, result.updated),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } ?: throw IllegalArgumentException("Could not open input stream for URI: $uri")
        }
    }

    companion object {
        private const val TAG = "BookmarkCsvFlow"
    }
}
