/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.common

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.SwordDocumentInfo
import net.bible.service.db.DatabaseContainer
import net.bible.service.sword.persistCipherKey
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * iOS H2 Task 6 (FeaturePathsUITests.testCipherWrongRetryRightPersists): a passphrase the user enters in the unlock dialog
 * must survive into the next process. The row that carries it (`SwordDocumentInfo`) is written on install and wiped by a
 * database reset or restore while the module stays installed; the dialog's store step then wrote nothing and the next
 * launch asked for the passphrase again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CipherKeyPersistenceTest {
    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()

    @After
    fun tearDown() { resetDatabase() }

    /** RED today: no row for an installed module, so the key is dropped (the stored-key read is `getUnlocked()`). */
    @Test
    fun `passphrase is stored for an installed module that has no row`() = runBlocking {
        val book = Books.installed().getBooks().first()
        docDao.deleteByOsisId(book.initials)
        assertEquals(null, docDao.getBook(book.initials))

        persistCipherKey(book, "k4-Test-key")

        val row = docDao.getBook(book.initials)
        assertNotNull("the key must create the missing row", row)
        assertEquals("k4-Test-key", row!!.cipherKey)
        assertEquals(listOf(book.initials), docDao.getUnlocked().map { it.initials })
    }

    /** Control, green today: with a row the key is written (guards the fix against breaking the common path). */
    @Test
    fun `passphrase replaces the key of an existing row and keeps its other fields`() = runBlocking {
        val book = Books.installed().getBooks().first()
        docDao.insert(SwordDocumentInfo(book.initials, book.name, book.abbreviation, book.language.name, "Repo", "old"))

        persistCipherKey(book, "new")

        val row = docDao.getBook(book.initials)!!
        assertEquals("new", row.cipherKey)
        assertEquals("Repo", row.repository)
    }
}
