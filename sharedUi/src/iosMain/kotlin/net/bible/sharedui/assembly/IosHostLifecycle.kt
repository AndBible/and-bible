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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.assembly

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

/**
 * iOS [HostLifecycle]: each event is one `NSNotificationCenter.defaultCenter` observer on `NSOperationQueue.mainQueue`,
 * added when [observe] is called and removed by the returned function, so handlers run synchronously when UIKit posts.
 */
@kotlin.native.HiddenFromObjC
object IosHostLifecycle : HostLifecycle {
    /** The UIKit notification behind [event]. */
    internal fun notificationName(event: HostLifecycleEvent): String? = when (event) {
        HostLifecycleEvent.Active -> UIApplicationDidBecomeActiveNotification
        HostLifecycleEvent.ResignActive -> UIApplicationWillResignActiveNotification
        HostLifecycleEvent.Background -> UIApplicationDidEnterBackgroundNotification
    }

    override fun observe(events: List<HostLifecycleEvent>, onEvent: (HostLifecycleEvent) -> Unit): () -> Unit {
        val center = NSNotificationCenter.defaultCenter
        val observers = events.map { event ->
            center.addObserverForName(notificationName(event), null, NSOperationQueue.mainQueue) { onEvent(event) }
        }
        return { observers.forEach { center.removeObserver(it) } }
    }
}
