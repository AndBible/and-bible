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

    @Test fun independentAiRouteLoadsPersistedStateBeforeServiceConstruction() {
        val db = DatabaseContainer.instance.aiSettingsDb
        val provider = LlmProviderConfig(providerType = "CUSTOM", displayName = "Cold provider")
        val model = LlmConfiguredModel(providerConfigId = provider.id, modelId = "cold-model")
        runBlocking {
            db.llmProviderConfigDao().insert(provider)
            db.llmConfiguredModelDao().insert(model)
            db.globalAiSettingsDao().set(GlobalAiSettings(aiDisclaimerAccepted = true, defaultModelId = model.id))
        }
        val env = GlobalContext.get().get<AppEnvironment>()
        val store = AiSettingsStore({ DatabaseContainer.instance.aiSettingsDb.globalAiSettingsDao() }, launcher, scope,
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
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            compose.waitForIdle()
            assertEquals(1, construction)
            assertTrue(CommonUtils.aiSettings.aiDisclaimerAccepted)
            assertEquals("cold-model", GlobalContext.get().get<AiSettingsService>().snapshot.value.defaultModelLabel)
        } finally { controller.close() }
    }
}
