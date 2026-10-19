/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.assembly

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.bible.service.db.ALL_DB_FILENAMES
import net.bible.service.db.DatabaseContainer
import net.bible.service.format.SaxTextPolicy
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.android.control.speak.SpeakPlatform
import net.bible.sharedlogic.fixtures.StartupFixture
import net.bible.sharedlogic.platform.PromptPackSource
import net.bible.sharedlogic.platform.ios.IosSwordEnvironment
import net.bible.sharedlogic.startup.SharedAppStartup
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.crosswire.common.util.Reporter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.index.fts.SearchFields
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Keychain stand-in: see the class comment of [IosAssemblyTest]. */
internal class InMemorySecretStore : net.bible.sharedcore.platform.secrets.SecretStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
}

/**
 * The I2 Stream C acceptance test: the real iOS bindings plus the shared bindings and the real
 * sync bindings start on an empty sandbox, every definition resolves, the databases open off the main thread, and a
 * real SWORD module is read through the real facade. Native only; runs on the Mac.
 *
 * SECRETS: the app stores its device identifier in the Keychain, and database opening reads it. The unsigned simulator
 * test binary has no usable keychain (SecItemCopyMatching answers -25291), so these tests pass an in-memory
 * `SecretStore` through `IosAppConfig.secretStore`. The Keychain-backed `IosKeychainSecretStore` is NOT exercised
 * here; the signed app run (Task 10) verifies that path.
 *
 * All tests share ONE sandbox root (with the module fixture): JSword's home folder and book list are process-wide,
 * so a second root would leave the first test's books registered. Each test gets empty databases instead.
 */
class IosAssemblyTest {
    private val files = FileSystem.SYSTEM

    private fun config(language: String? = "en") = IosAppConfig(root = sharedRoot, uiLanguage = language, repositories = org.crosswire.common.util.PropertyMap(), secretStore = InMemorySecretStore())
    private val databaseDir: Path get() = sharedRoot / IosAppConfig.DATABASES_DIR
    private val moduleDir: Path get() = sharedRoot / IosAppConfig.DOCUMENTS_DIR / "sword"

    private fun notInstalledMessage() =
        "K4RawText is not installed. module dir: $moduleDir (exists=${files.exists(moduleDir / "mods.d")}), " +
            "sword path: ${org.crosswire.jsword.book.sword.SwordBookPath.getSwordPath().toList()}, " +
            "installed: ${Books.installed().getBooks().map { it.initials }}"

    /** Opens the databases; a failure prints its whole cause chain (it lands in the test process output). */
    private fun openDatabasesDiagnosing() {
        try {
            runBlocking { IosAppStartup.openDatabases() }
        } catch (e: Throwable) {
            println("openDatabases failed:\n${e.stackTraceToString()}")
            throw e
        }
    }

    @BeforeTest
    fun setUp() {
        installUnhandledExceptionPrinter()
        resetEverything()
        files.deleteRecursively(databaseDir)
    }

    @AfterTest
    fun tearDown() = resetEverything()

    private fun resetEverything() {
        runBlocking { IosAppStartup.resetForTest() }
        files.deleteRecursively(databaseDir)
    }

    private fun startApp(language: String? = "en") = IosAppStartup.start(config(language))

    @Test
    fun startupResolvesTheGraphOpensDatabasesAndReadsAVerse() {
        assertFalse(files.exists(databaseDir), "the sandbox starts without a database directory")
        startApp()
        val koin = KoinPlatform.getKoin()

        // The graph is resolved AFTER the databases are open, as on Android: TextToSpeechServiceManager (and other
        // definitions) read their settings from the database when constructed, so they cannot be built before
        // openDatabases(). That start() itself never touches the databases is asserted in
        // startOnAnEmptySandboxCreatesTheFoldersAndNeverTouchesTheDatabases (C2).
        openDatabasesDiagnosing()
        assertTrue(IosAppStartup.databasesOpen)
        for (name in ALL_DB_FILENAMES) assertTrue(files.exists(databaseDir / name), "$name was not created in $databaseDir")

        // TextToSpeechServiceManager reads the active window's document, so the workspace must be loaded first; on
        // Android the reading bootstrap does exactly this (WindowRepository.initializeAtEdge) before anything speaks.
        koin.get<net.bible.android.control.page.window.WindowControl>().windowRepository.initializeAtEdge()

        val failures = koin.resolveAllDefinitions()
        assertEquals(emptyList(), failures.map { it.first }, "unresolvable definitions:\n" + describeFailures(failures))

        val book = assertNotNull(Books.installed().getBook("K4RawText"), notInstalledMessage())
        val text = SwordContentFacade.getCanonicalText(
            book, book.getKey("Gen.1.1"), strings = koin.get(), saxTextPolicy = koin.get<SaxTextPolicy>(),
        )
        assertEquals("In the beginning God created the heaven and the earth.", text.trim())
        assertNotNull(koin.get<SwordContentFacade>())
    }

    @Test
    fun startWithRealSyncBindingsResolvesEveryDefinition() {
        startApp() // no extra modules: the sync ports come from buildIosSyncBindings
        openDatabasesDiagnosing()
        val result = KoinPlatform.getKoin().probeAllDefinitions()
        assertEquals(emptyList(), result.failures, "unresolvable definitions")
        val syncNames = listOf("CloudAdapterFactory", "SyncSettingsPlatform", "DocumentSyncStarter", "SyncScheduler", "CloudSync", "DocumentSync", "IosBackgroundSync")
        assertTrue(result.phase2Dependents.none { d -> syncNames.any { d.contains(it) } }, "sync definitions still excused: ${result.phase2Dependents}")
    }

    @Test
    fun startOnAnEmptySandboxCreatesTheFoldersAndNeverTouchesTheDatabases() {
        startApp()
        // The fixture ships only mods.d and the module data, so the search folder exists only if startup created it.
        // (JSword's loaded flag is process-wide, so the folder creation itself runs once; the folders persist in the
        // shared root. Creation from a fully empty directory is covered by IosSwordEnvironmentTest in :sharedLogic.)
        assertTrue(files.exists(moduleDir / SearchFields.DIR_SEARCH), "search folder created by startup")
        assertTrue(IosSwordEnvironment.isLoaded)
        assertFalse(IosAppStartup.databasesOpen)
        assertFalse(DatabaseContainer.ready, "start() must not mark the databases ready (C2)")
        assertFalse(files.exists(databaseDir), "start() must not open or create any database (C2)")
        // Settings answer their defaults until the databases are open (no DataBaseNotReady on the main thread).
        val settings = KoinPlatform.getKoin().get<AppSettings>()
        assertTrue(settings.getBoolean("never-set-key", true))
        assertEquals("fallback", settings.getString("never-set-key", "fallback"))
    }

    @Test
    fun openDatabasesAloneCreatesEveryDatabaseOnAnEmptySandbox() {
        startApp()
        openDatabasesDiagnosing()
        assertTrue(IosAppStartup.databasesOpen)
        assertTrue(DatabaseContainer.ready)
        for (name in ALL_DB_FILENAMES) assertTrue(files.exists(databaseDir / name), "$name was not created in $databaseDir")
    }

    @Test
    fun startIsIdempotent() {
        startApp()
        val koin = KoinPlatform.getKoin()
        val environment = koin.get<AppEnvironment>()
        startApp()
        assertTrue(koin === KoinPlatform.getKoin(), "a second start must not start a second Koin")
        assertTrue(environment === KoinPlatform.getKoin().get<AppEnvironment>())

        // One JSword error listener. The hook de-duplicates per message, so the dialog count cannot show a second
        // listener; the hook's own call counter does: every registered listener calls it once per event.
        val dialogs = koin.get<AppDialogController>()
        Reporter.informUser(this, "assembly test error")
        assertEquals(1, IosAppStartup.errorHookCalls, "two JSword error listeners are registered")
        val first = assertNotNull(dialogs.pending.value, "the error was not reported to the user")
        dialogs.dismiss(first.id)
        assertNull(dialogs.pending.value, "one error must show one dialog")
    }

    @Test
    fun openDatabasesTwiceKeepsTheSameOpenContainer() {
        startApp()
        openDatabasesDiagnosing()
        val container = DatabaseContainer.instance
        openDatabasesDiagnosing() // a repeated call is a no-op
        assertTrue(DatabaseContainer.ready)
        assertTrue(container === DatabaseContainer.instance, "the second call replaced the container")
    }

    @Test
    fun aFailedOpenLeavesTheDatabasesClosedAndCanBeRetried() {
        startApp()
        // An unusable database location: a regular file where the databases directory must be.
        files.createDirectories(databaseDir.parent!!)
        files.write(databaseDir) { writeUtf8("not a directory") }
        val failure = runCatching { runBlocking { IosAppStartup.openDatabases() } }.exceptionOrNull()
        assertNotNull(failure, "opening with an unusable database location must fail")
        assertFalse(IosAppStartup.databasesOpen)
        assertFalse(DatabaseContainer.ready, "a failed open must not leave the databases marked ready")

        files.delete(databaseDir)
        openDatabasesDiagnosing() // the retry succeeds
        assertTrue(IosAppStartup.databasesOpen)
        for (name in ALL_DB_FILENAMES) assertTrue(files.exists(databaseDir / name), "$name missing after the retry")
    }

    @Test
    fun aCorruptDatabaseFileIsMovedAsideAndRecreatedEmpty() {
        startApp()
        // Garbage in one database file: buildAppDatabase (recoverIfCorrupt) moves it to `<name>.corrupt-<ts>` and the
        // container recreates the database, so the open SUCCEEDS (it does not report Failed).
        val victim = ALL_DB_FILENAMES.first()
        files.createDirectories(databaseDir)
        files.write(databaseDir / victim) { writeUtf8("this is not a sqlite database ".repeat(200)) }

        openDatabasesDiagnosing()

        assertTrue(IosAppStartup.databasesOpen)
        assertTrue(DatabaseContainer.ready)
        val movedAside = files.list(databaseDir).map { it.name }.filter { it.startsWith("$victim.corrupt-") }
        assertEquals(1, movedAside.size, "the corrupt $victim was not moved aside: ${files.list(databaseDir).map { it.name }}")
        assertTrue(files.metadata(databaseDir / victim).size!! > 0L, "$victim was not recreated")
        assertTrue(
            files.read(databaseDir / victim) { readUtf8(15) } == "SQLite format 3",
            "the recreated $victim is not a SQLite database",
        )
        for (name in ALL_DB_FILENAMES) assertTrue(files.exists(databaseDir / name), "$name missing")
    }

    @Test
    fun theBookInstallWatcherStartedAndWritesToTheBackupDatabase() {
        startApp()
        assertTrue(IosSwordEnvironment.isLoaded, "StartupHooks.isSwordLoaded must be true after folder initialisation")
        openDatabasesDiagnosing()
        val koin = KoinPlatform.getKoin()
        val book = assertNotNull(Books.installed().getBook("K4RawText"), notInstalledMessage())

        var reloaded = 0
        val subscription = koin.get<PromptPackSource>().reloaded.subscribe { reloaded++ }
        val dao = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
        // Bounded wait for a condition on the backup database (the watcher writes on the app scope, in order).
        fun awaitRow(present: Boolean) = runBlocking {
            var waited = 0
            var row = dao.getBook(book.initials)
            while ((row != null) != present && waited < 10_000) { delay(100); waited += 100; row = dao.getBook(book.initials) }
            row
        }
        try {
            // The watcher clears the add-on catalog on every install/uninstall, and the catalog tells PromptPackSource.
            Books.installed().removeBook(book)
            assertTrue(reloaded >= 1, "removing a book did not reach the watcher (it was not started)")
            // A row that existed before (a start-up scan) must be gone, so the row below provably comes from the add.
            assertNull(awaitRow(present = false), "the watcher did not remove ${book.initials} from the backup database")
            Books.installed().addBook(book)
            assertTrue(reloaded >= 2, "adding a book did not reach the watcher")
            val info = assertNotNull(awaitRow(present = true), "the watcher did not record ${book.initials} in the backup database")
            assertEquals(book.initials, info.initials)
            assertEquals(book.name, info.name)
            assertEquals(book.abbreviation, info.abbreviation)
        } finally {
            subscription.cancel()
            // Books is process-wide: never leave the module removed for the tests that follow.
            if (Books.installed().getBook(book.initials) == null) Books.installed().addBook(book)
        }
    }

    @Test
    fun speakPhrasesForFinnishComeOutFinnish() {
        startApp()
        val speak = KoinPlatform.getKoin().get<SpeakPlatform>()
        // Expected values are the literal `speak_first` of values/strings.xml and values-fi/strings.xml.
        assertEquals("First", speak.phrasesFor("en").first)
        assertEquals("Ensimmäinen", speak.phrasesFor("fi").first)
        assertEquals("Ensimmäinen", speak.phrasesFor("fi-FI").first, "a region variant falls back to the language")
        assertEquals("First", speak.phrasesFor("xx").first, "an unknown language falls back to English")
    }

    @Test
    fun theConfiguredUiLanguageDrivesTheBoundStringTables() {
        startApp(language = "fi")
        val labels = KoinPlatform.getKoin().get<net.bible.sharedlogic.platform.LabelNameStrings>()
        // Literal values of values-fi/strings.xml (speak, label_unlabelled).
        assertEquals("Puhe", labels.speak)
        assertEquals("Ei kirjanmerkkiryhmää", labels.unlabelled)
    }

    @Test
    fun theEnglishConfigGivesEnglishStrings() {
        startApp(language = "en")
        val labels = KoinPlatform.getKoin().get<net.bible.sharedlogic.platform.LabelNameStrings>()
        assertEquals("Speak", labels.speak)
        assertEquals("Unlabeled", labels.unlabelled) // generated English table (US spelling), checked in StringsData
    }

    @OptIn(org.koin.core.annotation.KoinInternalApi::class)
    @Test
    fun boundPortListMatchesTheModule() {
        startApp()
        val koin = KoinPlatform.getKoin()
        // Every declared port resolves from the running graph ...
        val unresolved = IOS_BOUND_PORT_TYPES.filter { type -> runCatching { koin.get<Any>(type) }.isFailure }
        assertEquals(emptyList(), unresolved.map { it.qualifiedName }, "declared ports that do not resolve")
        // ... and the platform module binds nothing that the declared list does not name (concrete helpers excepted).
        val module = buildIosPlatformBindings(config())
        val bound = module.mappings.values.flatMap { f -> listOf(f.beanDefinition.primaryType) + f.beanDefinition.secondaryTypes }.toSet()
        val undeclared = bound - IOS_BOUND_PORT_TYPES.toSet() - IOS_CONCRETE_BINDINGS
        assertEquals(emptyList(), undeclared.map { it.qualifiedName }, "bound by IosPlatformBindings but missing from IOS_BOUND_PORT_TYPES")
    }

    @Test
    fun theEightHostPortsResolve() {
        startApp()
        val koin = KoinPlatform.getKoin()
        val unresolved = IOS_HOST_BINDING_TYPES.filter { type -> runCatching { koin.get<Any>(type) }.isFailure }
        assertEquals(emptyList(), unresolved.map { it.qualifiedName }, "host ports that do not resolve")
    }

    @Test
    fun theResolveAllHelperReportsEveryFailureByName() {
        class Broken
        class Fine
        val koin = koinApplication {
            modules(module {
                single { Fine() }
                single<Broken> { error("boom from the broken definition") }
                factory<Module> { (n: Int) -> error("never: $n") } // a parameterised factory that is NOT documented
            })
        }.koin
        val failures = koin.resolveAllDefinitions(parameterised = emptyMap())
        val names = failures.map { it.first }
        assertTrue(names.any { it.contains("Broken") }, "Broken not reported: $names")
        assertTrue(names.none { it.contains("Fine") }, "Fine wrongly reported: $names")
        assertTrue(names.any { it.contains("Module") }, "an undocumented parameterised factory must be reported, not skipped: $names")
        assertTrue(failures.first { it.first.contains("Broken") }.second.message.orEmpty().contains("boom from the broken definition"),
            "the message must carry the root cause")
    }

    internal companion object {
        private var hookInstalled = false

        /**
         * On Native an exception that escapes a coroutine without a handler aborts the whole test process with only
         * a truncated trace. This hook prints the real exception and its causes first, so the Process output names it.
         */
        @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
        fun installUnhandledExceptionPrinter() {
            if (hookInstalled) return
            hookInstalled = true
            kotlin.native.setUnhandledExceptionHook { e -> println("UNHANDLED EXCEPTION:\n${e.stackTraceToString()}") }
        }

        /** Concrete platform classes the module binds beside the ports (not part of the shared-graph contract). */
        val IOS_CONCRETE_BINDINGS: Set<kotlin.reflect.KClass<*>> = setOf(
            net.bible.sharedcore.platform.IosAppEnvironment::class,
            net.bible.sharedcore.platform.IosDeviceSettings::class,
            net.bible.sharedcore.http.IosHttpClients::class,
        )

        /** One sandbox for the whole class, created (with the module fixture) on first use. */
        val sharedRoot: Path by lazy {
            // Remove the sandboxes of earlier runs so the simulator's tmp does not accumulate.
            val tmp = NSTemporaryDirectory().trimEnd('/').toPath()
            runCatching {
                FileSystem.SYSTEM.list(tmp).filter { it.name.startsWith("andbible-assembly-") }
                    .forEach { FileSystem.SYSTEM.deleteRecursively(it) }
            }
            val root = (NSTemporaryDirectory().trimEnd('/') + "/andbible-assembly-" + NSUUID().UUIDString).toPath()
            val sword = root / IosAppConfig.DOCUMENTS_DIR / "sword"
            StartupFixture.files.forEach { (name, bytes) ->
                val target = sword / name
                FileSystem.SYSTEM.createDirectories(target.parent!!)
                FileSystem.SYSTEM.write(target) { write(bytes) }
            }
            root
        }
    }
}
