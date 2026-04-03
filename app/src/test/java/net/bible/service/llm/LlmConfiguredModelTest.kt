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

package net.bible.service.llm

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.service.common.AiSettings
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter.resetDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LlmConfiguredModelTest {

    private lateinit var providerDao: LlmProviderConfigDao
    private lateinit var modelDao: LlmConfiguredModelDao

    private lateinit var testProvider: LlmProviderConfig
    private lateinit var testModel: LlmConfiguredModel

    @Before
    fun setUp() {
        val db = DatabaseContainer.instance.aiSettingsDb
        providerDao = db.llmProviderConfigDao()
        modelDao = db.llmConfiguredModelDao()

        // Create a test provider and model
        testProvider = LlmProviderConfig(
            providerType = "GEMINI",
            displayName = "Test Gemini",
        )
        providerDao.insert(testProvider)

        testModel = LlmConfiguredModel(
            providerConfigId = testProvider.id,
            modelId = "gemini-2.5-flash",

            inputPricePerMillion = 0.15,
            outputPricePerMillion = 0.60,
        )
        modelDao.insert(testModel)
        AiSettings.defaultModelId = testModel.id
    }

    @After
    fun tearDown() {
        AiSettings.defaultModelId = null
        resetDatabase()
    }

    // --- LlmModelConfig resolution ---

    @Test
    fun resolveConfiguredModel_withExplicitId_returnsSpecificModel() {
        val config = LlmModelConfig(configuredModelId = testModel.id)
        val resolved = config.resolveConfiguredModel()
        assertNotNull(resolved)
        assertEquals(testModel.modelId, resolved!!.modelId)
    }

    @Test
    fun resolveConfiguredModel_withNull_returnsGlobalDefault() {
        val config = LlmModelConfig(configuredModelId = null)
        val resolved = config.resolveConfiguredModel()
        assertNotNull(resolved)
        assertEquals(testModel.modelId, resolved!!.modelId)
    }

    @Test
    fun resolveConfiguredModel_withDeletedModelId_fallsBackToDefault() {
        val deletedId = IdType()
        val config = LlmModelConfig(configuredModelId = deletedId)
        val resolved = config.resolveConfiguredModel()
        // Should fall back to global default since the specified model doesn't exist
        assertNotNull(resolved)
        assertEquals(testModel.modelId, resolved!!.modelId)
    }

    @Test
    fun resolveConfiguredModel_noDefaultSet_returnsNull() {
        AiSettings.defaultModelId = null
        val config = LlmModelConfig(configuredModelId = null)
        val resolved = config.resolveConfiguredModel()
        assertNull(resolved)
    }

    @Test
    fun resolveConfiguredModel_defaultPointsToDeletedModel_returnsNull() {
        AiSettings.defaultModelId = IdType() // Points to non-existent model
        val config = LlmModelConfig(configuredModelId = null)
        val resolved = config.resolveConfiguredModel()
        assertNull(resolved)
    }

    @Test
    fun resolveProviderConfig_returnsProviderViaModel() {
        val config = LlmModelConfig(configuredModelId = testModel.id)
        val provider = config.resolveProviderConfig()
        assertNotNull(provider)
        assertEquals(testProvider.id, provider!!.id)
        assertEquals("GEMINI", provider.providerType)
    }

    @Test
    fun resolveProviderConfig_noModel_returnsNull() {
        AiSettings.defaultModelId = null
        val config = LlmModelConfig(configuredModelId = null)
        val provider = config.resolveProviderConfig()
        assertNull(provider)
    }

    @Test
    fun fromPrompt_mapsConfiguredModelId() {
        val prompt = AgentPrompt(
            name = "Test",
            promptTemplate = "Test template",
            configuredModelId = testModel.id,
        )
        val config = LlmModelConfig.fromPrompt(prompt)
        assertEquals(testModel.id, config.configuredModelId)
    }

    @Test
    fun fromPrompt_nullConfiguredModelId() {
        val prompt = AgentPrompt(
            name = "Test",
            promptTemplate = "Test template",
            configuredModelId = null,
        )
        val config = LlmModelConfig.fromPrompt(prompt)
        assertNull(config.configuredModelId)
    }

    // --- Multiple models, choosing between them ---

    @Test
    fun resolveConfiguredModel_choosesCorrectModel_fromMultiple() {
        val secondModel = LlmConfiguredModel(
            providerConfigId = testProvider.id,
            modelId = "gemini-2.5-pro",

            inputPricePerMillion = 1.25,
            outputPricePerMillion = 10.0,
        )
        modelDao.insert(secondModel)

        val config = LlmModelConfig(configuredModelId = secondModel.id)
        val resolved = config.resolveConfiguredModel()
        assertNotNull(resolved)
        assertEquals("gemini-2.5-pro", resolved!!.modelId)
    }

    // --- Pricing resolution ---

    @Test
    fun pricing_configuredModelPricing_takePriorityOverEnum() {
        // Create model with custom pricing different from enum
        val customPricedModel = LlmConfiguredModel(
            providerConfigId = testProvider.id,
            modelId = "custom-gemini-flash", // different from setUp's model

            inputPricePerMillion = 99.0,
            outputPricePerMillion = 199.0,
        )
        modelDao.insert(customPricedModel)

        val pricing = LlmPricing.getPricing("custom-gemini-flash", customPricedModel.id)
        assertNotNull(pricing)
        assertEquals(99.0, pricing!!.inputPerMillion, 0.001)
        assertEquals(199.0, pricing.outputPerMillion, 0.001)
    }

    @Test
    fun pricing_zeroPricedConfiguredModel_fallsBackToEnum() {
        val zeroPricedModel = LlmConfiguredModel(
            providerConfigId = testProvider.id,
            modelId = "zero-priced-flash",

            inputPricePerMillion = 0.0,
            outputPricePerMillion = 0.0,
        )
        modelDao.insert(zeroPricedModel)

        // getPricing with configuredModelId that has 0 pricing should fall back to enum
        val pricing = LlmPricing.getPricing("gemini-2.5-flash", zeroPricedModel.id)
        assertNotNull(pricing)
        // Should fall back to enum pricing (0.15/0.60)
        assertEquals(0.15, pricing!!.inputPerMillion, 0.001)
        assertEquals(0.60, pricing.outputPerMillion, 0.001)
    }

    @Test
    fun pricing_withoutConfiguredModelId_usesEnumPricing() {
        val pricing = LlmPricing.getPricing("gemini-2.5-flash", null)
        assertNotNull(pricing)
        assertEquals(0.15, pricing!!.inputPerMillion, 0.001)
    }

    @Test
    fun pricing_unknownModelWithoutConfiguredId_returnsNull() {
        val pricing = LlmPricing.getPricing("totally-unknown-model-xyz", null)
        assertNull(pricing)
    }

    // --- Cost tracker (per-model) ---

    @Test
    fun costTracker_addUsage_andRetrieve() = runBlocking {
        val usage = LlmUsage(inputTokens = 1000, outputTokens = 500)
        LlmCostTracker.addUsage(usage, "gemini-2.5-flash", testModel.id)

        val cumulative = LlmCostTracker.getCumulativeUsage(testModel.id)
        assertEquals(1000L, cumulative.inputTokens)
        assertEquals(500L, cumulative.outputTokens)
    }

    @Test
    fun costTracker_addUsage_accumulates() = runBlocking {
        val usage1 = LlmUsage(inputTokens = 1000, outputTokens = 500)
        val usage2 = LlmUsage(inputTokens = 2000, outputTokens = 1000)
        LlmCostTracker.addUsage(usage1, "gemini-2.5-flash", testModel.id)
        LlmCostTracker.addUsage(usage2, "gemini-2.5-flash", testModel.id)

        val cumulative = LlmCostTracker.getCumulativeUsage(testModel.id)
        assertEquals(3000L, cumulative.inputTokens)
        assertEquals(1500L, cumulative.outputTokens)
    }

    @Test
    fun costTracker_getCumulativeCost_calculatesCorrectly() = runBlocking {
        // 1M tokens each: cost = 0.15 + 0.60 = 0.75
        val usage = LlmUsage(inputTokens = 1_000_000, outputTokens = 1_000_000)
        LlmCostTracker.addUsage(usage, "gemini-2.5-flash", testModel.id)

        val cost = LlmCostTracker.getCumulativeCost(testModel.id)
        assertEquals(0.75, cost, 0.01)
    }

    @Test
    fun costTracker_reset_clearsUsage() = runBlocking {
        val usage = LlmUsage(inputTokens = 1000, outputTokens = 500)
        LlmCostTracker.addUsage(usage, "gemini-2.5-flash", testModel.id)

        LlmCostTracker.reset(testModel.id)

        val cumulative = LlmCostTracker.getCumulativeUsage(testModel.id)
        assertEquals(0L, cumulative.inputTokens)
        assertEquals(0L, cumulative.outputTokens)
    }

    @Test
    fun costTracker_separateModels_trackedIndependently() = runBlocking {
        val secondModel = LlmConfiguredModel(
            providerConfigId = testProvider.id,
            modelId = "gemini-2.5-pro",

        )
        modelDao.insert(secondModel)

        LlmCostTracker.addUsage(LlmUsage(inputTokens = 1000), "gemini-2.5-flash", testModel.id)
        LlmCostTracker.addUsage(LlmUsage(inputTokens = 5000), "gemini-2.5-pro", secondModel.id)

        assertEquals(1000L, LlmCostTracker.getCumulativeUsage(testModel.id).inputTokens)
        assertEquals(5000L, LlmCostTracker.getCumulativeUsage(secondModel.id).inputTokens)
    }

    // --- DAO tests ---

    @Test
    fun modelDao_getByProvider_returnsOnlyThatProviderModels() {
        val secondProvider = LlmProviderConfig(
            providerType = "OPENAI",
            displayName = "Test OpenAI",
        )
        providerDao.insert(secondProvider)

        val openaiModel = LlmConfiguredModel(
            providerConfigId = secondProvider.id,
            modelId = "gpt-4o-mini",

        )
        modelDao.insert(openaiModel)

        val geminiModels = modelDao.getByProvider(testProvider.id)
        assertEquals(1, geminiModels.size)
        assertEquals("gemini-2.5-flash", geminiModels[0].modelId)

        val openaiModels = modelDao.getByProvider(secondProvider.id)
        assertEquals(1, openaiModels.size)
        assertEquals("gpt-4o-mini", openaiModels[0].modelId)
    }

    @Test
    fun modelDao_cascadeDelete_removesModelsWhenProviderDeleted() {
        assertEquals(1, modelDao.all().size)
        providerDao.delete(testProvider)
        assertEquals(0, modelDao.all().size)
    }

    @Test
    fun modelDao_all_returnsAllModelsAcrossProviders() {
        val secondProvider = LlmProviderConfig(
            providerType = "OPENAI",
            displayName = "Test OpenAI",
        )
        providerDao.insert(secondProvider)
        modelDao.insert(LlmConfiguredModel(
            providerConfigId = secondProvider.id,
            modelId = "gpt-4o-mini",

        ))

        val all = modelDao.all()
        assertEquals(2, all.size)
    }
}
