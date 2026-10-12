package net.bible.service.llm

import android.os.Looper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.BibleApplication
import net.bible.android.view.activity.ai.*
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.ai.*
import net.bible.sharedcore.ai.reading.*
import net.bible.test.DatabaseResetter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AiSettingsFixtureLifetimeTest {
    @Test fun successiveApplicationLifetimesUseFreshSettingsAndEventsWithConcreteAliases() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val firstApp = BibleApplication.application as TestBibleApplication
        val koin = GlobalContext.get()
        val firstRaw = koin.get<RawLogService>()
        runBlocking {
            val record = LlmRawLogRecord(logData = byteArrayOf())
            val dao = DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao()
            dao.insert(record)
            firstRaw.deleteByIds(setOf(record.id.toString()))
            assertNull(dao.getById(record.id))
        }
        val firstScope = koin.get<net.bible.sharedcore.platform.AppCoroutineScope>()
        val firstLauncher = koin.get<net.bible.sharedcore.platform.OrderedLauncher>()
        val firstAgent = koin.get<AgentSessionService>()
        val firstReading = koin.get<ReadingLlmService>()
        val firstFilter = koin.get<DocumentFilterService>()
        val firstTools = koin.get<ToolPermissionService>()
        firstAgent.autoHideEnabled()
        firstReading.askModelBeforeRun()
        firstFilter.setExcluded(setOf("first"))
        firstTools.permissionFor(AgentTool.entries.first().name)
        val old = CommonUtils.aiSettings
        firstApp.onTerminate()
        val secondApp = TestBibleApplication()
        // Attach the same real Android context; onCreate exercises actual fixture binding replacement.
        val attach = android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
        attach.isAccessible = true
        attach.invoke(secondApp, firstApp.baseContext)
        secondApp.onCreate()
        try {
            val raw = koin.get<RawLogService>()
            assertNotSame(firstRaw, raw)
            assertSame(raw, koin.get<RawLogServiceImpl>())
            runBlocking {
                val record = LlmRawLogRecord(logData = byteArrayOf())
                val dao = DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao()
                dao.insert(record)
                raw.deleteByIds(setOf(record.id.toString()))
                assertNull(dao.getById(record.id))
            }
            assertNotSame(firstScope, koin.get<net.bible.sharedcore.platform.AppCoroutineScope>())
            assertNotSame(firstLauncher, koin.get<net.bible.sharedcore.platform.OrderedLauncher>())
            val agent = koin.get<AgentSessionService>()
            val reading = koin.get<ReadingLlmService>()
            val filter = koin.get<DocumentFilterService>()
            val tools = koin.get<ToolPermissionService>()
            assertNotSame(firstAgent, agent)
            assertNotSame(firstReading, reading)
            assertNotSame(firstFilter, filter)
            assertNotSame(firstTools, tools)
            assertSame(agent, koin.get<AgentSessionServiceImpl>())
            assertSame(reading, koin.get<ReadingLlmServiceImpl>())
            assertSame(filter, koin.get<DocumentFilterServiceImpl>())
            assertSame(tools, koin.get<ToolPermissionServiceImpl>())
            assertSame(koin.get<AiSettingsService>(), koin.get<AiSettingsServiceImpl>())
            assertSame(koin.get<LlmModelService>(), koin.get<LlmModelServiceImpl>())
            assertSame(koin.get<LlmProviderService>(), koin.get<LlmProviderServiceImpl>())
            assertSame(koin.get<PromptService>(), koin.get<PromptServiceImpl>())
            val settings = CommonUtils.aiSettings
            assertNotSame(old, settings)
            settings.autoHideAgentLogOnCompletion = true
            settings.askModelBeforeRun = true
            assertTrue(agent.autoHideEnabled())
            assertTrue(reading.askModelBeforeRun())
            val tool = AgentTool.entries.first()
            tools.save(mapOf(tool.name to ToolPermission.DENY))
            assertEquals(setOf(tool), settings.permanentlyDeniedTools)
            assertTrue(tools.permissionFor(tool.name) in setOf(ToolPermission.DENY, ToolPermission.DISABLED))
            filter.setExcluded(setOf("second"))
            assertEquals(setOf("second"), settings.aiExcludedDocuments)
            val db = DatabaseContainer.instance.aiSettingsDb
            val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "Fixture")
            val model = LlmConfiguredModel(providerConfigId = provider.id, modelId = "second-model")
            runBlocking { db.llmProviderConfigDao().insert(provider); db.llmConfiguredModelDao().insert(model) }
            settings.defaultModelId = model.id
            shadowOf(Looper.getMainLooper()).idle()
            runBlocking { kotlinx.coroutines.withTimeout(5000) { agent.snapshot.first { it.defaultModelText == "second-model" } } }
            assertEquals("second-model", agent.snapshot.value.defaultModelText)
        } finally {
            secondApp.onTerminate()
            DatabaseResetter.resetDatabase()
            Dispatchers.resetMain()
        }
    }
}
