/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.mybible

import net.bible.sharedcore.log.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.android.database.repoJson
import androidx.sqlite.SQLiteException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.bible.service.download.GenericFileDownloader
import org.crosswire.common.util.NetUtil
import org.crosswire.jsword.book.AbstractBookList
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.install.DownloadCancelledException
import org.crosswire.jsword.book.install.InstallException
import org.crosswire.jsword.book.install.Installer
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.common.util.KFiles
import org.crosswire.common.util.KUri
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import net.bible.service.download.parseKnownUrl
import net.bible.service.sword.epub.child
import net.bible.sharedlogic.util.currentTimeMillis
// The ZIP branch of prepareModuleFiles stays on java.io/java.util.zip until G2's common ZIP reader merges.
import java.io.File
import java.util.zip.ZipFile

@Serializable
class MyBibleModuleSpec (
    val file_name: String,
    val description: String,
    var download_url: String = "",
    val language_code: String = "en",
    val update_date: String = "",
    val update_info: String = "",
)

@Serializable
class MyBibleRepositorySpec (
    val url: String,
    val file_name: String,
    val description: String,
    val modules: List<MyBibleModuleSpec>
) {
    companion object {
        fun fromJson(jsonString: String): MyBibleRepositorySpec {
            return repoJson.decodeFromString(serializer(), jsonString)
        }
    }
}

class MyBibleInstaller(private val manifestUrl: String, private val downloader: GenericFileDownloader, private val environment: AppEnvironment): Installer, AbstractBookList() {
    override fun getType(): String  = "mybible-https"
    private val myBibleDir = myBibleModulesDir()
    private val localManifestFile = myBibleDir.child("manifest.json")
    private var spec: MyBibleRepositorySpec? = readSpec()
    private val moduleSpecs get() = spec?.modules?.filter { it.download_url.startsWith("https://") }?: emptyList()
    private fun readSpec(): MyBibleRepositorySpec? {
        if(KFiles.isFile(localManifestFile)) {
            val jsonString = KFiles.readBytes(localManifestFile).decodeToString()
            return try {
                val spec = MyBibleRepositorySpec.fromJson(jsonString)
                for(modSpec in spec.modules) {
                    modSpec.download_url = modSpec.download_url.replace("http://", "https://")
                }
                spec
            } catch (e: SerializationException) {
                KFiles.delete(localManifestFile)
                null
            }
        }
        return null
    }

    private val fakeDriver = SwordBookDriver.instance()
    private val nullBackend = NullBackend()

    /** `File(name).nameWithoutExtension` */
    private fun fileBaseName(fileName: String): String = fileName.substringAfterLast('/').substringBeforeLast('.')

    private fun categoryFromFilename(filename: String): String {
        val isCommentary = filename.contains(".commentaries")
        val isDictionary = filename.contains(".dictionaries")
        return when {
            isCommentary -> "Commentaries"
            isDictionary -> "Lexicons / Dictionaries"
            else -> "Biblical Texts"
        }
    }

    override fun getBooks(): MutableList<Book> = moduleSpecs.map {
        val initials = "MyBible-" + sanitizeModuleName(fileBaseName(it.file_name))
        val abbreviation = fileBaseName(it.file_name).split(".", limit = 2)[0]
        val config = getConfig(
            initials = initials,
            description = it.description,
            abbreviation = abbreviation,
            language = it.language_code,
            category = categoryFromFilename(it.file_name),
            moduleFileName = "",
            downloadUrl = it.download_url
        )
        val metadata = SwordBookMetaData(config.toByteArray(), initials)
        metadata.setDriver(fakeDriver)
        SwordBook(metadata, nullBackend)
    }.toMutableList()

    override fun getInstallerDefinition(): String {
        return "mybible-manifest:$manifestUrl"
    }

    override fun toRemoteURI(book: Book): KUri = KUri(book.myBibleDownloadUrl)

    override fun getBook(name: String): Book? = getBooks().find { it.name == name }

    override suspend fun getSize(book: Book): Int {
        // Note: This is completely unused in JSword API currently
        return NetUtil.getSize(KUri(book.myBibleDownloadUrl))
    }

    // Note: This is completely unused in JSword API currently
    override suspend fun isNewer(book: Book): Boolean = false

    override suspend fun reloadBookList() {
        val url = parseKnownUrl(manifestUrl)
        downloader.downloadFileWithProgress(url, localManifestFile, "MyBible manifest")
        spec = readSpec()
    }

    override suspend fun indexLastUpdated(): Long = downloader.lastModified(parseKnownUrl(manifestUrl)) ?: 0L

    override suspend fun install(book: Book) = install(book, null)

    override suspend fun install(book: Book, jobId: String?) {
        Log.i(TAG, "Installing MyBible book ${book.initials}, $jobId")
        val tmpDir = environment.cacheDir.toPath().child("mybible")
        // was File.mkdirs(), which only returned false; a failure still surfaces when the download writes there
        try { KFiles.createDirectories(tmpDir) } catch (e: IOException) { Log.w(TAG, "Could not create $tmpDir", e) }
        val url = book.myBibleDownloadUrl
        val moduleSpec = moduleSpecs.find { it.download_url == url } ?: throw InstallException("Spec error")
        val tmpFile = tmpDir.child(moduleSpec.file_name)
        val success = downloader.downloadFileWithProgress(KUri(url), tmpFile,
            jobId = jobId,
            description = "MyBible manifest",
            notifyUser = true
        )
        if(success && KFiles.isFile(tmpFile)) {
            val staging = tmpDir.child("install-${currentTimeMillis()}-${kotlin.random.Random.nextLong().toULong()}")
            try { KFiles.createDirectories(staging) } catch (e: IOException) { throw InstallException("Unable to stage MyBible module") }
            try {
                prepareModuleFiles(tmpFile, staging, moduleSpec.file_name)
                val modules = FileSystem.SYSTEM.listRecursively(staging)
                    .filter { KFiles.isFile(it) && it.name.substringAfterLast('.', "").equals("SQLite3", true) }.toList()
                if (modules.isEmpty()) throw InstallException("No supported MyBible module in download")
                modules.forEach { file ->
                    val state = SqliteVerseBackendState(file)
                    try {
                        val tables = state.rows("select name from sqlite_master where type = 'table'") { it.getText(0) }
                        // Match the reader's category precedence and validate columns even for empty tables.
                        val contentQuery = when {
                            "verses" in tables -> "select _rowid_, book_number, chapter, verse, text from verses limit 0"
                            "commentaries" in tables -> "select _rowid_, book_number, chapter_number_from, verse_number_from, chapter_number_to, verse_number_to, text from commentaries limit 0"
                            "dictionary" in tables -> "select _rowid_, topic, definition from dictionary limit 0"
                            else -> throw InstallException("Unsupported MyBible module")
                        }
                        state.rows(contentQuery) { Unit }
                        state.getBookMetaData()
                    } catch (e: SQLiteException) {
                        throw InstallException("Invalid MyBible module", e)
                    } finally {
                        state.close()
                    }
                }
                // Validate the complete payload before exposing any files to the installed-book scan.
                currentCoroutineContext().ensureActive()
                val dir = myBibleDir.child(book.initials)
                KFiles.createDirectories(dir)
                modules.forEach { file ->
                    val target = dir.child(file.relativeTo(staging).toString())
                    KFiles.createDirectories(target.parent!!)
                    FileSystem.SYSTEM.copy(file, target)
                }
                addManuallyInstalledMyBibleBooks()
            } catch (e: IOException) {
                throw InstallException("Unable to install MyBible module", e)
            } finally {
                KFiles.deleteRecursively(staging)
                KFiles.delete(tmpFile)
            }
        } else {
            throw DownloadCancelledException(KUri(url))
        }
    }

    private suspend fun prepareModuleFiles(download: Path, staging: Path, filename: String) {
        val sqliteHeader = "SQLite format 3\u0000".encodeToByteArray()
        val header = ByteArray(sqliteHeader.size)
        FileSystem.SYSTEM.source(download).buffer().use { it.readByteArray(header.size.toLong()) }.copyInto(header)
        if (header.contentEquals(sqliteHeader)) {
            FileSystem.SYSTEM.copy(download, staging.child(filename.substringAfterLast('/')))
        } else {
            val stagingDir = staging.toFile()
            ZipFile(download.toFile()).use { zip ->
                for (entry in zip.entries()) {
                    currentCoroutineContext().ensureActive()
                    if (entry.isDirectory) continue
                    // Preserve the legacy filename prefix while rejecting archive traversal.
                    val target = File(stagingDir, filename.replace(".zip", "") + entry.name.replace('\\', '/'))
                    if (!target.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)) {
                        throw InstallException("Invalid MyBible archive path")
                    }
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }

    override suspend fun downloadSearchIndex(book: Book, tempDest: KUri) {}

    override fun close() {}
}
