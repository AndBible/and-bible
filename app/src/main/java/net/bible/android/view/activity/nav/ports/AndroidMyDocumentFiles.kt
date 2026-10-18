package net.bible.android.view.activity.nav.ports

import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.android.platform.AndroidBackupHost
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedui.app.ports.DocumentFolderExport
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.ImportedFile
import net.bible.sharedui.app.ports.ImportedPage
import net.bible.sharedui.app.ports.MyDocumentFiles
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * [MyDocumentFiles] over `ContentResolver` and `DocumentFile`: the file I/O of the My Documents screens, moved
 * here unchanged from `NavHostComposeActivity`.
 *
 * [backupControl] and [askDestination] are providers because the Activity builds this port in a field initializer,
 * before its own injected members and host overlays exist.
 */
class AndroidMyDocumentFiles(
    private val activity: NavHostComposeActivity,
    private val backupControl: () -> BackupControl,
    private val askDestination: suspend () -> SaveOrShare?,
) : MyDocumentFiles {
    override fun displayName(ref: DocumentRef): String? = fileName(Uri.parse(ref.uri))

    /** Resolve a display file name for a content Uri. */
    private fun fileName(uri: Uri): String? {
        activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    override suspend fun importPageFile(ref: DocumentRef): ImportedPage? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(ref.uri)
        val fileName = fileName(uri) ?: activity.getString(R.string.my_document_imported_page_name)
        val content = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return@withContext null

        val contentType = when {
            fileName.endsWith(".html", ignoreCase = true) || fileName.endsWith(".htm", ignoreCase = true) ->
                MyDocumentContentType.HTML
            else -> MyDocumentContentType.MARKDOWN
        }
        ImportedPage(fileName.substringBeforeLast("."), contentType, content)
    }

    override suspend fun readDocumentFiles(refs: List<DocumentRef>): List<ImportedFile> = withContext(Dispatchers.IO) {
        refs.mapNotNull { ref ->
            val uri = Uri.parse(ref.uri)
            val fileName = fileName(uri) ?: return@mapNotNull null
            val content = activity.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: return@mapNotNull null
            ImportedFile(fileName, content)
        }.sortedBy { it.fileName }
    }

    /**
     * Export one page via `BackupControl.saveOrShare`, with the host's own destination chooser in place of
     * `saveOrShare`'s platform `AlertDialog`.
     */
    override suspend fun exportPage(page: MyDocumentPageWithContent) = withContext(Dispatchers.IO) {
        val ext = if (page.contentType == MyDocumentContentType.HTML) "html" else "md"
        val sanitizedTitle = page.title.replace(Regex("[^a-zA-Z0-9._\\- ]"), "").take(50)
            .ifEmpty { activity.getString(R.string.my_document_export_fallback_name) }
        val fileName = "$sanitizedTitle.$ext"
        val targetDir = File(SharedConstants.internalFilesDir, "export/")
        targetDir.mkdirs()
        val targetFile = File(targetDir, fileName)
        targetFile.writeText(page.content ?: "")
        val mimeType = if (ext == "html") "text/html" else "text/markdown"
        backupControl().saveOrShare(
            host = AndroidBackupHost { activity },
            file = targetFile.toOkioPath(),
            fileName = fileName,
            shareMimeType = mimeType,
            saveMimeType = mimeType,
            chooserTitle = activity.getString(R.string.my_document_export_page),
            chooseDestination = askDestination,
        )
        Unit
    }

    override suspend fun exportPagesToFolder(pages: List<MyDocumentPageWithContent>, tree: DocumentRef): Boolean =
        withContext(Dispatchers.IO) {
            val treeDoc = DocumentFile.fromTreeUri(activity, Uri.parse(tree.uri)) ?: return@withContext false
            writePagesInto(treeDoc, pages)
            true
        }

    override suspend fun exportDocumentsToFolder(documents: List<DocumentFolderExport>, tree: DocumentRef): Boolean =
        withContext(Dispatchers.IO) {
            val treeDoc = DocumentFile.fromTreeUri(activity, Uri.parse(tree.uri)) ?: return@withContext false
            for (document in documents) {
                val subDir = treeDoc.createDirectory(document.folderName) ?: continue
                writePagesInto(subDir, document.pages)
            }
            true
        }

    /** The per-page write loop, shared by the single and batch export. Runs on the caller's (IO) dispatcher. */
    private fun writePagesInto(dir: DocumentFile, pages: List<MyDocumentPageWithContent>) {
        for ((index, page) in pages.withIndex()) {
            val ext = if (page.contentType == MyDocumentContentType.HTML) "html" else "md"
            val mimeType = if (ext == "html") "text/html" else "text/markdown"
            val orderPrefix = String.format("%02d", index + 1)
            val sanitizedTitle = page.title
                .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
                .take(50)
                .ifEmpty { activity.getString(R.string.my_document_export_fallback_name) }
            val entryName = "$orderPrefix-$sanitizedTitle.$ext"
            val file = dir.createFile(mimeType, entryName) ?: continue
            activity.contentResolver.openOutputStream(file.uri)?.use { out ->
                out.write((page.content ?: "").toByteArray(Charsets.UTF_8))
            }
        }
    }
}
