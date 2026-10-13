package net.bible.android.platform

import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.view.activity.ai.LlmProviderServiceImpl
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.*
import net.bible.test.DatabaseResetter
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Crosses the real runner teardown/cache-reset/application-creation boundary in one sandbox. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AndroidSecretStoreLifetimeTest {
    companion object {
        private var firstGraph: Koin? = null
        private var firstApp: BibleApplication? = null
        private var firstPrefs: SharedPreferences? = null
        private var firstStore: SecretStore? = null
    }

    private fun captured(store: SecretStore): SharedPreferences? =
        if (store is AndroidSecretStore) AndroidSecretStore::class.java.getDeclaredField("prefs")
            .apply { isAccessible = true }.get(store) as SharedPreferences else null

    @Test fun a_captureRunnerOwnedLifetime() {
        val graph = GlobalContext.get()
        val current = CommonUtils.realSharedPreferences
        val store = graph.get<SecretStore>()
        assertTrue(store is AndroidSecretStore)
        assertSame("first method must not start with stale binding", current, captured(store))
        firstGraph = graph
        firstApp = BibleApplication.application
        firstPrefs = current
        firstStore = store
        println("secret-life A graph=${System.identityHashCode(graph)} app=${System.identityHashCode(firstApp)} prefs=${System.identityHashCode(current)} store=${System.identityHashCode(store)}")
    }

    @Test fun b_successorReadsAndDeletesCurrentRunnerPreferences() = runBlocking {
        val graph = GlobalContext.get()
        val app = BibleApplication.application
        val current = CommonUtils.realSharedPreferences
        val store = graph.get<SecretStore>()
        val captured = captured(store)
        val config = LlmProviderConfig(id = IdType(), providerType = "CUSTOM", displayName = "Local fixture")
        val key = "llm_api_key_${config.id}"
        try {
            assertNotNull("method A companion persists in same sandbox", firstApp)
            assertSame("natural successor retains actual graph", firstGraph, graph)
            assertNotSame("runner created successor application", firstApp, app)
            assertNotSame("actual runner preferences changed", firstPrefs, current)
            assertTrue(store is AndroidSecretStore)
            current.edit().putString(key, "synthetic-lifetime-key").apply()
            assertEquals("synthetic-lifetime-key", current.getString(key, null))
            println("secret-life B graphSame=${firstGraph === graph} appDistinct=${firstApp !== app} prefsDistinct=${firstPrefs !== current} capturedSameCurrent=${captured === current} storeSame=${firstStore === store} currentSynthetic=${current.getString(key, null) == "synthetic-lifetime-key"} capturedSynthetic=${captured?.getString(key, null) == "synthetic-lifetime-key"} capturedAbsent=${captured?.getString(key, null) == null}")
            assertEquals("synthetic-lifetime-key", config.apiKey(store))
            val dao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
            dao.insert(config)
            val service = LlmProviderServiceImpl(AndroidLlmStrings(app))
            assertEquals("synthetic-lifetime-key", service.apiKeyFor(config.id.toString()))
            service.deleteProvider(config.id.toString())
            assertNull(dao.getById(config.id))
            assertFalse(current.contains(key))
        } finally {
            current.edit().remove(key).apply()
            firstGraph = null; firstApp = null; firstPrefs = null; firstStore = null
            DatabaseResetter.resetDatabase()
        }
    }
}
