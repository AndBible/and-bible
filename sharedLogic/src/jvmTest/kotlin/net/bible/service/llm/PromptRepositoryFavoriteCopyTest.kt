package net.bible.service.llm

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.FakeDatabaseHost
import net.bible.service.db.newTestDir
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.event.EventSource
import net.bible.sharedlogic.platform.LocaleSupport
import net.bible.sharedlogic.platform.LocaleValue
import net.bible.sharedlogic.platform.PromptPackSource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PromptRepositoryFavoriteCopyTest {
    @Test
    fun copyingFavoriteRetainsFavoriteForNewPromptAndDoesNotRemoveSource() = withFixture { container, settings ->
        val source = BuiltInPrompts.productionPrompts().first()
        settings.favoritePrompts = setOf(source.id)
        settings.awaitWrites()

        val copyId = assertNotNull(PromptRepository.copyPrompt(source.id))

        assertTrue(PromptRepository.isFavorite(source.id))
        assertTrue(PromptRepository.isFavorite(copyId))
        assertEquals(setOf(source.id, copyId), PromptRepository.favoritePromptIds())
        settings.awaitWrites()
        assertEquals(setOf(source.id, copyId), container.aiSettingsDb.globalAiSettingsDao().get()?.favoritePrompts)
    }

    @Test
    fun copyingNonfavoriteDoesNotMakeCopyFavorite() = withFixture { container, settings ->
        val source = BuiltInPrompts.productionPrompts().first()
        settings.favoritePrompts = emptySet()
        settings.awaitWrites()

        val copyId = assertNotNull(PromptRepository.copyPrompt(source.id))

        assertFalse(PromptRepository.isFavorite(source.id))
        assertFalse(PromptRepository.isFavorite(copyId))
        assertEquals(emptySet(), PromptRepository.favoritePromptIds())
        settings.awaitWrites()
        assertEquals(emptySet(), container.aiSettingsDb.globalAiSettingsDao().get()?.favoritePrompts)
    }

    private object TestLocaleSupport : LocaleSupport {
        override val default = LocaleValue("en", "", "en", "en")
        override fun of(language: String, country: String) = LocaleValue(language, country, language, language)
        override fun parse(legacy: String) = LocaleValue(legacy, "", legacy, legacy)
        override fun displayLanguage(tag: String) = tag
        override fun defaultDisplayLanguage() = "English"
        override fun formatFixed(value: Double, decimals: Int) = value.toString()
        override fun formatGrouped(value: Long) = value.toString()
    }

    private fun withFixture(block: suspend (DatabaseContainer, AiSettingsStore) -> Unit) = runTest {
        val hostField = DatabaseContainer::class.java.getDeclaredField("_host").apply { isAccessible = true }
        val instanceField = DatabaseContainer::class.java.getDeclaredField("_instance").apply { isAccessible = true }
        val previousHost = hostField.get(null)
        val previousInstance = instanceField.get(null)
        val previousReady = DatabaseContainer.ready
        val previousFactory = DatabaseContainer.containerFactory
        val categoriesField = BuiltInPrompts::class.java.getDeclaredField("_defaultCategories").apply { isAccessible = true }
        val previousCategories = categoriesField.get(null)
        val dir = newTestDir()
        val scopeJob = SupervisorJob()
        var opened: DatabaseContainer? = null
        var koinStarted = false
        try {
            DatabaseContainer.detachInstanceForTest()
            DatabaseContainer.installHost(FakeDatabaseHost(dir))
            val container = DatabaseContainer.create().also { opened = it }
            DatabaseContainer.containerFactory = { container }
            DatabaseContainer.ready = true
            val env = Proxy.newProxyInstance(AppEnvironment::class.java.classLoader, arrayOf(AppEnvironment::class.java)) { _, method, _ ->
                when (method.name) {
                    "getIsDebugMode", "isDebugMode" -> true
                    "getDeviceIdentifier", "getApplicationNameMedium", "getPackageName", "getTmpDir", "getCacheDir", "getDbBackupPath", "databasePath" -> dir
                    "getVersionNumber", "getMegabytesFree", "getLastCrashTime" -> 0L
                    "deleteDatabase" -> false
                    "getIsInitialized", "getIsDiscrete", "getIsBeta", "getIsMeteredNetwork" -> false
                    "getApkPath" -> null
                    else -> error("Unexpected AppEnvironment method: ${method.name}")
                }
            } as AppEnvironment
            val scope = AppCoroutineScope(coroutineContext + scopeJob)
            val launcher = OrderedLauncher(scope)
            val settings = AiSettingsStore(
                { container.aiSettingsDb.globalAiSettingsDao() }, launcher, scope, env,
                lifetime = DatabaseContainer.aiLifetime,
                invalidations = { container.aiSettingsDb.invalidationTracker.createFlow("GlobalAiSettings").map { Unit } },
            )
            settings.ensureLoaded()
            startKoin { modules(module {
                single<AiSettings> { settings }
                single<AppEnvironment> { env }
                single<LocaleSupport> { TestLocaleSupport }
                single<LlmStrings> {
                    Proxy.newProxyInstance(LlmStrings::class.java.classLoader, arrayOf(LlmStrings::class.java)) { _, method, _ ->
                        if (method.returnType == String::class.java) method.name else error("Unexpected LlmStrings method: ${method.name}")
                    } as LlmStrings
                }
                single<PromptPackSource> {
                    object : PromptPackSource {
                        override val packs = emptyList<net.bible.sharedlogic.platform.PromptPackRef>()
                        override val reloaded = EventSource<Unit>()
                    }
                }
            }) }
            koinStarted = true
            block(container, settings)
        } finally {
            try {
                if (koinStarted) stopKoin()
                DatabaseContainer.detachInstanceForTest()
                opened?.let { container -> DatabaseContainer.aiLifetime.replace { container.closeAllOwned(it) } }
            } finally {
                scopeJob.cancel()
                scopeJob.join()
                instanceField.set(null, previousInstance)
                hostField.set(null, previousHost)
                DatabaseContainer.containerFactory = previousFactory
                DatabaseContainer.ready = previousReady
                categoriesField.set(null, previousCategories)
                File(dir).deleteRecursively()
            }
        }
    }
}
