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

package net.bible.sharedui.ios.reading

import net.bible.sharedlogic.platform.NightModeState
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSRunLoop
import platform.Foundation.NSUserDefaultsDidChangeNotification
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedui.host.reading.DisplayModeSignals
import net.bible.sharedui.host.reading.HostNightModeObserver

/** The night-mode observer's [DisplayModeSignals] seam: which iOS notifications it follows, and one observation per start. */
class IosDisplayModeSignalsTest {
    private class Night(var dark: Boolean) : NightModeState {
        override val nightMode: Boolean get() = dark
    }

    private fun spinMain() = NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.1))

    private fun post(name: String?) = NSNotificationCenter.defaultCenter.postNotificationName(name!!, null)

    @Test
    fun iosSignalsAreUserDefaultsChangedAndBecameActiveUntilStopped() {
        var signals = 0
        val stop = IosDisplayModeSignals.observe { signals++ }
        try {
            post(NSUserDefaultsDidChangeNotification)
            spinMain()
            assertEquals(1, signals, "UserDefaults changed")
            post(UIApplicationDidBecomeActiveNotification)
            spinMain()
            assertEquals(2, signals, "app became active")
        } finally {
            stop()
        }
        post(UIApplicationDidBecomeActiveNotification)
        spinMain()
        assertEquals(2, signals, "nothing after stop")
    }

    @Test
    fun theObserverObservesOncePerStartAndRefreshesOnEachSignal() {
        var observing = 0
        var stopped = 0
        var fire: () -> Unit = {}
        val signals = DisplayModeSignals { onSignal ->
            observing++
            fire = onSignal
            return@DisplayModeSignals { stopped++ }
        }
        val night = Night(false)
        var changes = 0
        val observer = HostNightModeObserver(night, signals, restored = { null }) { changes++ }
        observer.start()
        observer.start() // already started: no second observation
        assertEquals(1, observing)
        night.dark = true
        fire()
        assertEquals(1, changes)
        observer.stop()
        observer.stop()
        assertEquals(1, stopped)
        observer.start() // a restart observes again
        assertEquals(2, observing)
        observer.stop()
    }
}
