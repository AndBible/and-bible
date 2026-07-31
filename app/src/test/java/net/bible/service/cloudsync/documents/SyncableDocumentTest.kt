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
package net.bible.service.cloudsync.documents

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Document sync ships modules as SWORD archives, so it must only ever consider books backed by real
 * files on disk. [isSyncableDocument] is the single gate for that, consulted by both the periodic
 * scan and the auto-upload-on-install path.
 *
 * Books are built from byte-array metadata (no on-disk conf, no backend I/O), which is exactly how
 * the app registers the pseudo/MyDocument kinds these tests exclude.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SyncableDocumentTest {
    private fun book(initials: String, extraConf: String = ""): Book {
        val conf = """
            [$initials]
            Description=$initials
            Abbreviation=$initials
            Category=Generic Books
            ModDrv=RawGenBook
            DataPath=./modules/genbook/rawgenbook/$initials/$initials
            Encoding=UTF-8
            Versification=KJVA
            $extraConf
        """.trimIndent()
        return SwordBook(SwordBookMetaData(conf.toByteArray(), initials), NullBackend())
    }

    /** A normal file-backed SWORD module is the case document sync exists for. */
    @Test
    fun ordinarySwordModuleIsSyncable() {
        assertTrue(book("TestModule").isSyncableDocument)
    }

    /**
     * OSTicket 3392: MyDocument books (`AIDocuments`, `MyDoc_*`) are rows in MyDocumentDatabase
     * registered into JSword with byte-array metadata. They have no `configFile`, so packaging one
     * threw `NullPointerException` from `BackupControl.addBookToZip` and surfaced to the user as
     * "An error has occurred". They are already covered by the database sync, so document sync must
     * never look at them.
     */
    @Test
    fun myDocumentBookIsNotSyncable() {
        assertFalse(book("MyDoc_Dokumentti", "AndBibleMyDocument=1").isSyncableDocument)
        assertFalse(book("AIDocuments", "AndBibleMyDocument=1").isSyncableDocument)
    }

    /** Pseudo books are placeholders for unavailable documents — there is nothing to upload. */
    @Test
    fun pseudoBookIsNotSyncable() {
        assertFalse(book("ESV", "AndBiblePseudoBook=1").isSyncableDocument)
    }
}
