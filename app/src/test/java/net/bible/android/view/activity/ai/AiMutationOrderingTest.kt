package net.bible.android.view.activity.ai

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.BibleApplication
import net.bible.android.database.IdType
import net.bible.android.platform.AndroidLlmStrings
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.*
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pins the persisted state visible immediately after each public mutation returns. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AiMutationOrderingTest {
    private val strings get() = AndroidLlmStrings(BibleApplication.application)
    @After fun tearDown() { DatabaseResetter.resetDatabase() }

    @Test fun deletingPromptCompletesBeforeListing() = runBlocking {
        val prompt = AgentPrompt(name = "User prompt")
        PromptRepository.insertPrompt(prompt)
        val service = PromptServiceImpl(strings)
        service.deletePrompt(prompt.id.toString())
        assertNull(PromptRepository.promptById(prompt.id))
        assertFalse(PromptRepository.allPromptsIncludingHidden().any { it.id == prompt.id })
    }

    @Test fun deletingProviderCascadesAndSelectsRemainingDefaultBeforeReturn() = runBlocking {
        val db = DatabaseContainer.instance.aiSettingsDb
        val removed = LlmProviderConfig(providerType = "CUSTOM", displayName = "Removed")
        val kept = LlmProviderConfig(providerType = "CUSTOM", displayName = "Kept", orderNumber = 1)
        db.llmProviderConfigDao().insert(removed)
        db.llmProviderConfigDao().insert(kept)
        val first = LlmConfiguredModel(providerConfigId = removed.id, modelId = "removed")
        val remaining = LlmConfiguredModel(providerConfigId = kept.id, modelId = "kept")
        db.llmConfiguredModelDao().insert(first)
        db.llmConfiguredModelDao().insert(remaining)
        CommonUtils.aiSettings.defaultModelId = first.id
        LlmProviderServiceImpl(strings).deleteProvider(removed.id.toString())
        assertNull(db.llmProviderConfigDao().getById(removed.id))
        assertTrue(db.llmConfiguredModelDao().getByProvider(removed.id).isEmpty())
        assertEquals(remaining.id, CommonUtils.aiSettings.defaultModelId)
        assertEquals(remaining.id, db.globalAiSettingsDao().get()?.defaultModelId)
    }

    @Test fun retentionDeletesOldRecordButKeepsFreshRecordBeforeReturn() = runBlocking {
        val dao = DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao()
        val old = LlmRawLogRecord(timestamp = System.currentTimeMillis() - 2 * 86400000L, logData = byteArrayOf())
        val fresh = LlmRawLogRecord(logData = byteArrayOf())
        dao.insert(old)
        dao.insert(fresh)
        CommonUtils.aiSettings.rawLogRetentionDays = 1
        val service = RawLogServiceImpl(strings)
        service.deleteOlderThan(CommonUtils.aiSettings.rawLogRetentionDays!!)
        assertNull(dao.getById(old.id))
        assertNotNull(dao.getById(fresh.id))
        assertEquals(listOf(fresh.id.toString()), service.summaries.value.map { it.id })
    }

    @Test fun providerDeletionSurvivesCancelledWaiterAndStillRefreshes() = runBlocking {
        val koin = org.koin.core.context.GlobalContext.get()
        val original = koin.get<SecretStore>()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val released = java.util.concurrent.CountDownLatch(1)
        val completed = kotlinx.coroutines.CompletableDeferred<Unit>()
        val store = object : SecretStore by original {
            override fun remove(key: String) {
                entered.complete(Unit)
                check(released.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<SecretStore> { store } })
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "Cancelled waiter")
        val dao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
        dao.insert(provider)
        val service = LlmProviderServiceImpl(strings)
        val subscription = CommonUtils.aiSettings.configChanged.subscribe { completed.complete(Unit) }
        try {
            val waiter = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                service.deleteProvider(provider.id.toString())
            }
            kotlinx.coroutines.withTimeout(5000) { entered.await() }
            waiter.cancel()
            released.countDown()
            kotlinx.coroutines.withTimeout(5000) { completed.await() }
            waiter.join()
            assertNull(dao.getById(provider.id))
            service.refresh()
            assertFalse(service.providers.value.any { it.id == provider.id.toString() })
        } finally {
            released.countDown()
            subscription.cancel()
            org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<SecretStore> { original } })
        }
    }

    @Test fun cancelledConnectionCheckPropagatesCancellationNotFailureResult() = runBlocking {
        val job = kotlinx.coroutines.Job().also { it.cancel() }
        try {
            kotlinx.coroutines.withContext(job) {
                LlmProviderServiceImpl(strings).testConnection("CUSTOM", "", "dummy")
            }
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) {
            // No transport is invoked: cancellation is checked on the IO dispatcher hop.
        }
    }

    @Test fun resetCompletesBeforeUsageAndCostRead() = runBlocking {
        val id = IdType()
        DatabaseContainer.instance.aiSettingsDb.llmUsageRecordDao().upsert(
            LlmUsageRecord(configuredModelId = id, deviceId = "fixture", inputTokens = 123, estimatedCostUsd = 2.0)
        )
        assertEquals(123L, LlmCostTracker.getCumulativeUsage(id).inputTokens)
        LlmCostTracker.reset(id)
        assertEquals(0L, LlmCostTracker.getCumulativeUsage(id).totalTokens)
        assertEquals(0.0, LlmCostTracker.getCumulativeCost(id), 0.0)
    }
}
