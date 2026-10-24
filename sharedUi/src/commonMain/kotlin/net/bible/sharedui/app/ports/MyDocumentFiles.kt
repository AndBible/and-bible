@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ports

import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPageWithContent

/** One page read from a picked file: [title] is the file name without extension. */
@kotlin.native.HiddenFromObjC
data class ImportedPage(val title: String, val contentType: MyDocumentContentType, val content: String)

/** One picked text file, as [MyDocumentFiles.readDocumentFiles] returns it. */
@kotlin.native.HiddenFromObjC
data class ImportedFile(val fileName: String, val content: String)

/** One document's pages and the (already sanitised) sub-folder they are written into. */
@kotlin.native.HiddenFromObjC
data class DocumentFolderExport(val folderName: String, val pages: List<MyDocumentPageWithContent>)

/**
 * The file I/O of the My Documents and My Document pages screens: what needs a `ContentResolver`,
 * `DocumentFile` or the backup save-or-share flow on Android. Host-scoped: the Android implementation needs the
 * Activity. The picking itself goes through [DocumentPicker].
 *
 * Methods throw on an I/O failure; the host reports it to the user.
 */
@kotlin.native.HiddenFromObjC
interface MyDocumentFiles {
    /** Reads one picked text file as a page; null when the file cannot be opened. The title falls back to a localized name. */
    suspend fun importPageFile(ref: DocumentRef): ImportedPage?

    /** Reads the picked text files, sorted by file name; files that cannot be named or opened are skipped. */
    suspend fun readDocumentFiles(refs: List<DocumentRef>): List<ImportedFile>

    /** Exports one page through the save-or-share flow (the host's own destination chooser, then save or share). */
    suspend fun exportPage(page: MyDocumentPageWithContent)

    /**
     * Writes [pages] as numbered files straight into the picked folder [tree]. Returns false, having written
     * nothing, when the folder cannot be opened.
     */
    suspend fun exportPagesToFolder(pages: List<MyDocumentPageWithContent>, tree: DocumentRef): Boolean

    /**
     * Writes each document into its own sub-folder of the picked folder [tree]. Returns false, having written
     * nothing, when the folder cannot be opened.
     */
    suspend fun exportDocumentsToFolder(documents: List<DocumentFolderExport>, tree: DocumentRef): Boolean

    /** The display name of a picked file, falling back to the last path segment. */
    fun displayName(ref: DocumentRef): String?
}
