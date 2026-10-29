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

package net.bible.sharedui.ios.reading

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSUserDefaultsDidChangeNotification
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import net.bible.sharedui.host.reading.DisplayModeSignals

/** The iOS [DisplayModeSignals]: `UserDefaults` changed and app became active, delivered on the main queue. */
@kotlin.native.HiddenFromObjC
object IosDisplayModeSignals : DisplayModeSignals {
    override fun observe(onSignal: () -> Unit): () -> Unit {
        val observers = mutableListOf<Any>()
        val center = NSNotificationCenter.defaultCenter
        for (name in listOf(NSUserDefaultsDidChangeNotification, UIApplicationDidBecomeActiveNotification)) {
            observers += center.addObserverForName(name, null, NSOperationQueue.mainQueue) { onSignal() }
        }
        return {
            val c = NSNotificationCenter.defaultCenter
            observers.forEach { c.removeObserver(it) }
            observers.clear()
        }
    }
}
