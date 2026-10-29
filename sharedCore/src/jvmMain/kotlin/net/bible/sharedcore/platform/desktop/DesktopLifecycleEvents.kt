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
package net.bible.sharedcore.platform.desktop

import net.bible.sharedcore.log.Log
import java.util.concurrent.CopyOnWriteArrayList

/** The app-level lifecycle events of the desktop window, as `HostLifecycleEvent` has them in `:sharedUi`. */
enum class DesktopLifecycleEvent { Active, ResignActive, Background }

/**
 * The desktop app's lifecycle events for modules below `:sharedUi` (the host lifecycle itself lives there): the desktop
 * host lifecycle emits every event here after its own observers, on the same thread (the AWT event thread). Bound in Koin
 * by the desktop platform module.
 *
 * For plan D3c (desktop cloud sync: sync when the app is left, as Android and iOS do on background). Default = today's
 * behaviour: nobody observes, so emitting changes nothing.
 */
class DesktopLifecycleEvents {
    private val listeners = CopyOnWriteArrayList<(DesktopLifecycleEvent) -> Unit>()

    /** Calls [listener] for every later event; the returned function stops it. */
    fun observe(listener: (DesktopLifecycleEvent) -> Unit): () -> Unit {
        listeners += listener
        return { listeners.remove(listener) }
    }

    /** Delivers [event] to every listener in registration order; a throwing listener is logged and skipped. */
    fun emit(event: DesktopLifecycleEvent) {
        for (listener in listeners) {
            try {
                listener(event)
            } catch (e: Throwable) {
                Log.e(TAG, "Lifecycle listener failed on $event", e)
            }
        }
    }

    private companion object {
        const val TAG = "DesktopLifecycleEvents"
    }
}
