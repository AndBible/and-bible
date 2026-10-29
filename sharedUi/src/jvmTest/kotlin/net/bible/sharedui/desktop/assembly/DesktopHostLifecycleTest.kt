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

import net.bible.sharedui.assembly.HostLifecycleEvent
import net.bible.sharedui.assembly.HostLifecycleEvent.Active
import net.bible.sharedui.assembly.HostLifecycleEvent.Background
import net.bible.sharedui.assembly.HostLifecycleEvent.ResignActive
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopHostLifecycleTest {
    private val all = listOf(Active, ResignActive, Background)

    @Test fun windowCloseSendsResignThenBackground() {
        val lifecycle = DesktopHostLifecycle()
        val seen = mutableListOf<HostLifecycleEvent>()
        lifecycle.observe(all) { seen += it }
        lifecycle.onExit()
        // Synchronous: both events are delivered before onExit returns, in this order.
        assertEquals(listOf(ResignActive, Background), seen)
    }

    @Test fun activationSendsActiveAndDeactivationOnlyResignActive() {
        val lifecycle = DesktopHostLifecycle()
        val seen = mutableListOf<HostLifecycleEvent>()
        lifecycle.observe(all) { seen += it }
        lifecycle.onWindowActivated()
        lifecycle.onWindowDeactivated()
        assertEquals(listOf(Active, ResignActive), seen)
    }

    @Test fun observerGetsOnlyItsEventsAndNothingAfterUnregistering() {
        val lifecycle = DesktopHostLifecycle()
        val saves = mutableListOf<HostLifecycleEvent>()
        val others = mutableListOf<HostLifecycleEvent>()
        val stopSaves = lifecycle.observe(listOf(ResignActive, Background)) { saves += it }
        lifecycle.observe(listOf(Active)) { others += it }
        lifecycle.onWindowActivated()
        lifecycle.onExit()
        stopSaves()
        lifecycle.onExit()
        assertEquals(listOf(ResignActive, Background), saves)
        assertEquals(listOf(Active), others)
    }

    @Test fun anObserverMayUnregisterWhileAnEventIsDelivered() {
        val lifecycle = DesktopHostLifecycle()
        val seen = mutableListOf<String>()
        var stopFirst: () -> Unit = {}
        stopFirst = lifecycle.observe(all) { seen += "first $it"; stopFirst() }
        lifecycle.observe(all) { seen += "second $it" }
        lifecycle.onExit()
        assertEquals(listOf("first ResignActive", "second ResignActive", "second Background"), seen)
    }
}
