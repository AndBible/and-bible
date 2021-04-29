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
import org.apache.commons.lang3.StringUtils
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookException
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaData
import java.io.IOException

/** Create dummy sword Books used to download from Xiphos Repo that has unusual download file case
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
object FakeBookFactory {
    /** create dummy Book object for file available for download from repo
     */
    @JvmStatic
    @Throws(IOException::class, BookException::class)
    fun createFakeRepoSwordBook(module: String?, conf: String, repo: String?): SwordBook {
        val sbmd = createRepoSBMD(module, conf)
        if (StringUtils.isNotEmpty(repo)) {
            sbmd.setProperty(DownloadManager.REPOSITORY_KEY, repo)
        }
        return SwordBook(sbmd, NullBackend())
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

    val multiDocument: Book
        get() =
            _multiDocument ?: createFakeRepoSwordBook("Multi", MULTI_DUMMY_CONF, "").apply {
                _multiDocument = this
            }

    val compareDocument: Book
        get() =
            _compareDocument ?: createFakeRepoSwordBook("Compare", COMPARE_DUMMY_CONF, "").apply {
                _compareDocument = this
            }

    val journalDocument: Book
        get() =
            _journalDocument ?: createFakeRepoSwordBook("Journal", JOURNAL_DUMMY_CONF, "").apply {
                _journalDocument = this
            }

    val myNotesDocument: Book
        get() =
            _myNotesDocument ?: createFakeRepoSwordBook("My Note", MY_NOTE_DUMMY_CONF, "").apply {
                _myNotesDocument = this
            }

    private val JOURNAL_DUMMY_CONF get() = """[MyStudyPads]
Description=${application.getString(R.string.journal_description)}
Abbreviation=${application.getString(R.string.studypads)}
Category=Generic Books
LCSH=Bible--Commentaries.
Versification=KJVA"""

    private val COMPARE_DUMMY_CONF get() = """[Compare]
Description=${application.getString(R.string.compare_description)}
Abbreviation=${application.getString(R.string.compare_abbreviation)}
Category=Commentaries
LCSH=Bible--Commentaries.
Versification=KJVA"""


    private val MULTI_DUMMY_CONF get() = """[Multi]
Description=${application.getString(R.string.multi_description)}
Abbreviation=${application.getString(R.string.multi_abbreviation)}
Category=Generic Books
LCSH=Bible--Commentaries.
Versification=KJVA"""

    private val MY_NOTE_DUMMY_CONF get() = """[MyNote]
Description=${application.getString(R.string.my_notes_description)}
Abbreviation=${application.getString(R.string.my_notes_abbreviation)}
Category=Commentaries
LCSH=Bible--Commentaries.
Versification=KJVA"""

    val pseudoDocuments: List<Book> get() = listOf(myNotesDocument, journalDocument, compareDocument)
}

