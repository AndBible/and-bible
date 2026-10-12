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
        val koin = org.koin.core.context.GlobalContext.get()
        val original = koin.get<net.bible.sharedcore.llm.http.LlmHttpTransport>()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val cancellation = kotlinx.coroutines.CancellationException("held local transport cancelled")
        val transport = object : net.bible.sharedcore.llm.http.LlmHttpTransport {
            override suspend fun postJson(url: String, headers: Map<String, String>, jsonBody: String, timeouts: net.bible.sharedcore.llm.http.LlmTimeouts): net.bible.sharedcore.llm.http.LlmHttpResponse {
                entered.complete(Unit)
                release.await()
                throw cancellation
            }
            override suspend fun get(url: String, headers: Map<String, String>, timeouts: net.bible.sharedcore.llm.http.LlmTimeouts): net.bible.sharedcore.llm.http.LlmHttpResponse = error("not used")
        }
        org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<net.bible.sharedcore.llm.http.LlmHttpTransport> { transport } })
        try {
            val result = kotlinx.coroutines.CompletableDeferred<Throwable?>()
            val call = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                result.complete(try { LlmProviderServiceImpl(strings).testConnection("OPENAI", "", "dummy"); null } catch (e: Throwable) { e })
            }
            kotlinx.coroutines.withTimeout(5000) { entered.await() }
            release.complete(Unit)
            val thrown = kotlinx.coroutines.withTimeout(5000) { result.await() }
            assertTrue(thrown is kotlinx.coroutines.CancellationException)
            assertEquals(cancellation.message, thrown?.message)
            call.join()
        } finally { release.complete(Unit); org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<net.bible.sharedcore.llm.http.LlmHttpTransport> { original } }) }
    }

    @Test fun overlappingFavoritesPreserveBothTransformsAndSameKeyTogglesCancel() = runBlocking {
        val koin = org.koin.core.context.GlobalContext.get()
        val original = koin.get<AiSettings>()
        original.favoritePrompts = emptySet()
        original.awaitWrites()
        val barrier = java.util.concurrent.CyclicBarrier(2)
        val competing = object : AiSettings by original {
            override var favoritePrompts: Set<IdType>
                get() {
                    val captured = original.favoritePrompts
                    try { barrier.await(1, java.util.concurrent.TimeUnit.SECONDS) } catch (_: Exception) { }
                    return captured
                }
                set(value) { original.favoritePrompts = value }
        }
        org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<AiSettings> { competing } })
        try {
            val service = PromptServiceImpl(strings)
            val a = IdType()
            val b = IdType()
            val owner = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
            val first = owner.launch { service.toggleFavorite(a.toString()) }
            val second = owner.launch { service.toggleFavorite(b.toString()) }
            first.join(); second.join()
            assertEquals(setOf(a, b), original.favoritePrompts)
            assertEquals(setOf(a, b), DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao().get()?.favoritePrompts)
            val third = owner.launch { service.toggleFavorite(a.toString()) }
            val fourth = owner.launch { service.toggleFavorite(a.toString()) }
            third.join(); fourth.join()
            assertEquals(setOf(a, b), original.favoritePrompts)
            assertEquals(setOf(a, b), DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao().get()?.favoritePrompts)
            original.hiddenBuiltInPrompts = emptySet()
            original.hiddenBuiltInCategories = emptySet()
            original.awaitWrites()
            val hides = listOf(
                owner.launch { service.setPromptHidden(a.toString(), true) },
                owner.launch { service.setPromptHidden(b.toString(), true) },
                owner.launch { service.setCategoryHidden(BuiltInPrompts.CATEGORY_TEST_ID.toString(), true) },
            )
            hides.forEach { it.join() }
            assertEquals(setOf(a, b), original.hiddenBuiltInPrompts)
            val persisted = DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao().get()!!
            assertEquals(setOf(a, b), persisted.hiddenBuiltInPrompts)
            assertEquals(original.hiddenBuiltInCategories, persisted.hiddenBuiltInCategories)
        } finally {
            org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<AiSettings> { original } })
        }
    }

    @Test fun oldRawRefreshCannotRepublishDeletedRows() = runBlocking {
        val container = DatabaseContainer.instance
        val original = container.aiSettingsDb
        val real = original.llmRawLogRecordDao()
        val record = LlmRawLogRecord(logData = byteArrayOf())
        real.insert(record)
        val captured = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val deleted = kotlinx.coroutines.CompletableDeferred<Unit>()
        var first = true
        val held = object : LlmRawLogRecordDao by real {
            override suspend fun deleteByIds(ids: List<IdType>) { real.deleteByIds(ids); deleted.complete(Unit) }
            override suspend fun allSummaries(): List<LlmRawLogSummary> {
                val rows = real.allSummaries()
                if (first) { first = false; captured.complete(Unit); release.await() }
                return rows
            }
        }
        val db = org.mockito.Mockito.mock(net.bible.android.database.AiSettingsDatabase::class.java)
        org.mockito.Mockito.`when`(db.llmRawLogRecordDao()).thenReturn(held)
        container.aiSettingsDb = db
        try {
            val service = RawLogServiceImpl(strings)
            val owner = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
            val old = owner.launch { service.refresh() }
            captured.await()
            val deletion = owner.launch { service.deleteByIds(setOf(record.id.toString())) }
            kotlinx.coroutines.withTimeout(5000) { deleted.await() }
            release.complete(Unit)
            old.join(); deletion.join()
            assertNull(real.getById(record.id))
            assertFalse(service.summaries.value.any { it.id == record.id.toString() })
        } finally { release.complete(Unit); container.aiSettingsDb = original }
    }

    @Test fun heldInitialProviderLoadGatesAddAgainstAlreadyConfiguredProvider() = runBlocking {
        val container = DatabaseContainer.instance
        val original = container.aiSettingsDb
        val real = original.llmProviderConfigDao()
        val existing = LlmProviderConfig(providerType = "OPENAI", displayName = "Existing")
        real.insert(existing)
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val held = object : LlmProviderConfigDao by real {
            override suspend fun all(): List<LlmProviderConfig> { entered.complete(Unit); release.await(); return real.all() }
        }
        val db = org.mockito.Mockito.mock(net.bible.android.database.AiSettingsDatabase::class.java)
        org.mockito.Mockito.`when`(db.llmProviderConfigDao()).thenReturn(held)
        container.aiSettingsDb = db
        try {
            val service = LlmProviderServiceImpl(strings)
            val controller = net.bible.sharedcore.ai.AiProvidersController(service, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
            kotlinx.coroutines.withTimeout(5000) { entered.await() }
            assertFalse(service.ready.value)
            controller.startAdd()
            assertNull(controller.dialog.value)
            assertEquals(1, real.getCount())
            release.complete(Unit)
            service.refresh()
            assertTrue(service.ready.value)
            assertEquals(listOf(existing.id.toString()), service.providers.value.map { it.id })
        } finally { release.complete(Unit); container.aiSettingsDb = original }
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
