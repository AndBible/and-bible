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

package net.bible.android.database

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Behaviour of [WorkspaceDao] and [GlobalTextDisplaySettingsDao]; the `@Transaction` functions run under
 * `withTimeout` so a self-deadlocking transaction fails instead of hanging the suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WorkspaceDaoTest {
    private lateinit var db: WorkspaceDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, WorkspaceDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    private fun window(workspace: WorkspaceEntities.Workspace, order: Int) = WorkspaceEntities.Window(
        workspaceId = workspace.id, isSynchronized = true, isPinMode = false,
        windowLayout = WorkspaceEntities.WindowLayout("SPLIT"), orderNumber = order,
    )

    private fun pageManager(window: WorkspaceEntities.Window, doc: String) = WorkspaceEntities.PageManager(
        windowId = window.id,
        biblePage = WorkspaceEntities.BiblePage(doc, WorkspaceEntities.Verse("KJV", 0, 1, 1)),
        commentaryPage = null, dictionaryPage = null, generalBookPage = null, mapPage = null,
        currentCategoryName = "BIBLE", textDisplaySettings = null, jsState = null,
    )

    @Test fun cloneWorkspaceCopiesWindowsPageManagersAndLabelOverrides() = runBlocking {
        withTimeout(10_000) {
            val dao = db.workspaceDao()
            val ws = WorkspaceEntities.Workspace("orig", contentsText = "c", orderNumber = 3).also { dao.insertWorkspace(it) }
            val w1 = window(ws, 0).also { dao.insertWindow(it) }
            val w2 = window(ws, 1).also { dao.insertWindow(it) }
            dao.insertPageManager(pageManager(w1, "KJV"))
            dao.insertPageManager(pageManager(w2, "ESV"))
            val labelId = IdType()
            dao.insertOrUpdateLabelOverride(WorkspaceEntities.WorkspaceLabelOverride(ws.id, labelId, 2))

            val clone = dao.cloneWorkspace(ws.id, "copy")

            assertNotEquals(ws.id, clone.id)
            assertEquals("copy", dao.workspace(clone.id)!!.name)
            assertEquals(3, clone.orderNumber)
            val cloneWindows = dao.windows(clone.id)
            assertEquals(2, cloneWindows.size)
            assertTrue("clone windows get fresh ids", cloneWindows.none { it.id == w1.id || it.id == w2.id })
            assertEquals(listOf("KJV", "ESV"), cloneWindows.map { dao.pageManager(it.id)!!.biblePage.document })
            assertEquals(listOf(2), dao.labelOverrides(clone.id).map { it.overrideMode })
            // The original is untouched.
            assertEquals(listOf(w1.id, w2.id), dao.windows(ws.id).map { it.id })
            assertEquals("KJV", dao.pageManager(w1.id)!!.biblePage.document)
        }
    }

    @Test fun cloneOfMissingWorkspaceInsertsAnEmptyOneWithTheNewName() = runBlocking {
        withTimeout(10_000) {
            val dao = db.workspaceDao()
            val clone = dao.cloneWorkspace(IdType(), "fresh")
            assertEquals("fresh", dao.workspace(clone.id)!!.name)
            assertTrue(dao.windows(clone.id).isEmpty())
        }
    }

    @Test fun updateHistoryItemsReplacesThatWindowsHistoryOnly() = runBlocking {
        withTimeout(10_000) {
            val dao = db.workspaceDao()
            val ws = WorkspaceEntities.Workspace("w").also { dao.insertWorkspace(it) }
            val w1 = window(ws, 0).also { dao.insertWindow(it) }
            val w2 = window(ws, 1).also { dao.insertWindow(it) }
            fun item(w: WorkspaceEntities.Window, key: String) =
                WorkspaceEntities.HistoryItem(w.id, Date(1000), "KJV", key, null)
            dao.updateHistoryItems(w1.id, listOf(item(w1, "Gen.1.1"), item(w1, "Gen.1.2")))
            dao.updateHistoryItems(w2.id, listOf(item(w2, "Exo.1.1")))
            dao.updateHistoryItems(w1.id, listOf(item(w1, "Gen.2.1")))
            assertEquals(listOf("Gen.2.1"), dao.historyItems(w1.id).map { it.key })
            assertEquals(listOf("Exo.1.1"), dao.historyItems(w2.id).map { it.key })
        }
    }

    @Test fun applyTextToDisplaySettingsToAllWorkspacesUpdatesEveryWorkspace() = runBlocking {
        withTimeout(10_000) {
            val dao = db.workspaceDao()
            dao.insertWorkspace(WorkspaceEntities.Workspace("a", orderNumber = 0))
            dao.insertWorkspace(WorkspaceEntities.Workspace("b", orderNumber = 1))
            dao.insertWorkspace(WorkspaceEntities.Workspace("c", orderNumber = 2))
            dao.applyTextToDisplaySettingsToAllWorkspaces(WorkspaceEntities.TextDisplaySettings(strongsMode = 2))
            val all = dao.allWorkspaces()
            assertEquals(3, all.size)
            assertEquals(listOf(2, 2, 2), all.map { it.textDisplaySettings!!.strongsMode })
            assertEquals(listOf("a", "b", "c"), all.map { it.name })
        }
    }

    @Test fun deleteWorkspaceCascadesToWindowsAndPageManagers() = runBlocking {
        val dao = db.workspaceDao()
        val ws = WorkspaceEntities.Workspace("w").also { dao.insertWorkspace(it) }
        val w = window(ws, 0).also { dao.insertWindow(it) }
        dao.insertPageManager(pageManager(w, "KJV"))
        assertEquals(1, dao.workspacesCount())
        dao.deleteWorkspace(ws.id)
        assertEquals(0, dao.workspacesCount())
        assertTrue(dao.allWindows().isEmpty())
        assertNull(dao.pageManager(w.id))
    }

    @Test fun labelOverrideReplaceAndDeletes() = runBlocking {
        val dao = db.workspaceDao()
        val ws = WorkspaceEntities.Workspace("w").also { dao.insertWorkspace(it) }
        val l1 = IdType(); val l2 = IdType()
        dao.insertOrUpdateLabelOverride(WorkspaceEntities.WorkspaceLabelOverride(ws.id, l1, 0))
        dao.insertOrUpdateLabelOverride(WorkspaceEntities.WorkspaceLabelOverride(ws.id, l1, 3))
        dao.insertOrUpdateLabelOverride(WorkspaceEntities.WorkspaceLabelOverride(ws.id, l2, 1))
        assertEquals(mapOf(l1 to 3, l2 to 1), dao.labelOverrides(ws.id).associate { it.labelId to it.overrideMode })
        dao.deleteLabelOverride(ws.id, l1)
        assertEquals(listOf(l2), dao.labelOverrides(ws.id).map { it.labelId })
        dao.deleteOverridesByLabelId(l2)
        assertTrue(dao.labelOverrides(ws.id).isEmpty())
    }

    @Test fun globalTextDisplaySettingsSetReplacesSingleton() = runBlocking {
        val dao = db.globalTextDisplaySettingsDao()
        assertNull(dao.get())
        dao.set(GlobalTextDisplaySettings(textDisplaySettings = WorkspaceEntities.TextDisplaySettings(strongsMode = 1)))
        dao.set(GlobalTextDisplaySettings(textDisplaySettings = WorkspaceEntities.TextDisplaySettings(strongsMode = 2)))
        assertEquals(2, dao.get()!!.textDisplaySettings.strongsMode)
    }
}
