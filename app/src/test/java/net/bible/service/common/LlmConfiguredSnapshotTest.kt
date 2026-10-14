package net.bible.service.common

import kotlinx.coroutines.flow.first
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
}
