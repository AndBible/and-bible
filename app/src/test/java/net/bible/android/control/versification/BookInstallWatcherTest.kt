/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.documents.DocumentSyncSettings
import net.bible.android.database.RepoDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.koin.java.KoinJavaComponent
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.SwordDocumentInfo
import net.bible.service.cloudsync.documents.DocumentSync
import net.bible.service.cloudsync.documents.isSyncableDocument
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.DownloadManager
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BooksEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Review Focus #5: install/uninstall events for one book must hit the backup db in event order. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookInstallWatcherTest {
    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
    private val starter = object : DocumentSyncStarter {
                override fun start(push: List<String>, download: List<String>, remove: List<String>, purge: List<String>, uninstall: List<String>) = Unit
                override fun stop() = Unit
        override fun pushDocuments(initials: List<String>) {}
    }

    private val catalog = object : net.bible.sharedlogic.platform.AddonCatalog {
        val clears = AtomicInteger()
        override fun clearCaches() { clears.incrementAndGet() }
        override val readingPlans: Map<String, net.bible.sharedlogic.platform.AddonReadingPlan> get() = emptyMap()
    }

    @After
    fun tearDown() { resetDatabase() }

    /**
     * Real threads (Dispatchers.Default). The FIRST backup-db write is held on a latch (via the watcher's
     * [BookInstallWatcher.beforeWrite] seam) while the second event is submitted and given time to run; a naive
     * per-callback launch would let the second write overtake the held first one, the ordered launcher must not.
     */
    private fun runEvents(vararg events: String) = runBlocking {
        val appScope = AppCoroutineScope()
        val launcher = OrderedLauncher(appScope)
        val watcher = BookInstallWatcher(launcher, starter, org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.DocumentSyncProvider::class.java), org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java), catalog)
        val firstWriteHeld = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writes = AtomicInteger()
        watcher.beforeWrite = {
            if (writes.incrementAndGet() == 1) {
                firstWriteHeld.complete(Unit)
                release.await()
            }
        }
        val book = Books.installed().getBooks().first()
        val ev = BooksEvent(Books.installed(), book, true)
        try {
            events.forEachIndexed { i, it ->
                if (it == "add") watcher.listener.bookAdded(ev) else watcher.listener.bookRemoved(ev)
                if (i == 0) withTimeout(30_000) { firstWriteHeld.await() }
            }
            // Give an (incorrectly) unordered second write ample time to overtake the held first one.
            delay(300)
            release.complete(Unit)
            withTimeout(30_000) { appScope.coroutineContext[Job]!!.children.toList().forEach { it.join() } }
        } finally {
            release.complete(Unit)
            appScope.coroutineContext[Job]!!.cancel()
        }
        book.initials
    }

    @Test
    fun `auto upload loads enabled persisted preferences before checking install`() = runBlocking {
        val scope = AppCoroutineScope()
        val launcher = OrderedLauncher(scope)
        val db = DatabaseContainer.instance.documentSyncDb
        db.documentSyncPreferencesDao().set(net.bible.android.database.DocumentSyncPreferences(enabled = true, autoUpload = true, wifiOnly = false))
        val settings = net.bible.service.cloudsync.documents.DocumentSyncSettings(
            { db.documentSyncPreferencesDao() }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher,
            org.koin.java.KoinJavaComponent.get<net.bible.sharedcore.platform.AppEnvironment>(net.bible.sharedcore.platform.AppEnvironment::class.java),
        )
        val pushed = CompletableDeferred<List<String>>()
        val recordingStarter = object : DocumentSyncStarter by starter {
            override fun pushDocuments(initials: List<String>) { pushed.complete(initials) }
        }
        val watcher = BookInstallWatcher(launcher, recordingStarter,
            org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.DocumentSyncProvider::class.java), settings, catalog)
        val book = Books.installed().getBooks().first { it.isSyncableDocument }
        try {
            watcher.listener.bookAdded(BooksEvent(Books.installed(), book, true))
            withTimeout(30_000) { launcher.join("book-install-auto-upload") }
            org.junit.Assert.assertTrue("installation must enqueue auto-upload", pushed.isCompleted)
            assertEquals(listOf(book.initials), pushed.await())
            launcher.join("book-install")
        } finally {
            scope.coroutineContext[Job]!!.cancel()
        }
    }

    @Test
    fun `startup discovery queues no pushes and a subsequent real install still does`() = runBlocking {
        val scope = AppCoroutineScope()
        val launcher = OrderedLauncher(scope)
        val settings = KoinJavaComponent.get<DocumentSyncSettings>(DocumentSyncSettings::class.java)
        settings.ensureLoaded()
        settings.enabled = true
        settings.wifiOnly = false
        settings.autoUpload = true
        val pushed = java.util.Collections.synchronizedList(mutableListOf<String>())
        val recordingStarter = object : DocumentSyncStarter by starter {
            override fun pushDocuments(initials: List<String>) { pushed.addAll(initials) }
        }
        val watcher = BookInstallWatcher(launcher, recordingStarter,
            KoinJavaComponent.get(DocumentSyncProvider::class.java), settings, catalog)
        val book = Books.installed().getBooks().first { it.isSyncableDocument }
        val release = CompletableDeferred<Unit>()
        val discoveryListener = object : org.crosswire.jsword.book.BooksListener {
            override fun bookAdded(ev: BooksEvent) {
                if (ev.book === book) watcher.listener.bookAdded(ev)
            }
            override fun bookRemoved(ev: BooksEvent) = Unit
        }
        Books.installed().addBooksListener(discoveryListener)
        try {
            launcher.launch("book-install-auto-upload") { release.await() }
            repeat(2) { net.bible.service.sword.registerBook(book, startupDiscovery = true) }
            // Origin must be captured before the registration scope ends, not read by the deferred job.
            release.complete(Unit)
            withTimeout(30_000) { launcher.join("book-install-auto-upload"); watcher.awaitPendingInstalls() }
            assertEquals("startup discovery must not enqueue remote checks", emptyList<String>(), pushed)
            assertNotNull("discovery must still update the backup database", docDao.getBook(book.initials))
            net.bible.service.sword.registerBook(book)
            withTimeout(30_000) { launcher.join("book-install-auto-upload"); watcher.awaitPendingInstalls() }
            assertEquals("a real reinstall of the same version must still enqueue", listOf(book.initials), pushed)
        } finally {
            release.complete(Unit)
            Books.installed().removeBooksListener(discoveryListener)
            scope.coroutineContext[Job]!!.cancel()
        }
    }

    @Test
    fun `deferred auto upload keeps sync install echo suppressed`() = runBlocking {
        val scope = AppCoroutineScope()
        val launcher = OrderedLauncher(scope)
        val db = DatabaseContainer.instance.documentSyncDb
        db.documentSyncPreferencesDao().set(net.bible.android.database.DocumentSyncPreferences(enabled = true, wifiOnly = false))
        val settings = net.bible.service.cloudsync.documents.DocumentSyncSettings(
            { db.documentSyncPreferencesDao() }, { db.cloudListingStateDao() },
            { db.cloudDocumentSyncTimestampDao() }, launcher,
            org.koin.java.KoinJavaComponent.get<net.bible.sharedcore.platform.AppEnvironment>(net.bible.sharedcore.platform.AppEnvironment::class.java),
        )
        val sync = org.koin.java.KoinJavaComponent.get<DocumentSync>(DocumentSync::class.java)
        val field = DocumentSync::class.java.getDeclaredField("installingFromSync").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val installing = field.get(sync) as MutableSet<String>
        val release = CompletableDeferred<Unit>()
        val pushed = mutableListOf<String>()
        val recordingStarter = object : DocumentSyncStarter by starter {
            override fun pushDocuments(initials: List<String>) { pushed.addAll(initials) }
        }
        val watcher = BookInstallWatcher(launcher, recordingStarter, net.bible.service.cloudsync.DocumentSyncProvider { sync }, settings, catalog)
        val book = Books.installed().getBooks().first { it.isSyncableDocument }
        try {
            launcher.launch("book-install-auto-upload") { release.await() }
            installing.add(book.initials)
            watcher.listener.bookAdded(BooksEvent(Books.installed(), book, true))
            installing.remove(book.initials)
            release.complete(Unit)
            withTimeout(30_000) { launcher.join("book-install-auto-upload") }
            assertEquals(emptyList<String>(), pushed)
            launcher.join("book-install")
        } finally {
            installing.remove(book.initials)
            release.complete(Unit)
            scope.coroutineContext[Job]!!.cancel()
        }
    }

    /** A8/A7: a restore must not close the databases under a row write that is already running. */
    @Test
    fun `replace waits for a held row write`() = runBlocking {
        val appScope = AppCoroutineScope()
        val watcher = BookInstallWatcher(OrderedLauncher(appScope), starter, KoinJavaComponent.get(DocumentSyncProvider::class.java), KoinJavaComponent.get(DocumentSyncSettings::class.java), catalog)
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        watcher.beforeWrite = { held.complete(Unit); release.await() }
        val book = Books.installed().getBooks().first()
        val replaceStarted = AtomicBoolean(false)
        try {
            watcher.listener.bookAdded(BooksEvent(Books.installed(), book, true))
            withTimeout(30_000) { held.await() }
            val replace = async(Dispatchers.Default) { DatabaseContainer.replacingDatabases { replaceStarted.set(true) } }
            delay(500)
            assertFalse("the replace block must wait for the held row write", replaceStarted.get())
            release.complete(Unit)
            withTimeout(30_000) { replace.await() }
            assertTrue(replaceStarted.get())
        } finally {
            release.complete(Unit)
            appScope.coroutineContext[Job]!!.cancel()
        }
    }

    /** A7: a write submitted inside a restore must not deadlock it, and lands after it. */
    @Test
    fun `row write during replace lands after it`() = runBlocking {
        val appScope = AppCoroutineScope()
        val launcher = OrderedLauncher(appScope)
        val watcher = BookInstallWatcher(launcher, starter, KoinJavaComponent.get(DocumentSyncProvider::class.java), KoinJavaComponent.get(DocumentSyncSettings::class.java), catalog)
        val book = Books.installed().getBooks().first()
        docDao.deleteByOsisId(book.initials)
        var presentInsideReplace: SwordDocumentInfo? = null
        try {
            withTimeout(30_000) {
                DatabaseContainer.replacingDatabases { token ->
                    // Close and reopen the repo database, as a restore does: the write must land on the reopened one.
                    DatabaseContainer.instance.closeForReplaceOwned(RepoDatabase.dbFileName, token)
                    DatabaseContainer.resetOwned(token)
                    watcher.listener.bookAdded(BooksEvent(Books.installed(), book, true))
                    delay(300) // ample time for an ungated write to land
                    presentInsideReplace = docDao.getBook(book.initials)
                }
            }
            assertNull("the write must wait until the replace has ended", presentInsideReplace)
            withTimeout(30_000) { launcher.join("book-install") }
            assertNotNull(docDao.getBook(book.initials))
        } finally {
            appScope.coroutineContext[Job]!!.cancel()
        }
    }

    @Test
    fun `book added clears the addon catalog once`() {
        runEvents("add")
        assertEquals(1, catalog.clears.get())
    }

    @Test
    fun `book removed clears the addon catalog once`() {
        runEvents("remove")
        assertEquals(1, catalog.clears.get())
    }

    @Test
    fun `add then remove leaves the book out of the backup db`() {
        repeat(3) {
            val initials = runEvents("add", "remove")
            assertNull(runBlocking { docDao.getBook(initials) })
        }
    }

    @Test
    fun `remove then add leaves the book in the backup db`() {
        repeat(3) {
            val initials = runEvents("remove", "add")
            val row = runBlocking { docDao.getBook(initials) }
            assertNotNull(row)
            assertEquals(initials, row!!.initials)
        }
    }

    /**
     * Critical race (final review): JSword fires bookAdded synchronously inside `install()`, and the caller then
     * amends the row (repository / cipher key). The row insert is held on the latch while the caller's amendment
     * runs on another coroutine and is given time to land; an amendment that bypasses the watcher's queue either
     * finds no row (repository lost) or is overwritten by the delete-and-reinsert (cipher key lost).
     */
    private fun installThenAmend(preexisting: SwordDocumentInfo?, removeFirst: Boolean = false, amend: (suspend (BookInstallWatcher, Book) -> Unit)?): SwordDocumentInfo? =
        runBlocking {
            val appScope = AppCoroutineScope()
            val watcher = BookInstallWatcher(OrderedLauncher(appScope), starter, org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.DocumentSyncProvider::class.java), org.koin.java.KoinJavaComponent.get(net.bible.service.cloudsync.documents.DocumentSyncSettings::class.java), catalog)
            val firstWriteHeld = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val writes = AtomicInteger()
            watcher.beforeWrite = {
                if (writes.incrementAndGet() == 1) {
                    firstWriteHeld.complete(Unit)
                    release.await()
                }
            }
            val book = Books.installed().getBooks().first()
            // The amendment may unlock the shared test module with a made-up key; restore the real key so later tests can read it.
            val originalKey = book.bookMetaData.unlockKey
            preexisting?.let { docDao.insert(it.copy(initials = book.initials)) }
            try {
                if (removeFirst) watcher.listener.bookRemoved(BooksEvent(Books.installed(), book, false))
                watcher.listener.bookAdded(BooksEvent(Books.installed(), book, true))
                withTimeout(30_000) { firstWriteHeld.await() }
                val amendment = async(Dispatchers.Default) { amend?.invoke(watcher, book) }
                // Give an (incorrectly) unqueued amendment ample time to run before the held insert.
                delay(300)
                // An amendment that completes now bypassed this watcher's queue: whether its write lands
                // before, inside or after the held read-then-insert is then up to the scheduler.
                if (amend != null) assertFalse("the amendment must queue behind the held install write", amendment.isCompleted)
                release.complete(Unit)
                withTimeout(30_000) {
                    amendment.await()
                    appScope.coroutineContext[Job]!!.children.toList().forEach { it.join() }
                }
            } finally {
                release.complete(Unit)
                appScope.coroutineContext[Job]!!.cancel()
                val meta = book.bookMetaData as org.crosswire.jsword.book.sword.SwordBookMetaData
                if (originalKey == null) meta.removeProperty("CipherKey") else meta.unlock(originalKey)
            }
            docDao.getBook(book.initials)
        }

    /**
     * A [DocumentSync] that amends rows through [watcher], as production's Koin graph does (one watcher single
     * listens and serves DocumentSync). The Koin DocumentSync holds a different watcher with its own ordered
     * launcher, so its write would race the held insert instead of queuing behind it (#3947: cipher key null
     * in the full suite when load stretched the amendment past the 300 ms grace).
     */
    private fun documentSyncOf(watcher: BookInstallWatcher) = DocumentSync(
        KoinJavaComponent.get(net.bible.sharedcore.platform.AppEnvironment::class.java),
        KoinJavaComponent.get(net.bible.service.cloudsync.CloudSyncProvider::class.java),
        starter, watcher,
        KoinJavaComponent.get(DocumentSyncSettings::class.java),
        { KoinJavaComponent.get(net.bible.android.control.backup.BackupControl::class.java) },
    )

    @Test
    fun `plain reinstall preserves app metadata and refreshes module description`() {
        val old = SwordDocumentInfo("x", "Old name", "OLD", "Old language", "OldRepo", "old-key")
        val row = installThenAmend(old, amend = null)
        val book = Books.installed().getBooks().first()
        assertEquals("OldRepo", row?.repository)
        assertEquals("old-key", row?.cipherKey)
        assertEquals(book.name, row?.name)
        assertEquals(book.abbreviation, row?.abbreviation)
        assertEquals(book.language.name, row?.language)
    }

    @Test
    fun `new install uses empty app metadata`() {
        val row = installThenAmend(null, amend = null)
        assertNotNull(row)
        assertEquals("", row?.repository)
        assertNull(row?.cipherKey)
    }

    @Test
    fun `genuine uninstall before install discards prior app metadata`() {
        val old = SwordDocumentInfo("x", "Old", "OLD", "English", "OldRepo", "old-key")
        val row = installThenAmend(old, removeFirst = true, amend = null)
        assertNotNull(row)
        assertEquals("", row?.repository)
        assertNull(row?.cipherKey)
    }

    @Test
    fun `repository recorded after install survives the queued row insert`() {
        val row = installThenAmend(null) { watcher, book ->
            DownloadManager.recordRepository(watcher, book.initials, "CrossWire")
        }
        assertEquals("CrossWire", row?.repository)
    }

    @Test
    fun `cipher key stored after a sync install survives the queued row insert`() {
        val row = installThenAmend(null) { watcher, book -> documentSyncOf(watcher).applyCipherKey(book.initials, "secret") }
        assertEquals("secret", row?.cipherKey)
    }

    @Test
    fun `sync cipher amendment after reinstall keeps retained repository`() {
        val old = SwordDocumentInfo("x", "Old", "OLD", "English", "OldRepo", "old-key")
        val row = installThenAmend(old) { watcher, book -> documentSyncOf(watcher).applyCipherKey(book.initials, "new-key") }
        assertEquals("new-key", row?.cipherKey)
        assertEquals("OldRepo", row?.repository)
    }

    @Test
    fun `network repository amendment after reinstall keeps retained cipher key`() {
        val old = SwordDocumentInfo("x", "Old", "OLD", "English", "OldRepo", "old-key")
        val row = installThenAmend(old) { watcher, book ->
            DownloadManager.recordRepository(watcher, book.initials, "NewRepo")
        }
        assertEquals("NewRepo", row?.repository)
        assertEquals("old-key", row?.cipherKey)
    }
}
