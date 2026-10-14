package net.bible.service.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import net.bible.service.cloudsync.CloudSync
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.ai.PromptService
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `llmConfigured` is read on the UI thread, so it reads the [PromptService.configured] snapshot and never the database. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class LlmConfiguredSnapshotTest {
    @After fun tearDown() { DatabaseResetter.resetDatabase() }

    @Test fun readsTheSnapshotNotTheDatabase() = runBlocking {
        val service = KoinJavaComponent.get<PromptService>(PromptService::class.java)
        withTimeout(10_000) { service.ready.first { it } }
        assertFalse(CommonUtils.settings.llmConfigured)

        // Written behind the snapshot's back: a database read would already see it.
        DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao().insert(LlmProviderConfig(providerType = "CUSTOM", displayName = "P"))
        assertFalse("must not read the database", CommonUtils.settings.llmConfigured)

        CommonUtils.aiSettings.notifyConfigChanged()
        withTimeout(10_000) { service.configured.first { it } }
        assertTrue(CommonUtils.settings.llmConfigured)
    }

    private suspend fun readyService(): PromptService {
        val service = KoinJavaComponent.get<PromptService>(PromptService::class.java)
        withTimeout(10_000) { service.ready.first { it } }
        return service
    }

    private fun insertProviderBehindTheSnapshot() = runBlocking {
        DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao().insert(LlmProviderConfig(providerType = "CUSTOM", displayName = "P"))
    }

    /** A listener reading `llmConfigured` synchronously (BibleView.updateConfig) must see the new value. */
    @Test fun changeEventIsPublishedAfterTheValueIsVisible() = runBlocking {
        readyService()
        val seen = CompletableDeferred<Boolean>()
        val sub = CommonUtils.llmConfiguredChanges.subscribe { seen.complete(CommonUtils.settings.llmConfigured) }
        try {
            insertProviderBehindTheSnapshot()
            CommonUtils.aiSettings.notifyConfigChanged()
            assertTrue("listener read a stale llmConfigured", withTimeout(10_000) { seen.await() })
        } finally { sub.cancel() }
    }

    @Test fun followsADatabaseRestore() = runBlocking {
        val service = readyService()
        insertProviderBehindTheSnapshot()
        DatabaseContainer.notifyDatabaseRestored()
        assertTrue(kotlinx.coroutines.withTimeoutOrNull(5_000) { service.configured.first { it } } == true)
    }

    @Test fun followsACloudSyncRefresh() = runBlocking {
        val service = readyService()
        insertProviderBehindTheSnapshot()
        KoinJavaComponent.get<CloudSync>(CloudSync::class.java).notifyWorkspaceRefreshRequired()
        assertTrue(kotlinx.coroutines.withTimeoutOrNull(5_000) { service.configured.first { it } } == true)
    }

    @Test fun aFailingStepDoesNotSkipTheOthers() = runBlocking {
        val ran = mutableListOf<Int>()
        CommonUtils.runEachCatching({ ran += 1 }, { throw IllegalStateException("boom") }, { ran += 3 })
        assertTrue(ran == listOf(1, 3))
    }
}
