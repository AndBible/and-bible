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

package net.bible.sharedcore.backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller for the Backup & Restore screen (classic `BackupActivity`). Thin: every heavy
 * operation is delegated to the injected [service] -- this class only holds [state] and re-loads
 * it after an action that can change the on-disk backup-file list ([restore], [restoreFile],
 * [resetDb], [exportFile]; see [BackupService] for what each delegated call does). [backup] does
 * NOT re-load: it only starts the host-side save/share flow and does not itself add a row to
 * [BackupState.backupFiles].
 */
class BackupController(
    private val service: BackupService,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    /** (Re)loads the full screen state from [service]. */
    fun load() {
        scope.launch {
            _state.value = service.load()
        }
    }

    /** Optimistically flips [kind] in [state] and persists the change via [service]. */
    fun setToggle(kind: ToggleKind, value: Boolean) {
        _state.value = _state.value.copy(toggles = _state.value.toggles + (kind to value))
        service.setToggle(kind, value)
    }

    fun backup() {
        scope.launch { service.backup() }
    }

    fun restore() {
        scope.launch {
            service.restore()
            load()
        }
    }

    fun exportFile(token: String) {
        scope.launch {
            service.exportFile(token)
            load()
        }
    }

    fun restoreFile(token: String) {
        scope.launch {
            service.restoreFile(token)
            load()
        }
    }

    fun resetDb(dbFileName: String) {
        scope.launch {
            service.resetDb(dbFileName)
            load()
        }
    }
}
