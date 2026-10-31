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

package net.bible.service.cloudsync.canonical

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import net.bible.android.TestBibleApplication
import net.bible.android.control.progress.ProgressControl
import net.bible.android.database.DbDate
import net.bible.android.database.IdType
import net.bible.android.database.LogEntry
import net.bible.android.database.LogEntryTypes
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.mydocument.MyDocument
import net.bible.android.database.mydocument.MyDocumentPage
import net.bible.android.database.mydocument.MyDocumentPageContent
import net.bible.android.database.progress.MemorizedVerse
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlan
import net.bible.android.database.readingplan.ReadingPlanEntities.ReadingPlanStatus
import net.bible.android.platform.AndroidLocalCalendar
import net.bible.service.cloudsync.LAST_PATCH_WRITTEN_KEY
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.createTriggers
import net.bible.service.cloudsync.dropTriggers
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.openSqlite
import net.bible.service.db.queryRows
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.BuiltinPromptOverride
import net.bible.service.llm.GlobalAiSettings
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.LlmUsageRecord
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The local heal of #3906: rows created with random ids are re-keyed to their canonical ids. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35])
class CanonicalHealTest {
    private val db get() = DatabaseContainer.instance
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var previousDispatcher: kotlinx.coroutines.CoroutineDispatcher
    private lateinit var previousLauncher: OrderedLauncher

    private val docs get() = SyncableDatabaseDefinition.MYDOCUMENTS.accessor
    private val ai get() = SyncableDatabaseDefinition.AI_SETTINGS.accessor
    private val plans get() = SyncableDatabaseDefinition.READINGPLANS.accessor
    private val progress get() = SyncableDatabaseDefinition.PROGRESS.accessor

    @Before fun setUp() = runBlocking {
        // The container does not install the sync triggers in unit tests; the log entries are part of what is tested.
        for (accessor in listOf(plans, docs, progress, ai)) { dropTriggers(accessor); createTriggers(accessor) }
        previousDispatcher = MyDocumentBookManager.mainDispatcher
        previousLauncher = MyDocumentBookManager.launcher
        MyDocumentBookManager.mainDispatcher = Dispatchers.Unconfined
        MyDocumentBookManager.launcher = OrderedLauncher(syncScope)
    }

    @After fun tearDown() {
        canonicalHealFailureInjector = null
        MyDocumentBookManager.mainDispatcher = previousDispatcher
        MyDocumentBookManager.launcher = previousLauncher
        syncScope.cancel()
        DatabaseResetter.resetDatabase()
    }

    private fun <T> read(db: SyncableDatabaseAccessor<*>, sql: String, row: (androidx.sqlite.SQLiteStatement) -> T): List<T> =
        openSqlite(db.localDbPath).use { c -> c.queryRows(sql, row = row) }

    private fun idHex(id: IdType) = id.toHex()

    private suspend fun logOf(db: SyncableDatabaseAccessor<*>, table: String) =
        db.dao.allLogEntries().filter { it.tableName == table }

    private suspend fun addAiBook(initials: String = "AIDocuments"): MyDocument {
        val doc = MyDocument(name = "AI", initials = initials)
        db.myDocumentDb.myDocumentDao().insert(doc)
        return doc
    }

    @Test fun healMovesAiBookAndPagesFollow() = runBlocking {
        val dao = db.myDocumentDb.myDocumentDao()
        val doc = addAiBook()
        val pages = (1..2).map { MyDocumentPage(documentId = doc.id, title = "t$it", pageKey = "k$it", orderNumber = it) }
        pages.forEach { dao.insert(it); dao.insertOrUpdateContent(MyDocumentPageContent(it.id, "content ${it.pageKey}")) }

        assertEquals(1, healCanonicalIds(docs))

        val canonical = canonicalMyDocumentId("AIDocuments")
        val book = dao.allDocuments().single()
        assertEquals(canonical, book.id)
        assertEquals(pages.map { it.id }.toSet(), dao.pagesForDocument(canonical).map { it.id }.toSet())
        pages.forEach { assertEquals("content ${it.pageKey}", dao.getContent(it.id)) }
        assertEquals(setOf(idHex(canonical)), read(docs, "SELECT DISTINCT lower(hex(documentId)) FROM MyDocumentPage") { it.getText(0) }.toSet())
    }

    @Test fun healKeepsLogTimestamps() = runBlocking {
        val doc = addAiBook()
        db.myDocumentDb.myDocumentDao().insert(MyDocumentPage(documentId = doc.id, title = "t", pageKey = "k", orderNumber = 1))
        val before = logOf(docs, "MyDocument").single()
        Thread.sleep(10)

        healCanonicalIds(docs)

        val after = logOf(docs, "MyDocument").single()
        assertEquals(canonicalMyDocumentId("AIDocuments"), after.entityId1)
        assertEquals(before.lastUpdated, after.lastUpdated)
        assertEquals(before.sourceDevice, after.sourceDevice)
        assertEquals(LogEntryTypes.UPSERT, after.type)
        // Page log entries are keyed by the page id, which does not change.
        assertEquals(1, logOf(docs, "MyDocumentPage").size)
    }

    @Test fun healDropsOrphanUpserts() = runBlocking {
        val orphan = IdType()
        val gone = IdType()
        val dao = docs.localDb.syncDao()
        openSqlite(docs.localDbPath).use { c ->
            c.prepare("INSERT INTO LogEntry VALUES ('MyDocument', ?, '', 'UPSERT', 5, 'x')").use { it.bindBlob(1, orphan.toByteArray()!!); it.step() }
            c.prepare("INSERT INTO LogEntry VALUES ('MyDocument', ?, '', 'DELETE', 6, 'x')").use { it.bindBlob(1, gone.toByteArray()!!); it.step() }
        }
        healCanonicalIds(docs)
        val log = dao.allLogEntries().filter { it.tableName == "MyDocument" }
        assertEquals(listOf(gone), log.map { it.entityId1 })
        assertEquals(LogEntryTypes.DELETE, log.single().type)
    }

    @Test fun healIsIdempotent() = runBlocking {
        addAiBook()
        assertEquals(1, healCanonicalIds(docs))
        val log = docs.dao.allLogEntries()
        val rows = read(docs, "SELECT lower(hex(id)) FROM MyDocument") { it.getText(0) }
        assertEquals(0, healCanonicalIds(docs))
        assertEquals(0, healCanonicalIds(docs, force = true))
        assertEquals(log, docs.dao.allLogEntries())
        assertEquals(rows, read(docs, "SELECT lower(hex(id)) FROM MyDocument") { it.getText(0) })
        assertEquals(1L, docs.dao.getLong("canonicalIdsVersion"))
    }

    @Test fun healLowersWatermarkOnlyWhenRowsMoved() = runBlocking {
        // Nothing to move: an empty database.
        docs.dao.setConfig(LAST_PATCH_WRITTEN_KEY, 12345L)
        assertEquals(0, healCanonicalIds(docs))
        assertEquals(12345L, docs.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        assertEquals(true, docs.dao.getBoolean("canonicalMarkerPending"))
        assertNull(docs.dao.getBoolean("canonicalHealMovedRows"))
        assertEquals(false, docs.dao.getBoolean("triggersDisabled"))

        addAiBook()
        assertEquals(1, healCanonicalIds(docs, force = true))
        assertEquals(0L, docs.dao.getLong(LAST_PATCH_WRITTEN_KEY))
        assertEquals(true, docs.dao.getBoolean("canonicalHealMovedRows"))
        assertEquals(false, docs.dao.getBoolean("triggersDisabled"))
    }

    @Test fun healOfModelMovesChildren() = runBlocking {
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        db.aiSettingsDb.llmProviderConfigDao().insert(provider)
        val model = LlmConfiguredModel(providerConfigId = provider.id, modelId = "my-model")
        db.aiSettingsDb.llmConfiguredModelDao().insert(model)
        val prompt = AgentPrompt(name = "p").also { it.configuredModelId = model.id }
        db.aiSettingsDb.agentPromptDao().insert(prompt)
        val overrideId = IdType()
        val overrides = BuiltinPromptOverride(overrideId, model.id)
        db.aiSettingsDb.let { it.builtinPromptOverrideDao().upsert(overrides) }
        db.aiSettingsDb.globalAiSettingsDao().set(GlobalAiSettings(defaultModelId = model.id))
        val usage = LlmUsageRecord(configuredModelId = model.id, deviceId = "dev", inputTokens = 7)
        db.aiSettingsDb.llmUsageRecordDao().upsert(usage)

        assertEquals(1, healCanonicalIds(ai))

        val canonical = canonicalLlmConfiguredModelId(provider.id, "my-model")
        assertEquals(canonical, db.aiSettingsDb.llmConfiguredModelDao().all().single().id)
        assertEquals(canonical, db.aiSettingsDb.agentPromptDao().promptById(prompt.id)!!.configuredModelId)
        assertEquals(canonical, db.aiSettingsDb.builtinPromptOverrideDao().getById(overrideId)!!.configuredModelId)
        assertEquals(canonical, db.aiSettingsDb.globalAiSettingsDao().get()!!.defaultModelId)
        val moved = db.aiSettingsDb.llmUsageRecordDao().getByModel(canonical).single()
        assertEquals(usage.id, moved.id)
        assertEquals(7L, moved.inputTokens)
        // The model is now found under the canonical id in the log; the usage record is keyed by its own id.
        assertEquals(listOf(canonical), logOf(ai, "LlmConfiguredModel").map { it.entityId1 })
    }

    @Test fun healMovesPlansStatusesAndVerses() = runBlocking {
        val rp = db.readingPlanDb.readingPlanDao()
        rp.updatePlan(ReadingPlan("y1ot", DbDate(1000L)))
        rp.addPlanStatus(ReadingPlanStatus("y1ot", 3, "1"))
        db.progressDb.progressDao().insertMemorizedVerse(MemorizedVerse(kjvOrdinal = 42))

        assertEquals(2, healCanonicalIds(plans))
        assertEquals(1, healCanonicalIds(progress))

        assertEquals(canonicalReadingPlanId("y1ot"), rp.getPlan("y1ot")!!.id)
        assertEquals(canonicalReadingPlanStatusId("y1ot", 3), rp.getStatus("y1ot", 3)!!.id)
        assertEquals(canonicalMemorizedVerseId(42), db.progressDb.progressDao().allMemorizedVerses().single().id)
        assertEquals("1", rp.getStatus("y1ot", 3)!!.readingStatus)
    }

    @Test fun healOfRowsMadeByTheNewCodePathIsANoOp() = runBlocking {
        val repo = ReadingPlanRepository(daoProvider = { db.readingPlanDb.readingPlanDao() }, appScope = AppCoroutineScope(), calendar = AndroidLocalCalendar())
        repo.startPlan("y1ot"); repo.setReadingStatus("y1ot", 2, "1").join()
        MyDocumentBookManager.getOrCreateAIDocument()
        ProgressControl.markVerseMemorized(VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 1), Verse(KJVA, BibleBook.GEN, 1, 2)))
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        db.aiSettingsDb.llmProviderConfigDao().insert(provider)
        net.bible.android.view.activity.ai.LlmModelServiceImpl().saveModel(null, provider.id.toString(), "m1", null, null, false)

        for (accessor in listOf(plans, docs, progress, ai)) {
            assertEquals(accessor.categoryName, 0, healCanonicalIds(accessor))
        }
    }

    @Test fun healFailureRollsBack() = runBlocking {
        val doc = addAiBook()
        val logBefore = docs.dao.allLogEntries()
        canonicalHealFailureInjector = { error("boom") }
        try {
            healCanonicalIds(docs)
            fail("expected the injected failure")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        canonicalHealFailureInjector = null
        assertEquals(doc.id, db.myDocumentDb.myDocumentDao().allDocuments().single().id)
        assertNull(docs.dao.getLong("canonicalIdsVersion"))
        assertFalse(docs.dao.getBoolean("triggersDisabled") == true)
        assertEquals(logBefore, docs.dao.allLogEntries())
        // The writer and foreign_keys are usable afterwards, and a retry succeeds.
        assertEquals(1, healCanonicalIds(docs))
    }

    @Test fun rekeyToCanonicalToleratesMissingTables() = runBlocking {
        val file = java.io.File.createTempFile("empty", ".sqlite3")
        try {
            openSqlite(file.path).use { c ->
                c.prepare("CREATE TABLE ReadingPlan (id BLOB PRIMARY KEY, planCode TEXT)").use { it.step() }
                assertEquals(0, rekeyToCanonical(c, SyncableDatabaseDefinition.READINGPLANS))
                assertTrue(c.queryRows("SELECT 1 FROM ReadingPlan") { 1 }.isEmpty())
            }
        } finally { file.delete() }
    }
}
