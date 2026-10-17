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
import android.app.NotificationManager
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
import org.junit.Assert.assertNotNull
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
    private val installed get() = Books.installed()
    private val kjv get() = installed.getBook("KJV")!!
    /** The "second book" role; any installed book of the test-module set that is not KJV. */
    private val second get() = installed.getBook("FinPR")!!
    /** The "real index build" role: small, NT only, and not indexed by other test classes. */
    private val smallNt get() = installed.getBook("ISV")!!
    private var controller: ServiceController<SearchIndexingService>? = null
    private lateinit var savedBuilder: (Book) -> Unit

    @Before fun setUp() {
        listOf("KJV", "FinPR", "ISV").forEach {
            assertNotNull("test module $it missing from ~/.sword", installed.getBook(it))
        }
        savedBuilder = SearchIndexingService.builder
        SearchIndexingService.resetForTest()
    }

    @After fun tearDown() {
        try {
            runCatching { SearchIndexingService.awaitIdleForTest(30_000) }
            // Each cleanup step is independent: a failure in one must not skip the others.
            if (::savedBuilder.isInitialized) runCatching { SearchIndexingService.builder = savedBuilder }
            runCatching { SearchIndexingService.resetForTest() }
            listOf("KJV", "FinPR", "ISV").forEach { initials ->
                runCatching { installed.getBook(initials)?.indexStatus = IndexStatus.UNDONE }
            }
            runCatching { installed.getBook("ISV")?.let { FtsTestSupport.dropIndex(it) } }
        } finally {
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
        SearchIndexingService.enqueue(context, kjv)
        SearchIndexingService.enqueue(context, second)
        startService()
        gate.countDown()
        awaitIdle()
        assertEquals(listOf("KJV", second.initials), built.toList())
        assertEquals(IndexStatus.DONE, kjv.indexStatus)
        assertEquals(IndexStatus.DONE, second.indexStatus)
        assertTrue("service must stop itself", shadowOf(controller!!.get()).isStoppedBySelf)
    }

    @Test fun enqueueStartsTheForegroundService() {
        SearchIndexingService.builder = { it.indexStatus = IndexStatus.DONE }
        SearchIndexingService.enqueue(context, kjv)
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
        SearchIndexingService.enqueue(context, kjv)
        SearchIndexingService.enqueue(context, kjv) // while the first is queued or creating
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
        SearchIndexingService.enqueue(context, kjv)
        SearchIndexingService.enqueue(context, second)
        startService()
        awaitIdle()
        assertEquals(listOf("KJV", second.initials), built.toList())
        assertEquals(IndexStatus.UNDONE, kjv.indexStatus)
        assertEquals(IndexStatus.DONE, second.indexStatus)
        assertTrue(shadowOf(controller!!.get()).isStoppedBySelf)
    }

    @Test fun aBookRequestedAgainAfterItsBuildIsBuiltAgain() {
        val built = CopyOnWriteArrayList<String>()
        SearchIndexingService.builder = { book -> built += book.initials; book.indexStatus = IndexStatus.DONE }
        SearchIndexingService.enqueue(context, kjv)
        awaitIdle()
        SearchIndexingService.enqueue(context, kjv)
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
        SearchIndexingService.enqueue(context, kjv)
        startService()
        assertTrue(inBuild.await(10, TimeUnit.SECONDS))
        // The notification naming the book is the one posted with notify() (onBookStarted); the startForeground one
        // may predate the worker picking the book up and then has no text. startService() returns after attach(),
        // so by the time the build runs, onBookStarted has posted it (NOTIFICATION_ID is private, hence 8).
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val n = shadowOf(notificationManager).getNotification(8)
        release.countDown()
        awaitIdle()
        assertTrue("no foreground notification", n != null)
        assertEquals("Creating search index", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.extras.getString(Notification.EXTRA_TEXT), n.extras.getString(Notification.EXTRA_TEXT)!!.contains(kjv.name))
    }

    @Test fun aBookThatIsNoLongerInstalledIsSkipped() {
        val built = CopyOnWriteArrayList<String>()
        val queue = IndexingQueue(resolve = { Books.installed().getBook(it) }, build = { book -> built += book.initials; book.indexStatus = IndexStatus.DONE })
        assertTrue(queue.offer("NoSuchBook", null))
        assertTrue(queue.offer("KJV", null))
        assertTrue(queue.awaitIdle(10_000))
        assertEquals(listOf("KJV"), built.toList())
        assertFalse(queue.isBusy)
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
        second.indexStatus = IndexStatus.SCHEDULED
        SearchIndexingService.enqueue(context, kjv)
        SearchIndexingService.enqueue(context, second)
        SearchIndexingService.dequeue(second.initials)
        gate.countDown()
        awaitIdle()
        assertEquals(listOf("KJV"), built.toList())
        assertEquals(IndexStatus.UNDONE, second.indexStatus)
    }

    @Test fun aStaleIdleNotificationDoesNotStopTheServiceWhileABuildRuns() {
        val release = CountDownLatch(1)
        SearchIndexingService.builder = { book -> release.await(10, TimeUnit.SECONDS); book.indexStatus = IndexStatus.DONE }
        SearchIndexingService.enqueue(context, kjv)
        startService()
        controller!!.get().onIdle() // as a worker that went idle just before the enqueue would call it
        assertFalse("service stopped under a running build", shadowOf(controller!!.get()).isStoppedBySelf)
        release.countDown()
        awaitIdle()
        assertTrue(shadowOf(controller!!.get()).isStoppedBySelf)
    }

    @Test fun theForegroundTimeoutStopsTheServiceButTheBuildContinuesInProcess() {
        val inBuild = CountDownLatch(1)
        val release = CountDownLatch(1)
        val built = CopyOnWriteArrayList<String>()
        SearchIndexingService.builder = { book ->
            inBuild.countDown()
            release.await(10, TimeUnit.SECONDS)
            built += book.initials
            book.indexStatus = IndexStatus.DONE
        }
        SearchIndexingService.enqueue(context, kjv)
        SearchIndexingService.enqueue(context, second)
        startService()
        assertTrue(inBuild.await(10, TimeUnit.SECONDS))
        controller!!.get().onTimeout(1, 1)
        assertTrue("service must stop itself on the Android 15 timeout", shadowOf(controller!!.get()).isStoppedBySelf)
        release.countDown()
        awaitIdle()
        assertEquals(listOf("KJV", second.initials), built.toList())
    }

    @Test fun theDefaultBuilderBuildsARealIndex() {
        SearchIndexingService.builder = savedBuilder
        FtsTestSupport.dropIndex(smallNt)
        SearchIndexingService.enqueue(context, smallNt)
        startService()
        awaitIdle()
        assertEquals(IndexStatus.DONE, smallNt.indexStatus)
        assertTrue(FtsTestSupport.manager.isIndexed(smallNt))
    }
}
