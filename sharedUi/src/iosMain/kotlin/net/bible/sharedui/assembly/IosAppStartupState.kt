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
package net.bible.sharedui.assembly

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.bible.sharedcore.log.Log

/** Where the iOS cold start is: the splash shows while [Starting], the app once [Ready], an error on [Failed]. */
sealed interface StartupPhase {
    data object Starting : StartupPhase
    data object Ready : StartupPhase
    data class Failed(val error: Throwable) : StartupPhase
}

/**
 * Runs the off-main-thread part of the cold start ([open]: databases, then the workspace) and exposes it as [phase].
 * Never stuck in [StartupPhase.Starting] after a failure: any exception becomes [StartupPhase.Failed].
 */
class IosAppStartupState(
    private val open: suspend () -> Unit = {
        IosAppStartup.openDatabases()
        IosAppStartup.loadWorkspace()
    },
) {
    private val _phase = MutableStateFlow<StartupPhase>(StartupPhase.Starting)
    val phase: StateFlow<StartupPhase> = _phase

    private var launched = false

    /** Starts [open] on [Dispatchers.Default]; a second call does nothing (the first run decides the phase). */
    fun launch(scope: CoroutineScope) {
        if (launched) return
        launched = true
        scope.launch(Dispatchers.Default) {
            try {
                open()
                _phase.value = StartupPhase.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "startup failed", e)
                _phase.value = StartupPhase.Failed(e)
            }
        }
    }

    /** For a failure before [launch] (the main-thread stage of the startup): shows the error instead of starting. */
    fun fail(error: Throwable) {
        launched = true
        _phase.value = StartupPhase.Failed(error)
    }

    private companion object { const val TAG = "IosAppStartupState" }
}
