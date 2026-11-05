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

package net.bible.service.sword.epub

import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidAppEnvironment
import net.bible.test.testCoreStrings
import okio.Path.Companion.toOkioPath
import org.crosswire.jsword.book.Books
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * #3947: the shared EPUB fixture ([EpubBackendStateTest]) deleted its EPUB directories and `epub-*.sqlite3`
 * databases while the [EpubBackendState]s it had opened (and the books it had registered JVM-wide) stayed live.
 * A producer that outlived its test (e.g. `BibleView.loadDocument` -> `totalCharacters`) then wrote through a
 * connection whose file had been unlinked: SQLite error 1032 (`SQLITE_READONLY_DBMOVED`), reported as an
 * uncaught exception in the next test's `runTest` setup. Cleanup must retire what the fixture owns, through
 * the production retirement path (wait for leases, close, then delete), before deleting any file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class EpubFixtureOwnershipTest {
    private val fixture = EpubBackendStateTest()
    private var tornDown = false

    private fun setUp() = fixture.setUp()
    private fun tearDown() { tornDown = true; fixture.tearDown() }

    @After fun after() { if (!tornDown) fixture.tearDown() }

    @Test fun cleanupRetiresAnOpenStateBeforeItsDatabaseIsDeleted() {
        setUp()
        val state = fixture.openState(fixture.buildEpub("fixture-owned-state"))
        tearDown()
        // A late producer must meet a retired owner, never a live connection to an unlinked file (1032).
        val failure = runCatching { runBlocking { state.totalCharacters() } }.exceptionOrNull()
        assertTrue("late write after cleanup must be refused by retirement, got $failure",
            failure is IllegalStateException && failure.message == "EPUB backend has been retired")
    }

    /**
     * Mechanism of the historical red: deleting `epub-*.sqlite3` under a connection that has already written
     * makes the next write fail with 1032 (`SQLITE_READONLY_DBMOVED`). A characterization, green before and after.
     */
    @Test fun writeAfterTheDatabaseFileIsUnlinkedFailsWith1032() {
        setUp()
        val state = fixture.openState(fixture.buildEpub("fixture-unlinked-db"))
        val db = EpubBackendState::class.java.getDeclaredField("readDb").apply { isAccessible = true }
            .get(state) as net.bible.android.database.EpubDatabase
        try {
            runBlocking { db.epubDao().insert(net.bible.android.database.EpubMeta(totalCharacters = 1)) }
            assertTrue(application.deleteDatabase(state.appDbFilename))
            val failure = runCatching {
                runBlocking { db.epubDao().insert(net.bible.android.database.EpubMeta(totalCharacters = 2)) }
            }.exceptionOrNull()
            assertTrue("expected SQLite 1032, got $failure", failure?.message?.contains("Error code: 1032") == true)
        } finally {
            state.close()
        }
    }

    /**
     * Production entry point with the same root cause: [EpubBackendState.delete] on a state that is not the
     * registered owner (never registered, or replaced) retired only the registered state, then deleted the
     * database under this one's live connection.
     */
    @Test fun deletingAnUnregisteredStateRetiresItBeforeItsDatabaseGoes() {
        setUp()
        val state = fixture.openState(fixture.buildEpub("unregistered-delete"))
        state.delete()
        val failure = runCatching { runBlocking { state.totalCharacters() } }.exceptionOrNull()
        assertTrue("a deleted state must be retired, got $failure",
            failure is IllegalStateException && failure.message == "EPUB backend has been retired")
    }

    @Test fun cleanupUnregistersADiscoveredBook() {
        setUp()
        val dir = fixture.buildEpub("fixture-owned-book")
        addEpubBook(dir.toOkioPath(), testCoreStrings(), AndroidAppEnvironment(application))
        val book = Books.installed().getBook(epubInitials(dir.name))!!
        val state = book.epubBackend!!.state
        tearDown()
        assertNull("the JVM-wide registry must not keep a book whose files are gone",
            Books.installed().getBook(epubInitials(dir.name)))
        val failure = runCatching { runBlocking { state.totalCharacters() } }.exceptionOrNull()
        assertTrue("registered state must be retired, got $failure", failure is IllegalStateException)
    }

    @Test fun cleanupWaitsForALeaseBeforeDeletingFiles() {
        setUp()
        val dir = fixture.buildEpub("fixture-leased-state")
        val state = fixture.openState(dir)
        val resource = state.openResource("content.opf")!!
        val cleanup = thread(name = "fixture-cleanup") { tearDown() }
        try {
            cleanup.join(500)
            assertTrue("cleanup must wait while a reader holds a lease", cleanup.isAlive)
            assertTrue("files must stay while a reader holds a lease", dir.exists())
        } finally {
            resource.close()
            cleanup.join(TimeUnit.SECONDS.toMillis(30))
        }
        assertFalse(cleanup.isAlive)
        assertFalse("files are deleted once the lease is released", dir.exists())
        assertEquals(null, state.openResource("content.opf"))
    }
}
