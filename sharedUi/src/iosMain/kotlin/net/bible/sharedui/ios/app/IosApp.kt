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

package net.bible.sharedui.ios.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.testTag
import net.bible.sharedui.ios.reading.IosNightModeObserver
import net.bible.sharedui.ios.reading.IosReadingHost
import net.bible.sharedui.ios.reading.IosReadingWindows
import net.bible.sharedui.ios.reading.iosReadingNavDeps
import net.bible.sharedui.ios.reading.rememberIosReadingIcons
import net.bible.sharedui.reading.host.LocalReadingPane
import net.bible.sharedui.reading.host.ReadingHost
import net.bible.sharedui.reading.nav.readingNavGraph
import net.bible.sharedui.webview.iosReadingPaneFactory
import org.koin.core.Koin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.bible.service.startup.ReadingAppInit
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.hasUsableBible
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.app.AppHosts
import net.bible.sharedui.app.AppRoot
import net.bible.sharedui.app.gatedReadingRoute
import net.bible.sharedui.assembly.IosStringTableHolder
import net.bible.sharedui.assembly.resolveDeviceUiLanguage
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosDebugLaunchArgs
import net.bible.sharedui.navigation.LocalCategoryIcon
import net.bible.sharedui.reading.LocalPinIcon
import net.bible.sharedui.settings.nav.LocalLiveSettingsLanguage
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.book.Book
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNotification
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIBackgroundTaskInvalid
import org.koin.mp.KoinPlatform

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
private suspend fun prepareIosStart(table: StringTable): String {
    val route = if (kotlin.native.Platform.isDebugBinary && "-task5Settings" in platform.Foundation.NSProcessInfo.processInfo.arguments)
        NavRoutes.SETTINGS else resolveIosStartRoute()
    if (route == NavRoutes.READING) IosReadingReady.ensure(table)
    return route
}

/**
 * Leaving Welcome for [route] (Android `WelcomeFlow.leaveFor`): initialise the app off the main thread, then navigate
 * in-graph. A second tap while it runs is ignored; a failure is logged and leaves Welcome usable.
 */
internal fun leaveWelcomeFor(scope: CoroutineScope, shell: IosHostShell, table: StringTable): (String) -> Unit {
    var leaving = false // main thread only
    return { route ->
        if (!leaving) {
            leaving = true
            scope.launch {
                try {
                    withContext(Dispatchers.Default) { ReadingAppInit.initialise(IosReadingAppInitPlatform(table)) }
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
 * the shared [AppRoot] with every feature graph and the reading destination (the iOS reading host).
 */
@Composable
@kotlin.native.HiddenFromObjC
fun IosApp(recoveryStartRoute: String? = null, recoveryDark: Boolean = false, onRecoveryBack: () -> Unit = {}) {
    val koin = remember { KoinPlatform.getKoin() }
    val holder = remember { koin.get<IosStringTableHolder>() }
    val language = holder.current.tag
    val strings = remember(language) { iosStrings(language) }
    val liveStrings = remember { iosStrings { holder.current.tag } }
    // Recovery deliberately stops after Koin-only startup. No night observer, usable-Bible query,
    // reading initialisation, workspace saver or return-to-reading listener is allowed on this path.
    if (recoveryStartRoute != null) {
        check(recoveryStartRoute == NavRoutes.BACKUP && net.bible.sharedui.app.routeStartsUninitialised(recoveryStartRoute))
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalLiveSettingsLanguage provides true,
            net.bible.sharedui.readingplan.LocalExplicitReadingPlanTitle provides true,
            LocalCategoryIcon provides { rememberVectorPainter(Icons.Outlined.Book) },
            LocalPinIcon provides { rememberVectorPainter(Icons.Outlined.PushPin) },
        ) {
            AbTheme(darkTheme = recoveryDark) {
                check(!IosAppStartup.databasesOpen && !net.bible.service.db.DatabaseContainer.ready)
                Box(Modifier.fillMaxSize().testTag("recovery-backup-uninitialised")) {
                    IosAppContent(liveStrings, IosAppStartup.stringTable, recoveryStartRoute, null, holder, onRecoveryBack)
                }
            }
        }
        return
    }
    val table = remember { IosAppStartup.stringTable }
    val nightObserver = remember { IosNightModeObserver(koin.get<NightModeState>()) { IosReadingWindows.nightModeChanged() } }
    DisposableEffect(nightObserver) {
        nightObserver.start()
        nightObserver.refresh()
        onDispose { nightObserver.stop() }
    }
    val dark by nightObserver.dark
    val startRoute by produceState<String?>(null) {
        value = try {
            prepareIosStart(table)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Throwable) {
            nsLog("IosApp: start failed, falling back to Welcome: ${e.stackTraceToString()}")
            NavRoutes.WELCOME
        }
    }
    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLiveSettingsLanguage provides true,
        net.bible.sharedui.readingplan.LocalExplicitReadingPlanTitle provides true,
        LocalCategoryIcon provides { rememberVectorPainter(Icons.Outlined.Book) },
        LocalPinIcon provides { rememberVectorPainter(Icons.Outlined.PushPin) },
    ) {
        AbTheme(darkTheme = dark) {
            val route = startRoute
            if (route == null) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                IosAppContent(liveStrings, table, route, nightObserver, holder)
            }
        }
    }
}

@Composable
private fun IosAppContent(
    strings: Strings,
    table: StringTable,
    route: String,
    nightObserver: IosNightModeObserver?,
    holder: IosStringTableHolder,
    onRecoveryBack: (() -> Unit)? = null,
) {
    val koin = remember { KoinPlatform.getKoin() }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    // App-wide, not per destination: Android saves on every destination (the repository belongs to the host).
    if (nightObserver != null) SaveWorkspaceOnBackground(koin)
    val shell = remember {
        IosHostShell(scope, NavControllerNavigator { navController }, onRecoveryBack) {
            holder.switchTo(resolveDeviceUiLanguage(koin.get<IosDeviceSettings>()))
        }
    }
    DisposableEffect(shell) {
        net.bible.service.cloudsync.ios.IosErrorReportHooks.backup = { shell.navigate(NavRoutes.BACKUP) }
        net.bible.service.cloudsync.ios.IosErrorReportHooks.report = { cause -> net.bible.sharedui.ios.report.IosBugReport(table).report(cause) }
        onDispose {
            net.bible.service.cloudsync.ios.IosErrorReportHooks.backup = null
            net.bible.service.cloudsync.ios.IosErrorReportHooks.report = null
        }
    }
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val liveWindowSize = androidx.compose.runtime.rememberUpdatedState(windowSize)
    val ports = remember {
        buildIosHostPorts(shell, koin, table,
            initialiseWelcome = {
                withContext(Dispatchers.Default) { ReadingAppInit.initialise(IosReadingAppInitPlatform(table)) }
            },
            isPortrait = { liveWindowSize.value.let { it.width <= it.height } },
        )
    }
    val appHosts = remember { AppHosts(koin, shell, ports, strings, onReportBug = {
        scope.launch { net.bible.sharedui.ios.report.IosBugReport(table).report() }
    }) }
    DisposableEffect(appHosts) {
        val backup = ports.backupHost as net.bible.service.cloudsync.ios.IosBackupHost
        val restore: () -> Unit = { appHosts.installZip.open() }
        backup.onRestoreModules = restore
        onDispose {
            if (backup.onRestoreModules === restore) backup.onRestoreModules = {}
        }
    }
    LaunchedEffect(shell) {
        if (kotlin.native.Platform.isDebugBinary && "-task5Error" in platform.Foundation.NSProcessInfo.processInfo.arguments) {
            koin.get<net.bible.android.control.backup.BackupHost>().showErrorReport(strings.errorOccurred, IllegalStateException("Task 5 simulator error"))
        }
    }
    val welcome = remember { ports.welcomePlatform() as net.bible.sharedui.ios.ports.IosWelcomeHostPlatform }
    SideEffect {
        welcome.openImport = { appHosts.installZip.open() }
        welcome.refreshTextIfCreated()
    }
    val channels = remember { iosResultChannels { navController } }
    val readingToken = remember { Any() }
    val readingHostHolder = remember { ReadingHostHolder() }
    val debugReadingReady = remember { androidx.compose.runtime.mutableStateOf(false) }
    val language = holder.current.tag
    val graphs = remember(navController, language) { appHosts.graphs(navController, channels) }
    if (nightObserver != null) DisposableEffect(navController) {
        val gate = IosWelcomeReturnGate(
            scope = scope,
            usableBible = { withContext(Dispatchers.Default) { hasUsableBible(SwordDocumentFacade.bibles) } },
            unlockIfNeeded = {
                net.bible.service.sword.unlockLockedBiblesIfNoneUsable(
                    object : net.bible.service.sword.BookUnlocker {
                        override suspend fun unlock(book: Book): Boolean = ports.documentDialogs.awaitUnlock(book.initials)
                    },
                )
            },
            ensureReading = { IosReadingReady.ensure(table) },
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
            readingNavGraph(navController, iosReadingNavDeps(readingToken, shell, table) {
                if (nightObserver != null) IosReadingDestination(shell, table, koin, nightObserver, readingHostHolder) { debugReadingReady.value = true }
            })
        },
        overlays = {
            appHosts.Overlays()
            net.bible.sharedui.ios.files.IosFilesRuntimeHarness.Overlay(strings) {
                navController.currentBackStackEntryFlow.first()
            }
            val incomingEntry by navController.currentBackStackEntryFlow.collectAsState(initial = null)
            net.bible.sharedui.ios.files.IosIncomingRuntimeHarness.Overlay(
                strings,
                backupRoute = incomingEntry?.destination?.route == NavRoutes.BACKUP,
                openBackup = { shell.navigate(NavRoutes.BACKUP) },
            )
            net.bible.sharedui.ios.files.IosFilesFeatureHarness.Overlay(
                strings, incomingEntry?.destination?.route,
                awaitGraph = { navController.currentBackStackEntryFlow.first() },
                navigate = { shell.navigate(it) },
            )
            if (IosDebugLaunchArgs.task6Controls()) {
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
            IosDebugFeatureRoutes(debugReadingReady.value, navController, channels, koin, appHosts)
            IosAppDialogOverlay(
                // Alert dialogs may cover sheets; sheet-shaped global requests must exclude reading sheets.
                onSheetOpening = { readingHostHolder.closeSheetsIfCreated() },
            )
        },
    )
    LaunchedEffect(appHosts, navController) {
        // A real back-stack entry proves NavHost attached its graph, not merely that graphs() built deps.
        navController.currentBackStackEntryFlow.first()
        if (!net.bible.sharedui.ios.files.IosIncomingRuntimeHarness.preflightBeforeBind()) return@LaunchedEffect
        val owner = net.bible.sharedui.ios.files.IosIncomingFiles.bind(
            open = { refs, complete ->
                appHosts.installZip.open(
                    action = net.bible.android.view.activity.installzip.INSTALL_ZIP_ACTION_VIEW,
                    uris = refs,
                    onResult = { complete() },
                )
            },
            onFailure = {
                koin.get<net.bible.sharedcore.platform.UserNotifier>()
                    .showError(koin.get<net.bible.sharedcore.platform.CoreStrings>().errorOccurred)
            },
        )
        try { kotlinx.coroutines.awaitCancellation() }
        finally { net.bible.sharedui.ios.files.IosIncomingFiles.detach(owner) }
    }
}

/** Holds the one [IosReadingHost] for the app's lifetime so leaving and re-entering reading keeps the sessions. */
private class ReadingHostHolder {
    private var host: IosReadingHost? = null

    fun closeSheetsIfCreated() { host?.hostState?.closeModalOverlays() }

    fun get(shell: IosHostShell, koin: Koin, nightObserver: IosNightModeObserver): IosReadingHost =
        host ?: IosReadingHost(
            IosReadingReady.scope, shell, koin, koin.get<NightModeState>(), nightObserver.dark,
        ).also {
            host = it
            IosReadingWindows.bind(it, IosReadingReady.scope, koin, koin.get<NightModeState>())
        }
}

/**
 * The reading destination's content: a spinner until [IosReadingReady] has run (the Welcome path gets here before
 * it), then the shared [ReadingHost] with the iOS pane factory. A failure is logged and leaves the spinner (nothing
 * to retry from here).
 */
@Composable
private fun IosReadingDestination(
    shell: IosHostShell,
    table: StringTable,
    koin: Koin,
    nightObserver: IosNightModeObserver,
    holder: ReadingHostHolder,
    onDebugReady: () -> Unit,
) {
    val ready by produceState<Boolean?>(null) {
        value = try {
            IosReadingReady.ensure(table)
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
        IosDebugLaunchArgs.openRefOnce()
        onDebugReady()
    }
    val host = remember { holder.get(shell, koin, nightObserver) }
    val icons = rememberIosReadingIcons()
    val deps = remember(host) { host.deps(icons) }
    val paneFactory = remember { iosReadingPaneFactory(IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor, { IosReadingWindows.nightMode }, IosReadingWindows::isOpen) }
    CompositionLocalProvider(LocalReadingPane provides paneFactory) {
        ReadingHost(deps, Modifier.testTag("reading-host"))
    }
}

/**
 * Android saves the workspace in `onPause`; iOS may kill a backgrounded app, so the same save runs when the app
 * resigns active / enters the background, inside a background task so the writes finish before suspension.
 */
@Composable
private fun SaveWorkspaceOnBackground(koin: Koin) {
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val save: (NSNotification?) -> Unit = {
            val app = UIApplication.sharedApplication
            var task = UIBackgroundTaskInvalid
            fun endTask() {
                if (task != UIBackgroundTaskInvalid) {
                    val id = task
                    task = UIBackgroundTaskInvalid
                    app.endBackgroundTask(id)
                }
            }
            task = app.beginBackgroundTaskWithExpirationHandler { endTask() }
            scope.launch {
                try {
                    IosWorkspaceSaver.saveNow(koin)
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    nsLog("IosApp: saving the workspace failed: ${e.stackTraceToString()}")
                } finally {
                    endTask()
                }
            }
        }
        val observers = listOf(UIApplicationWillResignActiveNotification, UIApplicationDidEnterBackgroundNotification)
            .map { center.addObserverForName(it, null, NSOperationQueue.mainQueue, save) }
        onDispose { observers.forEach { center.removeObserver(it) } }
    }
}
