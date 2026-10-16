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
import net.bible.android.SharedConstants
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
import java.io.File
import java.net.URI
import java.net.URL
import java.io.IOException
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
    private val myBibleDir = File(SharedConstants.modulesDir, "mybible")
    private val localManifestFile = File(myBibleDir, "manifest.json")
    private var spec: MyBibleRepositorySpec? = readSpec()
    private val moduleSpecs get() = spec?.modules?.filter { it.download_url.startsWith("https://") }?: emptyList()
    private fun readSpec(): MyBibleRepositorySpec? {
        if(localManifestFile.canRead()) {
            val jsonString = String(localManifestFile.inputStream().readBytes())
            return try {
                val spec = MyBibleRepositorySpec.fromJson(jsonString)
                for(modSpec in spec.modules) {
                    modSpec.download_url = modSpec.download_url.replace("http://", "https://")
                }
                spec
            } catch (e: SerializationException) {
                localManifestFile.delete()
                null
            }
        }
        return null
    }

    private val fakeDriver = SwordBookDriver.instance()
    private val nullBackend = NullBackend()

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
        val initials = "MyBible-" + sanitizeModuleName(File(it.file_name).nameWithoutExtension)
        val abbreviation = File(it.file_name).nameWithoutExtension.split(".", limit = 2)[0]
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

    override fun toRemoteURI(book: Book): URI = URI(book.myBibleDownloadUrl)

    override fun getBook(name: String): Book? = getBooks().find { it.name == name }

    override suspend fun getSize(book: Book): Int {
        // Note: This is completely unused in JSword API currently
        val url = URL(book.myBibleDownloadUrl)
        return NetUtil.getSize(url.toURI())
    }

    // Note: This is completely unused in JSword API currently
    override suspend fun isNewer(book: Book): Boolean = false

    override suspend fun reloadBookList() {
        val url = URL(manifestUrl)
        downloader.downloadFileWithProgress(url, localManifestFile, "MyBible manifest")
        spec = readSpec()
    }

    override suspend fun indexLastUpdated(): Long = downloader.lastModified(URL(manifestUrl)) ?: 0L

    override suspend fun install(book: Book) = install(book, null)

    override suspend fun install(book: Book, jobId: String?) {
        Log.i(TAG, "Installing MyBible book ${book.initials}, $jobId")
        val tmpDir = File(environment.cacheDir, "mybible")
        tmpDir.mkdirs()
        val url = book.myBibleDownloadUrl
        val moduleSpec = moduleSpecs.find { it.download_url == url } ?: throw InstallException("Spec error")
        val tmpFile = File(tmpDir, moduleSpec.file_name)
        val success = downloader.downloadFileWithProgress(URL(url), tmpFile,
            jobId = jobId,
            description = "MyBible manifest",
            notifyUser = true
        )
        if(success && tmpFile.canRead()) {
            val staging = File.createTempFile("install-", "", tmpDir).apply {
                if (!delete() || !mkdir()) throw InstallException("Unable to stage MyBible module")
            }
            try {
                prepareModuleFiles(tmpFile, staging, moduleSpec.file_name)
                val modules = staging.walkTopDown().filter { it.isFile && it.extension.equals("SQLite3", true) }.toList()
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
                val dir = File(myBibleDir, book.initials)
                dir.mkdirs()
                modules.forEach { file ->
                    val target = File(dir, file.relativeTo(staging).path)
                    target.parentFile?.mkdirs()
                    file.copyTo(target, overwrite = true)
                }
                addManuallyInstalledMyBibleBooks()
            } catch (e: IOException) {
                throw InstallException("Unable to install MyBible module", e)
            } finally {
                staging.deleteRecursively()
                tmpFile.delete()
            }
        } else {
            throw DownloadCancelledException(URI(url))
        }
    }

    private suspend fun prepareModuleFiles(download: File, staging: File, filename: String) {
        val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val header = ByteArray(sqliteHeader.size)
        download.inputStream().use { java.io.DataInputStream(it).readFully(header) }
        if (header.contentEquals(sqliteHeader)) {
            download.copyTo(File(staging, File(filename).name))
        } else {
            ZipFile(download).use { zip ->
                for (entry in zip.entries()) {
                    currentCoroutineContext().ensureActive()
                    if (entry.isDirectory) continue
                    // Preserve the legacy filename prefix while rejecting archive traversal.
                    val target = File(staging, filename.replace(".zip", "") + entry.name.replace('\\', '/'))
                    if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                        throw InstallException("Invalid MyBible archive path")
                    }
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }

    override suspend fun downloadSearchIndex(book: Book, tempDest: URI) {}

    override fun close() {}
}
