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

package net.bible.service.db

import kotlinx.atomicfu.locks.synchronized
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** The companion object is the container's lock: re-entrant, and held across instance publication (D2 Task 4). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ContainerLockTest {
    @Before fun setUp() = net.bible.test.DatabaseResetter.resetDatabase()
    @After fun tearDown() = net.bible.test.DatabaseResetter.resetDatabase()

    @Test fun instanceIsReentrantUnderTheContainerLock() {
        val failure = AtomicReference<Throwable?>()
        val t = Thread {
            try { synchronized(DatabaseContainer.Companion) { DatabaseContainer.instance } }
            catch (e: Throwable) { failure.set(e) }
        }
        t.start()
        t.join(10_000)
        assertFalse("instance deadlocked under the companion lock", t.isAlive)
        assertTrue("failed: ${failure.get()}", failure.get() == null)
    }

    @Test fun aThreadHoldingTheCompanionMonitorBlocksInstancePublication() {
        DatabaseContainer.forgetReplacesForTest()
        DatabaseContainer.dropInstanceWithoutOpening()
        val held = CountDownLatch(1)
        val release = CountDownLatch(1)
        val a = Thread { synchronized(DatabaseContainer.Companion) { held.countDown(); release.await(10, TimeUnit.SECONDS) } }
        a.start()
        assertTrue(held.await(10, TimeUnit.SECONDS))
        val b = Thread { DatabaseContainer.instance }
        b.start()
        b.join(300)
        assertTrue("instance published while another thread held the companion monitor", b.isAlive)
        release.countDown()
        b.join(20_000)
        assertFalse("instance did not complete after release", b.isAlive)
        a.join(10_000)
    }
}
