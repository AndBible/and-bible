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

import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins [IosHostLifecycle] to the UIKit notifications HostApp observed directly before the seam. */
class IosHostLifecycleTest {
    private fun post(name: String?) {
        NSNotificationCenter.defaultCenter.postNotificationName(name!!, null)
        NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.1))
    }

    @Test
    fun eachEventIsItsUiKitNotification() {
        assertEquals(UIApplicationDidBecomeActiveNotification, IosHostLifecycle.notificationName(HostLifecycleEvent.Active))
        assertEquals(UIApplicationWillResignActiveNotification, IosHostLifecycle.notificationName(HostLifecycleEvent.ResignActive))
        assertEquals(UIApplicationDidEnterBackgroundNotification, IosHostLifecycle.notificationName(HostLifecycleEvent.Background))
    }

    @Test
    fun handlersRunOnlyForObservedEventsUntilUnregistered() {
        val seen = mutableListOf<HostLifecycleEvent>()
        val stop = IosHostLifecycle.observe(listOf(HostLifecycleEvent.ResignActive, HostLifecycleEvent.Background)) { seen += it }
        try {
            post(UIApplicationWillResignActiveNotification)
            assertEquals(listOf(HostLifecycleEvent.ResignActive), seen, "resign active")
            post(UIApplicationDidBecomeActiveNotification)
            assertEquals(listOf(HostLifecycleEvent.ResignActive), seen, "an event not asked for is not delivered")
            post(UIApplicationDidEnterBackgroundNotification)
            assertEquals(listOf(HostLifecycleEvent.ResignActive, HostLifecycleEvent.Background), seen)
        } finally {
            stop()
        }
        post(UIApplicationWillResignActiveNotification)
        post(UIApplicationDidEnterBackgroundNotification)
        assertEquals(2, seen.size, "nothing after unregistering")
    }
}
