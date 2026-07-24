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
package net.bible.android.view.activity.backup

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.backup.BackupServiceImpl
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.backup.BackupController
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.backup.BackupRestoreScreen
import net.bible.sharedui.theme.AbTheme

/**
 * Compose host for the Backup & Restore screen -- the new-path twin of classic
 * [net.bible.android.control.backup.BackupActivity]. `doNotInitializeApp = true` mirrors the
 * classic Activity's own flag (backup/restore/reset can legitimately be entered around a crash,
 * e.g. from [net.bible.android.control.report.ErrorReportControl] via `BackupControl.backupPopup`,
 * before a full app re-init makes sense).
 *
 * Drives the shared [BackupController] over a host-constructed [BackupServiceImpl] (NOT a Koin
 * singleton -- the service needs THIS activity instance for [net.bible.android.control.backup.BackupControl]'s
 * dialog/SAF suspend calls; same reasoning as `SyncSettingsServiceImpl`/`SyncSettingsComposeActivity`)
 * and renders [BackupRestoreScreen].
 *
 * `load()` is called both in [onCreate] (initial population -- [BackupController.load] is not
 * auto-invoked on construction, see its kdoc) and in [onResume] (a SAF export/import/reset can
 * change the on-disk backup-file list, or clear the crash file, while this activity was paused
 * behind a share-sheet or file picker).
 */
class BackupComposeActivity : ActivityBase() {
    override val doNotInitializeApp = true

    private val service by lazy { BackupServiceImpl(this) }
    private val controller by lazy { BackupController(service, lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller.load()
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val state by controller.state.collectAsState()
                    BackupRestoreScreen(
                        state = state,
                        onToggle = controller::setToggle,
                        onBackup = controller::backup,
                        onRestore = controller::restore,
                        onExportFile = controller::exportFile,
                        onRestoreFile = controller::restoreFile,
                        onResetDb = controller::resetDb,
                        onUp = { finish() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.load()
    }
}
