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

package net.bible.sharedui.installzip.nav

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import kotlinx.coroutines.flow.StateFlow
import net.bible.sharedcore.nav.InstallZipResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.installzip.InstallZipContent
import net.bible.sharedui.nav.NavResultChannel

/** One InstallZip entry's state machine, as the destination drives it; `:app`'s `InstallZipFlow` implements it. */
interface InstallZipSession {
    val state: StateFlow<InstallUiState?>
    fun start()
    fun confirm()
    fun dismiss()
    fun back()
}

/**
 * What the InstallZip destination needs from its host (slice 8 §3.2).
 *
 * [installZipResults] exits the host WITH a result when this is the start destination (the external
 * redirect's entry), and publishes + pops when an in-graph caller opened it -- `NavResultChannel`'s two
 * shapes. [lockOrientation] returns the restore lambda (classic's manifest portrait lock, now
 * destination-scoped; spec §3.2 keeps it purely to preserve behaviour, since the host absorbs rotation).
 * [hostBound]/[hostUnbound] are `DocumentInstallService`'s "a host UI is showing the ask-backs" pair, which
 * the Activity called from `onStart`/`onStop`.
 */
class InstallZipNavDeps(
    val setWindowTitle: (String) -> Unit,
    val windowTitle: String,
    val installZipResults: NavResultChannel<InstallZipResult>,
    val sessionFor: (action: String?, uris: List<String>, onFinished: (InstallZipResult) -> Unit) -> InstallZipSession,
    val lockOrientation: () -> (() -> Unit),
    val hostBound: () -> Unit,
    val hostUnbound: () -> Unit,
)

fun NavGraphBuilder.installZipNavGraph(navController: NavHostController, deps: InstallZipNavDeps) {
    composable(
        route = NavRoutes.INSTALL_ZIP_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_INSTALL_ACTION) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_INSTALL_URIS) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        // Read plainly: the library has already decoded each argument once; decodeInstallZipUris undoes
        // installZip()'s per-URI encoding.
        val action = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_INSTALL_ACTION) }
        val uris = NavRoutes.decodeInstallZipUris(backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_INSTALL_URIS) })

        val session = remember(backStackEntry) {
            deps.sessionFor(action, uris) { result -> deps.installZipResults.deliver(navController, result) }
        }

        LaunchedEffect(deps.windowTitle) { deps.setWindowTitle(deps.windowTitle) }
        LaunchedEffect(session) { session.start() }
        DisposableEffect(Unit) {
            val restore = deps.lockOrientation()
            onDispose { restore() }
        }
        LifecycleStartEffect(Unit) {
            deps.hostBound()
            onStopOrDispose { deps.hostUnbound() }
        }
        PlatformBackHandler(enabled = true) { session.back() }

        val state by session.state.collectAsState()
        state?.let { InstallZipContent(state = it, onConfirm = session::confirm, onDismiss = session::dismiss) }
    }
}
