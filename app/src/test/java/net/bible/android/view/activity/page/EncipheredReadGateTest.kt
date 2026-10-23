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

package net.bible.android.view.activity.page

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.contentJson
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.sword.hasUsableBible
import net.bible.service.startup.ReadingAppInit
import net.bible.sharedcore.nav.NavRoutes
import org.crosswire.common.activate.Activator
import org.crosswire.common.util.NetUtilJvm
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaDataJvm
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A REALLY enciphered, locked Bible (the checked-in K4 ciphertext, empty `CipherKey`, as every enciphered module is
 * after a process start) with its key stored in the repo DB. The startup unlock is held: a window load issued then
 * must wait and return plaintext, never the error/garbage a locked read produces, and the Bible must count as usable
 * for the start-route gate while the unlock is pending.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class EncipheredReadGateTest {
    private val release = CountDownLatch(1)
    private val entered = CountDownLatch(1)
    private lateinit var originalHost: DatabaseHost
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<NavHostComposeActivity>>()
    private var fixture: Book? = null
    private var root: File? = null

    @After fun tearDown() {
        release.countDown()
        DatabaseContainer.installHost(originalHost)
        try {
            net.bible.test.closeReadingHostsBeforeReset(controllers)
        } finally {
            ReadingAppInit.resetForTest()
            fixture?.let { book ->
                Activator.deactivate(book)
                val manager = org.crosswire.jsword.book.sword.state.OpenFileStateManager.instance()
                val field = manager.javaClass.getDeclaredField("metaToStates").apply { isAccessible = true }
                val lock = manager.javaClass.getDeclaredField("lock").apply { isAccessible = true }.get(manager)!!
                synchronized(lock) {
                    @Suppress("UNCHECKED_CAST")
                    (field.get(manager) as MutableMap<org.crosswire.jsword.book.BookMetaData, ArrayDeque<org.crosswire.jsword.book.sword.state.OpenFileState>>)
                        .remove(book.bookMetaData)?.forEach { it.releaseResources() }
                }
                withoutInstallWatcher { Books.installed().removeBook(book) }
            }
            root?.deleteRecursively()
        }
    }

    /**
     * `Books.installed()` and the [net.bible.android.control.versification.BookInstallWatcher] listener are JVM-global:
     * a watcher left registered by an earlier test rewrites the `SwordDocumentInfo` row of every book this test adds
     * or removes (asynchronously, so also after the DB reset). Take it out for the call and put it back after.
     */
    private fun withoutInstallWatcher(block: () -> Unit) {
        val field = net.bible.android.control.versification.BookInstallWatcher::class.java.getDeclaredField("registered")
            .apply { isAccessible = true }
        val listener = field.get(null) as org.crosswire.jsword.book.BooksListener?
        listener?.let { Books.installed().removeBooksListener(it) }
        try { block() } finally { listener?.let { Books.installed().addBooksListener(it) } }
    }

    private fun installLockedFixture(): Book {
        val dir = Files.createTempDirectory("startup-cipher-").toFile().also { root = it }
        val source = File(requireNotNull(System.getProperty("k5.project.root")), "jsword/src/commonTest/resources/k4-modules/space dir/ä")
        val conf = File(dir, "mods.d/E2Cipher.conf").apply { parentFile.mkdirs() }
        conf.writeText(File(source, "mods.d/K4Cipher.conf").readText()
            .replace("[K4Cipher]", "[$INITIALS]")
            .replace("./modules/K4Cipher", "./modules/E2Cipher")
            .replace("CipherKey=$KEY", "CipherKey="))
        File(source, "modules/K4Cipher").copyRecursively(File(dir, "modules/E2Cipher"))
        val metadata = requireNotNull(SwordBookMetaDataJvm.create(conf, NetUtilJvm.getURI(dir)))
        metadata.setDriver(SwordBookDriver.instance())
        val book = BookType.fromString("zText")!!.createBook(metadata)
        withoutInstallWatcher { Books.installed().addBook(book) }
        assertTrue("fixture must start locked", book.isLocked)
        fixture = book
        return book
    }

    @Test fun aWindowLoadIssuedWhileTheUnlockIsHeldWaitsAndReturnsPlaintext() {
        originalHost = DatabaseContainer.host
        val book = installLockedFixture()
        runBlocking {
            DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
                .insert(SwordDocumentInfo(INITIALS, "E2", "E2", "en", "repo", cipherKey = KEY))
        }
        val c = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }
        c.create().start().resume().visible()
        ReadingAppInit.resetForTest()
        ReadingAppInit.installCipherSeamsForTest(unlockBook = { initials, key ->
            entered.countDown()
            release.await(120, TimeUnit.SECONDS)
            Books.installed().getBook(initials)?.unlock(key)
        })
        DatabaseContainer.installHost(object : DatabaseHost by originalHost {
            override val isRunningTests: Boolean get() = false
        })
        // What the host's initializeApp(blockOnCipherUnlock = false) does on a recreated host.
        ReadingAppInit.unlockStoredCipherKeysInBackground()
        val main = android.os.Looper.getMainLooper()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (entered.count > 0 && System.nanoTime() < deadline) {
            shadowOf(main).idle()
            entered.await(50, TimeUnit.MILLISECONDS)
        }
        assertEquals(
            "the stored-key unlock never started (rows=${runBlocking { DatabaseContainer.instance.repoDb.swordDocumentInfoDao().getUnlocked().map { it.initials } }} runningTests=${DatabaseContainer.host.isRunningTests} unlocked=${ReadingAppInit.cipherKeysUnlocked} pending=${ReadingAppInit.cipherUnlockPending} " +
                "scopeActive=${org.koin.core.context.GlobalContext.get().get<net.bible.sharedcore.platform.AppCoroutineScope>().coroutineContext[kotlinx.coroutines.Job]?.isActive})",
            0L, entered.count,
        )
        assertTrue("precondition: the book is still locked while the unlock is held", book.isLocked)

        // C2: a start-route gate that runs now must not send a user with an enciphered Bible to WELCOME.
        assertTrue("an enciphered Bible must count as usable while its unlock is pending", hasUsableBible(listOf(book)))

        val window = c.get().hostWindowRepository.activeWindow
        val kjv = Versifications.instance().getVersification("KJV")
        window.pageManager.setCurrentDocumentAndKey(book, VerseFactory.fromString(kjv, "John.1.1")!!)
        val load = CompletableDeferred<String>()
        val loader = Thread {
            runBlocking {
                val doc = window.fetchDocument(window.pageManager.currentPage)
                load.complete(doc!!.contentJson())
            }
        }.apply { start() }

        assertFalse("the window read ran against the locked book instead of waiting", load.isCompleted.also { Thread.sleep(1500) } || load.isCompleted)
        release.countDown()
        val json = runBlocking { withTimeout(30_000) { load.await() } }
        loader.join(10_000)
        assertTrue("plaintext expected after the unlock, got: ${json.take(300)}", json.contains("In the beginning was the Word"))
        assertFalse(book.isLocked)
    }

    @Test fun anEncipheredBibleWithNoStoredKeyIsNotUsableWhileTheUnlockIsPending() {
        originalHost = DatabaseContainer.host
        val book = installLockedFixture()
        // Another module's stored key keeps the unlock job running; the fixture itself has NO row.
        runBlocking {
            DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
                .insert(SwordDocumentInfo("OtherLocked", "Other", "OT", "en", "repo", cipherKey = "x"))
        }
        ReadingAppInit.resetForTest()
        ReadingAppInit.installCipherSeamsForTest(unlockBook = { _, _ ->
            entered.countDown()
            release.await(120, TimeUnit.SECONDS)
        })
        DatabaseContainer.installHost(object : DatabaseHost by originalHost {
            override val isRunningTests: Boolean get() = false
        })
        ReadingAppInit.unlockStoredCipherKeysInBackground()
        assertTrue("the unlock must be running", entered.await(30, TimeUnit.SECONDS))
        assertTrue(ReadingAppInit.cipherUnlockPending)
        assertFalse("no stored key: not usable, as before the change (WELCOME path)", hasUsableBible(listOf(book)))
    }

    companion object {
        private const val INITIALS = "E2Cipher"
        private const val KEY = "k4-Test-key"
    }
}
