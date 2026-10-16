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

import org.koin.core.component.KoinComponent
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer

/**
 * Transport object that travels through the LLM call chain.
 *
 * Built from an AgentPrompt's `configuredModelId`. When null, the global default
 * model from [GlobalAiSettings.defaultModelId] is used.
 */
data class LlmModelConfig(
    val configuredModelId: IdType? = null,
) : KoinComponent {
    private val aiSettings: AiSettings get() = getKoin().get()
    private val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
    private val providerDao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()

    /**
     * Resolve the configured model. Falls back to the global default.
     * Returns null if the model was deleted or no default is configured.
     */
    suspend fun resolveConfiguredModel(): LlmConfiguredModel? {
        if (configuredModelId != null) {
            modelDao.getById(configuredModelId)?.let { return it }
            // Model was deleted → fall back to global default
        }
        val defaultId = aiSettings.defaultModelId ?: return null
        return modelDao.getById(defaultId)
    }

    /** Resolve the provider config via the configured model. */
    suspend fun resolveProviderConfig(): LlmProviderConfig? {
        val model = resolveConfiguredModel() ?: return null
        return providerDao.getById(model.providerConfigId)
    }

    companion object {
        /** Build from an AgentPrompt's per-prompt model override. */
        fun fromPrompt(prompt: AgentPrompt): LlmModelConfig =
            LlmModelConfig(prompt.configuredModelId)
    }
}
