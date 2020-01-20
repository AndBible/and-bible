/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.service.sword

import net.bible.android.control.ApplicationScope
import net.bible.service.common.CommonUtils.isHttpUrlAvailable
import net.bible.service.common.Logger
import net.bible.service.download.DownloadManager
import net.bible.service.download.RepoBookDeduplicator
import net.bible.service.download.RepoFactory
import net.bible.service.sword.index.IndexCreator
import org.crosswire.common.util.Version
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.BookFilter
import org.crosswire.jsword.book.BookFilters
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.Defaults
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.install.InstallException
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.IndexStatus
import java.util.*
import javax.inject.Inject

/** JSword facade
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class SwordDocumentFacade @Inject constructor(private val repoFactory: RepoFactory) {
    val bibles: List<Book>
        get() {
            log.debug("Getting bibles")
            val documents = Books.installed().getBooks(BookFilters.getBibles())
            log.debug("Got bibles, Num=" + documents.size)
            return documents
        }

    fun getBooks(bookCategory: BookCategory): List<Book> {
        log.debug("Getting books of type " + bookCategory.getName())
        val documents = Books.installed().getBooks { book -> book.bookCategory == bookCategory && !book.isLocked }
        log.debug("Got books, Num=" + documents.size)
        return documents
    }

	// currently only bibles and commentaries are supported
    /** return all supported documents - bibles and commentaries for now
     */
    val documents: List<Book>
        get() {
            log.debug("Getting books")
            // currently only bibles and commentaries are supported
            val allDocuments = Books.installed().getBooks(SUPPORTED_DOCUMENT_TYPES)
            log.debug("Got books, Num=" + allDocuments.size)
            return allDocuments
        }// default to StrongsRealGreek or StrongsGreek

    /** prefer the Real alternatives to the default versions because they contain the native Greek Hebrew words
     */
    val defaultStrongsGreekDictionary: Book
        get() { // default to StrongsRealGreek or StrongsGreek
            val preferredBooks = arrayOf("StrongsRealGreek", "StrongsGreek")
            for (prefBook in preferredBooks) {
                val strongs = Books.installed().getBook(prefBook)
                if (strongs != null) {
                    return strongs
                }
            }
            return Defaults.getGreekDefinitions()
        }

    // default to StrongsRealHebrew or StrongsHebrew
    val defaultStrongsHebrewDictionary: Book
        get() { // default to StrongsRealHebrew or StrongsHebrew
            val preferredBooks = arrayOf("StrongsRealHebrew", "StrongsHebrew")
            for (prefBook in preferredBooks) {
                val strongs = Books.installed().getBook(prefBook)
                if (strongs != null) {
                    return strongs
                }
            }
            return Defaults.getHebrewDefinitions()
        }

    val defaultBibleWithStrongs: Book?
        get() {
            val bibles = bibles
            for (book in bibles) {
                if (book.hasFeature(FeatureType.STRONGS_NUMBERS)) {
                    if (book.indexStatus == IndexStatus.DONE) {
                        return book
                    }
                }
            }
            return null
        }

    fun getDocumentByInitials(initials: String?): Book? {
        return Books.installed().getBook(initials)
    }

    @Throws(InstallException::class)
    fun getDownloadableDocuments(refresh: Boolean): List<Book> {
        log.debug("Getting downloadable documents.  Refresh:$refresh")
        return try {
			// there are so many sbmd's to load that we can only load what is required for the display list.
			// If About is selected or a document is downloaded the sbmd is then loaded fully.
            SwordBookMetaData.setPartialLoading(true)
            val repoBookDeduplicator = RepoBookDeduplicator()
            repoBookDeduplicator.addAll(repoFactory.andBibleRepo.getRepoBooks(refresh))
            repoBookDeduplicator.addAll(repoFactory.ibtRepo.getRepoBooks(refresh))
            repoBookDeduplicator.addAll(repoFactory.crosswireRepo.getRepoBooks(refresh))
            repoBookDeduplicator.addAll(repoFactory.eBibleRepo.getRepoBooks(refresh))
            // beta repo must never override live books especially if later version so use addIfNotExists
            repoBookDeduplicator.addIfNotExists(repoFactory.betaRepo.getRepoBooks(refresh))
            val bookList = repoBookDeduplicator.books
            // get them in the correct order
            Collections.sort(bookList)
            bookList
        } finally {
            SwordBookMetaData.setPartialLoading(false)
        }
    }

    @Throws(InstallException::class, BookException::class)
    fun isIndexDownloadAvailable(document: Book): Boolean { // not sure how to integrate reuse this in JSword index download
        val version = document.bookMetaData.getProperty("Version")
        val versionSuffix = if (version != null) "-" + Version(version).toString() else ""
        val url = "http://www.crosswire.org/and-bible/indices/v1/" + document.initials + versionSuffix + ".zip"
        return isHttpUrlAvailable(url)
    }

    @Throws(InstallException::class, BookException::class)
    fun downloadIndex(document: Book?) {
        val downloadManager = DownloadManager()
        downloadManager.installIndexInNewThread(repoFactory.andBibleRepo.repoName, document)
    }

    @Throws(BookException::class)
    fun deleteDocument(document: Book) { // make sure we have the correct Book and not just a copy e.g. one from a Download Manager
        val realDocument = getDocumentByInitials(document.initials)
        // delete index first if it exists but wrap in try to ensure an attempt is made to delete the document
        try {
            val imanager = IndexManagerFactory.getIndexManager()
            if (imanager.isIndexed(realDocument)) {
                imanager.deleteIndex(realDocument)
            }
        } catch (e: Exception) { // just log index delete error, deleting doc is the important thing
            log.error("Error deleting document index", e)
        }
        document.driver.delete(realDocument)
    }

    @Throws(BookException::class)
    fun deleteDocumentIndex(document: Book?) { // make sure we have the correct Book and not just a copy e.g. one from a Download Manager
        val realDocument = getDocumentByInitials(document?.initials)
        val indexManager = IndexManagerFactory.getIndexManager()
        if (indexManager.isIndexed(realDocument)) {
            indexManager.deleteIndex(realDocument)
        }
    }

    /** this custom index creation has been optimised for slow, low memory devices
     * If an index is in progress then nothing will happen
     */
    @Throws(BookException::class)
    fun ensureIndexCreation(book: Book) {
        log.debug("ensureIndexCreation")
        // ensure this isn't just the user re-clicking the Index button
        if (book.indexStatus != IndexStatus.CREATING && book.indexStatus != IndexStatus.SCHEDULED) {
            val ic = IndexCreator()
            ic.scheduleIndexCreation(book)
        }
    }

    // SwordBookPath.setAugmentPath(new File[] {new
	// File("/data/bible")});
    private val paths: String?
        get() {
            var text = "Paths:"
            try {
				// SwordBookPath.setAugmentPath(new File[] {new
				// File("/data/bible")});
                val swordBookPaths = SwordBookPath.getSwordPath()
                for (file in swordBookPaths) {
                    text += file.absolutePath
                }
                text += "Augmented paths:"
                val augBookPaths = SwordBookPath.getAugmentPath()
                for (file in augBookPaths) {
                    text += file.absolutePath
                }
            } catch (e: Exception) {
                text += e.message
            }
            return text
        }

    companion object {
        private val SUPPORTED_DOCUMENT_TYPES: BookFilter = AcceptableBookTypeFilter()
        private val log = Logger(SwordDocumentFacade::class.java.name)
    }

}
