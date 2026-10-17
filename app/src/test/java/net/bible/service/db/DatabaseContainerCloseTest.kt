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

import kotlinx.coroutines.*
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.SettingsDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room 3 close semantics the restore flow relies on (D1 Task 17): `closeForReplace` followed by the `closeAll`
 * of [DatabaseContainer.reset] closes a database twice, which must be harmless; and a closed Room 3 database never
 * reopens, so a DAO call in the window between the two fails instead of writing into whatever file is in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DatabaseContainerCloseTest {
    companion object {
        private var retainedPreparation: DatabaseContainerCloseTest? = null
    }
    private var fixtureOwner: TestBibleApplication? = null
    private var fixtureChildren: List<Job> = emptyList()
    private var fixtureHadLiveChildren = false
    private var drainOwner: (TestBibleApplication) -> Unit = { app ->
        net.bible.test.DatabaseResetter.runResetBlocking { app.drainAiSettingsFixture() }
    }
    private var resetAction: () -> Unit = { DatabaseContainer.reset() }

    private fun retainCurrentOwner() {
        val app = net.bible.android.BibleApplication.application as TestBibleApplication
        check(fixtureOwner == null || fixtureOwner === app) { "Cannot replace retained preparation owner" }
        fixtureOwner = app
        retainedPreparation = this
        val scope = TestBibleApplication::class.java.getDeclaredField("aiScope")
            .apply { isAccessible = true }.get(app) as CoroutineScope
        val children = checkNotNull(scope.coroutineContext[Job]).children.toList()
        fixtureChildren = (fixtureChildren + children).distinct()
        fixtureHadLiveChildren = fixtureChildren.any { !it.isCompleted }
    }

    private fun drainRetainedOwner() {
        val owner = checkNotNull(fixtureOwner)
        drainOwner(owner) // Successful return proves the exact owner's named queues were joined too.
        assertTrue("INVARIANT exact fixture children must complete before exclusive database experiment",
            fixtureChildren.all { it.isCompleted })
    }

    @Before fun setUp() {
        check(retainedPreparation == null) { "Previous exact preparation owner requires teardown retry" }
        retainCurrentOwner()
        drainRetainedOwner()
        resetAction()
    }

    @After fun tearDown() {
        // A blocked new instance must not reset over another instance's retained preparation.
        if (fixtureOwner == null || retainedPreparation == null) return
        check(retainedPreparation === this) { "Another preparation owner is retained" }
        drainRetainedOwner()
        resetAction()
        retainedPreparation = null
    }

    /** Fixture-owner completion, not a replay of the historical factory bypass. */
    @Test fun exactLiveFixtureChildrenCompleteBeforeExclusiveDatabaseExperiment() {
        assertTrue("Real current fixture must have live observer children before drain", fixtureHadLiveChildren)
        assertTrue("Retained exact fixture children completed", fixtureChildren.all { it.isCompleted })
    }

    @Test fun refusedPreparationRetainsExactOwnerUntilCompletedTeardownRetry() {
        val owner = checkNotNull(fixtureOwner)
        val scope = TestBibleApplication::class.java.getDeclaredField("aiScope")
            .apply { isAccessible = true }.get(owner) as CoroutineScope
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val child = scope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                entered.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "preparation gate timeout" }
            }
        }
        val actualDrain = drainOwner
        val actualReset = resetAction
        val refusal = IllegalStateException("injected preparation drain refusal")
        var resets = 0
        var primary: Throwable? = null
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            retainCurrentOwner()
            assertTrue(fixtureChildren.any { it === child })
            drainOwner = { exact -> assertTrue(exact === owner); throw refusal }
            resetAction = {
                assertTrue("INVARIANT teardown cannot reset with incomplete exact owner", child.isCompleted)
                resets++
                actualReset()
            }
            val error = runCatching { tearDown() }.exceptionOrNull()
            if (error !== refusal) throw AssertionError("Teardown must report drain refusal before reset", error)
            assertEquals(0, resets)
            assertTrue(fixtureOwner === owner && retainedPreparation === this)
            val next = DatabaseContainerCloseTest()
            assertNotNull(runCatching { next.setUp() }.exceptionOrNull())
            next.tearDown()
            assertEquals(0, resets)
            release.countDown()
            drainOwner = actualDrain
            tearDown()
            assertTrue(child.isCompleted)
            assertEquals(1, resets)
            assertTrue(retainedPreparation == null)
        } catch (failure: Throwable) { primary = failure }
        finally {
            release.countDown()
            drainOwner = actualDrain
            resetAction = actualReset
            try { tearDown() } catch (failure: Throwable) {
                val first = primary
                if (first == null) primary = failure else if (first !== failure) first.addSuppressed(failure)
            }
        }
        primary?.let { throw it }
    }

    /**
     * A container factory that builds only for the threads in [allowed] (the test's own, its workers) and refuses every
     * other caller. The singleton is also built by production producers that these tests do not own: the AI settings
     * observer (`AiDatabaseLifetime.observe` -> `AiSettingsStore.refresh` -> `DatabaseContainer.instance` on a
     * DefaultDispatcher worker, re-armed by every `generation` bump of a drop/reset/closeAll) and app-scope service
     * loaders. A stray build between the test's drop and its own first build would publish an instance the test did not
     * make, or be counted as one. Refusing is deterministic where draining is not: it holds for every producer, also
     * after the generation bump of the test's own reset/drop. Install it BEFORE the first drop so no stray can publish
     * after that drop (a stray published earlier is the one the first drop removes).
     */
    private fun onlyBuildsOn(allowed: Set<Thread>, build: () -> DatabaseContainer): () -> DatabaseContainer = {
        check(Thread.currentThread() in allowed) { "stray container build refused on ${Thread.currentThread().name}" }
        build()
    }

    @Test fun resetWaitsForReadyClearedConstructionThenClosesItsPublishedContainer() {
        val originalApp = net.bible.android.BibleApplication.application
        val originalReady = DatabaseContainer.ready
        val originalFactory = DatabaseContainer.containerFactory
        val productionApp = object : net.bible.android.BibleApplication() { }
        android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
            .apply { isAccessible = true }.invoke(productionApp, originalApp.baseContext)
        val applicationField = net.bible.android.BibleApplication::class.java.getDeclaredField("application").apply { isAccessible = true }
        applicationField.set(null, originalApp)
        val constructing = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val built = java.util.concurrent.atomic.AtomicReference<DatabaseContainer>()
        val allowed = java.util.concurrent.ConcurrentHashMap.newKeySet<Thread>().apply { add(Thread.currentThread()) }
        var opener: Thread? = null
        var resetter: Thread? = null
        try {
            DatabaseContainer.ready = true
            DatabaseContainer.containerFactory = onlyBuildsOn(allowed) {
                originalFactory().also { current ->
                    built.set(current)
                    applicationField.set(null, productionApp)
                    DatabaseContainer.withReadyCleared {
                        constructing.countDown()
                        check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "construction harness timeout" }
                    }
                }
            }
            DatabaseContainer.dropInstanceWithoutOpening()
            opener = Thread { allowed.add(Thread.currentThread()); try { DatabaseContainer.instance } catch (e: Throwable) { failure.set(e) } }.apply { start() }
            assertTrue(constructing.await(5, java.util.concurrent.TimeUnit.SECONDS))
            resetter = Thread { allowed.add(Thread.currentThread()); try { DatabaseContainer.reset() } catch (e: Throwable) { failure.set(e) } }.apply { start() }
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
            while (resetter.state != Thread.State.BLOCKED && resetter.isAlive && System.nanoTime() < deadline) Thread.yield()
            assertEquals("reset really waits for the constructor monitor", Thread.State.BLOCKED, resetter.state)
            release.countDown()
            opener.join(5_000); resetter.join(5_000)
            assertTrue("workers completed", !opener.isAlive && !resetter.isAlive)
            failure.get()?.let { throw AssertionError("worker failed", it) }
            val current = built.get()
            assertNotNull("reset closes the container published after readiness returns",
                runCatching { runBlocking { current.bookmarkDb.bookmarkDao().allLabelsSortedByName() } }.exceptionOrNull())
            DatabaseContainer.containerFactory = originalFactory
            assertNotSame(current, DatabaseContainer.instance)
        } finally {
            release.countDown()
            opener?.join(5_000); resetter?.join(5_000)
            applicationField.set(null, originalApp)
            DatabaseContainer.ready = originalReady
            DatabaseContainer.containerFactory = originalFactory
        }
    }

    @Test fun resetIsEagerButDropNeverConstructsAContainer() {
        val originalFactory = DatabaseContainer.containerFactory
        var opens = 0
        val allowed = setOf(Thread.currentThread())
        try {
            // Factory first (see onlyBuildsOn); each drop/reset below bumps the AI lifetime generation again, and a
            // refused stray cannot publish or be counted after any of those bumps.
            DatabaseContainer.containerFactory = onlyBuildsOn(allowed) { opens++; originalFactory() }
            DatabaseContainer.dropInstanceWithoutOpening()
            DatabaseContainer.dropInstanceWithoutOpening()
            assertEquals(0, opens)
            DatabaseContainer.reset()
            assertEquals(1, opens)
            DatabaseContainer.dropInstanceWithoutOpening()
            assertEquals(1, opens)
        } finally { DatabaseContainer.containerFactory = originalFactory }
    }

    @Test fun closeForReplaceThenCloseAllIsSafeAndTheNextContainerWorks() {
        val old = DatabaseContainer.instance
        runBlocking { old.bookmarkDb.bookmarkDao().insert(BookmarkEntities.Label(name = "before close")) }

        old.closeForReplace(BookmarkDatabase.dbFileName)
        old.closeForReplace(SettingsDatabase.dbFileName)
        DatabaseContainer.reset() // closeAll(): closes both of them a second time

        val fresh = DatabaseContainer.instance
        assertNotSame(old, fresh)
        assertEquals(listOf("before close"),
            runBlocking { fresh.bookmarkDb.bookmarkDao().allLabelsSortedByName() }.map { it.name }.filter { it == "before close" })
    }

    @Test fun aDaoCallOnADatabaseClosedForReplaceFails() {
        val old = DatabaseContainer.instance
        old.closeForReplace(BookmarkDatabase.dbFileName)
        val error = runCatching { runBlocking { old.bookmarkDb.bookmarkDao().allLabelsSortedByName() } }.exceptionOrNull()
        assertNotNull("a closed Room 3 database must not reopen on a DAO call", error)
        // Room 3's RoomDatabase.throwIfClosed(); only a call racing the close sees SQLiteException(MISUSE) instead.
        assertTrue(error.toString(), error is IllegalStateException)
    }

    /**
     * A first cloud sync swaps the downloaded file in and calls resetXDb(), which replaces the instance. The container's
     * database lists must follow: sync()/vacuum() (run by every backup) must use the live instance, not throw on the
     * closed old one, and closeForReplace/closeAll must close the live one.
     */
    @Test fun aResetDatabaseIsTheOneSyncedVacuumedAndClosed() {
        val c = DatabaseContainer.instance
        val oldBookmarks = c.bookmarkDb
        val liveBookmarks = c.resetBookmarkDb()
        val liveWorkspaces = c.resetWorkspaceDb()
        assertNotSame(oldBookmarks, liveBookmarks)

        runBlocking { c.sync(); c.vacuum() } // threw "Connection pool is closed" on the stale instances

        c.closeForReplace(BookmarkDatabase.dbFileName)
        assertNotNull("closeForReplace must close the live bookmark database",
            runCatching { runBlocking { liveBookmarks.bookmarkDao().allLabelsSortedByName() } }.exceptionOrNull())

        DatabaseContainer.reset() // closeAll()
        assertNotNull("closeAll must close the live workspace database",
            runCatching { runBlocking { liveWorkspaces.workspaceDao().allWorkspaces() } }.exceptionOrNull())
    }
}
