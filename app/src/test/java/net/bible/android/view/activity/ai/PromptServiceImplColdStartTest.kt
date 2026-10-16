package net.bible.android.view.activity.ai

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.platform.AndroidLlmStrings
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AiSettings
import net.bible.service.llm.AiSettingsStore
import net.bible.service.llm.BuiltInPrompts
import net.bible.service.llm.GlobalAiSettings
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import net.bible.test.KoinFactorySnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/** [PromptServiceImpl] must not touch [AiSettings] before `ensureLoaded()` has run (cold start). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class PromptServiceImplColdStartTest {
    private val scope = AppCoroutineScope()
    private val launcher = OrderedLauncher(scope)

    @After fun tearDown() {
        DatabaseResetter.runResetBlocking {
            launcher.join("ai-settings")
            val job = scope.coroutineContext[kotlinx.coroutines.Job]!!
            job.cancel(); job.join()
        }
        DatabaseResetter.resetDatabase()
    }

    private fun unloadedDebugStore(): AiSettingsStore {
        val env = GlobalContext.get().get<AppEnvironment>()
        return AiSettingsStore({ DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao() }, launcher, scope,
            object : AppEnvironment by env { override val isDebugMode = true }, DatabaseContainer.databaseRestored,
            lifetime = DatabaseContainer.aiLifetime,
            invalidations = { DatabaseContainer.instance.aiSettingsDb.invalidationTracker.createFlow("GlobalAiSettings").map { Unit } })
    }

    private inline fun <T> withStore(store: AiSettingsStore, block: () -> T): T {
        val snapshot = KoinFactorySnapshot.of<AiSettings>()
        loadKoinModules(module { single<AiSettings> { store } })
        try { return block() } finally { snapshot.restore() }
    }

    @Test fun constructingTheServiceDoesNotReadUnloadedSettings() {
        val store = unloadedDebugStore()
        withStore(store) {
            assertThrows(IllegalStateException::class.java) { store.hiddenBuiltInPrompts }
            val service = PromptServiceImpl(AndroidLlmStrings(BibleApplication.application))
            assertEquals(false, service.hasHiddenPrompts.value)
        }
    }

    @Test fun hasHiddenPromptsReflectsPersistedSettingsAfterLoad() = runBlocking {
        DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao()
            .set(GlobalAiSettings(hiddenBuiltInPrompts = setOf(BuiltInPrompts.SUMMARY_ID)))
        val store = unloadedDebugStore()
        withStore(store) {
            val service = PromptServiceImpl(AndroidLlmStrings(BibleApplication.application))
            withTimeout(10.seconds) { service.ready.first { it } }
            assertTrue(service.hasHiddenPrompts.value)
        }
    }
}
