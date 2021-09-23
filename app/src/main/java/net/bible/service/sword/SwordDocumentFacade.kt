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

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.bible.android.control.ApplicationScope
import net.bible.service.common.CommonUtils
import net.bible.service.common.Logger
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.RepoBookDeduplicator
import net.bible.service.download.RepoFactory
import net.bible.service.sword.index.IndexCreator
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.BookFilter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.Defaults
import org.crosswire.jsword.book.FeatureType
import org.crosswire.jsword.book.install.InstallException
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordBookPath
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.IndexStatus
import javax.inject.Inject

/** JSword facade
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@ApplicationScope
class SwordDocumentFacade @Inject constructor() {
    val bibles: List<Book>
        get() {
            log.debug("Getting bibles")
            val documents = Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }
            log.debug("Got bibles, Num=" + documents.size)
            return documents
        }

    val unlockedBibles: List<Book>
        get() {
            log.debug("Getting bibles")
            val documents = Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE  && !it.isLocked}
            log.debug("Got bibles, Num=" + documents.size)
            return documents
        }

    fun getBooks(bookCategory: BookCategory): List<Book> {
        log.debug("Getting books of type " + bookCategory.getName())
        val documents = Books.installed().getBooks { it.bookCategory == bookCategory }
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
        }

    val defaultRobinsonGreekMorphology: Book
        get() {
            val bookInitials = CommonUtils.settings.getString("robinson_greek_morphology", null)
            if(bookInitials != null) {
                val book = Books.installed().getBook(bookInitials)
                if(book != null) return book
            }
            val preferredBooks = arrayOf("robinson")
            for (prefBook in preferredBooks) {
                val mod = Books.installed().getBook(prefBook)
                if (mod != null) {
                    return mod
                }
            }
            return Defaults.getGreekParse()?: FakeBookFactory.giveDoesNotExist("Robinson", BookCategory.DICTIONARY)
        }

    val defaultStrongsGreekDictionary: Book
        get() {
            val bookInitials = CommonUtils.settings.getString("strongs_greek_dictionary", null)
            if(bookInitials != null) {
                val book = Books.installed().getBook(bookInitials)
                if(book != null) return book
            }

            val preferredBooks = arrayOf("StrongsRealGreek", "StrongsGreek")

            for (prefBook in preferredBooks) {
                val strongs = Books.installed().getBook(prefBook)
                if (strongs != null) {
                    return strongs
                }
            }
            return Defaults.getGreekDefinitions()?: FakeBookFactory.giveDoesNotExist("StrongsGreek", BookCategory.DICTIONARY)
        }

    val defaultStrongsHebrewDictionary: Book
        get() {
            val bookInitials = CommonUtils.settings.getString("strongs_hebrew_dictionary", null)
            if(bookInitials != null) {
                val book = Books.installed().getBook(bookInitials)
                if(book != null) return book
            }

            val preferredBooks = arrayOf("StrongsRealHebrew", "StrongsHebrew")
            for (prefBook in preferredBooks) {
                val strongs = Books.installed().getBook(prefBook)
                if (strongs != null) {
                    return strongs
                }
            }
            return Defaults.getHebrewDefinitions()?: FakeBookFactory.giveDoesNotExist("StrongsHebrew", BookCategory.DICTIONARY)
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
    suspend fun getDownloadableDocuments(repoFactory: RepoFactory, refresh: Boolean): MutableList<Book>  = coroutineScope{
        log.debug("Getting downloadable documents.  Refresh:$refresh")
        return@coroutineScope try {
			// there are so many sbmd's to load that we can only load what is required for the display list.
			// If About is selected or a document is downloaded the sbmd is then loaded fully.
            SwordBookMetaData.setPartialLoading(true)
            val repoBookDeduplicator = RepoBookDeduplicator()

            val promises = mutableListOf<Deferred<List<Book>>>()
            for (r in repoFactory.normalRepositories) {
                promises.add( async { r.getRepoBooks(refresh) })
            }
            for (r in repoFactory.betaRepositories) {
                // beta repo must never override live books especially if later version so use addIfNotExists
                promises.add( async { r.getRepoBooks(refresh) })
            }

            for(l in promises.awaitAll()) {
                repoBookDeduplicator.addAll(l)
            }

            val bookList = repoBookDeduplicator.books
            // get them in the correct order
            bookList.sort()
            bookList
        } finally {
            SwordBookMetaData.setPartialLoading(false)
        }
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

    @Throws(BookException::class)
    fun hasIndex(document: Book?): Boolean { // make sure we have the correct Book and not just a copy e.g. one from a Download Manager
        val realDocument = getDocumentByInitials(document?.initials)
        val indexManager = IndexManagerFactory.getIndexManager()
        if (indexManager.isIndexed(realDocument)) {
            return true
        }
        return false
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
