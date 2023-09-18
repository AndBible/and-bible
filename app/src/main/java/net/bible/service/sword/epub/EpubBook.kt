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

import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.database.EpubDatabase
import net.bible.android.database.epubMigrations
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
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
import org.jdom2.input.JDOMParseException
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
    override fun getCardinality(): Int = state.cardinality

    override fun iterator(): MutableIterator<Key> =
        state.keys
            .toMutableList()
            .iterator()
    fun getKey(originalKey: String, htmlId: String): Key = state.getKey(originalKey, htmlId)
    override fun get(index: Int): Key = state.get(index)
    override fun indexOf(that: Key): Int = state.indexOf(that)

    val tocKeys: List<Key> get() = state.tocKeys

    fun getResource(resourcePath: String): File = state.getResource(resourcePath)
    fun styleSheets(key: Key): List<File> = state.styleSheets(key)
    override fun readRawContent(state: EpubBackendState, key: Key): String = state.read(key)
    fun delete() = state.delete()
    fun getOrdinalRange(key: Key) = state.getOrdinalRange(key)
}

val epubBookType = object: BookType("EpubBook", BookCategory.GENERAL_BOOK, KeyType.TREE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book =
        SwordGenBook(sbmd, backend)
    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val state = EpubBackendState(File(sbmd.location), sbmd)
        return EpubBackend(state, sbmd)
    }
}

fun addEpubBook(epubDir: File) {
    if(!(epubDir.canRead() && epubDir.isDirectory)) return

    val optimizeLockFile = File(epubDir, "optimize.lock")
    if(optimizeLockFile.exists()) {
        // Optimization has failed, better we remove module so that
        // it does crash every time. Hoping user also sends bug report about crash...
        epubDir.deleteRecursively()
        return
    }

    val state = EpubBackendState(epubDir)
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

fun addManuallyInstalledEpubBooks(): Boolean {
    val dir = File(SharedConstants.modulesDir, "epub")
    dir.mkdirs()
    if(!(dir.isDirectory && dir.canRead())) return false
    var ok = true

    for(f in dir.listFiles()!!) {
        try {
            addEpubBook(f)
        } catch (e: JDOMParseException) {
            Log.e(TAG, "addEpubBook catched JDOMParseException", e)
            f.deleteRecursively()
            ok = false
        }
    }
    return ok
}

val Book.isManuallyInstalledEpub get() = bookMetaData.getProperty("AndBibleEpubModule") != null
val Book.isEpub get() = isManuallyInstalledEpub || ((bookMetaData as? SwordBookMetaData)?.bookType == epubBookType)
val Book.epubDir get() = bookMetaData.getProperty("AndBibleEpubDir")
