package net.bible.service.llm

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidLlmStrings
import net.bible.android.BibleApplication
import net.bible.android.view.activity.ai.AgentSessionServiceImpl
import net.bible.android.view.activity.ai.ReadingLlmServiceImpl
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LlmPortableGlobalsTest {
    @After fun cleanup() { DatabaseResetter.resetDatabase() }

    @Test fun portablePreferencesUseInjectedStoreNotAndroidGlobal() {
        val original = GlobalContext.get().get<AppSettings>()
        val values = mutableMapOf<String, Boolean>()
        val local = object : AppSettings by original {
            override fun getBoolean(key: String, default: Boolean) = values[key] ?: default
            override fun setBoolean(key: String, value: Boolean?) { if (value == null) values.remove(key) else values[key] = value }
        }
        loadKoinModules(module { single<AppSettings> { local } })
        try {
            val reading = ReadingLlmServiceImpl(AndroidLlmStrings(BibleApplication.application))
            val sessions = AgentSessionServiceImpl()
            reading.setCategoryCollapsed("NOTE_EDITOR", null, true)
            sessions.setLogVisiblePref(true)
            assertEquals(true, values["llm_cat_collapsed_NOTE_EDITOR_uncategorized"])
            assertEquals(true, values["agent_log_widget_visible"])
            assertTrue(reading.isCategoryCollapsed("NOTE_EDITOR", null))
            assertTrue(sessions.logVisiblePref())
        } finally { loadKoinModules(module { single<AppSettings> { original } }) }
    }

    @Test fun usageIsAttributedToInjectedDevice() = runBlocking {
        val original = GlobalContext.get().get<AppEnvironment>()
        val local = object : AppEnvironment by original {
            override val deviceIdentifier = "task6-fixture-device"
        }
        loadKoinModules(module { single<AppEnvironment> { local } })
        try {
            val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "Fixture")
            val model = LlmConfiguredModel.create(provider.id, "fixture-model")
            val db = net.bible.service.db.DatabaseContainer.instance.aiSettingsDb
            db.llmProviderConfigDao().insert(provider)
            db.llmConfiguredModelDao().insert(model)
            LlmCostTracker.addUsage(LlmUsage(inputTokens = 7), model.modelId, model.id)
            assertEquals(7L, db.llmUsageRecordDao().get(model.id, "task6-fixture-device")?.inputTokens)
        } finally { loadKoinModules(module { single<AppEnvironment> { original } }) }
    }

    @Test fun injectedCacheDirectoryAndDebugModeAreObserved() {
        val original = GlobalContext.get().get<AppEnvironment>()
        val directory = File(original.cacheDir, "task6-${UUID.randomUUID()}").apply { mkdirs() }
        val provider = "task6-${UUID.randomUUID()}"
        var debug = false
        val local = object : AppEnvironment by original {
            override val cacheDir get() = directory.absolutePath
            override val isDebugMode get() = debug
        }
        val cacheFile = File(directory, "models_cache_$provider.json")
        cacheFile.writeText("{\"data\":[{\"id\":\"fixture-model\"}]}")
        loadKoinModules(module { single<AppEnvironment> { local } })
        try {
            assertEquals(cacheFile.lastModified(), DynamicModelService.cacheTimestamp(provider))
            val testIds = BuiltInPrompts.testPrompts().map { it.id }.toSet()
            assertTrue(testIds.isNotEmpty())
            assertTrue(PromptRepository.allPromptsIncludingHidden().none { it.id in testIds })
            debug = true
            assertTrue(PromptRepository.allPromptsIncludingHidden().any { it.id in testIds })
        } finally {
            loadKoinModules(module { single<AppEnvironment> { original } })
            directory.deleteRecursively()
        }
    }
}
