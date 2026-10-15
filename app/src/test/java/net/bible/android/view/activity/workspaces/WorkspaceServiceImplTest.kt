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
package net.bible.android.view.activity.workspaces

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [WorkspaceServiceImpl]'s database members are `suspend` (L1a.8 Task 13). They run inside
 * `runBlocking` rather than `runTest`: the service reads Room on its own dispatcher and a
 * `runTest` + Main-dispatcher mix is a known hang in this suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WorkspaceServiceImplTest {
    private lateinit var repo: WindowRepository
    private lateinit var service: WorkspaceServiceImpl
    private var wasReady = false
    private var wasInitialized = false
    private val dao get() = DatabaseContainer.instance.workspaceDb.workspaceDao()

    @Before fun setUp() {
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true
        CommonUtils.settings.setBoolean("first-time", false)
        wasInitialized = CommonUtils.initialized
        CommonUtils.initialized = true
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        runBlocking { repo.persistence.awaitPersisted() }
        service = WorkspaceServiceImpl(CommonUtils.windowControl)
    }

    @After fun tearDown() {
        try {
            runBlocking { repo.persistence.awaitPersisted() }
        } finally {
            try { DatabaseResetter.resetDatabase() } finally {
                DatabaseContainer.ready = wasReady
                CommonUtils.initialized = wasInitialized
                repo.clear()
            }
        }
    }

    @Test fun applyChangesOrdersDeletesAndRenames() = runBlocking {
        val current = service.loadAll().single()
        val b = service.createWorkspace("b")
        val c = service.createWorkspace("c")
        val d = service.createWorkspace("d")

        service.applyChanges(
            orderedIds = listOf(d.id, c.id, current.id),
            deletedIds = listOf(b.id),
            renamed = mapOf(c.id to "c-renamed"),
            changedIds = setOf(c.id, d.id, current.id),
        )

        val rows = dao.allWorkspaces()
        assertEquals(listOf(d.id, c.id, current.id), rows.map { it.id.toString() })
        assertEquals("c-renamed", rows.first { it.id.toString() == c.id }.name)
        assertNull("the deleted workspace is gone", dao.workspace(IdType(b.id)))
        assertEquals(listOf(0, 1, 2), rows.map { it.orderNumber })
    }

    @Test fun deleteCreatedRemovesOnlyTheNamedRows() = runBlocking {
        service.loadAll()
        val keep = service.createWorkspace("keep")
        val drop = service.createWorkspace("drop")
        service.deleteCreated(listOf(drop.id))
        val ids = dao.allWorkspaces().map { it.id.toString() }
        assertFalse(drop.id in ids)
        assertEquals(true, keep.id in ids)
    }

    /**
     * A host destroyed mid-visit cancels its scope; the cleanup delete must still reach the database.
     * Several rows, so that a delete which stopped at the first cancellation point would show.
     */
    @Test fun deleteCreatedStillCompletesWhenTheCallerIsCancelled() {
        runBlocking { service.loadAll() }
        val drops = runBlocking { (1..4).map { service.createWorkspace("drop$it") } }
        val callerScope = CoroutineScope(Dispatchers.Main)
        callerScope.cancel()
        callerScope.launch(start = CoroutineStart.UNDISPATCHED) { service.deleteCreated(drops.map { it.id }) }
        val deadline = System.nanoTime() + 10_000_000_000L
        fun remaining() = runBlocking { dao.allWorkspaces().count { w -> drops.any { it.id == w.id.toString() } } }
        while (remaining() > 0 && System.nanoTime() < deadline) Thread.sleep(20)
        assertEquals(0, remaining())
    }

    /**
     * Save, then an immediate recreate: the new host's `loadAll()` replaces the shared working list while Save is
     * still waiting for the queued workspace write. The rows to update must have been captured before that wait.
     */
    @Test fun applyChangesWritesTheRenamesEvenIfLoadAllReplacesTheWorkingListWhileItWaits() = runBlocking {
        val current = service.loadAll().single()
        val b = service.createWorkspace("b")
        val c = service.createWorkspace("c")
        val gate = CompletableDeferred<Unit>()
        repo.persistence.enqueue { gate.await() }

        val recreate = launch(start = CoroutineStart.UNDISPATCHED) { service.loadAll() }
        val save = launch(start = CoroutineStart.UNDISPATCHED) {
            service.applyChanges(
                orderedIds = listOf(c.id, current.id),
                deletedIds = listOf(b.id),
                renamed = mapOf(c.id to "c-renamed"),
                changedIds = setOf(c.id, current.id),
            )
        }
        gate.complete(Unit)
        save.join(); recreate.join()

        val rows = dao.allWorkspaces()
        assertEquals("c-renamed", rows.first { it.id.toString() == c.id }.name)
        assertEquals(listOf(c.id, current.id), rows.map { it.id.toString() })
    }
}
