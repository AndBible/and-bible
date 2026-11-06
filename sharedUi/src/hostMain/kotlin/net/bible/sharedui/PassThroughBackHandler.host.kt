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
package net.bible.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo

/** iOS and desktop share the same back chain, including synchronous pass-through to navigation. */
@Composable
actual fun PassThroughBackHandler(enabled: Boolean, onBack: (passThrough: () -> Unit) -> Unit) {
    HostBackDispatcher.Attach()
    val dispatcher = HostBackDispatcher.currentDispatcher()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentEnabled by rememberUpdatedState(enabled)
    val handler = remember {
        object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, enabled) {
            override fun onBackCompleted() {
                currentOnBack {
                    isBackEnabled = false
                    try {
                        HostBackDispatcher.passThrough()
                    } finally {
                        isBackEnabled = currentEnabled
                    }
                }
            }
        }
    }
    SideEffect { handler.isBackEnabled = enabled }
    DisposableEffect(dispatcher) {
        dispatcher?.addHandler(handler)
        onDispose { if (dispatcher != null) handler.remove() }
    }
}
