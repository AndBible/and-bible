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
package net.bible.sharedui.desktop.assembly

import net.bible.sharedui.assembly.HostLifecycle
import net.bible.sharedui.assembly.HostLifecycleEvent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The desktop [HostLifecycle], driven by the application window (`main()` wires the AWT window events to it):
 * - window activated: [HostLifecycleEvent.Active];
 * - window deactivated: [HostLifecycleEvent.ResignActive] only (the app is still visible; iOS sends `Background`
 *   only when the app leaves the screen);
 * - exit (window close): [HostLifecycleEvent.ResignActive] and then [HostLifecycleEvent.Background].
 *
 * Every event is delivered synchronously, on the calling thread (the AWT event thread), to the observers registered
 * for it, in registration order, before the `on...` call returns. The workspace save hangs off these events.
 */
class DesktopHostLifecycle : HostLifecycle {
    private class Registration(val events: List<HostLifecycleEvent>, val onEvent: (HostLifecycleEvent) -> Unit) {
        @Volatile var active = true
    }

    private val registrations = CopyOnWriteArrayList<Registration>()

    override fun observe(events: List<HostLifecycleEvent>, onEvent: (HostLifecycleEvent) -> Unit): () -> Unit {
        val registration = Registration(events.toList(), onEvent)
        registrations += registration
        return {
            registration.active = false
            registrations.remove(registration)
        }
    }

    fun onWindowActivated() = deliver(HostLifecycleEvent.Active)

    fun onWindowDeactivated() = deliver(HostLifecycleEvent.ResignActive)

    /** The app is closing: both events have been handled when this returns. */
    fun onExit() {
        deliver(HostLifecycleEvent.ResignActive)
        deliver(HostLifecycleEvent.Background)
    }

    private fun deliver(event: HostLifecycleEvent) {
        // A snapshot (CopyOnWriteArrayList); an observer removed while this event is delivered gets no further call.
        for (registration in registrations) {
            if (registration.active && event in registration.events) registration.onEvent(event)
        }
    }
}
