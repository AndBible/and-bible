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

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.SwordDocumentInfo
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.DatabaseHost
import net.bible.service.startup.ReadingAppInit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ANR residual (Task 2): a host recreated directly (process death, widget, media button) runs
 * `CommonUtils.initializeApp()` on Main, which used to finish with a DB read plus a JSword unlock of every
 * enciphered module on Main. The unlock is held here inside the real stored-key loop (real repo DB rows, the real
 * `unlockStoredCipherKeys` path; only the JSword `unlock` call is faked): Main must stay responsive, a reader of an
 * enciphered book must wait for the unlock instead of racing it, and the unlocked state must be applied afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class StartupMainResponsivenessTest {
    private lateinit var originalHost: DatabaseHost
    private val release = CountDownLatch(1)
    private val entered = CountDownLatch(1)
    private val unlockedBooks = CopyOnWriteArrayList<Pair<String, String?>>()
    private val unlockThreads = CopyOnWriteArrayList<Thread>()
    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<NavHostComposeActivity>>()

    @Before fun setUp() {
        originalHost = DatabaseContainer.host
        ReadingAppInit.resetForTest()
    }

    @After fun tearDown() {
        release.countDown()
        DatabaseContainer.installHost(originalHost)
        try {
            // Joins the host's own owners before the reset (a bare close() leaves a retained reset worker).
            net.bible.test.closeReadingHostsBeforeReset(controllers)
        } finally {
            ReadingAppInit.resetForTest()
        }
    }

    @Test fun mainStaysResponsiveWhileEncipheredBooksUnlockAndReadersWaitForIt() {
        runBlocking {
            val dao = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
            dao.insert(SwordDocumentInfo("Locked1", "Locked one", "L1", "en", "repo", cipherKey = "key-1"))
            dao.insert(SwordDocumentInfo("Locked2", "Locked two", "L2", "en", "repo", cipherKey = "key-2"))
        }
        ReadingAppInit.installCipherSeamsForTest(hasInstalledBible = { true }, unlockBook = { initials, key ->
            unlockThreads += Thread.currentThread()
            entered.countDown()
            // Stands in for the slow JSword decrypt. Bounded so a regression cannot hang forever.
            release.await(120, TimeUnit.SECONDS)
            unlockedBooks += initials to key
        })
        // The stored-key read is skipped under isRunningTests; switch it off after the DB is open and seeded.
        DatabaseContainer.installHost(object : DatabaseHost by originalHost {
            override val isRunningTests: Boolean get() = false
        })

        val main = Looper.getMainLooper()
        val c = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).also { controllers += it }
        c.create().start().resume().visible()

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (entered.count > 0 && System.nanoTime() < deadline) {
            shadowOf(main).idle()
            entered.await(50, TimeUnit.MILLISECONDS)
        }
        assertEquals("the unlock never started", 0L, entered.count)
        assertTrue(
            "the unlock ran on Main (initializeApp blocked the host start behind it)",
            unlockThreads.none { it === main.thread },
        )
        assertEquals(Lifecycle.State.RESUMED, c.get().lifecycle.currentState)

        var posted = false
        Handler(main).post { posted = true }
        shadowOf(main).idle()
        assertTrue("a Main runnable did not run while the unlock was still held", posted)
        assertFalse("the unlock is still held, so it cannot be done", ReadingAppInit.cipherKeysUnlocked)

        // A reader of an enciphered book arriving now must wait, not race the unlock.
        val readerDone = CountDownLatch(1)
        val reader = Thread { runBlocking { ReadingAppInit.awaitCipherUnlock() }; readerDone.countDown() }.apply { start() }
        assertFalse("a reader passed while the unlock was running", readerDone.await(500, TimeUnit.MILLISECONDS))

        // A second starter while the job runs must not start a second unlock.
        val running = ReadingAppInit.unlockStoredCipherKeysInBackground()
        assertTrue("a second start must reuse the running job", running?.isActive == true)

        release.countDown()
        assertTrue("the reader must be released once the unlock ends", readerDone.await(10, TimeUnit.SECONDS))
        reader.join(10_000)
        runBlocking { withTimeout(10_000) { ReadingAppInit.awaitCipherUnlock() } }
        assertTrue(ReadingAppInit.cipherKeysUnlocked)
        assertEquals(
            "each enciphered row is unlocked exactly once, with its stored key",
            setOf("Locked1" to "key-1", "Locked2" to "key-2"), unlockedBooks.toSet(),
        )
        assertEquals(2, unlockedBooks.size)
    }
}
