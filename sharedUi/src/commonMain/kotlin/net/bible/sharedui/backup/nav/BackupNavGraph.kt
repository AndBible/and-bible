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

package net.bible.sharedui.backup.nav

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import net.bible.sharedcore.backup.BackupController
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.backup.BackupRestoreScreen
import net.bible.sharedui.nav.popOrExitOnFailedPop

/**
 * What the Backup & restore destination needs from its host (slice 8 §3.3) -- the classic
 * `BackupComposeActivity`'s two collaborators, supplied by `NavHostComposeActivity`.
 *
 * [controllerFor] builds a controller over a HOST-constructed service (`BackupServiceImpl(activity)`):
 * `BackupControl`'s dialog/SAF calls need the calling Activity, so the service is not a Koin singleton.
 *
 * This route can be the host's START destination (`ErrorReportControl.checkCrash` from `StartupActivity`,
 * before DB init), so its Up has a real exit: [exitHost] finishes the host (spec §3.1 rule 1). Backup
 * produces no result, so there is no channel.
 */
class BackupNavDeps(
    val exitHost: () -> Unit,
    val setWindowTitle: (String) -> Unit,
    /** Classic `BackupComposeActivity`'s manifest `android:label` (`@string/backup_and_restore`). */
    val windowTitle: String,
    val controllerFor: () -> BackupController,
)

/**
 * The Backup & restore destination. `load()` runs on every ON_RESUME of the entry -- the first entry,
 * and every return from a share sheet / file picker that may have changed the backup-file list or cleared
 * the crash file -- which is what classic's `onCreate` + `onResume` pair did.
 */
fun NavGraphBuilder.backupNavGraph(navController: NavHostController, deps: BackupNavDeps) {
    composable(route = NavRoutes.BACKUP) {
        val controller = remember { deps.controllerFor() }
        LaunchedEffect(deps.windowTitle) { deps.setWindowTitle(deps.windowTitle) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.load() }
        val state by controller.state.collectAsState()
        BackupRestoreScreen(
            state = state,
            onToggle = controller::setToggle,
            onBackup = controller::backup,
            onRestore = controller::restore,
            onExportFile = controller::exportFile,
            onRestoreFile = controller::restoreFile,
            onResetDb = controller::resetDb,
            onUp = { navController.popOrExit(deps.exitHost) },
        )
    }
}

/** This cluster's private copy of the one-line extension every graph carries -- see [popOrExitOnFailedPop]. */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}
