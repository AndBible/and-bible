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

package net.bible.service.sword.index

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.index.IndexStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The [IndexingQueue] contract on its own, without the foreground service host. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class IndexingQueueTest {
    private val kjv get() = Books.installed().getBook("KJV")!!
    private val installed get() = Books.installed().getBooks().filter { it.initials != "KJV" }.take(2)

    private fun queue(build: (Book) -> Unit) = IndexingQueue(resolve = { Books.installed().getBook(it) }, build = build, startWorker = { n, b -> Thread(b, n).apply { isDaemon = true }.start() })

    @Test fun booksAreBuiltFirstInFirstOut() {
        val (b, c) = installed
        val built = CopyOnWriteArrayList<String>()
        val gate = CountDownLatch(1)
        val q = queue { book -> gate.await(10, TimeUnit.SECONDS); built += book.initials }
        assertTrue(q.offer("KJV"))
        assertTrue(q.offer(b.initials))
        assertTrue(q.offer(c.initials))
        gate.countDown()
        assertTrue(q.awaitIdle(10_000))
        assertEquals(listOf("KJV", b.initials, c.initials), built.toList())
    }

    @Test fun aDuplicateOfferIsRefusedWhileQueuedOrBuilding() {
        val b = installed.first()
        val gate = CountDownLatch(1)
        val q = queue { gate.await(10, TimeUnit.SECONDS) }
        assertTrue(q.offer("KJV"))
        assertTrue(q.offer(b.initials))
        assertFalse("queued", q.offer(b.initials))
        assertFalse("building", q.offer("KJV"))
        gate.countDown()
        assertTrue(q.awaitIdle(10_000))
    }

    @Test fun removingAQueuedBookSetsItUndoneAndSkipsIt() {
        val b = installed.first()
        val built = CopyOnWriteArrayList<String>()
        val gate = CountDownLatch(1)
        val q = queue { book -> gate.await(10, TimeUnit.SECONDS); built += book.initials }
        b.indexStatus = IndexStatus.SCHEDULED
        assertTrue(q.offer("KJV"))
        assertTrue(q.offer(b.initials, b))
        q.remove(b.initials)
        gate.countDown()
        assertTrue(q.awaitIdle(10_000))
        assertEquals(listOf("KJV"), built.toList())
        assertEquals(IndexStatus.UNDONE, b.indexStatus)
    }

    @Test fun aThrowingBuildLeavesTheBookUndoneAndTheQueueContinues() {
        val b = installed.first()
        val built = CopyOnWriteArrayList<String>()
        kjv.indexStatus = IndexStatus.SCHEDULED
        val gate = CountDownLatch(1)
        // hold the worker on a first book so both are queued before either is built
        val q2 = queue { book ->
            gate.await(10, TimeUnit.SECONDS)
            if (book.initials == "KJV") throw IllegalStateException("boom")
            built += book.initials
        }
        assertTrue(q2.offer("KJV", kjv))
        assertTrue(q2.offer(b.initials))
        gate.countDown()
        assertTrue(q2.awaitIdle(10_000))
        assertEquals(IndexStatus.UNDONE, kjv.indexStatus)
        assertEquals(listOf(b.initials), built.toList())
    }

    @Test fun runIfIdleRunsOnlyWhenNothingIsQueuedOrRunning() {
        val gate = CountDownLatch(1)
        val q = queue { gate.await(10, TimeUnit.SECONDS) }
        var ran = 0
        q.runIfIdle { ran++ }
        assertEquals(1, ran)
        assertTrue(q.offer("KJV"))
        q.runIfIdle { ran++ }
        assertEquals("busy", 1, ran)
        gate.countDown()
        assertTrue(q.awaitIdle(10_000))
        q.runIfIdle { ran++ }
        assertEquals(2, ran)
    }

    @Test fun theWorkerIsADaemonThreadNamedSearchIndexing() {
        var name: String? = null
        var daemon: Boolean? = null
        val q = queue { name = Thread.currentThread().name; daemon = Thread.currentThread().isDaemon }
        assertTrue(q.offer("KJV"))
        assertTrue(q.awaitIdle(10_000))
        assertEquals("search-indexing", name)
        assertEquals(true, daemon)
    }
}
