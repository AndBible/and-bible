/*
 * Copyright (c) 2022-2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.epub

import androidx.room.Room
import androidx.room.RoomDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.database.EpubDatabase
import net.bible.android.database.epubMigrations
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.KeyType
import org.crosswire.jsword.book.basic.AbstractBookDriver
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.Backend
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.IndexStatus
import org.crosswire.jsword.passage.DefaultKeyList
import org.crosswire.jsword.passage.Key
import java.io.File

fun getConfig(
    initials: String,
    abbreviation: String,
    description: String,
    language: String,
    about: String,
    path: String,
): String = """
[$initials]
Description=$description
Abbreviation=$abbreviation
Category=${BookCategory.GENERAL_BOOK.name}
AndBibleEpubModule=1
AndBibleEpubDir=$path
Lang=$language
Version=0.0
Encoding=UTF-8
SourceType=OSIS
ModDrv=RawGenBook
About=$about
"""

const val TAG = "EpubBook"

val dbFactory = if(application.isRunningTests) null else RequerySQLiteOpenHelperFactory()
fun getEpubDatabase(name: String): EpubDatabase =
    Room.databaseBuilder(
        application, EpubDatabase::class.java, name
    )
        .allowMainThreadQueries()
        .addMigrations(*epubMigrations)
        .openHelperFactory(dbFactory)
        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
        .build()

class EpubSwordDriver: AbstractBookDriver() {
    override fun getBooks(): Array<Book> {
        return emptyArray()
    }

    override fun getDriverName(): String {
        return "EpubSwordDriver"
    }

    override fun isDeletable(book: Book): Boolean {
        return true
    }

    override fun delete(book: Book) {
        ((book as? SwordGenBook)?.backend as? EpubBackend)?.delete()
        Books.installed().removeBook(book)
    }
}

class EpubBackend(val state: EpubBackendState, metadata: SwordBookMetaData): AbstractKeyBackend<EpubBackendState>(metadata) {
    override fun initState(): EpubBackendState = state
    override fun readIndex(): Key {
        val key = DefaultKeyList(null, bookMetaData.name)
        for(i in iterator()) {
            key.addAll(i)
        }
        return key
    }
    override fun getCardinality(): Int = state.optimizedCardinality

    override fun iterator(): MutableIterator<Key> =
        state.optimizedKeys
            .toMutableList()
            .iterator()
    fun getKey(originalKey: String, htmlId: String): Key = state.getKey(originalKey, htmlId)
    override fun get(index: Int): Key = state.getFromOptimizedIndex(index)
    override fun indexOf(that: Key): Int = state.indexOfOptimizedKey(that)
    fun getResource(resourcePath: String): File = state.getResource(resourcePath)
    fun styleSheets(key: Key): List<File> = state.styleSheetsForOptimizedKey(key)
    override fun readRawContent(state: EpubBackendState, key: Key): String =
        state.readOptimized(key)
    fun delete() = state.delete()
}

val epubBookType = object: BookType("EpubBook", BookCategory.GENERAL_BOOK, KeyType.TREE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book =
        SwordGenBook(sbmd, backend)
    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val state = EpubBackendState(File(sbmd.location), sbmd)
        return EpubBackend(state, sbmd)
    }
}

fun addEpubBook(file: File) {
    if(!(file.canRead() && file.isDirectory)) return
    val state = EpubBackendState(file)
    val metadata = state.bookMetaData
    if(Books.installed().getBook(metadata.initials) != null) return
    val backend = EpubBackend(state, metadata)
    val book = SwordGenBook(metadata, backend)

    if(IndexManagerFactory.getIndexManager().isIndexed(book)) {
        metadata.indexStatus = IndexStatus.DONE
    } else {
        metadata.indexStatus = IndexStatus.UNDONE
    }

    Books.installed().addBook(book)
}

fun addManuallyInstalledEpubBooks() {
    val dir = File(SharedConstants.modulesDir, "epub")
    dir.mkdirs()
    if(!(dir.isDirectory && dir.canRead())) return

    for(f in dir.listFiles()!!) {
        addEpubBook(f)
    }
}

val Book.isManuallyInstalledEpub get() = bookMetaData.getProperty("AndBibleEpubModule") != null
val Book.isEpub get() = isManuallyInstalledEpub || ((bookMetaData as? SwordBookMetaData)?.bookType == epubBookType)
val Book.epubDir get() = bookMetaData.getProperty("AndBibleEpubDir")
