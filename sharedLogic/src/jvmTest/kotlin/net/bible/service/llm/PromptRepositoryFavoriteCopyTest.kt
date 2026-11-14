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
import kotlin.test.assertNull
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

    /** #4108 review: an add-on pack's permissionMode=ALLOW_ALL is not the user's choice either. */
    @Test
    fun addonAllowAllModeDoesNotSkipTheDialog() {
        val csvDir = newTestDir()
        val csv = File(csvDir, "prompts.csv").apply {
            writeText(
                "name;promptTemplate;showIn;permissionMode;allowedTools;deniedTools;id\n" +
                    "Addon wipe;Delete them;VERSE_SELECTION;ALLOW_ALL;GET_VERSE_CONTENT;;" +
                    "0b2c4d6e-1111-2222-3333-444455557777\n"
            )
        }
        PromptRepository.clearAddonCache()
        try {
            withFixture(addonPacks = listOf(PromptPackRef("AddonMod", csv.path))) { _, _ ->
                val addon = assertNotNull(PromptRepository.allPrompts().find { it.name == "Addon wipe" })
                for (tool in listOf(AgentTool.DELETE_BOOKMARK, AgentTool.CREATE_BOOKMARK)) {
                    assertEquals(PermissionCheckResult.NeedsDialog, decide(tool, addon), "$tool of the add-on")
                }
                val copy = assertNotNull(PromptRepository.promptById(assertNotNull(PromptRepository.copyPrompt(addon.id))))
                assertNull(copy.permissionMode, "a copy must not inherit the pack's ALLOW_ALL")
                assertEquals(PermissionCheckResult.NeedsDialog, decide(AgentTool.DELETE_BOOKMARK, copy), "copy of add-on")
            }
        } finally {
            PromptRepository.clearAddonCache()
            File(csvDir).deleteRecursively()
        }
    }

    /**
     * Grants apply only to user prompts stored in the DB (positive condition). An add-on row without an id
     * gets a random id per parse, and a pack reload empties the add-on cache, so a prompt object a run holds
     * may no longer look like an add-on; it must still not count as a user prompt.
     */
    @Test
    fun addonPromptAfterPackReloadGetsNoGrants() {
        val csvDir = newTestDir()
        val csv = File(csvDir, "prompts.csv").apply {
            writeText(
                "name;promptTemplate;showIn;permissionMode;allowedTools;deniedTools\n" +
                    "Addon noid;Bookmark it;VERSE_SELECTION;ALLOW_ALL;CREATE_BOOKMARK;\n"
            )
        }
        PromptRepository.clearAddonCache()
        try {
            withFixture(addonPacks = listOf(PromptPackRef("AddonMod", csv.path))) { _, _ ->
                val addon = assertNotNull(PromptRepository.allPrompts().find { it.name == "Addon noid" })
                PromptRepository.clearAddonCache() // what PromptPackSource.reloaded does
                assertNull(PromptRepository.autoAllowedTools(addon))
                assertNull(PromptRepository.effectivePermissionMode(addon))
                assertEquals(PermissionCheckResult.NeedsDialog, decide(AgentTool.CREATE_BOOKMARK, addon))
            }
        } finally {
            PromptRepository.clearAddonCache()
            File(csvDir).deleteRecursively()
        }
    }

    /** A prompt that is not stored in the DB (e.g. built in memory) carries no user grants. */
    @Test
    fun unstoredPromptGetsNoGrants() = withFixture { _, _ ->
        val prompt = AgentPrompt(
            name = "Not stored",
            permissionMode = PermissionMode.ALLOW_ALL,
            allowedTools = setOf(AgentTool.CREATE_BOOKMARK),
            deniedTools = emptySet(),
        )
        assertNull(PromptRepository.autoAllowedTools(prompt))
        assertNull(PromptRepository.effectivePermissionMode(prompt))
        // A stricter mode than the global one still applies: it takes nothing from the user.
        assertEquals(PermissionMode.DENY_ALL, PromptRepository.effectivePermissionMode(prompt.copy(permissionMode = PermissionMode.DENY_ALL)))
    }

    @Test
    fun userAllowAllModeIsKept() = withFixture { _, _ ->
        val prompt = AgentPrompt(name = "Mine all", permissionMode = PermissionMode.ALLOW_ALL, allowedTools = null, deniedTools = emptySet())
        PromptRepository.insertPrompt(prompt)
        val stored = assertNotNull(PromptRepository.promptById(prompt.id))
        assertEquals(PermissionCheckResult.Allowed, decide(AgentTool.DELETE_BOOKMARK, stored))
        val copy = assertNotNull(PromptRepository.promptById(assertNotNull(PromptRepository.copyPrompt(prompt.id))))
        assertEquals(PermissionMode.ALLOW_ALL, copy.permissionMode)
    }

    @Test
    fun withoutGrantsStripsWriteToolsAndAllowAll() {
        val prompt = AgentPrompt(
            name = "Shared",
            permissionMode = PermissionMode.ALLOW_ALL,
            allowedTools = setOf(AgentTool.CREATE_BOOKMARK, AgentTool.GET_VERSE_CONTENT),
            deniedTools = setOf(AgentTool.DELETE_BOOKMARK),
        )
        val stripped = PromptRepository.withoutGrants(prompt)
        assertEquals(setOf(AgentTool.GET_VERSE_CONTENT), stripped.allowedTools)
        assertNull(stripped.permissionMode)
        assertEquals(setOf(AgentTool.DELETE_BOOKMARK), stripped.deniedTools)
        assertEquals(prompt.id, stripped.id)
        assertEquals(PermissionMode.ALWAYS_ASK,
            PromptRepository.withoutGrants(prompt.copy(permissionMode = PermissionMode.ALWAYS_ASK)).permissionMode)
    }

    /**
     * #4108 review: a CSV row whose id is an installed add-on prompt's id must not be stored under that id (a
     * duplicate id that promptById/isReadOnly resolve to the add-on, and that would make the add-on id look
     * stored). It becomes a new user prompt with a fresh id, without grants.
     */
    @Test
    fun csvRowWithAddonIdIsImportedUnderAFreshIdWithoutGrants() {
        val addonId = "0b2c4d6e-1111-2222-3333-444455558888"
        val csvDir = newTestDir()
        val csv = File(csvDir, "prompts.csv").apply {
            writeText(
                "name;promptTemplate;showIn;permissionMode;allowedTools;deniedTools;id\n" +
                    "Addon one;Do it;VERSE_SELECTION;ASK_ONCE_PER_RUN;GET_VERSE_CONTENT;;$addonId\n"
            )
        }
        PromptRepository.clearAddonCache()
        try {
            withFixture(addonPacks = listOf(PromptPackRef("AddonMod", csv.path))) { container, _ ->
                val import = "name;promptTemplate;permissionMode;allowedTools;deniedTools;id\n" +
                    "Imported;Mine;ALLOW_ALL;CREATE_BOOKMARK,GET_VERSE_CONTENT;;$addonId\n"
                val result = PromptCsvUtils.importPromptsFromCsv(okio.Buffer().writeUtf8(import))
                assertEquals(1, result.created)
                assertEquals(0, result.updated)

                val stored = container.aiSettingsDb.agentPromptDao().allPrompts()
                assertEquals(1, stored.size)
                val imported = stored.single()
                assertTrue(imported.id.toString() != addonId, "imported under the add-on's id")
                assertEquals("Imported", imported.name)
                assertNull(imported.permissionMode)
                assertEquals(setOf(AgentTool.GET_VERSE_CONTENT), imported.allowedTools)

                val all = PromptRepository.allPrompts().map { it.id }
                assertEquals(all.size, all.toSet().size, "duplicate ids in allPrompts()")
                assertEquals("Imported", PromptRepository.promptById(imported.id)?.name)
                assertFalse(PromptRepository.isReadOnly(imported.id))
                val addon = assertNotNull(PromptRepository.promptById(IdTypeOf(addonId)))
                assertFalse(PromptRepository.isStoredUserPrompt(addon))
            }
        } finally {
            PromptRepository.clearAddonCache()
            File(csvDir).deleteRecursively()
        }
    }

    /**
     * #4108 review: a run's write-permission grant is one flag for all write tools, so a non-user prompt's
     * ASK_ONCE_PER_RUN must not loosen a stricter global ALWAYS_ASK (one approval would unlock every write
     * tool). Its mode is the stricter of its own and the global one; a stored user prompt keeps its own.
     */
    @Test
    fun nonUserPromptModeIsTheStricterOfItsOwnAndTheGlobalMode() = withFixture { _, settings ->
        settings.agentPermissionMode = PermissionMode.ALWAYS_ASK
        val unstored = AgentPrompt(name = "Unstored", permissionMode = PermissionMode.ASK_ONCE_PER_RUN, allowedTools = null, deniedTools = null)
        assertEquals(PermissionMode.ALWAYS_ASK, PromptRepository.effectivePermissionMode(unstored))
        assertEquals(PermissionMode.DENY_ALL, PromptRepository.effectivePermissionMode(unstored.copy(permissionMode = PermissionMode.DENY_ALL)))
        assertNull(PromptRepository.effectivePermissionMode(unstored.copy(permissionMode = null)))
        assertNull(PromptRepository.effectivePermissionMode(unstored.copy(permissionMode = PermissionMode.ALLOW_ALL)))

        settings.agentPermissionMode = PermissionMode.ASK_ONCE_PER_RUN
        assertEquals(PermissionMode.ALWAYS_ASK, PromptRepository.effectivePermissionMode(unstored.copy(permissionMode = PermissionMode.ALWAYS_ASK)))
        assertEquals(PermissionMode.ASK_ONCE_PER_RUN, PromptRepository.effectivePermissionMode(unstored))

        settings.agentPermissionMode = PermissionMode.ALWAYS_ASK
        val mine = unstored.copy(name = "Mine")
        PromptRepository.insertPrompt(mine)
        assertEquals(PermissionMode.ASK_ONCE_PER_RUN, PromptRepository.effectivePermissionMode(assertNotNull(PromptRepository.promptById(mine.id))))
    }

    /**
     * #4108 review: isStoredUserPrompt must not trust the passed object's grants. An object with the id of a
     * stored prompt but other grants (e.g. an add-on object that lost its sourceModule in a copy) is not that
     * stored prompt.
     */
    @Test
    fun objectWithAStoredIdButOtherGrantsIsNotAStoredUserPrompt() = withFixture { _, _ ->
        val mine = AgentPrompt(name = "Mine", permissionMode = PermissionMode.ASK_ONCE_PER_RUN, allowedTools = setOf(AgentTool.GET_VERSE_CONTENT), deniedTools = null)
        PromptRepository.insertPrompt(mine)
        assertTrue(PromptRepository.isStoredUserPrompt(assertNotNull(PromptRepository.promptById(mine.id))))

        val forged = mine.copy(permissionMode = PermissionMode.ALLOW_ALL, allowedTools = setOf(AgentTool.DELETE_BOOKMARK))
        assertFalse(PromptRepository.isStoredUserPrompt(forged))
        assertNull(PromptRepository.autoAllowedTools(forged))
        assertNull(PromptRepository.effectivePermissionMode(forged))
    }

    /**
     * #4108 review: re-importing over a stored prompt keeps its grants only where the file does not tighten
     * them: a granted write tool the file denies is not put back (allowed and denied at once), and a stricter
     * mode in the file replaces the existing ALLOW_ALL. A file without a mode keeps ALLOW_ALL.
     */
    @Test
    fun csvReimportTightensButDoesNotLoosenExistingGrants() = withFixture { container, _ ->
        val granted = AgentPrompt(
            name = "Granted", promptTemplate = "t", permissionMode = PermissionMode.ALLOW_ALL,
            allowedTools = setOf(AgentTool.CREATE_BOOKMARK, AgentTool.DELETE_BOOKMARK, AgentTool.GET_VERSE_CONTENT),
            deniedTools = null,
        )
        val keepMode = granted.copy(id = net.bible.android.database.IdType(), name = "Keep mode")
        PromptRepository.insertPrompt(granted)
        PromptRepository.insertPrompt(keepMode)

        val import = "name;promptTemplate;permissionMode;allowedTools;deniedTools;id\n" +
            "Granted;t;ALWAYS_ASK;GET_VERSE_CONTENT;DELETE_BOOKMARK;${granted.id}\n" +
            "Keep mode;t;;GET_VERSE_CONTENT;;${keepMode.id}\n"
        assertEquals(2, PromptCsvUtils.importPromptsFromCsv(okio.Buffer().writeUtf8(import)).updated)

        val dao = container.aiSettingsDb.agentPromptDao()
        val g = assertNotNull(dao.promptById(granted.id))
        assertEquals(PermissionMode.ALWAYS_ASK, g.permissionMode)
        assertEquals(setOf(AgentTool.GET_VERSE_CONTENT, AgentTool.CREATE_BOOKMARK), g.allowedTools)
        assertEquals(setOf(AgentTool.DELETE_BOOKMARK), g.deniedTools)

        val k = assertNotNull(dao.promptById(keepMode.id))
        assertEquals(PermissionMode.ALLOW_ALL, k.permissionMode)
        assertEquals(setOf(AgentTool.GET_VERSE_CONTENT, AgentTool.CREATE_BOOKMARK, AgentTool.DELETE_BOOKMARK), k.allowedTools)
    }

    private fun IdTypeOf(s: String) = net.bible.android.database.IdType(s)

    /** The decision AgentSessionManager/AgentExecutor make for [tool] in a fresh run of [prompt]. */
    private suspend fun decide(tool: AgentTool, prompt: AgentPrompt) = checkPermission(
        tool = tool,
        settings = PermissionSettings(PermissionMode.ASK_ONCE_PER_RUN, emptySet(), emptySet()),
        promptAllowedTools = PromptRepository.autoAllowedTools(prompt),
        promptDeniedTools = prompt.deniedTools,
        promptPermissionMode = PromptRepository.effectivePermissionMode(prompt),
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
