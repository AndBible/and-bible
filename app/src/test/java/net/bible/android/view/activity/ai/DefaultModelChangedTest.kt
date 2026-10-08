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

import kotlinx.coroutines.runBlocking
import android.os.Looper
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.service.common.AiSettings
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmConfiguredModel
import net.bible.service.llm.LlmProviderConfig
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.ai.LlmModelService
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [AiSettings.defaultModelChanged] is emitted per write and refreshes the AI services. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DefaultModelChangedTest {
    @After fun tearDown() { DatabaseResetter.resetDatabase() }

    @Test fun theSetterEmitsOncePerWrite() {
        var emitted = 0
        val subscription = AiSettings.defaultModelChanged.subscribe { emitted++ }
        try {
            AiSettings.defaultModelId = IdType()
            AiSettings.defaultModelId = null
        } finally { subscription.cancel() }
        assertEquals(2, emitted)
    }

    @Test fun aDefaultModelWriteRefreshesTheServiceSnapshots() {
        // Fresh instances, not the Koin singletons: another test class may have created those, and
        // TestBibleApplication.onTerminate resets AiSettings' sources, which kills the process-lifetime
        // subscriptions of an already-built singleton.
        val settingsService: AiSettingsService = AiSettingsServiceImpl()
        val modelService: LlmModelService = LlmModelServiceImpl()
        val db = DatabaseContainer.instance.aiSettingsDb
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "P")
        runBlocking { db.llmProviderConfigDao().insert(provider) }
        val first = LlmConfiguredModel(providerConfigId = provider.id, modelId = "model-a", orderNumber = 0)
        val second = LlmConfiguredModel(providerConfigId = provider.id, modelId = "model-b", orderNumber = 1)
        runBlocking { db.llmConfiguredModelDao().insert(first) }
        runBlocking { db.llmConfiguredModelDao().insert(second) }

        AiSettings.defaultModelId = second.id
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("model-b", settingsService.snapshot.value.defaultModelLabel)
        assertEquals(listOf("model-b"), modelService.models.value.filter { it.isDefault }.map { it.modelId })
    }
}
