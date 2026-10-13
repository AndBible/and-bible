package net.bible.android.platform

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.BibleApplication
import net.bible.android.database.IdType
import net.bible.android.view.activity.ai.LlmProviderServiceImpl
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmProviderConfig
import net.bible.service.llm.SecretStore
import net.bible.service.llm.apiKey
import net.bible.service.llm.setApiKey
import net.bible.service.llm.removeApiKey
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidSecretStoreTest {
    private val config = LlmProviderConfig(id = IdType(), providerType = "CUSTOM", displayName = "Local fixture")
    // Intentionally literal: changing the production key must break legacy compatibility.
    private val rawKey get() = "llm_api_key_${config.id}"
    private val prefs get() = CommonUtils.realSharedPreferences

    @After fun cleanup() {
        prefs.edit().remove(rawKey).apply()
        DatabaseResetter.resetDatabase()
    }

    @Test fun fixtureRestoresPreviousSecretStoreOnlyWhileItOwnsBinding() {
        val app = BibleApplication.application as TestBibleApplication
        val graph = GlobalContext.get()
        val previous = TestBibleApplication::class.java.getDeclaredField("previousSecretStore")
            .apply { isAccessible = true }.get(app)
        app.onTerminate()
        assertSame(previous, graph.get<SecretStore>())
        app.onCreate()
        val replacement = AndroidSecretStore(prefs)
        org.koin.core.context.loadKoinModules(org.koin.dsl.module { single<SecretStore> { replacement } })
        try {
            app.onTerminate()
            assertSame("foreign replacement binding must not be overwritten", replacement, graph.get<SecretStore>())
        } finally {
            app.onCreate()
        }
    }

    @Test fun legacyRawKeyIsReadAndMissingKeyIsEmpty() {
        val store = AndroidSecretStore(prefs)
        assertEquals("", config.apiKey(store))
        prefs.edit().putString(rawKey, "sk-old").apply()
        assertEquals("sk-old", config.apiKey(store))
    }

    @Test fun writesAndRemovalUseOriginalRawKey() {
        val store = AndroidSecretStore(prefs)
        config.setApiKey(store, "sk-new")
        assertEquals("sk-new", prefs.getString(rawKey, null))
        config.removeApiKey(store)
        assertFalse(prefs.contains(rawKey))
        assertEquals("", config.apiKey(store))
    }

    @Test fun boundStoreAndActualProviderDeletionRemoveLegacyKey() = runBlocking {
        val graph = GlobalContext.get()
        val store = graph.get<SecretStore>()
        val current = prefs
        val app = BibleApplication.application
        val runnerApp = org.robolectric.RuntimeEnvironment.getApplication()
        val captured = if (store is AndroidSecretStore) AndroidSecretStore::class.java.getDeclaredField("prefs")
            .apply { isAccessible = true }.get(store) as android.content.SharedPreferences else null
        assertSame(graph, GlobalContext.get())
        assertSame(current, prefs)
        current.edit().putString(rawKey, "sk-old").apply()
        assertEquals("sk-old", current.getString(rawKey, null))
        println("secret-bound graph=${System.identityHashCode(graph)} storeClass=${store.javaClass.name} store=${System.identityHashCode(store)} app=${System.identityHashCode(app)} runnerApp=${System.identityHashCode(runnerApp)} appSameRunner=${app === runnerApp} appContext=${System.identityHashCode(app.applicationContext)} current=${System.identityHashCode(current)} captured=${System.identityHashCode(captured)} capturedSameCurrent=${captured === current} currentSynthetic=${current.getString(rawKey, null) == "sk-old"} capturedSynthetic=${captured?.getString(rawKey, null) == "sk-old"} capturedAbsent=${captured?.getString(rawKey, null) == null} legacyKeyMatches=${net.bible.service.llm.apiKeyName(config.id) == rawKey}")
        assertEquals("sk-old", config.apiKey(store))
        val dao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
        dao.insert(config)
        val service = LlmProviderServiceImpl(AndroidLlmStrings(BibleApplication.application))
        assertEquals("sk-old", service.apiKeyFor(config.id.toString()))
        service.deleteProvider(config.id.toString())
        assertNull(dao.getById(config.id))
        assertFalse(prefs.contains(rawKey))
        assertEquals("", service.apiKeyFor(config.id.toString()))
    }
}
