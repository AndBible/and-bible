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
        val store = GlobalContext.get().get<SecretStore>()
        prefs.edit().putString(rawKey, "sk-old").apply()
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
