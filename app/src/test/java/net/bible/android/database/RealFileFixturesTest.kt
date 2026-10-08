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

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.mydocument.MyDocumentDatabase
import net.bible.android.database.progress.ProgressDatabase
import net.bible.service.db.DatabaseContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Opens database files that the pre-Room-3 (requery SQLite) release build wrote on a real device
 * through the production [DatabaseContainer] and reads back what was entered by hand. Unlike the
 * schema-export fixtures this catches anything a real file carries that a fresh CREATE does not
 * (journal mode, page layout, real identity hashes, real value encodings). The fixture files must
 * never be regenerated from a newer build; the contents are listed in the Task 3 notes of the D1 plan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class RealFileFixturesTest {
    @Before fun before() = DatabaseContainer.reset()
    @After fun after() = DatabaseContainer.reset()

    private fun install(name: String) {
        val src = File("src/test/resources/db-fixtures/requery-2026-10/$name")
        assertTrue("missing fixture ${src.absolutePath}", src.isFile)
        src.copyTo(application.getDatabasePath(name).also { it.parentFile!!.mkdirs() }, overwrite = true)
    }

    @Test fun requeryBuiltBookmarksOpenAndReadBack() {
        install(BookmarkDatabase.dbFileName)
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

        val bookmarks = dao.allBookmarks()
        assertEquals(3, bookmarks.size)
        assertEquals(setOf(1, 26137, 14000), bookmarks.map { it.kjvOrdinalStart }.toSet())
        assertTrue(bookmarks.all { it.v11n.name == "KJVA" })

        val noted = bookmarks.single { it.kjvOrdinalStart == 26137 }
        assertEquals("d1-fixture-note", noted.notes)
        assertEquals(1, bookmarks.count { it.notes != null })

        val labelled = bookmarks.single { it.kjvOrdinalStart == 14000 }
        assertEquals(setOf("d1-label-a", "d1-label-b"), dao.labelsForBookmark(labelled.id).map { it.name }.toSet())
        assertTrue(dao.labelsForBookmark(bookmarks.single { it.kjvOrdinalStart == 1 }.id).isEmpty())

        val labels = dao.allLabelsSortedByName()
        assertEquals(listOf("d1-label-a", "d1-label-b", "d1-studypad"), labels.map { it.name })
        val entries = dao.studyPadTextEntriesByLabelId(labels.single { it.name == "d1-studypad" }.id)
        assertEquals(listOf("d1-entry-one", "d1-entry-two"), entries.sortedBy { it.orderNumber }.map { it.text })
    }

    @Test fun requeryBuiltWorkspacesOpenAndReadBack() {
        install(WorkspaceDatabase.dbFileName)
        val dao = DatabaseContainer.instance.workspaceDb.workspaceDao()

        val workspaces = dao.allWorkspaces()
        assertEquals(1, workspaces.size)
        assertEquals("d1-workspace-2", workspaces.single().name)
        val windows = dao.windows(workspaces.single().id)
        assertEquals(3, windows.size)
        assertEquals(3, dao.allWindows().size)
        windows.forEach {
            val page = dao.pageManager(it.id)
            assertNotNull(page)
            assertEquals("KJV", page!!.biblePage.document)
            assertEquals("BIBLE", page.currentCategoryName)
            assertEquals("KJVA", page.biblePage.verse.versification)
        }
    }

    @Test fun requeryBuiltReadingPlanOpensAndReadsBack() = runBlocking {
        install(ReadingPlanDatabase.dbFileName)
        val dao = DatabaseContainer.instance.readingPlanDb.readingPlanDao()

        val plan = dao.getPlan("d1-plan")
        assertNotNull(plan)
        assertEquals(2, plan!!.planCurrentDay)
        assertEquals(1700000000000L, plan.planStartDate.time)
        assertEquals("""{"d1":true}""", dao.getStatus("d1-plan", 1)!!.readingStatus)
        assertEquals(null, dao.getStatus("d1-plan", 2))
    }

    @Test fun requeryBuiltMyDocumentsOpenAndReadBack() {
        install(MyDocumentDatabase.dbFileName)
        val docs = DatabaseContainer.instance.myDocumentDb.myDocumentDao().allDocuments()

        assertEquals(1, docs.size)
        assertEquals("d1-mydoc", docs.single().name)
        assertEquals("MyDoc_d1", docs.single().initials)
        assertEquals(1791489829006L, docs.single().createdAt)
    }

    @Test fun requeryBuiltProgressOpensAndReadsBack() {
        install(ProgressDatabase.dbFileName)
        val dao = DatabaseContainer.instance.progressDb.progressDao()

        assertEquals(listOf(26137), dao.allMemorizedVerses().map { it.kjvOrdinal })
        assertEquals(1791489829008L, dao.allMemorizedVerses().single().memorizedAt)
        val history = dao.getChapterReadHistory(42, 3, 1)
        assertEquals(1, history.size)
        assertEquals("d1", history.single().bookInitials)
        assertEquals("MANUAL", history.single().source.name)
        assertEquals(1791489829011L, history.single().readAt)
    }

    @Test fun requeryBuiltSettingsOpenAndReadBack() = runBlocking {
        install(SettingsDatabase.dbFileName)
        val db = DatabaseContainer.instance.settingsDb

        assertTrue(db.booleanSettingDao().get("d1-bool", false))
        assertEquals("d1-value", db.stringSettingDao().get("d1-string", null))
        assertEquals(123456789012L, db.longSettingDao().get("d1-long", -1L))
        assertEquals(7L, db.longSettingDao().get("d1-int", -1L))
        assertEquals(2.5, db.doubleSettingDao().get("d1-double", -1.0), 0.0)
        assertFalse(db.booleanSettingDao().get("d1-absent", false))
    }

    @Test fun requeryBuiltAiSettingsOpenAndReadBack() {
        install(AiSettingsDatabase.dbFileName)
        val db = DatabaseContainer.instance.aiSettingsDb

        val provider = db.llmProviderConfigDao().all().single()
        assertEquals("d1-provider", provider.displayName)
        assertEquals("CUSTOM", provider.providerType)
        assertEquals("https://d1.example/v1", provider.endpoint)
        val model = db.llmConfiguredModelDao().all().single()
        assertEquals("d1-model", model.modelId)
        assertEquals(provider.id, model.providerConfigId)
        assertEquals(model, db.llmConfiguredModelDao().getByProvider(provider.id).single())
    }

    /**
     * Opens the bookmarks file and returns how many bookmarks it serves, or the exception that
     * rejected it. The platform's default corruption handler (and possibly the requery one) deletes
     * a corrupt file and recreates it empty instead of throwing, so "rejected" means: it throws OR
     * it serves no fixture data. Silently serving the bytes as valid data is the only failure.
     */
    private fun openBookmarkCount(): Result<Int> {
        // Container creation must not be what throws: the rejection has to come from opening the file.
        val bookmarkDb = DatabaseContainer.instance.bookmarkDb
        return runCatching { bookmarkDb.bookmarkDao().allBookmarks().size }
    }

    @Test fun corruptedFixtureIsRejected() {
        // Control: the very same open path on the intact file serves the 3 bookmarks, so the
        // assertion below cannot pass vacuously (e.g. a broken open path that always yields 0).
        install(BookmarkDatabase.dbFileName)
        assertEquals(3, openBookmarkCount().getOrThrow())
        DatabaseContainer.reset()

        val file = application.getDatabasePath(BookmarkDatabase.dbFileName)
        file.writeBytes(ByteArray(4096) { 7 })
        val result = openBookmarkCount()
        result.onSuccess { assertEquals("corrupt file must not serve fixture data", 0, it) }
        result.onFailure { assertTrue(it.toString(), it is SQLiteException) }
    }
}
