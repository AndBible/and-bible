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

package net.bible.android.view.activity.ai

import net.bible.service.llm.LlmProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QuickSetupModelsTest {
    @Test
    fun quickSetupRecommendsTheNewestEfficientModels() {
        assertEquals(
            listOf(
                LlmProvider.GEMINI to "gemini-3.8-flash",
                LlmProvider.ANTHROPIC to "claude-haiku-5-5",
                LlmProvider.OPENAI to "gpt-6-luna",
            ),
            LlmProviderServiceImpl.QUICK_SETUP_MODELS,
        )
    }

    @Test
    fun everyQuickSetupModelHasPricingAndIsSupported() {
        for ((provider, modelId) in LlmProviderServiceImpl.QUICK_SETUP_MODELS) {
            assertTrue(modelId in provider.models, "$modelId missing from ${provider.name}'s model list")
            assertNotNull(LlmProvider.findPricing(modelId), "no pricing for $modelId")
            assertTrue(LlmProvider.isModelSupported(modelId), "$modelId should be supported")
        }
    }

    @Test
    fun previousRecommendationsKeepTheirPricingForExistingUsers() {
        for (model in listOf("gemini-3-flash-preview", "claude-haiku-4-5", "gpt-5.4-mini")) {
            assertTrue(LlmProvider.isModelSupported(model), "$model lost its pricing/support")
        }
    }

    @Test
    fun newModelPricesMatchTheProviderPriceLists() {
        val luna = assertNotNull(LlmProvider.findPricing("gpt-6-luna"))
        assertEquals(listOf(0.10, 0.50, 0.125, 0.01),
            listOf(luna.inputPerMillion, luna.outputPerMillion, luna.cacheCreationPerMillion, luna.cacheReadPerMillion))
        val haiku = assertNotNull(LlmProvider.findPricing("claude-haiku-5-5"))
        assertEquals(listOf(0.10, 0.50), listOf(haiku.inputPerMillion, haiku.outputPerMillion))
        val flash = assertNotNull(LlmProvider.findPricing("gemini-3.8-flash"))
        assertEquals(listOf(1.50, 7.50, 0.15),
            listOf(flash.inputPerMillion, flash.outputPerMillion, flash.cacheReadPerMillion))
    }
}
