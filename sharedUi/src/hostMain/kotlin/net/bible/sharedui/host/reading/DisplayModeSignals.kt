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

package net.bible.sharedui.host.reading

/**
 * The platform signals after which the night mode may have changed without a settings write, for
 * [IosNightModeObserver]: on iOS, `NSUserDefaultsDidChangeNotification` (the night-mode keys live in `UserDefaults`)
 * and `UIApplicationDidBecomeActiveNotification` (the system appearance may have changed in the background).
 *
 * A callback, not a flow: the observer refreshes synchronously on the main thread as each signal arrives, and it may be
 * started without a coroutine scope.
 */
@kotlin.native.HiddenFromObjC
fun interface DisplayModeSignals {
    /** Calls [onSignal] on the main thread for each signal until the returned function is called, which stops it. */
    fun observe(onSignal: () -> Unit): () -> Unit
}
