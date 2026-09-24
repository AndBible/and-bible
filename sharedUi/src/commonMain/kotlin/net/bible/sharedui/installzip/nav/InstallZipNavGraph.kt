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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavBackStackEntry
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

/**
 * One InstallZip entry's state machine, as the destination drives it; `:app`'s `InstallZipFlow` implements it.
 * It lives as long as its NavBackStackEntry ([InstallZipEntrySessions]), not as long as a composition.
 */
interface InstallZipSession {
    val state: StateFlow<InstallUiState?>
    fun start()
    fun confirm()
    fun dismiss()
    fun back()

    /**
     * The entry is gone (popped by anyone, or the host destroyed): stop everything this session runs and
     * never answer. A running install keeps running in the service, as after [back].
     */
    fun close()

    /**
     * Called by the destination immediately before this session's answer is DELIVERED through
     * [InstallZipNavDeps.installZipResults]. That is at once while the entry is current, or later when a
     * covered entry is on top again, and never if the entry leaves first. The host hands the opener's
     * callback back here, so a held answer that is never delivered leaves nothing behind.
     */
    fun onDelivering() {}
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
) {
    /** Host-lifetime (these deps are `remember`ed by the host): outlives any one composition of an entry. */
    internal val entrySessions = InstallZipEntrySessions()
}

/**
 * One [InstallZipSession] per InstallZip NavBackStackEntry OBJECT (slice 8 Task D1).
 *
 * Navigation disposes a destination's composition when another destination covers it and recomposes it on
 * return; a session tied to that composition would be recreated and `start()` again -- re-enqueueing a
 * SEND's files. So the session is created once per entry and closed only when the entry's own Lifecycle
 * reaches DESTROYED: popped by anything (its own answer, an outside `popBackStack`/`popUpTo`) or the host
 * finishing. Never on composition dispose.
 *
 * Keyed by object identity, NOT by `entry.id`: a `launchSingleTop` navigate onto a current InstallZip (a
 * second share while one is on screen) replaces the top entry with a NEW object carrying the SAME id and the
 * new arguments. The old object is NOT moved to DESTROYED (measured on navigation 2.9.2: it stays at its
 * last state), so its observer would never fire. Fix round 2 ruling: the re-share replaces the session, as
 * classic started a fresh Activity per share -- so the first time a new object with a held object's id
 * asks for a session, the held one is closed and forgotten (an install it started keeps running in the
 * service) and the new object gets its own session, which enqueues its URIs.
 *
 * Its answer leaves through ITS OWN entry only: delivered at once while that entry is the current one; held
 * while something covers it and delivered when the entry is resumed again; dropped if the entry is gone. It
 * never pops "whatever is current".
 */
internal class InstallZipEntrySessions {

    private class Held(val entry: NavBackStackEntry, val session: InstallZipSession) {
        var pending: InstallZipResult? = null
        lateinit var observer: LifecycleEventObserver

        fun release() {
            entry.lifecycle.removeObserver(observer)
            session.close()
        }
    }

    private val live = mutableListOf<Held>()

    private fun heldFor(entry: NavBackStackEntry): Held? = live.firstOrNull { it.entry === entry }

    fun sessionFor(
        entry: NavBackStackEntry,
        navController: NavHostController,
        deps: InstallZipNavDeps,
        action: String?,
        uris: List<String>,
    ): InstallZipSession {
        heldFor(entry)?.let { return it.session }
        live.filter { it.entry.id == entry.id }.forEach { replaced ->
            live.remove(replaced)
            replaced.release()
        }
        lateinit var held: Held
        val session = deps.sessionFor(action, uris) onFinished@{ result ->
            if (heldFor(entry) !== held) return@onFinished // entry gone: closed, answer nothing
            if (navController.currentBackStackEntry === entry) {
                held.session.onDelivering()
                deps.installZipResults.deliver(navController, result)
            } else {
                held.pending = result // covered: answer when this entry is on top again
            }
        }
        held = Held(entry, session)
        if (entry.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            session.close()
            return session
        }
        live += held
        held.observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> held.pending?.let { result ->
                    held.pending = null
                    if (navController.currentBackStackEntry === entry) {
                        held.session.onDelivering()
                        deps.installZipResults.deliver(navController, result)
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    live.remove(held)
                    held.release()
                }
                else -> Unit
            }
        }
        entry.lifecycle.addObserver(held.observer)
        return session
    }
}

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
            deps.entrySessions.sessionFor(backStackEntry, navController, deps, action, uris)
        }

        LaunchedEffect(deps.windowTitle) { deps.setWindowTitle(deps.windowTitle) }
        // start() is idempotent: a recomposition after the entry was covered finds the same, started session.
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
