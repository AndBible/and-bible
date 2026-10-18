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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import net.bible.service.cloudsync.ios.buildIosSyncBindings
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.IosAppDirectories
import net.bible.sharedcore.platform.IosAppEnvironment
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.secrets.IosKeychainSecretStore
import net.bible.sharedlogic.platform.ios.IosDatabaseHost
import net.bible.sharedlogic.platform.ios.IosLocaleProvider
import net.bible.sharedlogic.platform.ios.IosSwordEnvironment
import net.bible.sharedlogic.startup.SharedAppStartup
import net.bible.sharedlogic.startup.StartupHooks
import net.bible.sharedui.platform.IosPlatformBootstrap
import net.bible.sharedui.strings.keyed.StringTable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.crosswire.common.util.PropertyMap
import org.crosswire.common.util.ReporterEvent
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.mp.KoinPlatform
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSLocale
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.preferredLanguages

private const val TAG = "IosAppStartup"

/**
 * What the platform bindings and the startup share for one app run: the device settings, keychain, environment,
 * directories and the string table. Everything is lazy, so building a module for inspection starts nothing (the
 * environment owns a network path monitor).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosPlatformParts(val config: IosAppConfig) {
    val deviceSettings: IosDeviceSettings by lazy { IosDeviceSettings() }
    val secrets: net.bible.sharedcore.platform.secrets.SecretStore by lazy { config.secretStore ?: IosKeychainSecretStore() }

    /** Discrete mode is a device-local preference (`discrete_mode`), read before any database exists. */
    val environment: IosAppEnvironment by lazy {
        IosAppEnvironment(secrets, isDiscrete = { deviceSettings.getBoolean("discrete_mode", false) })
    }

    private val documents: Path
        get() = config.root?.let { it / IosAppConfig.DOCUMENTS_DIR } ?: searchPath(NSDocumentDirectory).toPath()
    private val supportRoot: Path
        get() = config.root?.let { it / IosAppConfig.SUPPORT_DIR } ?: (searchPath(NSApplicationSupportDirectory) + "/AndBible").toPath()

    val directories: AppDirectories by lazy { IosAppDirectories(supportRoot, documents) }

    /** Test roots keep the databases below the root; production leaves the choice to [IosAppEnvironment]. */
    val databaseRoot: String? get() = config.root?.let { (it / IosAppConfig.DATABASES_DIR).toString() }

    val bundleResourcePath: String get() = NSBundle.mainBundle.resourcePath.orEmpty()

    /** The UI language (C4); read once. */
    val uiLanguage: String by lazy {
        config.uiLanguage ?: resolveDeviceUiLanguage(deviceSettings)
    }

    val strings: StringTable by lazy { iosStringTable(uiLanguage, ::bundleRawResource) }

    /** The locale JSword formats with: the UI language when the config pins one, else the device locale. */
    fun jswordLocaleTag(): String =
        config.uiLanguage
            ?: deviceSettings.getString("locale_pref")?.takeIf { it.isNotBlank() }
            ?: NSLocale.preferredLanguages.firstOrNull()?.toString()
            ?: "en"

    fun repositories(): PropertyMap = config.repositories ?: PropertyMap().apply {
        val path = NSBundle.mainBundle.pathForResource("repositories", "txt")
            ?: error("repositories.txt is not in the app bundle (iosApp/project.yml copies app/src/main/res/raw/repositories)")
        load(FileSystem.SYSTEM.read(path.toPath()) { readByteArray() })
    }

    /**
     * Raw text resources (the two LLM system prompts, `llm_agent_system_prompt.md` and
     * `llm_text_transformation_system_prompt.md`) are bundle files. A missing one fails when the text is first read,
     * naming the file, so tests and screens that never read a prompt are unaffected.
     */
    private fun bundleRawResource(name: String): String {
        val path = NSBundle.mainBundle.pathForResource(name, "md")
            ?: NSBundle.mainBundle.pathForResource(name, "md", inDirectory = "raw")
            ?: error("Raw resource '$name.md' is not in the app bundle (copy app/src/main/res/raw/$name.md in iosApp/project.yml)")
        return FileSystem.SYSTEM.read(path.toPath()) { readUtf8() }
    }

    private fun searchPath(directory: NSSearchPathDirectory): String =
        NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).first().toString()
}

/**
 * iOS app startup, in two stages (C2, C6):
 * 1. [start] is main-thread safe and quick: environment, database host, Koin, JSword folders, error listener,
 *    book-install watcher. It never touches a database.
 * 2. [openDatabases] opens every database off the main thread, behind the splash/progress state.
 */
object IosAppStartup {
    private var parts: IosPlatformParts? = null
    private val shownErrors = HashMap<String, Long>()
    private val errorLock = NSRecursiveLock()

    private val openLock = Mutex()

    @kotlin.concurrent.Volatile
    private var opened = false

    /** Test seam: how many times the JSword error hook ran (once per registered listener per event, before de-duplication). */
    @kotlin.concurrent.Volatile
    internal var errorHookCalls = 0
        private set

    /** True once [openDatabases] finished; `DatabaseAppSettings` answers defaults before that. */
    val databasesOpen: Boolean get() = opened

    /**
     * Environment + Koin; never touches the databases. MAIN THREAD ONLY (it is not thread-safe). Idempotent: a repeated
     * call reuses the first call's [config] and [extraModules]; different arguments are IGNORED. `SharedAppStartup.start`
     * itself starts Koin and the JSword error listener once.
     */
    fun start(config: IosAppConfig = IosAppConfig(), extraModules: List<Module> = emptyList()) {
        val existing = parts
        val p = existing ?: IosPlatformParts(config).also { parts = it }
        if (existing == null) DatabaseContainer.installHost(IosDatabaseHost(p.environment, p.databaseRoot))

        val localeProvider = IosLocaleProvider(localeTag = p::jswordLocaleTag)
        SharedAppStartup.installEnvironment(p.repositories(), localeProvider)
        SharedAppStartup.start(
            StartupHooks(
                initialiseJSwordFolders = { IosSwordEnvironment.initialiseJSwordFolders(p.directories.modulesDir) },
                koinModules = {
                    val sync = buildIosSyncBindings(iosSyncStrings(p.strings), IosPlatformBootstrap.backgroundRefresh)
                    listOf(buildIosCoreModule(p.config, listOf(sync) + extraModules, p))
                },
                reportJSwordError = { ev -> reportJSwordError(p, ev) },
                isSwordLoaded = { IosSwordEnvironment.isLoaded },
            ),
        )
    }

    /**
     * Opens every database off the main thread (C2): [DatabaseContainer.ready] on, the container built through the
     * host (backup, legacy split, settings load), then each database touched so a corrupt file fails here, not on the
     * first screen. On failure the databases stay closed (`databasesOpen` false, `ready` off) and the error
     * propagates for the startup state to show.
     */
    suspend fun openDatabases() = openLock.withLock {
        if (opened) return@withLock // a repeated or concurrent call after success must not touch `ready` again
        withContext(Dispatchers.IO) {
            DatabaseContainer.ready = true
            try {
                DatabaseContainer.instance.sync()
            } catch (e: Throwable) {
                DatabaseContainer.ready = false
                throw e
            }
        }
        opened = true
    }

    /** Shows a JSword error once per five minutes per message, as Android's `reportJSwordError` does. */
    private fun reportJSwordError(p: IosPlatformParts, ev: ReporterEvent) {
        errorHookCalls++
        val message = when {
            !ev.message.isNullOrEmpty() -> ev.message!!
            !ev.exception?.message.isNullOrEmpty() -> p.strings.fmt("error_occurred_with_link", ev.exception?.message)
            else -> KoinPlatform.getKoin().get<CoreStrings>().errorOccurred
        }
        val now = Clock.System.now().toEpochMilliseconds()
        errorLock.lock()
        val recent = try {
            val last = shownErrors[message]
            shownErrors[message] = now
            last != null && now - last < FIVE_MINUTES_MS
        } finally {
            errorLock.unlock()
        }
        if (recent) return
        KoinPlatform.getKoin().get<UserNotifier>().showError(message, ev.exception)
    }

    /**
     * Test only: forgets everything [start] and [openDatabases] set up (Koin, error listener, open databases,
     * environment monitor) so the next test starts clean. JSword's folders and book list are process-wide and stay.
     */
    internal suspend fun resetForTest() {
        // Stop the app scope first: coroutines of a finished test (store collectors, ordered launches) must not run
        // into the next test's databases.
        runCatching { KoinPlatform.getKoinOrNull()?.getOrNull<net.bible.sharedcore.platform.AppCoroutineScope>()?.cancel() }
        runCatching { stopKoin() }
        SharedAppStartup.resetForTest()
        if (opened) {
            DatabaseContainer.aiLifetime.replace { DatabaseContainer.resetOwned(it) }
        }
        DatabaseContainer.ready = false
        DatabaseContainer.forgetReplacesForTest()
        DatabaseContainer.detachInstanceForTest()
        runCatching { parts?.environment?.close() }.onFailure { Log.w(TAG, "environment close failed", it) }
        parts = null
        opened = false
        errorHookCalls = 0
        errorLock.lock()
        try { shownErrors.clear() } finally { errorLock.unlock() }
    }

    private const val FIVE_MINUTES_MS = 5 * 60 * 1000L
}
