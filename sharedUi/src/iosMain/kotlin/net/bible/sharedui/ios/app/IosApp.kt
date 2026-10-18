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

/** The start route, with the app initialised first when it is READING (the Welcome flow initialises later). */
private suspend fun prepareIosStart(table: StringTable): String {
    val route = resolveIosStartRoute()
    if (route == NavRoutes.READING) {
        withContext(Dispatchers.Default) {
            val platform = IosReadingAppInitPlatform(table)
            ReadingAppInit.prepareData(platform)
            ReadingAppInit.initialise(platform)
        }
    }
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
fun IosApp(strings: Strings, readingGraph: NavGraphBuilder.(NavHostController) -> Unit) {
    val koin = remember { KoinPlatform.getKoin() }
    val table = remember { IosAppStartup.stringTable }
    val dark = remember { koin.get<NightModeState>().nightMode }
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
                IosAppContent(strings, table, route, readingGraph)
            }
        }
    }
}

@Composable
private fun IosAppContent(
    strings: Strings,
    table: StringTable,
    route: String,
    readingGraph: NavGraphBuilder.(NavHostController) -> Unit,
) {
    val koin = remember { KoinPlatform.getKoin() }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val shell = remember { IosHostShell(scope, NavControllerNavigator { navController }) }
    val ports = remember { buildIosHostPorts(shell, koin, table, leaveWelcomeFor(scope, shell, table)) }
    val appHosts = remember { AppHosts(koin, shell, ports, strings) }
    val channels = remember { iosResultChannels { navController } }
    val graphs = remember(navController) { appHosts.graphs(navController, channels) }
    AppRoot(
        navController = navController,
        startRoute = route,
        graphs = graphs,
        readingGraph = { readingGraph(navController) },
        overlays = { appHosts.Overlays() },
    )
}
