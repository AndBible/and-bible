package net.bible.service.llm

import kotlinx.coroutines.flow.map

import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AiSettingsColdRouteTest {
    @get:Rule val compose = createEmptyComposeRule()
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

    @Test fun independentAiRouteLoadsPersistedStateBeforeServiceConstruction() = coldRoute(loadDelayMs = 0)

    /**
     * Forced interleaving of the flake seen in contended forked suites: the persisted-state read finishes only after
     * more than the 2 simulated seconds the test used to pump. The route must still construct the service once the
     * load completes, and the test must wait for that real-time effect rather than for simulated time.
     */
    @Test fun slowPersistedStateLoadStillGatesServiceConstruction() = coldRoute(loadDelayMs = 3_000)

    private fun coldRoute(loadDelayMs: Long) {
        val db = DatabaseContainer.instance.aiSettingsDb
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "Cold provider")
        val model = LlmConfiguredModel(providerConfigId = provider.id, modelId = "cold-model")
        runBlocking {
            db.llmProviderConfigDao().insert(provider)
            db.llmConfiguredModelDao().insert(model)
            db.globalAiSettingsDao().set(GlobalAiSettings(aiDisclaimerAccepted = true, defaultModelId = model.id))
        }
        val env = GlobalContext.get().get<AppEnvironment>()
        val store = AiSettingsStore({
            if (loadDelayMs > 0) Thread.sleep(loadDelayMs) // real time, like a contended background DB read
            DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao()
        }, launcher, scope,
            object : AppEnvironment by env { override val isDebugMode = true }, DatabaseContainer.databaseRestored, lifetime = DatabaseContainer.aiLifetime,
            invalidations = { DatabaseContainer.instance.aiSettingsDb.invalidationTracker.createFlow("GlobalAiSettings").map { Unit } })
        var construction = 0
        loadKoinModules(module {
            single<AiSettings> { store }
            single<AiSettingsService> {
                // This is the actual route's synchronous service resolver, not a test preload.
                assertTrue(store.aiDisclaimerAccepted)
                assertEquals(model.id, store.defaultModelId)
                construction++
                net.bible.android.view.activity.ai.AiSettingsServiceImpl(get())
            }
        })
        assertThrows(IllegalStateException::class.java) { store.defaultModelId }
        val controller = Robolectric.buildActivity(NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.AI_CONNECTION_SETTINGS))
        try {
            val activity = controller.setup().get()
            assertFalse(activity.doNotInitializeApp)
            // The persisted-state load and the settings gate run on real background threads and the compose rule's
            // own dispatcher; idleFor() only advances simulated time, so wait (real time, bounded) for the actual
            // effect instead of assuming 2 simulated seconds is enough under parallel-fork load.
            net.bible.test.awaitHostUntil("AI route must construct AiSettingsService") {
                compose.waitForIdle()
                construction > 0
            }
            assertEquals(1, construction)
            assertTrue(CommonUtils.aiSettings.aiDisclaimerAccepted)
            // The service's snapshot is filled by its own asynchronous load, so wait for it too.
            net.bible.test.awaitHostUntil("AiSettingsService snapshot must carry the persisted default model") {
                compose.waitForIdle()
                GlobalContext.get().get<AiSettingsService>().snapshot.value.defaultModelLabel == "cold-model"
            }
            assertEquals("cold-model", GlobalContext.get().get<AiSettingsService>().snapshot.value.defaultModelLabel)
        } finally { controller.close() }
    }
}
