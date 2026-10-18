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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

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
import androidx.compose.runtime.DisposableEffect
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
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.navigation.LocalCategoryIcon
import net.bible.sharedui.reading.LocalPinIcon
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.book.Book
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
    val route = resolveIosStartRoute()
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
 * the shared [AppRoot] with every feature graph; [readingGraph] is the reading destination slot.
 */
@Composable
@kotlin.native.HiddenFromObjC
fun IosApp(strings: Strings) {
    val koin = remember { KoinPlatform.getKoin() }
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
                IosAppContent(strings, table, route, nightObserver)
            }
        }
    }
}

@Composable
private fun IosAppContent(
    strings: Strings,
    table: StringTable,
    route: String,
    nightObserver: IosNightModeObserver,
) {
    val koin = remember { KoinPlatform.getKoin() }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val shell = remember { IosHostShell(scope, NavControllerNavigator { navController }) }
    val ports = remember { buildIosHostPorts(shell, koin, table, leaveWelcomeFor(scope, shell, table)) }
    val appHosts = remember { AppHosts(koin, shell, ports, strings) }
    val channels = remember { iosResultChannels { navController } }
    val readingToken = remember { Any() }
    val readingHostHolder = remember { ReadingHostHolder() }
    val graphs = remember(navController) { appHosts.graphs(navController, channels) }
    DisposableEffect(navController) {
        val gate = IosWelcomeReturnGate(
            scope = scope,
            usableBible = { withContext(Dispatchers.Default) { hasUsableBible(SwordDocumentFacade.bibles) } },
            ensureReading = { IosReadingReady.ensure(table) },
            currentRoute = { navController.currentDestination?.route },
            openReading = { shell.openReading() },
            refreshWelcome = { nsLog("IosApp: no usable Bible after the flow, staying on Welcome") },
            log = { nsLog("IosApp: $it") },
        )
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
                IosReadingDestination(shell, table, koin, nightObserver, readingHostHolder)
            })
        },
        overlays = { appHosts.Overlays() },
    )
}

/** Holds the one [IosReadingHost] for the app's lifetime so leaving and re-entering reading keeps the sessions. */
private class ReadingHostHolder {
    private var host: IosReadingHost? = null

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
) {
    val ready by produceState(false) {
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
    if (!ready) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { net.bible.sharedui.assembly.IosDebugLaunchArgs.openRefOnce() }
    val host = remember { holder.get(shell, koin, nightObserver) }
    val icons = rememberIosReadingIcons()
    val deps = remember(host) { host.deps(icons) }
    val paneFactory = remember { iosReadingPaneFactory(IosReadingWindows::sessionFor, IosReadingWindows::bridgeFor, { IosReadingWindows.nightMode }, IosReadingWindows::isOpen) }
    CompositionLocalProvider(LocalReadingPane provides paneFactory) {
        ReadingHost(deps, Modifier.testTag("reading-host"))
    }
}
