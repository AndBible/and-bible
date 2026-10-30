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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlin.experimental.ExperimentalNativeApi::class)

package net.bible.sharedui.host.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.unit.dp
import net.bible.sharedui.reading.host.LocalReadingTopSafeInset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.page.CurrentPageManager
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowLayout
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.android.control.backup.IncomingBackupDetector
import net.bible.android.control.backup.restorePickedBackup
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.service.cloudsync.host.HostBackupHost
import net.bible.service.cloudsync.host.HostErrorReportHooks
import net.bible.service.startup.ReadingAppInit
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.hasUsableBible
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.app.AppHosts
import net.bible.sharedui.app.AppRoot
import net.bible.sharedui.app.gatedReadingRoute
import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.assembly.HostDebugArgs
import net.bible.sharedui.assembly.HostLifecycleEvent
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.components.AppToastHost
import net.bible.sharedui.components.FailClosedLinkRouting
import net.bible.sharedui.components.LocalSheetDragHandleDescription
import net.bible.sharedui.host.reading.ChooserKind
import net.bible.sharedui.host.reading.ChooserTarget
import net.bible.sharedui.host.reading.HostNightModeObserver
import net.bible.sharedui.host.reading.HostReadingHost
import net.bible.sharedui.host.reading.HostReadingResults
import net.bible.sharedui.host.reading.HostReadingWindows
import net.bible.sharedui.host.reading.askedByActiveWindow
import net.bible.sharedui.host.reading.hostChooserTarget
import net.bible.sharedui.host.reading.hostReadingNavDeps
import net.bible.sharedui.host.reading.rememberIosPaneIcons
import net.bible.sharedui.host.reading.rememberIosReadingIcons
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.navigation.LocalCategoryIcon
import net.bible.sharedui.reading.LocalDrawerRowsGrowWithText
import net.bible.sharedui.reading.LocalPinIcon
import net.bible.sharedui.settings.nav.LocalLiveSettingsLanguage
import net.bible.sharedui.reading.host.LocalReadingPane
import net.bible.sharedui.reading.host.ReadingHost
import net.bible.sharedui.reading.nav.readingNavGraph
import net.bible.sharedui.reading.session.ReadingKeepScreenOn
import net.bible.sharedui.reading.session.guardedChooserTarget
import net.bible.sharedui.reading.session.watchSettingKeys
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.ProvideLanguageLayoutDirection
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedui.theme.AbHostTheme
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.book.Book
import org.koin.core.Koin
import org.koin.mp.KoinPlatform
import net.bible.sharedui.host.ports.HostWelcomeHostPlatform

/**
 * The start route of a cold launch: READING when a usable (unlocked) Bible is installed, else WELCOME, decided by the
 * shared [gatedReadingRoute] gate. Runs off the main thread. It only asks whether a usable Bible exists; it never
 * resolves a default Bible, so a workspace whose Bible was deleted cannot fail here. Nothing is remembered between
 * launches: a process killed during the first download starts at WELCOME again.
 *
 * [bibles] is the seam for tests; the default reads the installed Bibles.
 */
internal suspend fun resolveIosStartRoute(bibles: () -> List<Book> = { SwordDocumentFacade.bibles }): String =
    withContext(Dispatchers.Default) {
        gatedReadingRoute(NavRoutes.READING, { hasUsableBible(bibles()) }) { nsLog("IosApp: $it") }
    }

/** The start route, with reading made ready first when it is READING (the Welcome flow does it on entering reading). */
private suspend fun prepareIosStart(table: StringTable, debugArgs: HostDebugArgs): String {
    val route = when {
        debugArgs.hasLaunchFlag("-task5Settings") -> NavRoutes.SETTINGS
        // H2: the real sync settings route, entered directly as the reading drawer's cloud-sync row will.
        debugArgs.hasLaunchFlag("-h2SyncSettings") -> NavRoutes.SYNC_SETTINGS
        else -> resolveIosStartRoute()
    }
    if (route == NavRoutes.READING) HostReadingReady.ensure(table)
    return route
}

/**
 * Leaving Welcome for [route] (Android `WelcomeFlow.leaveFor`): initialise the app off the main thread, then navigate
 * in-graph. A second tap while it runs is ignored; a failure is logged and leaves Welcome usable.
 */
internal fun leaveWelcomeFor(scope: CoroutineScope, shell: HostNavShell, table: StringTable): (String) -> Unit {
    var leaving = false // main thread only
    return { route ->
        if (!leaving) {
            leaving = true
            scope.launch {
                try {
                    withContext(Dispatchers.Default) { ReadingAppInit.initialise(HostReadingAppInitPlatform(table)) }
                    shell.navigate(route)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    nsLog("IosApp: leaving Welcome failed: ${e.stackTraceToString()}")
                } finally {
                    leaving = false
                }
            }
        }
    }
}

/**
 * The production iOS app root, shown once startup is Ready: resolves the start route behind a splash, then composes
 * the shared [AppRoot] with every feature graph and the reading destination (the iOS reading host). [platform] holds
 * the host's own calls and objects (iOS: `IosAppHostPlatform`).
 */
@Composable
@kotlin.native.HiddenFromObjC
fun HostApp(platform: AppHostPlatform, recoveryStartRoute: String? = null, recoveryDark: Boolean = false, onRecoveryBack: () -> Unit = {}) {
    val koin = remember { KoinPlatform.getKoin() }
    val holder = remember { koin.get<HostStringTableHolder>() }
    val language = holder.current.tag
    val strings = remember(language) { iosStrings(language) }
    val liveStrings = remember { iosStrings { holder.current.tag } }
    // Recovery deliberately stops after Koin-only startup. No night observer, usable-Bible query,
    // reading initialisation, workspace saver or return-to-reading listener is allowed on this path.
    if (recoveryStartRoute != null) {
        check(recoveryStartRoute == NavRoutes.BACKUP && net.bible.sharedui.app.routeStartsUninitialised(recoveryStartRoute))
        platform.TextScale {
          ProvideLanguageLayoutDirection(language) {
            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalSheetDragHandleDescription provides strings.sheetDragHandle,
                LocalLiveSettingsLanguage provides true,
                net.bible.sharedui.readingplan.LocalExplicitReadingPlanTitle provides true,
                LocalCategoryIcon provides { rememberVectorPainter(Icons.Outlined.Book) },
                LocalPinIcon provides { rememberVectorPainter(Icons.Outlined.PushPin) },
            ) {
                AbTheme(darkTheme = recoveryDark) {
                    check(!HostStartup.databasesOpen && !net.bible.service.db.DatabaseContainer.ready)
                    Box(Modifier.fillMaxSize().testTag("recovery-backup-uninitialised")) {
                        HostAppContent(platform, liveStrings, HostStartup.stringTable, recoveryStartRoute, null, holder, onRecoveryBack)
                        platform.DebugFontScaleProbe()
                    }
                }
            }
          }
        }
        return
    }
    val table = remember { HostStartup.stringTable }
    // Colour mode and the animation setting reach the Compose theme through [modesVersion] and every WebView through
    // set_config; a night switch does the same (no reload of the page). Colour mode and animations live in the
    // database-backed settings, whose SettingsStore.changes flow announces writes (no UserDefaults notification).
    val modesVersion = remember { mutableIntStateOf(0) }
    val settingsChanges = remember { hostSettingsChanges() }
    val nightObserver = remember {
        HostNightModeObserver(koin.get<NightModeState>(), platform.displayModeSignals, koin.get<AppSettings>(), settingsChanges) {
            modesVersion.intValue++
            HostReadingWindows.nightModeChanged()
        }
    }
    val appScope = rememberCoroutineScope()
    DisposableEffect(nightObserver) {
        nightObserver.start(appScope)
        nightObserver.refresh()
        onDispose { nightObserver.stop() }
    }
    ReportSystemAppearance(nightObserver)
    val dark by nightObserver.dark
    val startRoute by produceState<String?>(null) {
        value = try {
            prepareIosStart(table, platform.debugArgs)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            nsLog("IosApp: start failed, falling back to Welcome: ${e.stackTraceToString()}")
            NavRoutes.WELCOME
        }
    }
    platform.TextScale {
      ProvideLanguageLayoutDirection(language) {
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalSheetDragHandleDescription provides strings.sheetDragHandle,
            LocalDrawerRowsGrowWithText provides true,
            LocalLiveSettingsLanguage provides true,
            net.bible.sharedui.readingplan.LocalExplicitReadingPlanTitle provides true,
            LocalCategoryIcon provides { rememberVectorPainter(Icons.Outlined.Book) },
            LocalPinIcon provides { rememberVectorPainter(Icons.Outlined.PushPin) },
        ) {
            HostTheme(dark, koin, modesVersion) {
                val route = startRoute
                if (route == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                        platform.DebugFontScaleProbe()
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        HostAppContent(platform, liveStrings, table, route, nightObserver, holder)
                        AppToastHost(Modifier.align(Alignment.BottomCenter))
                        platform.DebugFontScaleProbe()
                    }
                }
            }
        }
      }
    }
}

/** `SettingsStore.changes` of the open settings database (the key of every `AppSettings` write); empty before it exists. */
private fun hostSettingsChanges(): kotlinx.coroutines.flow.Flow<String> =
    try {
        net.bible.service.db.DatabaseContainer.instance.settingsStore.changes
    } catch (e: Exception) {
        kotlinx.coroutines.flow.emptyFlow()
    }

/**
 * Subscribes to [net.bible.service.db.DatabaseContainer.databaseRestored] and, after a database restore replaced the
 * databases under the live window repository, runs [reloadAfterRestore] (iOS twin of Android's
 * `ReadingCommands.applyRestoredDatabase`): reloads the repository from the restored database (lifting the restore
 * save freeze), stores the loaded workspace id under `current_workspace_id`, releases the old window assemblies so
 * the panes re-assemble on the new Window objects, and forces a full reload.
 *
 * Skipped while reading is not ready (no repository yet, Android's `readingAppBootstrapped` gate). It never creates
 * a host and never calls `switchToWorkspace`; a failure is logged and swallowed so it cannot break the restore.
 * Call once per app composition; cancel the returned subscription on dispose.
 */
internal fun installRestoreReload(koin: Koin, scope: CoroutineScope): net.bible.sharedcore.event.Subscription {
    val subscription = net.bible.service.db.DatabaseContainer.databaseRestored.subscribeOnMain {
        scope.launch(Dispatchers.Main) {
            if (!HostReadingReady.isReady) return@launch
            try {
                val repository = koin.get<WindowControl>().windowRepository
                val settings = koin.get<AppSettings>()
                net.bible.android.control.backup.reloadAfterRestore(
                    target = net.bible.android.control.backup.restoreReloadTarget(repository),
                    releaseWindow = { HostReadingWindows.release(it) },
                    setCurrentWorkspaceId = { settings.setString("current_workspace_id", it) },
                )
            } catch (e: Exception) {
                nsLog("IosApp: reload after restore failed: ${e.stackTraceToString()}")
            }
        }
    }
    return subscription
}

/** The workspace colour that seeds the theme, or null before the window repository exists (Welcome) or on a read failure. */
private fun hostWorkspaceSeed(koin: Koin): Int? {
    if (!HostReadingReady.isReady) return null
    return try {
        koin.get<WindowControl>().windowRepository.workspaceSettings.workspaceColor
    } catch (e: Exception) {
        null
    }
}

/**
 * The common host theme ([AbHostTheme]: colour mode, animations, palette style from the app settings, the workspace
 * colour as seed) around the whole app, as Android's `AbAppTheme` does. The seed follows workspace switches and colour
 * edits ([WorkspaceChanges]) and is read once more when reading becomes ready (the first load emits no change that
 * this subscription could have seen).
 */
@Composable
private fun HostTheme(dark: Boolean, koin: Koin, modesVersion: androidx.compose.runtime.IntState, content: @Composable () -> Unit) {
    var seed by remember { mutableStateOf(hostWorkspaceSeed(koin)) }
    DisposableEffect(Unit) {
        val subscription = WorkspaceChanges.changes.subscribeOnMain { seed = hostWorkspaceSeed(koin) }
        onDispose { subscription.cancel() }
    }
    LaunchedEffect(Unit) {
        HostReadingReady.readyFlow.first { it }
        seed = hostWorkspaceSeed(koin)
    }
    val settings = remember { koin.get<AppSettings>() }
    AbHostTheme(darkTheme = dark, settings = settings, isOnyx = false, // workspaceSeed runs in composition: reading modesVersion there makes a mode change recompose the theme, which re-reads the settings.
    workspaceSeed = { modesVersion.intValue; seed }, content = content)
}

@Composable
private fun HostAppContent(
    platform: AppHostPlatform,
    strings: Strings,
    table: StringTable,
    route: String,
    nightObserver: HostNightModeObserver?,
    holder: HostStringTableHolder,
    onRecoveryBack: (() -> Unit)? = null,
) {
    val koin = remember { KoinPlatform.getKoin() }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    // App-wide, not per destination: Android saves on every destination (the repository belongs to the host).
    if (nightObserver != null) SaveWorkspaceOnBackground(koin, platform)
    val shell = remember {
        HostNavShell(scope, NavControllerNavigator { navController }, onRecoveryBack) {
            holder.switchTo(platform.deviceUiLanguage(koin))
        }.also { if (nightObserver != null) HostRootLinks.attachShell(it) }
    }
    DisposableEffect(shell) {
        HostErrorReportHooks.backup = { shell.navigate(NavRoutes.BACKUP) }
        HostErrorReportHooks.report = { cause -> platform.reportBug(table, cause) }
        onDispose {
            HostErrorReportHooks.backup = null
            HostErrorReportHooks.report = null
        }
    }
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val liveWindowSize = androidx.compose.runtime.rememberUpdatedState(windowSize)
    val ports = remember {
        platform.buildHostPorts(shell, koin, table,
            initialiseWelcome = {
                withContext(Dispatchers.Default) { ReadingAppInit.initialise(HostReadingAppInitPlatform(table)) }
            },
            isPortrait = { liveWindowSize.value.let { it.width <= it.height } },
        )
    }
    val appHosts = remember { AppHosts(koin, shell, ports, strings, onReportBug = {
        scope.launch { platform.reportBug(table, null) }
    }) }
    DisposableEffect(appHosts) {
        val backup = ports.backupHost as HostBackupHost
        val restore: () -> Unit = { appHosts.installZip.open() }
        backup.onRestoreModules = restore
        onDispose {
            if (backup.onRestoreModules === restore) backup.onRestoreModules = {}
        }
    }
    LaunchedEffect(shell) {
        if (platform.debugArgs.hasLaunchFlag("-task5Error")) {
            koin.get<net.bible.android.control.backup.BackupHost>().showErrorReport(strings.errorOccurred, IllegalStateException("Task 5 simulator error"))
        }
    }
    val welcome = remember { ports.welcomePlatform() as HostWelcomeHostPlatform }
    SideEffect {
        welcome.openImport = { appHosts.installZip.open() }
        welcome.refreshTextIfCreated()
    }
    val channels = remember { hostResultChannels({ navController }, shell::openReading) }
    val readingToken = remember { Any() }
    val readingResults = remember {
        HostReadingResults(
            keyChooser = channels.keyChooser,
            passage = channels.passage,
            document = channels.document,
            myDocumentPages = channels.myDocumentPages,
            myDocuments = channels.myDocuments,
            bookmark = channels.bookmark,
            readingProgress = channels.readingProgress,
            workspace = channels.workspace,
            // A window closed while its chooser was open is gone: the answer is dropped, never applied to its page manager.
            targetFor = guardedChooserTarget(
                isOpen = { asker ->
                    koin.get<WindowControl>().windowRepository.sortedWindows
                        .any { it.pageManager === asker && it.windowState != WindowLayout.WindowState.CLOSED }
                },
            ) { asker -> (asker as? CurrentPageManager)?.let { hostChooserTarget(koin, table, it, scope) } },
            activeAskerProvider = { koin.get<WindowControl>().activeWindowPageManager },
        )
    }
    val readingHostHolder = remember { ReadingHostHolder(channels.manageLabels, readingResults) }
    DisposableEffect(Unit) { val restoreReload = installRestoreReload(koin, scope); onDispose { restoreReload.cancel() } }
    val debugReadingReady = remember { androidx.compose.runtime.mutableStateOf(false) }
    val language = holder.current.tag
    val graphs = remember(navController, language) { appHosts.graphs(navController, channels) }
    if (nightObserver != null) DisposableEffect(navController) {
        val gate = HostWelcomeReturnGate(
            scope = scope,
            usableBible = { withContext(Dispatchers.Default) { hasUsableBible(SwordDocumentFacade.bibles) } },
            unlockIfNeeded = {
                net.bible.service.sword.unlockLockedBiblesIfNoneUsable(
                    object : net.bible.service.sword.BookUnlocker {
                        override suspend fun unlock(book: Book): Boolean = ports.documentDialogs.awaitUnlock(book.initials)
                    },
                )
            },
            ensureReading = { HostReadingReady.ensure(table) },
            currentRoute = { navController.currentDestination?.route },
            openReading = { shell.openReading() },
            refreshWelcome = {
                nsLog("IosApp: no usable Bible after the flow, staying on Welcome")
                welcome.controller.setProgress(null)
                scope.launch { welcome.refreshInfo() }
            },
            log = { nsLog("IosApp: $it") },
        )
        welcome.afterRestore = gate::afterExternalFlow
        val listener = NavController.OnDestinationChangedListener { controller, destination, _ ->
            shell.routeChanged(destination.route)
            // A chooser left without an answer (Android's cancel arm); an answer published before the pop is still pending.
            if (destination.route?.substringBefore('?') == NavRoutes.READING) readingResults.readingReached()
            gate.onDestination(
                destination.route,
                controller.currentBackStack.value.any { it.destination.route?.substringBefore('?') == NavRoutes.WELCOME },
            )
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    AppRoot(
        navController = navController,
        startRoute = route,
        graphs = graphs,
        readingGraph = {
            readingNavGraph(navController, hostReadingNavDeps(readingToken, shell, table, readingResults.collectors()) {
                if (nightObserver != null) HostReadingDestination(platform, shell, table, strings, koin, nightObserver, readingHostHolder) { debugReadingReady.value = true }
            })
        },
        overlays = {
            appHosts.Overlays()
            platform.DebugHarnessOverlays(strings, navController) { shell.navigate(it) }
            if (platform.debugArgs.task6Controls()) {
                val debug = remember { koin.get<net.bible.sharedui.app.ports.DebugActions>() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    androidx.compose.material3.Surface {
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.TextButton(
                                onClick = { debug.crashAppDelayed() },
                                modifier = Modifier.testTag("task6-crash"),
                            ) { androidx.compose.material3.Text(strings.crashApp) }
                            androidx.compose.material3.TextButton(
                                onClick = { debug.showTestError("") },
                                modifier = Modifier.testTag("task6-error"),
                            ) { androidx.compose.material3.Text(strings.testErrorDialog) }
                        }
                    }
                }
            }
            platform.DebugFeatureRoutes(debugReadingReady.value, navController, channels, koin, appHosts) {
                readingResults.askedByActiveWindow(ChooserKind.Bookmarks)
            }
            HostAppDialogOverlay(
                platform.dialogHost,
                // Alert dialogs may cover sheets; sheet-shaped global requests must exclude reading sheets.
                onSheetOpening = { readingHostHolder.closeSheetsIfCreated() },
            )
        },
    )
    LaunchedEffect(appHosts, navController) {
        // A real back-stack entry proves NavHost attached its graph, not merely that graphs() built deps.
        navController.currentBackStackEntryFlow.first()
        val io = net.bible.sharedui.host.ports.installPipelineIo()
        fun sourceOf(ref: DocumentRef): PickedSource {
            val path = io.filePath(ref.uri)
            return PickedSource(path, path.substringAfterLast('/'), null)
        }
        val router = IncomingFileRouter(
            scope = scope,
            isBackup = { ref ->
                val source = sourceOf(ref)
                val host = koin.get<BackupHost>()
                withContext(Dispatchers.Default) {
                    IncomingBackupDetector.isDatabaseBackup(source.displayName) { host.openInput(source) }
                }
            },
            restore = { ref ->
                val source = sourceOf(ref)
                val host = koin.get<BackupHost>()
                // The Welcome screen's restore, minus its picker: errors are reported and Welcome is released the same way.
                restorePickedBackup(
                    pick = { source },
                    restore = { koin.get<BackupControl>().restoreAppDatabaseFromSourceWithUI(host, it) },
                    reportError = { message, cause -> host.showErrorReport(message, cause) },
                    fallbackError = { koin.get<net.bible.sharedcore.platform.CoreStrings>().errorOccurred },
                    afterRestore = { afterIncomingRestore(navController.currentDestination?.route, welcome.afterRestore) },
                )
            },
            install = { refs, complete ->
                appHosts.installZip.open(
                    action = net.bible.android.view.activity.installzip.INSTALL_ZIP_ACTION_VIEW,
                    uris = refs,
                    onResult = { complete() },
                )
            },
        )
        platform.receiveIncomingFiles(
            open = { refs, complete -> router.route(refs, complete) },
            onFailure = {
                koin.get<net.bible.sharedcore.platform.UserNotifier>()
                    .showError(koin.get<net.bible.sharedcore.platform.CoreStrings>().errorOccurred)
            },
        )
    }
}

/** Holds the one [HostReadingHost] for the app's lifetime so leaving and re-entering reading keeps the sessions. */
private class ReadingHostHolder(
    private val labelResults: NavResultChannel<ManageLabelsResult>,
    private val readingResults: HostReadingResults,
) {
    private var host: HostReadingHost? = null

    /** The app's current strings: the host reads them per use, so a language change shows without rebuilding the host. */
    private var currentStrings: Strings? = null

    fun updateStrings(strings: Strings) {
        currentStrings = strings
    }

    fun closeSheetsIfCreated() { host?.hostState?.closeModalOverlays() }

    fun get(platform: AppHostPlatform, shell: HostNavShell, koin: Koin, nightObserver: HostNightModeObserver, strings: Strings): HostReadingHost =
        host ?: HostReadingHost(
            HostReadingReady.scope, shell, koin, platform.readingHost, koin.get<NightModeState>(), nightObserver.dark, nightObserver.monochrome,
            { currentStrings ?: strings },
            labelResults = labelResults,
            readingResults = readingResults,
        ).also {
            host = it
            HostRootLinks.attachReading(it.navigator, it::openSearchStrongs)
            HostReadingWindows.bind(it, HostReadingReady.scope, koin, koin.get<NightModeState>(), platform.paneEngine)
        }
}

/**
 * The reading destination's content: a spinner until [HostReadingReady] has run (the Welcome path gets here before
 * it), then the shared [ReadingHost] with the iOS pane factory. A failure is logged and leaves the spinner (nothing
 * to retry from here).
 */
@Composable
private fun HostReadingDestination(
    platform: AppHostPlatform,
    shell: HostNavShell,
    table: StringTable,
    strings: Strings,
    koin: Koin,
    nightObserver: HostNightModeObserver,
    holder: ReadingHostHolder,
    onDebugReady: () -> Unit,
) {
    val ready by produceState<Boolean?>(null) {
        value = try {
            HostReadingReady.ensure(table)
            true
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            nsLog("IosApp: reading not ready: ${e.stackTraceToString()}")
            false
        }
    }
    if (ready == false) {
        // Reading cannot start: leave to Welcome (honest and recoverable) instead of an endless spinner.
        LaunchedEffect(Unit) { shell.showWelcome() }
    }
    if (ready != true) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    // Debug-only `-openRef <osisRef>` (no-op in release / without the argument). Do not remove: AppUITests'
    // lifecycle tests launch with it and assert John 3 before saving and relaunching their own workspace.
    LaunchedEffect(Unit) {
        platform.debugArgs.openRefOnce()
        onDebugReady()
    }
    holder.updateStrings(strings)
    val host = remember { holder.get(platform, shell, koin, nightObserver, strings) }
    val icons = rememberIosReadingIcons()
    val paneIcons = rememberIosPaneIcons()
    val deps = remember(host) { host.deps(icons, paneIcons) }
    val paneFactory = remember { platform.readingPaneFactory() }
    // The host theme wraps the whole app; the reading view adds the fail-closed link routing around its HTML text.
    MeasureReadingOffsets(host)
    HideStatusBarInFullScreen(host)
    KeepScreenOnWhileReading(koin, remember { hostSettingsChanges() }, platform)
    TiltScrollWhileReading(koin, platform)
    FailClosedLinkRouting(koin.get<ExternalLinks>()) {
        val density = LocalDensity.current
        val topInset = if (host.fullScreen.state.value) with(density) { WindowInsets.safeDrawing.getTop(density).toDp() } else 0.dp
        CompositionLocalProvider(LocalReadingPane provides paneFactory, LocalReadingTopSafeInset provides topInset) {
            ReadingHost(deps, Modifier.testTag("reading-host"))
        }
    }
}

/**
 * Feeds the real layout into the document offsets: the bottom safe-area inset (home indicator) in dp = CSS px, read from
 * the same `WindowInsets.navigationBars` the reading rail pads itself with. The other terms (search sheet, agent log,
 * speak bar) are reported by the host's own slots; the host pushes `set_offsets` to every window when the sum changes,
 * and when the container size changes (rotation: the bottom window can change while the inset stays the same).
 */
@Composable
private fun MeasureReadingOffsets(host: HostReadingHost) {
    val density = LocalDensity.current
    val bottomPx = WindowInsets.navigationBars.getBottom(density)
    val containerSize = LocalWindowInfo.current.containerSize
    // Full screen removes the toolbar that pads the top; the notch / island safe area stays (see ReadingOffsets.top).
    val topPx = if (host.fullScreen.state.value) WindowInsets.safeDrawing.getTop(density) else 0
    SideEffect {
        host.offsets.setSystemTopInset(topPx / density.density)
        host.offsets.setSystemBottomInset(bottomPx / density.density)
        host.offsets.setContainerSize(containerSize.width, containerSize.height)
    }
}

/**
 * The status bar follows full screen only while the reading route is composed: other screens reached from full screen
 * (settings, search results) get it back, and returning to reading hides it again if full screen is still on.
 */
@Composable
private fun HideStatusBarInFullScreen(host: HostReadingHost) {
    val fullScreen = host.fullScreen.state.value
    DisposableEffect(fullScreen) {
        host.platform.setStatusBarHidden(fullScreen)
        onDispose { host.platform.setStatusBarHidden(false) }
    }
}

/**
 * Keep-screen-on (`screen_keep_on_pref`) only while the reading route is composed and the app is active:
 * `idleTimerDisabled` is set from [ReadingKeepScreenOn] and reset on leaving reading and when backgrounding.
 */
@Composable
private fun KeepScreenOnWhileReading(koin: Koin, settingsChanges: kotlinx.coroutines.flow.Flow<String>, platform: AppHostPlatform) {
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val settings = koin.get<AppSettings>()
        val keepOn = ReadingKeepScreenOn(
            read = { settings.getBoolean("screen_keep_on_pref", false) },
            apply = { platform.setIdleTimerDisabled(it) },
        )
        val stopLifecycle = platform.lifecycle.observe(listOf(HostLifecycleEvent.Active, HostLifecycleEvent.ResignActive)) {
            keepOn.setActive(it == HostLifecycleEvent.Active)
        }
        // screen_keep_on_pref lives in the database-backed settings: its writes arrive through SettingsStore.changes.
        val watch = watchSettingKeys(settingsChanges, setOf("screen_keep_on_pref"), scope) { keepOn.refresh() }
        keepOn.setReading(true)
        onDispose {
            stopLifecycle()
            watch.cancel()
            keepOn.setReading(false)
        }
    }
}

/**
 * Tilt scroll (`enableTiltToScroll`) only while the reading route is composed and the app is active: the motion
 * updates stop when the app resigns active / backgrounds and restart (with a fresh neutral pose) on becoming active.
 */
@Composable
private fun TiltScrollWhileReading(koin: Koin, platform: AppHostPlatform) {
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val tilt = platform.newTiltScroll(koin)
        val stopLifecycle = platform.lifecycle.observe(
            listOf(HostLifecycleEvent.Active, HostLifecycleEvent.ResignActive, HostLifecycleEvent.Background),
        ) { tilt.setAppActive(it == HostLifecycleEvent.Active) }
        tilt.start(scope)
        onDispose {
            stopLifecycle()
            tilt.stop()
        }
    }
}

/**
 * Android saves the workspace in `onPause`; iOS may kill a backgrounded app, so the same save runs when the app
 * resigns active / enters the background, inside a background task so the writes finish before suspension.
 */
@Composable
private fun SaveWorkspaceOnBackground(koin: Koin, platform: AppHostPlatform) {
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val save: () -> Unit = {
            var task = platform.invalidBackgroundTaskId
            fun endTask() {
                if (task != platform.invalidBackgroundTaskId) {
                    val id = task
                    task = platform.invalidBackgroundTaskId
                    platform.endBackgroundTask(id)
                }
            }
            task = platform.beginBackgroundTask { endTask() }
            scope.launch {
                try {
                    HostWorkspaceSaver.saveNow(koin)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    nsLog("IosApp: saving the workspace failed: ${e.stackTraceToString()}")
                } finally {
                    endTask()
                }
            }
        }
        val stopLifecycle = platform.lifecycle.observe(listOf(HostLifecycleEvent.ResignActive, HostLifecycleEvent.Background)) { save() }
        onDispose { stopLifecycle() }
    }
}

/**
 * Reports a system appearance change to [observer] while the app is in the foreground (a change while backgrounded is
 * picked up by the observer's own become-active refresh). The observer decides whether anything changed and, if so,
 * re-sends the page config without a reload.
 */
@Composable
private fun ReportSystemAppearance(observer: HostNightModeObserver) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    LaunchedEffect(systemDark) { observer.refresh() }
}
