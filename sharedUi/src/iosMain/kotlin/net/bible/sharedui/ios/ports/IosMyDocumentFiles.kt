/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.android.database.mydocument.MyDocumentContentType
import net.bible.android.database.mydocument.MyDocumentPageWithContent
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedui.app.ports.DocumentFolderExport
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.ImportedFile
import net.bible.sharedui.app.ports.ImportedPage
import net.bible.sharedui.app.ports.MyDocumentFiles
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.strings.keyed.StringTable
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.koin.mp.KoinPlatform
import platform.Foundation.NSURL

/** File URL I/O matching Android's My Documents naming and export rules. Dependencies resolve only when used. */
@kotlin.native.HiddenFromObjC
class IosMyDocumentFiles(
    private val backupControl: () -> BackupControl = { KoinPlatform.getKoin().get() },
    private val backupHost: () -> BackupHost = { KoinPlatform.getKoin().get() },
    private val askDestination: suspend () -> SaveOrShare? = {
        val koin = KoinPlatform.getKoin()
        val strings = koin.get<CoreStrings>()
        koin.get<BackupControl>().classicDestinationPrompt(strings.backupBackupTitle, strings.backupBackupMessage)
    },
    private val startAccess: (NSURL) -> Boolean = { it.startAccessingSecurityScopedResource() },
    private val stopAccess: (NSURL) -> Unit = { it.stopAccessingSecurityScopedResource() },
    private val strings: () -> StringTable = { IosAppStartup.stringTable },
    private val exportDirectory: () -> Path = { KoinPlatform.getKoin().get<AppDirectories>().internalFilesDir / "export" },
) : MyDocumentFiles {
    override fun displayName(ref: DocumentRef): String? = fileUrl(ref)?.lastPathComponent?.takeIf { it.isNotEmpty() }

    override suspend fun importPageFile(ref: DocumentRef): ImportedPage? = withContext(Dispatchers.IO) {
        access(ref) { url, path ->
            val name = url.lastPathComponent?.takeIf { it.isNotEmpty() }
                ?: strings().text("my_document_imported_page_name")
            ImportedPage(name.substringBeforeLast("."), contentType(name), FileSystem.SYSTEM.read(path) { readUtf8() })
        }
    }

    override suspend fun readDocumentFiles(refs: List<DocumentRef>): List<ImportedFile> = withContext(Dispatchers.IO) {
        refs.mapNotNull { ref ->
            access(ref) { url, path ->
                val name = url.lastPathComponent?.takeIf { it.isNotEmpty() } ?: return@access null
                ImportedFile(name, FileSystem.SYSTEM.read(path) { readUtf8() })
            }
        }.sortedBy { it.fileName }
    }

    override suspend fun exportPage(page: MyDocumentPageWithContent) = withContext(Dispatchers.IO) {
        val name = "${exportTitle(page.title)}.${extension(page)}"
        val dir = exportDirectory()
        FileSystem.SYSTEM.createDirectories(dir)
        val file = dir / name
        FileSystem.SYSTEM.write(file) { writeUtf8(page.content ?: "") }
        val mime = if (page.contentType == MyDocumentContentType.HTML) "text/html" else "text/markdown"
        backupControl().saveOrShare(
            host = backupHost(), file = file, fileName = name,
            shareMimeType = mime, saveMimeType = mime,
            chooserTitle = strings().text("my_document_export_page"), chooseDestination = askDestination,
        )
        Unit
    }

    override suspend fun exportPagesToFolder(pages: List<MyDocumentPageWithContent>, tree: DocumentRef): Boolean =
        withContext(Dispatchers.IO) {
            access(tree) { _, path ->
                if (!FileSystem.SYSTEM.metadata(path).isDirectory) return@access false
                writePages(path, pages)
                true
            } ?: false
        }

    override suspend fun exportDocumentsToFolder(documents: List<DocumentFolderExport>, tree: DocumentRef): Boolean =
        withContext(Dispatchers.IO) {
            access(tree) { _, path ->
                if (!FileSystem.SYSTEM.metadata(path).isDirectory) return@access false
                for (document in documents) {
                    val dir = createDocumentDirectory(path, document.folderName) ?: continue
                    writePages(dir, document.pages)
                }
                true
            } ?: false
        }

    /** Reserve a fresh subfolder atomically, never merge two documents or interpret names as paths. */
    private fun createDocumentDirectory(root: Path, name: String): Path? {
        if (name.isEmpty() || name == "." || name == ".." || '/' in name || '\\' in name || '\u0000' in name) return null
        var suffix = 1
        while (true) {
            val candidate = root / if (suffix == 1) name else "$name ($suffix)"
            try {
                FileSystem.SYSTEM.createDirectory(candidate, mustCreate = true)
                return candidate
            } catch (_: IOException) {
                // Only an existing directory is a naming collision; real creation failures skip this document.
                val existingDirectory = try {
                    FileSystem.SYSTEM.metadataOrNull(candidate)?.isDirectory == true
                } catch (_: IOException) { false }
                if (!existingDirectory) return null
                suffix++
            }
        }
    }

    private fun writePages(dir: Path, pages: List<MyDocumentPageWithContent>) {
        for ((index, page) in pages.withIndex()) {
            val prefix = (index + 1).toString().padStart(2, '0')
            val name = "$prefix-${exportTitle(page.title)}.${extension(page)}"
            try {
                FileSystem.SYSTEM.write(dir / name) { writeUtf8(page.content ?: "") }
            } catch (_: IOException) { continue }
        }
    }

    private fun exportTitle(title: String): String = title.replace(Regex("[^a-zA-Z0-9._\\- ]"), "").take(50)
        .ifEmpty { strings().text("my_document_export_fallback_name") }

    private fun extension(page: MyDocumentPageWithContent) = if (page.contentType == MyDocumentContentType.HTML) "html" else "md"

    private fun contentType(name: String) =
        if (name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)) MyDocumentContentType.HTML
        else MyDocumentContentType.MARKDOWN

    private fun fileUrl(ref: DocumentRef): NSURL? = NSURL.URLWithString(ref.uri)
        ?.takeIf { it.isFileURL() && it.path?.startsWith("/") == true }

    /** A denied scope is normal for sandbox-local URLs; release only scopes that were acquired. */
    private inline fun <T> access(ref: DocumentRef, block: (NSURL, Path) -> T): T? {
        val url = fileUrl(ref) ?: return null
        val scoped = startAccess(url)
        return try {
            block(url, url.path!!.toPath())
        } catch (_: IOException) {
            null
        } finally {
            if (scoped) stopAccess(url)
        }
    }
}
