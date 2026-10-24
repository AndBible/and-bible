/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedlogic.sync

import org.crosswire.jsword.versification.BookName
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MonitorIdentityJvmTest {
    @Test fun bookNameMonitorIsTheBookNameClassObject() {
        assertSame(BookName::class.java, bookNameMonitor)
    }

    @Test fun withMonitorExcludesAgainstPlainSynchronized() {
        val obj = Any()
        val held = CountDownLatch(1)
        val holder = thread {
            synchronized(obj) {
                held.countDown()
                Thread.sleep(200)
            }
        }
        held.await()
        var waitedMs = 0L
        val contender = thread {
            val start = System.nanoTime()
            withMonitor(obj) { }
            waitedMs = (System.nanoTime() - start) / 1_000_000
        }
        holder.join()
        contender.join()
        assertTrue(waitedMs >= 150, "waited $waitedMs ms")
    }
}
