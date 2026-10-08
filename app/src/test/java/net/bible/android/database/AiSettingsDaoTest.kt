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
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.llm.AgentPrompt
import net.bible.service.llm.BuiltinPromptOverride
import net.bible.service.llm.GlobalAiSettings
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.LlmUsageRecord
import net.bible.service.llm.PromptCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Behaviour of the AI settings DAOs (providers, models, usage, prompts, global settings). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AiSettingsDaoTest {
    private lateinit var db: AiSettingsDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, AiSettingsDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    @Test fun providerAndConfiguredModelRoundTrip() = runBlocking {
        val providers = db.llmProviderConfigDao()
        val models = db.llmConfiguredModelDao()
        val p2 = LlmProviderConfig(providerType = "OPENAI", displayName = "B", orderNumber = 2)
        val p1 = LlmProviderConfig(providerType = "ANTHROPIC", displayName = "A", orderNumber = 1)
        providers.insert(p2)
        providers.insert(p1)
        assertEquals("ordered by orderNumber", listOf("A", "B"), providers.all().map { it.displayName })
        assertEquals(2, providers.getCount())
        assertEquals(p1, providers.getById(p1.id))

        val m = LlmConfiguredModel(providerConfigId = p1.id, modelId = "m-1")
        models.insert(m)
        models.insert(LlmConfiguredModel(providerConfigId = p2.id, modelId = "m-2"))
        assertEquals(listOf(m), models.getByProvider(p1.id))
        models.update(m.copy(inputPricePerMillion = 3.0))
        assertEquals(3.0, models.getById(m.id)!!.inputPricePerMillion, 0.0)

        models.deleteByProvider(p2.id)
        assertEquals(listOf("m-1"), models.all().map { it.modelId })
    }

    @Test fun deletingProviderCascadesToItsModels() = runBlocking {
        val p = LlmProviderConfig(providerType = "OPENAI", displayName = "P")
        db.llmProviderConfigDao().insert(p)
        db.llmConfiguredModelDao().insert(LlmConfiguredModel(providerConfigId = p.id, modelId = "x"))
        db.llmProviderConfigDao().delete(p)
        assertTrue(db.llmConfiguredModelDao().all().isEmpty())
    }

    @Test fun usageRecordUpsertReplacesPerDeviceAndAggregates() = runBlocking {
        val p = LlmProviderConfig(providerType = "OPENAI", displayName = "P")
        val m = LlmConfiguredModel(providerConfigId = p.id, modelId = "x")
        val dao = db.llmUsageRecordDao()
        dao.upsert(LlmUsageRecord(configuredModelId = m.id, deviceId = "d1", inputTokens = 10, estimatedCostUsd = 0.5))
        dao.upsert(LlmUsageRecord(configuredModelId = m.id, deviceId = "d2", inputTokens = 5, estimatedCostUsd = 0.25))
        // Same (model, device) again replaces the row (unique index + REPLACE), it does not add a second one.
        dao.upsert(LlmUsageRecord(configuredModelId = m.id, deviceId = "d1", inputTokens = 20, estimatedCostUsd = 1.0))
        val rows = dao.getByModel(m.id)
        assertEquals(2, rows.size)
        assertEquals(25L, rows.sumOf { it.inputTokens })
        assertEquals(1.25, rows.sumOf { it.estimatedCostUsd }, 1e-9)
        assertEquals(20L, dao.get(m.id, "d1")!!.inputTokens)
        assertNull(dao.get(m.id, "nobody"))
        dao.deleteByModel(m.id)
        assertTrue(dao.all().isEmpty())
    }

    @Test fun globalAiSettingsSetReplacesSingleton() = runBlocking {
        val dao = db.globalAiSettingsDao()
        assertNull(dao.get())
        dao.set(GlobalAiSettings(maxIterations = 7))
        dao.set(GlobalAiSettings(maxIterations = 9))
        assertEquals(9, dao.get()!!.maxIterations)
    }

    @Test fun promptsCategoriesAndOverrides() = runBlocking {
        val cat = PromptCategory(name = "c")
        db.promptCategoryDao().insert(cat)
        val a = AgentPrompt(name = "a", orderNumber = 1, categoryId = cat.id)
        val b = AgentPrompt(name = "b", orderNumber = 2)
        db.agentPromptDao().insertAll(listOf(a, b))
        assertEquals(listOf("a", "b"), db.agentPromptDao().allPrompts().map { it.name })
        db.agentPromptDao().shiftOrderNumbersAfter(1)
        assertEquals(3, db.agentPromptDao().promptById(b.id)!!.orderNumber)
        assertEquals(1, db.agentPromptDao().promptById(a.id)!!.orderNumber)
        db.promptCategoryDao().clearCategoryFromPrompts(cat.id)
        assertNull(db.agentPromptDao().promptById(a.id)!!.categoryId)

        val ov = BuiltinPromptOverride(id = IdType())
        db.builtinPromptOverrideDao().upsert(ov)
        db.builtinPromptOverrideDao().upsert(ov)
        assertEquals(1, db.builtinPromptOverrideDao().all().size)
        db.builtinPromptOverrideDao().deleteById(ov.id)
        assertNull(db.builtinPromptOverrideDao().getById(ov.id))
    }
}
