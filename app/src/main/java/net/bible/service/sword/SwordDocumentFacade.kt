/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.sword

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.bible.service.common.CommonUtils
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

/** JSword facade
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object SwordDocumentFacade {
    val bibles: List<Book>
        get() {
            Log.d(TAG, "Getting bibles")
            val documents = Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE }
            Log.d(TAG, "Got bibles, Num=" + documents.size)
            return documents
        }

    val unlockedBibles: List<Book>
        get() {
            Log.d(TAG, "Getting bibles")
            val documents = Books.installed().getBooks { it.bookCategory == BookCategory.BIBLE  && !it.isLocked}
            Log.d(TAG, "Got bibles, Num=" + documents.size)
            return documents
        }

    fun getBooks(bookCategory: BookCategory): List<Book> {
        Log.d(TAG, "Getting books of type " + bookCategory.getName())
        val documents = Books.installed().getBooks { it.bookCategory == bookCategory }
        Log.d(TAG, "Got books, Num=" + documents.size)
        return documents
    }

	// currently only bibles and commentaries are supported
    /** return all supported documents - bibles and commentaries for now
     */
    val documents: List<Book>
        get() {
            Log.d(TAG, "Getting books")
            // currently only bibles and commentaries are supported
            val allDocuments = Books.installed().getBooks(SUPPORTED_DOCUMENT_TYPES)
            Log.d(TAG, "Got books, Num=" + allDocuments.size)
            return allDocuments
        }

    private fun getDictionaries(keyName: String, fakeBookName: String, type: FeatureType): List<Book> {
        val bookInitials = CommonUtils.settings.getStringSet(keyName, null)
        if(bookInitials != null) {
            return bookInitials.mapNotNull{ Books.installed().getBook(it)}
        }
        val dictionaries = Books.installed().books.filter { it.hasFeature(type) }
        if(dictionaries.isNotEmpty()) return dictionaries
        return listOf(FakeBookFactory.giveDoesNotExist(fakeBookName, BookCategory.DICTIONARY))
    }

    val defaultRobinsonGreekMorphology: List<Book> get() =
        getDictionaries("robinson_greek_morphology","Robinson", FeatureType.GREEK_PARSE)
    val defaultStrongsGreekDictionary: List<Book> get() =
        getDictionaries("strongs_greek_dictionary", "StrongsGreek", FeatureType.GREEK_DEFINITIONS)
    val defaultStrongsHebrewDictionary: List<Book> get() =
        getDictionaries("strongs_hebrew_dictionary", "StrongsHebrew", FeatureType.HEBREW_DEFINITIONS)

    val defaultBibleWithStrongs: Book? get() = bibles
        .sortedWith(compareBy({ !it.hasFeature(FeatureType.STRONGS_NUMBERS) }, { it.indexStatus != IndexStatus.DONE }))
        .firstOrNull()

    fun getDocumentByInitials(initials: String?): Book? {
        return Books.installed().getBook(initials)
    }

    @Throws(InstallException::class)
    suspend fun getDownloadableDocuments(repoFactory: RepoFactory, refresh: Boolean): MutableList<Book>  = coroutineScope{
        Log.d(TAG, "Getting downloadable documents.  Refresh:$refresh")
        return@coroutineScope try {
			// there are so many sbmd's to load that we can only load what is required for the display list.
			// If About is selected or a document is downloaded the sbmd is then loaded fully.
            SwordBookMetaData.setPartialLoading(true)
            val repoBookDeduplicator = RepoBookDeduplicator()

            val promises = repoFactory.repositories
                .map { async { it.getRepoBooks(refresh) } }
                .toList()

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
            Log.e(TAG, "Error deleting document index", e)
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
        Log.d(TAG, "ensureIndexCreation")
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
    private val SUPPORTED_DOCUMENT_TYPES: BookFilter = AcceptableBookTypeFilter()
    private val TAG = "DocFacade"
}
