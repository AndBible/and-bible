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
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import net.bible.android.control.event.UserMessagesPresenter
import net.bible.service.cloudsync.ios.buildIosSyncBindings
import net.bible.service.db.DatabaseContainer
import net.bible.sharedui.ios.app.buildIosHostBindings
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
import net.bible.sharedlogic.platform.host.HostSwordEnvironment
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
import net.bible.sharedui.host.startup.HostStartupOwner

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
        IosAppEnvironment(
            secrets, isDiscrete = { deviceSettings.getBoolean("discrete_mode", false) },
            initialized = { net.bible.service.startup.ReadingAppInit.initialized },
            deviceSettings = deviceSettings,
        )
    }

    private val documents: Path
        get() = config.root?.let { it / IosAppConfig.DOCUMENTS_DIR } ?: searchPath(NSDocumentDirectory).toPath()
    private val supportRoot: Path
        get() = config.root?.let { it / IosAppConfig.SUPPORT_DIR } ?: (searchPath(NSApplicationSupportDirectory) + "/AndBible").toPath()

    val directories: AppDirectories by lazy { IosAppDirectories(supportRoot, documents) }

    /** Test roots keep the databases below the root; production leaves the choice to [IosAppEnvironment]. */
    val databaseRoot: String? get() = config.root?.let { (it / IosAppConfig.DATABASES_DIR).toString() }

    val bundleResourcePath: String get() = NSBundle.mainBundle.resourcePath.orEmpty()

    /** Initial language (C4); later changes use the same holder, without touching databases. */
    val uiLanguage: String by lazy {
        config.uiLanguage ?: resolveDeviceUiLanguage(deviceSettings, ::iosPreferredLanguages)
    }

    val stringTableHolder by lazy { HostStringTableHolder(hostStringTable(uiLanguage, ::bundleRawResource)) }
    val strings: StringTable get() = stringTableHolder.live

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
 * 1. [ensureStarted] joins IO-owned environment, database host, Koin, JSword folders, error listener and
 *    book-install watcher initialization. It never opens an application database.
 * 2. [openDatabases] opens every database off the main thread, behind the splash/progress state.
 */
object IosAppStartup {
    private var parts: IosPlatformParts? = null
    private val shownErrors = HashMap<String, Long>()
    private val errorLock = NSRecursiveLock()

    private val processScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    // Selected on Main, used only by the process-owned initialization operation on IO.
    private var selectedParts: IosPlatformParts? = null
    private var owner: HostStartupOwner<IosPlatformParts>? = null
    private var userMessages: UserMessagesPresenter? = null

    @kotlin.concurrent.Volatile
    private var opened = false

    /** Test seam: how many times the JSword error hook ran (once per registered listener per event, before de-duplication). */
    @kotlin.concurrent.Volatile
    internal var errorHookCalls = 0
        private set

    /** True once [openDatabases] finished; `DatabaseAppSettings` answers defaults before that. */
    val databasesOpen: Boolean get() = opened

    init {
        // Parts exist only after this object ran, so a HostStartup reader before this point sees "not started" either way.
        HostStartup.register(object : HostStartupInfo {
            override val isStarted: Boolean get() = this@IosAppStartup.isStarted
            override val databasesOpen: Boolean get() = this@IosAppStartup.databasesOpen
            override val stringTable: StringTable get() = this@IosAppStartup.stringTable
        })
    }

    /** True once [ensureStarted] has published the platform parts (a later [resetForTest] clears it again). */
    internal val isStarted: Boolean get() = parts != null

    /** The UI-language string table of the started app (the resource-keyed lookups the hosts read). */
    internal val stringTable: StringTable get() = checkNotNull(parts) { "IosAppStartup.ensureStarted has not completed" }.strings

    /**
     * Joins process-owned initialization. Main only selects the first configuration and publishes ready
     * parts; filesystem, Keychain, JSword and Koin initialization run on IO, independent of either waiter.
     * Repeated/concurrent callers retain the first configuration, including retries after failure.
     */
    suspend fun ensureStarted(config: IosAppConfig = IosAppConfig(), extraModules: List<Module> = emptyList()) {
        val selected = withContext(Dispatchers.Main.immediate) {
            // Subscribe before JSword/Koin initialization: neither the notifier nor a UI exists yet.
            if (userMessages == null) userMessages = UserMessagesPresenter(processScope)
            owner ?: HostStartupOwner(processScope,
                initialize = {
                    val p = selectedParts ?: IosPlatformParts(config).also { selectedParts = it }
                    if (IosDebugLaunchArgs.hasLaunchFlag("-startupUserMessage")) {
                        net.bible.android.control.event.UserMessages.errorNotification(p.strings.text("debug_test_error"), false)
                    }
                    if (IosDebugLaunchArgs.resetData()) IosDebugLaunchArgs.resetAppData()
                    else if (IosDebugLaunchArgs.resetDatabases()) IosDebugLaunchArgs.resetAppDatabases()
                    DatabaseContainer.installHost(IosDatabaseHost(p.environment, p.databaseRoot))
                    val localeProvider = IosLocaleProvider(localeTag = p::jswordLocaleTag)
                    SharedAppStartup.installEnvironment(p.repositories(), localeProvider)
                    SharedAppStartup.start(StartupHooks(
                        initialiseJSwordFolders = { HostSwordEnvironment.initialiseJSwordFolders(p.directories.modulesDir) },
                        koinModules = {
                            val sync = buildIosSyncBindings(hostSyncStrings(p.strings), IosPlatformBootstrap.backgroundRefresh)
                            val namespace = org.koin.dsl.module {
                                IosDebugLaunchArgs.syncNamespace()?.let { value ->
                                    single { net.bible.service.cloudsync.CloudSyncNamespace(value) }
                                }
                            }
                            listOf(buildIosCoreModule(p.config, listOf(sync, namespace, buildIosHostBindings()) + extraModules, p))
                        },
                        reportJSwordError = { ev -> reportJSwordError(p, ev) },
                        isSwordLoaded = { HostSwordEnvironment.isLoaded },
                        isolateKoinPublication = true,
                    ))
                    withContext(Dispatchers.Main.immediate) {
                        userMessages?.attach(KoinPlatform.getKoin().get<UserNotifier>())
                        parts = p
                    }
                    p
                },
                open = {
                    DatabaseContainer.ready = true
                    try { DatabaseContainer.instance.sync() }
                    catch (e: Throwable) { DatabaseContainer.ready = false; throw e }
                    withContext(Dispatchers.Main.immediate) { opened = true }
                },
            ).also { owner = it }
        }
        selected.ensureStarted()
    }

    /**
     * Opens every database off the main thread (C2): [DatabaseContainer.ready] on, the container built through the
     * host (backup, legacy split, settings load), then each database touched so a corrupt file fails here, not on the
     * first screen. On failure the databases stay closed (`databasesOpen` false, `ready` off) and the error
     * propagates for the startup state to show.
     */
    suspend fun openDatabases() {
        ensureStarted()
        val selected = withContext(Dispatchers.Main.immediate) { checkNotNull(owner) }
        selected.openDatabases()
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
     * Test only: forgets everything [ensureStarted] and [openDatabases] set up (Koin, error listener, open databases,
     * environment monitor) so the next test starts clean. JSword's folders and book list are process-wide and stay.
     */
    internal suspend fun resetForTest() {
        val previousOwner = withContext(Dispatchers.Main.immediate) { owner }
        previousOwner?.joinInFlight()
        withContext(Dispatchers.Main.immediate) {
            userMessages?.close()
            userMessages = null
        }
        // Stop the app scope first: coroutines of a finished test (store collectors, ordered launches) must not run
        // into the next test's databases.
        runCatching {
            KoinPlatform.getKoinOrNull()?.getOrNull<net.bible.sharedcore.platform.AppCoroutineScope>()?.let { appScope ->
                appScope.cancel()
                // A coroutine already running on a worker thread survives cancel(); closing Koin and the databases
                // under it races (a Native SIGSEGV, not just a ClosedScopeException), so wait for the children to end.
                withTimeoutOrNull(1_000) { appScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() } }
            }
        }
        // JSword outlives Koin: detach this app's exact listener before its dependencies are closed.
        KoinPlatform.getKoinOrNull()?.getOrNull<net.bible.android.control.versification.BookInstallWatcher>()?.let {
            org.crosswire.jsword.book.Books.installed().removeBooksListener(it.listener)
        }
        runCatching { stopKoin() }
        SharedAppStartup.resetForTest()
        if (opened) {
            DatabaseContainer.aiLifetime.replace { DatabaseContainer.resetOwned(it) }
        }
        DatabaseContainer.ready = false
        DatabaseContainer.forgetReplacesForTest()
        DatabaseContainer.detachInstanceForTest()
        runCatching { parts?.environment?.close() }.onFailure { Log.w(TAG, "environment close failed", it) }
        withContext(Dispatchers.Main.immediate) { owner = null; parts = null }
        selectedParts = null
        opened = false
        errorHookCalls = 0
        errorLock.lock()
        try { shownErrors.clear() } finally { errorLock.unlock() }
    }

    private const val FIVE_MINUTES_MS = 5 * 60 * 1000L
}
