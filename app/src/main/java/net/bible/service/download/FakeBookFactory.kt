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
package net.bible.service.download

import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.view.activity.base.PseudoBook
import net.bible.service.sword.addBooks
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.BookMetaData
import org.crosswire.jsword.book.sword.AbstractKeyBackend
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.book.sword.SwordDictionary
import org.crosswire.jsword.book.sword.state.OpenFileState
import org.crosswire.jsword.passage.Key
import java.io.IOException
import java.util.*

class NullOpenFileState(val metadata: SwordBookMetaData): OpenFileState {
    override fun close() = Unit
    override fun getBookMetaData(): BookMetaData = metadata
    override fun releaseResources() = Unit
    override fun getLastAccess(): Long = 0L
    override fun setLastAccess(lastAccess: Long) = Unit
}

class NullKeyBackend(private val metadata: SwordBookMetaData): AbstractKeyBackend<NullOpenFileState>(metadata) {
    override fun initState(): NullOpenFileState = NullOpenFileState(metadata)
    override fun getCardinality(): Int = 1
    override fun get(index: Int): Key {
        return lastKey ?: this
    }
    var lastKey: Key? = null
    override fun indexOf(that: Key?): Int {
        lastKey = that
        return 0
    }
    override fun readRawContent(state: NullOpenFileState?, key: Key?): String = ""
}


/** Create dummy sword Books used to download from Xiphos Repo that has unusual download file case
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object FakeBookFactory {
    /** create dummy Book object for file available for download from repo
     */
    @Throws(IOException::class, BookException::class)
    fun createFakeRepoBook(module: String?, conf: String, repo: String?, type: BookCategory = BookCategory.COMMENTARY): Book {
        val sbmd = createRepoSBMD(module, conf)
        if (StringUtils.isNotEmpty(repo)) {
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repo)
        }
        return when(type) {
            BookCategory.DICTIONARY -> SwordDictionary(sbmd, NullKeyBackend(sbmd))
            else -> SwordBook(sbmd, NullBackend())
        }
    }

    /** create sbmd for file available for download from repo
     */
    @Throws(IOException::class, BookException::class)
    fun createRepoSBMD(module: String?, conf: String): SwordBookMetaData {
        val sbmd = SwordBookMetaData(conf.toByteArray(), module)
        val fake = SwordBookDriver.instance()
        sbmd.driver = fake
        return sbmd
    }

    private var _compareDocument: Book? = null
    private var _multiDocument: Book? = null
    private var _journalDocument: Book? = null
    private var _myNotesDocument: Book? = null

    private var _doesNotExistBooks: MutableMap<String, Book> = TreeMap()

    val multiDocument: Book
        get() =
            _multiDocument ?: createFakeRepoBook("Multi", MULTI_DUMMY_CONF, "").apply {
                _multiDocument = this
            }

    fun giveDoesNotExist(id: String, type: BookCategory = BookCategory.COMMENTARY): Book =
            _doesNotExistBooks["$id-$type"] ?: createFakeRepoBook(id, doesNotExistConf(id, type), "", type).apply {
                _doesNotExistBooks["$id-$type"] = this
            }

    val compareDocument: Book
        get() =
            _compareDocument ?: createFakeRepoBook("Compare", COMPARE_DUMMY_CONF, "").apply {
                _compareDocument = this
            }

    val journalDocument: Book
        get() =
            _journalDocument ?: createFakeRepoBook("Journal", JOURNAL_DUMMY_CONF, "").apply {
                _journalDocument = this
            }

    val myNotesDocument: Book
        get() =
            _myNotesDocument ?: createFakeRepoBook("My Note", MY_NOTE_DUMMY_CONF, "").apply {
                _myNotesDocument = this
            }

    private val JOURNAL_DUMMY_CONF get() = """[MyStudyPads]
Description=${application.getString(R.string.journal_description)}
Abbreviation=${application.getString(R.string.studypads)}
Encoding=UTF-8
Category=Generic Books
LCSH=Bible--Commentaries.
Versification=KJVA"""

    private val COMPARE_DUMMY_CONF get() = """[Compare]
Description=${application.getString(R.string.compare_description)}
Abbreviation=${application.getString(R.string.compare_abbreviation)}
Category=Commentaries
Feature=StrongsNumbers
Encoding=UTF-8
LCSH=Bible--Commentaries.
Versification=KJVA"""


    private val MULTI_DUMMY_CONF get() = """[Multi]
Description=${application.getString(R.string.multi_description)}
Abbreviation=${application.getString(R.string.multi_abbreviation)}
Category=Generic Books
Encoding=UTF-8
LCSH=Bible--Commentaries.
Versification=KJVA"""

    private val MY_NOTE_DUMMY_CONF get() = """[MyNote]
Description=${application.getString(R.string.my_notes_description)}
Abbreviation=${application.getString(R.string.my_notes_abbreviation)}
Category=Commentaries
Feature=StrongsNumbers
Encoding=UTF-8
LCSH=Bible--Commentaries.
Versification=KJVA"""

    private fun doesNotExistConf(id: String, type: BookCategory) = """[$id]
Description=$id
Abbreviation=$id
Category=${type.getName()}
Encoding=UTF-8
LCSH=Bible--Commentaries.
AndBibleDoesNotExist=1
Versification=KJVA"""

    private fun getPseudoBookConf(modName: String, suggested: String) = """[$modName]
Description=This popular translation is not available due to Copyright Holder not granting us distribution permission. $suggested
Abbreviation=$modName
Category=Biblical Texts
AndBiblePseudoBook=1
Language=en
Version=0.0
Encoding=UTF-8
LCSH=Bible
Versification=KJVA"""

    private fun getPseudoBook(modName: String, suggested: String) = createFakeRepoBook(modName, getPseudoBookConf(modName, suggested), "Not Available")

    fun pseudoDocuments(l: List<PseudoBook>?): List<Book> = l?.map { getPseudoBook(it.id, it.suggested) }?: emptyList()

    val pseudoDocuments: List<Book> get() = listOf(myNotesDocument, journalDocument, compareDocument)

    init {
        addBooks()
    }
}

val Book.isPseudoBook get() = bookMetaData.getProperty("AndBiblePseudoBook") != null
val Book.doesNotExist get() = bookMetaData.getProperty("AndBibleDoesNotExist") != null
