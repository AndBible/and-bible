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
package net.bible.android.control.versification

import android.util.Log
import net.bible.android.database.SwordDocumentInfo
import net.bible.service.common.AndBibleAddons
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.DownloadManager
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BooksEvent
import org.crosswire.jsword.book.BooksListener
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.versification.VersificationsMapper

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object BookInstallWatcher {
    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
    fun startListening() {
        Books.installed().addBooksListener(object : BooksListener {
            override fun bookAdded(ev: BooksEvent) {
                val book = ev.book
                initialiseRequiredMapping(book)
                addBookToDb(book)
                AndBibleAddons.clearCaches()
                SwordContentFacade.clearCaches()
            }

            override fun bookRemoved(ev: BooksEvent) {
                AndBibleAddons.clearCaches()
                removeBookFromDb(ev.book)
                SwordContentFacade.clearCaches()
            }
        })
    }
    private fun addBookToDb(book: Book) {
        // if book is already installed, we remove it, else it deletes nothing
        docDao.deleteByOsisId(book.initials)
        Log.i(DownloadManager.TAG, "Adding ${book.name} to document backup database")
        // insert the new book info into backup db
        docDao.insert(SwordDocumentInfo(
            book.initials,
            book.name,
            book.abbreviation,
            book.language.name,
            ""
        ))
    }

    private fun removeBookFromDb(book: Book) {
        docDao.deleteByOsisId(book.initials)
    }

    /**
     * pre-initialise mappings to prevent pauses during interaction
     */
    @Synchronized
    private fun initialiseRequiredMapping(book: Book) {
        if (book is SwordBook) {
            val versification = book.versification
            // initialise in a background thread to allow normal startup to continue
            Thread {
                Log.i(TAG, "AVMAP Initialise v11n mappings for " + versification.name)
                VersificationsMapper.instance().ensureMappingDataLoaded(versification)
            }.start()
        }
    }

    private const val TAG = "BookInstallWatcher"
}
