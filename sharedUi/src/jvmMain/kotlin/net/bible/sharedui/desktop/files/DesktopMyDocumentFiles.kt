package net.bible.sharedui.desktop.files

import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedui.app.ports.*
import net.bible.sharedui.assembly.IosStringTableHolder
import org.koin.mp.KoinPlatform

class DesktopMyDocumentFiles(private val text: (String) -> String = { KoinPlatform.getKoin().get<IosStringTableHolder>().live.text(it) }) : MyDocumentFiles {
    private fun path(ref: DocumentRef): Path? = try {
        val uri = URI(ref.uri)
        if (uri.scheme != "file") null else Path.of(uri).takeIf { it.isAbsolute }
    } catch (_: Exception) { null }
    override fun displayName(ref: DocumentRef): String? = path(ref)?.fileName?.toString()
    override suspend fun importPageFile(ref: DocumentRef): ImportedPage? = withContext(Dispatchers.IO) {
        val p = path(ref)?.takeIf { it.fileName != null } ?: return@withContext null
        try {
            val name = p.fileName.toString()
            val type = if (name.endsWith(".html", true) || name.endsWith(".htm", true)) MyDocumentContentType.HTML else MyDocumentContentType.MARKDOWN
            ImportedPage(name.substringBeforeLast('.'), type, Files.readString(p))
        } catch (_: java.io.IOException) { null }
    }
    override suspend fun readDocumentFiles(refs: List<DocumentRef>): List<ImportedFile> = withContext(Dispatchers.IO) {
        refs.mapNotNull { ref ->
            val p = path(ref)?.takeIf { it.fileName != null } ?: return@mapNotNull null
            try { ImportedFile(p.fileName.toString(), Files.readString(p)) } catch (_: java.io.IOException) { null }
        }.sortedBy { it.fileName }
    }
    private fun title(value: String) = value.replace(Regex("[^a-zA-Z0-9._\\- ]"), "").take(50).ifEmpty { text("my_document_export_fallback_name") }
    private fun ext(page: MyDocumentPageWithContent) = if (page.contentType == MyDocumentContentType.HTML) "html" else "md"
    override suspend fun exportPage(page: MyDocumentPageWithContent) = withContext(Dispatchers.IO) {
        val koin = KoinPlatform.getKoin()
        val name = "${title(page.title)}.${ext(page)}"
        val dir = koin.get<AppDirectories>().internalFilesDir / "export"
        okio.FileSystem.SYSTEM.createDirectories(dir)
        val file = dir / name
        okio.FileSystem.SYSTEM.write(file) { writeUtf8(page.content ?: "") }
        val mime = if (page.contentType == MyDocumentContentType.HTML) "text/html" else "text/markdown"
        val backup = koin.get<BackupControl>()
        backup.saveOrShare(host = koin.get<BackupHost>(), file = file, fileName = name, shareMimeType = mime, saveMimeType = mime,
            chooserTitle = text("my_document_export_page"), chooseDestination = {
                val strings = koin.get<CoreStrings>()
                backup.classicDestinationPrompt(strings.backupBackupTitle, strings.backupBackupMessage)
            })
        Unit
    }
    private fun writePages(dir: Path, pages: List<MyDocumentPageWithContent>): Boolean {
        var success = true
        pages.forEachIndexed { index, page ->
            val stem = "${(index + 1).toString().padStart(2, '0')}-${title(page.title)}"
            var suffix = 1
            while (true) {
                val name = "$stem${if (suffix == 1) "" else " ($suffix)"}.${ext(page)}"
                try { Files.writeString(dir.resolve(name), page.content ?: "", CREATE_NEW); break }
                catch (_: java.nio.file.FileAlreadyExistsException) {
                    if (!Files.isRegularFile(dir.resolve(name), NOFOLLOW_LINKS)) { success = false; break }
                    suffix++
                } catch (_: java.io.IOException) { success = false; break }
            }
        }
        return success
    }
    override suspend fun exportPagesToFolder(pages: List<MyDocumentPageWithContent>, tree: DocumentRef): Boolean = withContext(Dispatchers.IO) {
        val dir = path(tree) ?: return@withContext false
        if (!Files.isDirectory(dir, NOFOLLOW_LINKS)) return@withContext false
        writePages(dir, pages)
    }
    override suspend fun exportDocumentsToFolder(documents: List<DocumentFolderExport>, tree: DocumentRef): Boolean = withContext(Dispatchers.IO) {
        val root = path(tree) ?: return@withContext false
        if (!Files.isDirectory(root, NOFOLLOW_LINKS)) return@withContext false
        var success = true
        for (doc in documents) {
            val name = doc.folderName
            if (name.isEmpty() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it == '\u0000' }) { success = false; continue }
            var suffix = 1
            while (true) {
                val dir = root.resolve(if (suffix == 1) name else "$name ($suffix)")
                try { Files.createDirectory(dir); if (!writePages(dir, doc.pages)) success = false; break }
                catch (_: java.nio.file.FileAlreadyExistsException) {
                    if (!Files.isDirectory(dir, NOFOLLOW_LINKS)) { success = false; break }
                    suffix++
                } catch (_: java.io.IOException) { success = false; break }
            }
        }
        success
    }
}
