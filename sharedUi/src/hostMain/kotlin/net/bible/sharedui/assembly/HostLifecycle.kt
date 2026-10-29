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

/** An application lifecycle transition the shared host code reacts to. */
@kotlin.native.HiddenFromObjC
enum class HostLifecycleEvent {
    /** The app became active (iOS `UIApplicationDidBecomeActiveNotification`). */
    Active,

    /** The app is about to stop being active (iOS `UIApplicationWillResignActiveNotification`). */
    ResignActive,

    /** The app entered the background (iOS `UIApplicationDidEnterBackgroundNotification`). */
    Background,
}

/**
 * The host's application lifecycle notifications, as a callback registration rather than a `Flow`: the iOS handlers
 * must run synchronously inside the notification (a resign-active save begins its background task there, before the
 * app can be suspended), and a registration made in a `DisposableEffect` must be active when the effect returns. A
 * collected flow would deliver through a coroutine dispatch and subscribe only once its collector started.
 *
 * iOS implements it with `IosHostLifecycle` (one `NSNotificationCenter` observer per event on the main queue). A host
 * without these transitions (desktop) may return a no-op unregister and never call the handler.
 */
@kotlin.native.HiddenFromObjC
fun interface HostLifecycle {
    /**
     * Registers [onEvent] for each of [events], in that order, on the main thread; [onEvent] receives the event that
     * fired. The returned function unregisters them all (in the same order); call it once.
     */
    fun observe(events: List<HostLifecycleEvent>, onEvent: (HostLifecycleEvent) -> Unit): () -> Unit
}
