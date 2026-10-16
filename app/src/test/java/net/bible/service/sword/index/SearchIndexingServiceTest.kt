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

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.search.FtsTestSupport
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.index.IndexStatus
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
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** K6 task 17: the foreground service that builds the FTS5 search indexes, one book at a time. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SearchIndexingServiceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val kjv get() = Books.installed().getBook("KJV")!!
    private val geneva get() = Books.installed().getBook("Geneva")!!
    private val turntb get() = Books.installed().getBook("TurNTB")!!
    private var controller: ServiceController<SearchIndexingService>? = null
    private lateinit var savedBuilder: (Book) -> Unit

    @Before fun setUp() {
        savedBuilder = SearchIndexingService.builder
        SearchIndexingService.resetForTest()
    }

    @After fun tearDown() {
        try {
            SearchIndexingService.awaitIdleForTest(30_000)
        } finally {
            SearchIndexingService.builder = savedBuilder
            SearchIndexingService.resetForTest()
            listOf(kjv, geneva, turntb).forEach { it.indexStatus = IndexStatus.UNDONE }
            runCatching { FtsTestSupport.dropIndex(turntb) }
            DatabaseResetter.resetDatabase()
        }
    }

    private fun startService() {
        controller = Robolectric.buildService(SearchIndexingService::class.java, Intent(context, SearchIndexingService::class.java))
            .create().startCommand(0, 1)
    }

    private fun awaitIdle() = assertTrue("queue did not drain", SearchIndexingService.awaitIdleForTest(30_000))

    @Test fun twoBooksAreBuiltInOrderAndTheServiceStopsWhenDone() {
        val built = CopyOnWriteArrayList<String>()
        val gate = CountDownLatch(1)
        SearchIndexingService.builder = { book ->
            gate.await(10, TimeUnit.SECONDS)
            built += book.initials
            book.indexStatus = IndexStatus.DONE
        }
        SearchIndexingService.enqueue(context, "KJV")
        SearchIndexingService.enqueue(context, "Geneva")
        startService()
        gate.countDown()
        awaitIdle()
        assertEquals(listOf("KJV", "Geneva"), built.toList())
        assertEquals(IndexStatus.DONE, kjv.indexStatus)
        assertEquals(IndexStatus.DONE, geneva.indexStatus)
        assertTrue("service must stop itself", shadowOf(controller!!.get()).isStoppedBySelf)
    }

    @Test fun enqueueStartsTheForegroundService() {
        SearchIndexingService.builder = { it.indexStatus = IndexStatus.DONE }
        SearchIndexingService.enqueue(context, "KJV")
        val started = shadowOf(context as android.app.Application).nextStartedService
        assertEquals(SearchIndexingService::class.java.name, started.component!!.className)
        awaitIdle()
    }

    @Test fun theSameBookTwiceIsBuiltOnce() {
        val built = CopyOnWriteArrayList<String>()
        val gate = CountDownLatch(1)
        SearchIndexingService.builder = { book ->
            gate.await(10, TimeUnit.SECONDS)
            built += book.initials
            book.indexStatus = IndexStatus.DONE
        }
        SearchIndexingService.enqueue(context, "KJV")
        SearchIndexingService.enqueue(context, "KJV") // while the first is queued or creating
        gate.countDown()
        awaitIdle()
        assertEquals(listOf("KJV"), built.toList())
    }

    @Test fun aFailedBuildIsUndoneAndTheQueueContinues() {
        val built = CopyOnWriteArrayList<String>()
        SearchIndexingService.builder = { book ->
            built += book.initials
            if (book.initials == "KJV") throw IllegalStateException("boom")
            book.indexStatus = IndexStatus.DONE
        }
        kjv.indexStatus = IndexStatus.SCHEDULED
        SearchIndexingService.enqueue(context, "KJV")
        SearchIndexingService.enqueue(context, "Geneva")
        startService()
        awaitIdle()
        assertEquals(listOf("KJV", "Geneva"), built.toList())
        assertEquals(IndexStatus.UNDONE, kjv.indexStatus)
        assertEquals(IndexStatus.DONE, geneva.indexStatus)
        assertTrue(shadowOf(controller!!.get()).isStoppedBySelf)
    }

    @Test fun aBookRequestedAgainAfterItsBuildIsBuiltAgain() {
        val built = CopyOnWriteArrayList<String>()
        SearchIndexingService.builder = { book -> built += book.initials; book.indexStatus = IndexStatus.DONE }
        SearchIndexingService.enqueue(context, "KJV")
        awaitIdle()
        SearchIndexingService.enqueue(context, "KJV")
        awaitIdle()
        assertEquals(listOf("KJV", "KJV"), built.toList())
    }

    @Test fun theNotificationNamesTheBookBeingIndexed() {
        val inBuild = CountDownLatch(1)
        val release = CountDownLatch(1)
        SearchIndexingService.builder = { book ->
            inBuild.countDown()
            release.await(10, TimeUnit.SECONDS)
            book.indexStatus = IndexStatus.DONE
        }
        SearchIndexingService.enqueue(context, "KJV")
        startService()
        assertTrue(inBuild.await(10, TimeUnit.SECONDS))
        // the service enters the foreground with the notification of the book being built
        val n = shadowOf(controller!!.get()).lastForegroundNotification
        release.countDown()
        awaitIdle()
        assertTrue("no foreground notification", n != null)
        assertEquals("Creating search index", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.extras.getString(Notification.EXTRA_TEXT), n.extras.getString(Notification.EXTRA_TEXT)!!.contains(kjv.name))
    }

    @Test fun aBookThatIsNoLongerInstalledIsSkipped() {
        val built = CopyOnWriteArrayList<String>()
        SearchIndexingService.builder = { book -> built += book.initials; book.indexStatus = IndexStatus.DONE }
        SearchIndexingService.enqueue(context, "NoSuchBook")
        SearchIndexingService.enqueue(context, "KJV")
        awaitIdle()
        assertEquals(listOf("KJV"), built.toList())
        assertFalse(SearchIndexingService.isBusy)
    }

    @Test fun anInstanceNotInTheInstalledRegistryIsStillBuilt() {
        val built = CopyOnWriteArrayList<String>()
        val queue = IndexingQueue(resolve = { null }, build = { book -> built += book.initials; book.indexStatus = IndexStatus.DONE })
        kjv.indexStatus = IndexStatus.SCHEDULED
        assertTrue(queue.offer("KJV", kjv))
        assertTrue(queue.awaitIdle(10_000))
        assertEquals(listOf("KJV"), built.toList())
        assertEquals(IndexStatus.DONE, kjv.indexStatus)
    }

    @Test fun aQueuedBookRemovedBeforeItsTurnIsNotBuiltAndEndsUndone() {
        val built = CopyOnWriteArrayList<String>()
        val gate = CountDownLatch(1)
        SearchIndexingService.builder = { book ->
            gate.await(10, TimeUnit.SECONDS)
            built += book.initials
            book.indexStatus = IndexStatus.DONE
        }
        geneva.indexStatus = IndexStatus.SCHEDULED
        SearchIndexingService.enqueue(context, kjv)
        SearchIndexingService.enqueue(context, geneva)
        SearchIndexingService.dequeue("Geneva")
        gate.countDown()
        awaitIdle()
        assertEquals(listOf("KJV"), built.toList())
        assertEquals(IndexStatus.UNDONE, geneva.indexStatus)
    }

    @Test fun aStaleIdleNotificationDoesNotStopTheServiceWhileABuildRuns() {
        val release = CountDownLatch(1)
        SearchIndexingService.builder = { book -> release.await(10, TimeUnit.SECONDS); book.indexStatus = IndexStatus.DONE }
        SearchIndexingService.enqueue(context, "KJV")
        startService()
        controller!!.get().onIdle() // as a worker that went idle just before the enqueue would call it
        assertFalse("service stopped under a running build", shadowOf(controller!!.get()).isStoppedBySelf)
        release.countDown()
        awaitIdle()
        assertTrue(shadowOf(controller!!.get()).isStoppedBySelf)
    }

    @Test fun theDefaultBuilderBuildsARealIndex() {
        SearchIndexingService.builder = savedBuilder
        FtsTestSupport.dropIndex(turntb)
        SearchIndexingService.enqueue(context, "TurNTB")
        startService()
        awaitIdle()
        assertEquals(IndexStatus.DONE, turntb.indexStatus)
        assertTrue(FtsTestSupport.manager.isIndexed(turntb))
    }
}
