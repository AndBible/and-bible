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
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.progress.ProgressControl
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.platform.AndroidLocalCalendar
import net.bible.android.view.activity.ai.LlmModelServiceImpl
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.readingplan.ReadingPlanRepository
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.create
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rows created by the app in the five synced natural-key tables carry the canonical id derived from their
 * natural key (#3906), so two devices that independently create the same row already agree on its id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35]) // 35: ReadingPlanDao upserts need the newer SQLite
class CanonicalNewRowsTest {
    private val db get() = DatabaseContainer.instance
    private val planDao get() = db.readingPlanDb.readingPlanDao()
    private val appScope = AppCoroutineScope()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var previousDispatcher: kotlinx.coroutines.CoroutineDispatcher
    private lateinit var previousLauncher: OrderedLauncher

    @Before fun setUp() {
        previousDispatcher = MyDocumentBookManager.mainDispatcher
        previousLauncher = MyDocumentBookManager.launcher
        MyDocumentBookManager.mainDispatcher = Dispatchers.Unconfined
        MyDocumentBookManager.launcher = OrderedLauncher(syncScope)
    }

    @After fun tearDown() {
        MyDocumentBookManager.unregisterDocument("AIDocuments") // getOrCreateAIDocument registers it in the JVM-wide registry
        MyDocumentBookManager.mainDispatcher = previousDispatcher
        MyDocumentBookManager.launcher = previousLauncher
        syncScope.cancel()
        DatabaseResetter.resetDatabase()
    }

    private fun repo() = ReadingPlanRepository(daoProvider = { planDao }, appScope = appScope, calendar = AndroidLocalCalendar())

    @Test fun startingAPlanUsesTheCanonicalPlanId() = runBlocking {
        val repo = repo()
        repo.startPlan("y1ot")
        assertEquals(canonicalReadingPlanId("y1ot"), planDao.getPlan("y1ot")!!.id)

        repo.setReadingStatus("y1ot", 2, "1").join()
        assertEquals(canonicalReadingPlanStatusId("y1ot", 2), planDao.getStatus("y1ot", 2)!!.id)
    }

    @Test fun settingTheCurrentDayOfAnUnstartedPlanUsesTheCanonicalPlanId() = runBlocking {
        repo().setCurrentDay("y1ot", 5).join()
        assertEquals(canonicalReadingPlanId("y1ot"), planDao.getPlan("y1ot")!!.id)
    }

    @Test fun resetAndRestartReusesTheId() = runBlocking {
        val repo = repo()
        repo.startPlan("y1ot")
        val first = planDao.getPlan("y1ot")!!.id
        repo.resetPlan("y1ot").join()
        repo.startPlan("y1ot")
        assertEquals(first, planDao.getPlan("y1ot")!!.id)
        assertEquals(canonicalReadingPlanId("y1ot"), first)
    }

    @Test fun aiDocumentsBookHasTheCanonicalId() = runBlocking {
        val doc = MyDocumentBookManager.getOrCreateAIDocument()
        assertEquals(canonicalMyDocumentId("AIDocuments"), doc.id)
    }

    @Test fun memorizingAVerseUsesTheCanonicalId() = runBlocking {
        ProgressControl.markVerseMemorized(VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 1), Verse(KJVA, BibleBook.GEN, 1, 3)))
        val rows = db.progressDb.progressDao().allMemorizedVerses()
        assertEquals(3, rows.size)
        rows.forEach { assertEquals(canonicalMemorizedVerseId(it.kjvOrdinal), it.id) }
    }

    @Test fun configuringAModelUsesTheCanonicalId() = runBlocking {
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        db.aiSettingsDb.llmProviderConfigDao().insert(provider)
        LlmModelServiceImpl().saveModel(null, provider.id.toString(), "my-custom-model", "1", "2", false)
        val saved = db.aiSettingsDb.llmConfiguredModelDao().getByProvider(provider.id).single()
        assertEquals(canonicalLlmConfiguredModelId(provider.id, "my-custom-model"), saved.id)

        val created = LlmConfiguredModel.create(provider.id, "gemini-2.5-flash", 1)
        assertEquals(canonicalLlmConfiguredModelId(provider.id, "gemini-2.5-flash"), created.id)
    }

    @Test fun guard_everyCanonicalTableRowIsCanonical() = runBlocking {
        val repo = repo()
        repo.startPlan("y1ot"); repo.setReadingStatus("y1ot", 2, "1").join(); repo.setReadingStatus("y1ot", 3, "1").join()
        MyDocumentBookManager.getOrCreateAIDocument()
        ProgressControl.markVerseMemorized(VerseRange(KJVA, Verse(KJVA, BibleBook.GEN, 1, 1), Verse(KJVA, BibleBook.GEN, 1, 2)))
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        db.aiSettingsDb.llmProviderConfigDao().insert(provider)
        LlmModelServiceImpl().saveModel(null, provider.id.toString(), "m1", null, null, false)

        assertNotNull(planDao.getPlan("y1ot"))
        listOf(2, 3).forEach { day ->
            val s = planDao.getStatus("y1ot", day)!!
            assertEquals(canonicalId("ReadingPlanStatus", s.planCode, s.planDay.toString()), s.id)
        }
        assertEquals(canonicalId("ReadingPlan", "y1ot"), planDao.getPlan("y1ot")!!.id)
        db.myDocumentDb.myDocumentDao().allDocuments().also { assertEquals(1, it.size) }
            .forEach { assertEquals(canonicalId("MyDocument", it.initials), it.id) }
        db.progressDb.progressDao().allMemorizedVerses().also { assertEquals(2, it.size) }
            .forEach { assertEquals(canonicalId("MemorizedVerse", it.kjvOrdinal.toString()), it.id) }
        db.aiSettingsDb.llmConfiguredModelDao().all().also { assertEquals(1, it.size) }
            .forEach { assertEquals(canonicalId("LlmConfiguredModel", it.providerConfigId.toString(), it.modelId), it.id) }
    }
}
