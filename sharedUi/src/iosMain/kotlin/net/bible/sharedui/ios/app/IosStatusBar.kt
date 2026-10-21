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
package net.bible.sharedui.ios.app

import platform.Foundation.NSNotificationCenter

/** The notification [iosSetStatusBarHidden] posts; `userInfo["hidden"]` is a Boolean. The SwiftUI root listens to it. */
internal const val IOS_STATUS_BAR_HIDDEN_NOTIFICATION: String = "net.bible.andbible.statusBarHidden"

/** The last value given to [iosSetStatusBarHidden]; false until the first call. */
internal var iosStatusBarHidden: Boolean = false
    private set

/**
 * Hides or shows the system status bar (full-screen reading hides it, leaving shows it again).
 *
 * Builder's choice for how: the app is a SwiftUI `App` that embeds the Compose controller as a child of a
 * `UIHostingController`, and UIKit asks the hosting controller, not the child, whether the status bar is hidden, so
 * overriding `prefersStatusBarHidden` on a Kotlin controller would never be consulted, and
 * `UIApplication.setStatusBarHidden` is ignored under view-controller-based status bar appearance (the default). The one
 * supported route is SwiftUI's `.statusBarHidden(_:)`, which `iOSApp.swift` drives from this notification. This keeps the
 * Kotlin side free of ObjC-exported API (the notification carries a plain Boolean), and works on iPhone and iPad.
 * Safe-area insets are read live by the Compose layout, so text stays inside the safe area either way.
 *
 * Call on the main thread (SwiftUI state is changed from the observer).
 */
@kotlin.native.HiddenFromObjC
fun iosSetStatusBarHidden(hidden: Boolean) {
    iosStatusBarHidden = hidden
    NSNotificationCenter.defaultCenter.postNotificationName(
        IOS_STATUS_BAR_HIDDEN_NOTIFICATION,
        `object` = null,
        userInfo = mapOf<Any?, Any?>("hidden" to hidden),
    )
}
