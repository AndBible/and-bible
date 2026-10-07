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

package net.bible.sharedui.startup.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.startup.StartupWelcomeController
import net.bible.sharedui.startup.StartupWelcomeScreen

/**
 * What the first-run welcome destination needs from its host (slice 8 §4) -- `StartupComposeActivity`'s
 * orchestration, supplied by `:app`'s `WelcomeFlow`. Every action is a host seam; [dialogs] is the host's
 * redownload multi-select, composed over the screen (its options are `:app` Room entities).
 *
 * WELCOME is a START destination with nothing beneath it, so system back leaves the host (the app) -- no
 * handler here. Leaving for reading is the host's gate (b), never a navigate from this arm.
 */
class WelcomeNavDeps(
    val setWindowTitle: (String) -> Unit,
    val windowTitle: String,
    val controller: () -> StartupWelcomeController,
    /** F62: discrete-aware, chosen by the host (`startupWelcomeAppNameRes`). */
    val appName: String,
    /** F62: discrete-aware, chosen by the host (`startupWelcomeLogoRes`); a slot because painters are resources. */
    val logo: @Composable () -> Painter?,
    val onDownload: () -> Unit,
    val onImport: () -> Unit,
    val onRestore: () -> Unit,
    val onRedownload: () -> Unit,
    val onEasyStart: () -> Unit,
    val onOpenHomepage: () -> Unit,
    val onOpenGithub: () -> Unit,
    val dialogs: @Composable () -> Unit,
)

fun NavGraphBuilder.welcomeNavGraph(deps: WelcomeNavDeps) {
    composable(route = NavRoutes.WELCOME) {
        LaunchedEffect(deps.windowTitle) { deps.setWindowTitle(deps.windowTitle) }
        val controller = remember { deps.controller() }
        val state by controller.state.collectAsState()
        StartupWelcomeScreen(
            state = state,
            appName = deps.appName,
            logo = deps.logo(),
            onSelectTab = controller::selectTab,
            onDownload = deps.onDownload,
            onImport = deps.onImport,
            onRestore = deps.onRestore,
            onRedownload = deps.onRedownload,
            onEasyStart = deps.onEasyStart,
            onOpenHomepage = deps.onOpenHomepage,
            onOpenGithub = deps.onOpenGithub,
        )
        deps.dialogs()
    }
}
