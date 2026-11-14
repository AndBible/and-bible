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
import net.bible.service.llm.agent.PermissionCheckResult
import net.bible.service.llm.agent.PermissionMode
import net.bible.service.llm.agent.PermissionSettings
import net.bible.service.llm.agent.checkPermission
import net.bible.service.llm.agent.computeExcludedTools
import net.bible.sharedlogic.platform.PromptPackRef
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

    /** #4108: a built-in's allowedTools lists the tools it may use, not tools the user has granted. */
    @Test
    fun copiedBuiltInWriteToolsStillAskForPermission() = withFixture { _, _ ->
        val source = assertNotNull(BuiltInPrompts.promptById(BuiltInPrompts.BOOKMARK_ANNOTATE_ID))
        assertTrue(AgentTool.CREATE_BOOKMARK in source.allowedTools.orEmpty())

        val copy = assertNotNull(PromptRepository.promptById(assertNotNull(PromptRepository.copyPrompt(source.id))))

        for (tool in listOf(AgentTool.CREATE_BOOKMARK, AgentTool.ADD_BOOKMARK_NOTE)) {
            assertEquals(PermissionCheckResult.NeedsDialog, decide(tool, copy), "$tool of the copy")
            assertEquals(PermissionCheckResult.NeedsDialog, decide(tool, source), "$tool of the built-in")
            // The copy can still use the tool: it asks, it is not hidden from the model.
            assertFalse(tool in computeExcludedTools(emptySet(), copy.deniedTools, copy.allowedTools), "$tool excluded")
        }
        // Read tools stay enabled in the copy.
        assertTrue(AgentTool.GET_VERSE_CONTENT in copy.allowedTools.orEmpty())
    }

    @Test
    fun addonPromptWriteToolsAskForPermissionAndSoDoTheirCopies() {
        val csvDir = newTestDir()
        val csv = File(csvDir, "prompts.csv").apply {
            writeText(
                "name;promptTemplate;showIn;permissionMode;allowedTools;deniedTools;id\n" +
                    "Addon bookmark;Bookmark it;VERSE_SELECTION;ASK_ONCE_PER_RUN;GET_VERSE_CONTENT,CREATE_BOOKMARK;;" +
                    "0b2c4d6e-1111-2222-3333-444455556666\n"
            )
        }
        PromptRepository.clearAddonCache()
        try {
            withFixture(addonPacks = listOf(PromptPackRef("AddonMod", csv.path))) { _, _ ->
                val addon = assertNotNull(PromptRepository.allPrompts().find { it.name == "Addon bookmark" })
                assertTrue(PromptRepository.isAddon(addon.id))
                assertEquals(PermissionCheckResult.NeedsDialog, decide(AgentTool.CREATE_BOOKMARK, addon), "add-on")

                val copy = assertNotNull(PromptRepository.promptById(assertNotNull(PromptRepository.copyPrompt(addon.id))))
                assertEquals(PermissionCheckResult.NeedsDialog, decide(AgentTool.CREATE_BOOKMARK, copy), "copy of add-on")
            }
        } finally {
            PromptRepository.clearAddonCache()
            csvDir.let { File(it).deleteRecursively() }
        }
    }

    @Test
    fun userGrantedWriteToolIsStillAutoAllowed() = withFixture { _, _ ->
        val prompt = AgentPrompt(
            name = "Mine",
            permissionMode = PermissionMode.ASK_ONCE_PER_RUN,
            allowedTools = setOf(AgentTool.CREATE_BOOKMARK),
            deniedTools = emptySet(),
        )
        PromptRepository.insertPrompt(prompt)
        val stored = assertNotNull(PromptRepository.promptById(prompt.id))
        assertEquals(PermissionCheckResult.Allowed, decide(AgentTool.CREATE_BOOKMARK, stored))

        // A copy of a user prompt keeps the user's own grant.
        val copy = assertNotNull(PromptRepository.promptById(assertNotNull(PromptRepository.copyPrompt(prompt.id))))
        assertEquals(PermissionCheckResult.Allowed, decide(AgentTool.CREATE_BOOKMARK, copy))
    }

    /** The decision AgentSessionManager/AgentExecutor make for [tool] in a fresh run of [prompt]. */
    private fun decide(tool: AgentTool, prompt: AgentPrompt) = checkPermission(
        tool = tool,
        settings = PermissionSettings(PermissionMode.ASK_ONCE_PER_RUN, emptySet(), emptySet()),
        promptAllowedTools = PromptRepository.autoAllowedTools(prompt),
        promptDeniedTools = prompt.deniedTools,
        promptPermissionMode = prompt.permissionMode,
        grantedWritePermission = false,
        grantedAllToolsPermission = false,
    )

    /**
     * One shared source for every test: PromptRepository resolves its [PromptPackSource] once per JVM
     * (`by lazy`), so a per-test instance would be ignored after the first test.
     */
    private object TestPromptPacks : PromptPackSource {
        var current: List<PromptPackRef> = emptyList()
        override val packs get() = current
        override val reloaded = EventSource<Unit>()
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

    private fun withFixture(
        addonPacks: List<PromptPackRef> = emptyList(),
        block: suspend (DatabaseContainer, AiSettingsStore) -> Unit,
    ) = runTest {
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
                single<PromptPackSource> { TestPromptPacks }
            }) }
            koinStarted = true
            TestPromptPacks.current = addonPacks
            block(container, settings)
        } finally {
            try {
                TestPromptPacks.current = emptyList()
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
