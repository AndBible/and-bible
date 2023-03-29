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

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.database.repoJson
import net.bible.service.common.CommonUtils.unzipFile
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
import javax.net.ssl.HttpsURLConnection

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

class MyBibleInstaller(private val manifestUrl: String): Installer, AbstractBookList() {
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

    private val downloader = GenericFileDownloader()
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
        metadata.driver = fakeDriver
        SwordBook(metadata, nullBackend)
    }.toMutableList()

    override fun getInstallerDefinition(): String {
        return "mybible-manifest:$manifestUrl"
    }

    override fun toRemoteURI(book: Book): URI = URI(book.myBibleDownloadUrl)

    override fun getBook(name: String): Book? = books.find { it.name == name }

    override fun getSize(book: Book): Int {
        // Note: This is completely unused in JSword API currently
        val url = URL(book.myBibleDownloadUrl)
        return NetUtil.getSize(url.toURI())
    }

    // Note: This is completely unused in JSword API currently
    override fun isNewer(book: Book): Boolean = false

    override fun reloadBookList() {
        val url = URL(manifestUrl)
        downloader.downloadFileSync(url, localManifestFile, "MyBible manifest")
        spec = readSpec()
    }

    override fun indexLastUpdated(): Long {
        val url = URL(manifestUrl)
        return try {
            val httpsURLConnection = url.openConnection() as HttpsURLConnection
            val lastModified: Long = httpsURLConnection.lastModified
            httpsURLConnection.disconnect()
            lastModified
        } catch (e: Exception) {
            Log.e(TAG, "Could not check last modified time for $url")
            0L
        }
    }

    override fun install(book: Book) = install(book, null)

    override fun install(book: Book, jobId: String?) {
        Log.i(TAG, "Installing MyBible book ${book.initials}, $jobId")
        val tmpDir = File(application.cacheDir, "mybible")
        tmpDir.mkdirs()
        val url = book.myBibleDownloadUrl
        val moduleSpec = moduleSpecs.find { it.download_url == url } ?: throw InstallException("Spec error")
        val tmpFile = File(tmpDir, moduleSpec.file_name)
        val success = downloader.downloadFileSync(URL(url), tmpFile,
            jobId = jobId,
            description = "MyBible manifest",
            notifyUser = true
        )
        if(success && tmpFile.canRead()) {
            val dir = File(myBibleDir, book.initials)
            dir.mkdirs()
            unzipFile(tmpFile, dir, filePrefix = moduleSpec.file_name.replace(".zip", ""))
            tmpFile.delete()
            addManuallyInstalledMyBibleBooks()
        } else {
            throw DownloadCancelledException(URI(url))
        }
    }

    override fun downloadSearchIndex(book: Book, tempDest: URI) {}

    override fun close() {}
}
