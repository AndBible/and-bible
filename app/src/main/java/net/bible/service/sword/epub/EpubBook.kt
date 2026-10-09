/*
 * Copyright (c) 2022-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.sharedcore.log.Log
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.database.EpubDatabase
import net.bible.android.database.epubMigrations
import net.bible.service.db.buildAppDatabase
import net.bible.service.db.deleteAppDatabase
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
import org.crosswire.jsword.passage.DefaultKeyList
import org.crosswire.jsword.passage.Key
import org.jdom2.input.JDOMParseException
import java.io.File
import java.io.IOException

fun getConfig(
    initials: String,
    abbreviation: String,
    description: String,
    language: String,
    version: Long,
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
Version=$version
Encoding=UTF-8
SourceType=OSIS
ModDrv=RawGenBook
DistributionLicense=Unknown
About=$about
"""

const val TAG = "EpubBook"

/** A regenerable cache; a corrupt file is moved aside and recreated empty like every app database ([buildAppDatabase]). */
fun getEpubDatabase(name: String): EpubDatabase = buildAppDatabase<EpubDatabase>(name, *epubMigrations)

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
    fun getKey(originalKey: String, htmlId: String): Key? = state.getKey(originalKey, htmlId)
    override fun get(index: Int): Key = state.get(index)
    override fun indexOf(that: Key?): Int = state.indexOf(that!!)

    val tocKeys: List<Key> get() = state.tocKeys

    fun getResource(resourcePath: String): File = state.getResource(resourcePath)
    fun styleSheets(key: Key): List<File> = state.styleSheets(key)
    override fun readRawContent(state: EpubBackendState, key: Key?): String = state.read(key!!)
    fun delete() = state.delete()
    fun getOrdinalRange(key: Key) = state.getOrdinalRange(key)
    val bookOrdinalSpan get() = state.bookOrdinalSpan
    fun fragmentOffset(key: Key) = state.fragmentOffset(key)
    val totalCharacters get() = state.totalCharacters
}

val epubBookType = object: BookType("EpubBook", BookCategory.GENERAL_BOOK, KeyType.TREE) {
    override fun getBook(sbmd: SwordBookMetaData, backend: Backend<*>): Book =
        SwordGenBook(sbmd, backend)
    override fun getBackend(sbmd: SwordBookMetaData): Backend<*> {
        val state = EpubBackendState(File(sbmd.location), sbmd)
        return EpubBackend(state, sbmd)
    }
}

/**
 * Remove an EPUB module completely: the (external) epub directory *and* its Room database
 * in internal storage. The database must be deleted explicitly — [File.deleteRecursively] on
 * the epub dir leaves the database orphaned, and a later re-download of a same-named epub
 * would reuse it via [getEpubDatabase], resurrecting stale fragment rows that reference
 * fragment files the fresh optimization never wrote (which then crash the reader).
 */
fun deleteEpubModule(epubDir: File) {
    epubDir.deleteRecursively()
    val appDbFilename = "epub-${epubInitials(epubDir.name)}.sqlite3"
    deleteAppDatabase(appDbFilename)
}

fun addEpubBook(epubDir: File) {
    if(!(epubDir.canRead() && epubDir.isDirectory)) return

    val optimizeLockFile = File(epubDir, "optimize.lock")
    if(optimizeLockFile.exists()) {
        // Optimization has failed, better we remove module so that
        // it does crash every time. Hoping user also sends bug report about crash...
        deleteEpubModule(epubDir)
        return
    }

    val state = EpubBackendState(epubDir)
    val metadata = state.getBookMetaData()
    if(Books.installed().getBook(metadata.initials) != null) return
    val backend = EpubBackend(state, metadata)
    val book = SwordGenBook(metadata, backend)

    // NO indexStatus re-derivation here. `EpubBackendState`'s init (:254-256) has already set it from
    // the FTS5 table, which is the only index an EPUB ever has; the LUCENE check that used to run here
    // can never be true for an EPUB, so it forced UNDONE onto every manually-installed EPUB and made
    // its search silently unreachable (finding F43).
    //
    // This fixes the MANUALLY-INSTALLED path only. The SWORD-installed (repo-downloaded) path
    // `epubBookType.getBackend` never had this overwrite, but is NOT therefore correct: it reaches
    // `EpubBackendState`'s SECONDARY constructor (`EpubBackendState.kt:66-68`), whose body assigns
    // `_metadata` only AFTER the primary constructor's initializers and both `init` blocks have run
    // — so the FTS5-derived assignment at `EpubBackendState.kt:255` writes `indexStatus` onto a
    // throwaway lazily-built `SwordBookMetaData` that is then discarded when the real sbmd arrives.
    // A repo-installed EPUB therefore still reads UNDONE regardless of its FTS5 index. That is a
    // real, still-OPEN defect (initialization ORDER, not this hunk); fixing it means reordering that
    // constructor, which is its own round — see `docs/superpowers/status/compose-port-status.md`.

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
            deleteEpubModule(f)
            ok = false
        } catch (e: IOException) {
            Log.e(TAG, "addEpubBook catched IOException", e)
            deleteEpubModule(f)
            ok = false
        } catch (e: Exception) {
            Log.e(TAG, "addEpubBook catched another exception", e)
            deleteEpubModule(f)
            ok = false
        }
    }
    return ok
}

val Book.isManuallyInstalledEpub get() = bookMetaData.getProperty("AndBibleEpubModule") != null
val Book.isEpub get() = isManuallyInstalledEpub || ((bookMetaData as? SwordBookMetaData)?.bookType == epubBookType)
val Book.epubBackend get() = (this as? SwordGenBook)?.backend as? EpubBackend
val Book.epubDir get() = bookMetaData.getProperty("AndBibleEpubDir")
