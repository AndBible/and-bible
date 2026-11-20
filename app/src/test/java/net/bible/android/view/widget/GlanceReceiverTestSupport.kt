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
package net.bible.android.view.widget

import android.content.BroadcastReceiver

/**
 * Runs a Glance receiver callback the way a real broadcast does, and waits for its work.
 *
 * Glance's `onDeleted`/`onUpdate` call `goAsync()` and finish the result from a `Dispatchers.Default` coroutine. Called
 * directly (not by a broadcast) `goAsync()` is null and that coroutine throws an NPE after the test ended, which fails
 * whichever test runs next on that JVM (`UncaughtExceptionsBeforeTest`). Robolectric's `createWidget` has the same
 * problem and also reaches WorkManager, so tests use `bindAppWidgetIdIfAllowed` to place a widget instead.
 */
internal fun BroadcastReceiver.callAsBroadcast(timeoutMs: Long = 10_000, call: () -> Unit) {
    // Reflective: ShadowBroadcastPendingResult is package-private in Robolectric 4.14.1 (gradle/libs.versions.toml).
    val create = Class.forName("org.robolectric.shadows.ShadowBroadcastPendingResult").declaredMethods
        .first { it.name == "create" }.apply { isAccessible = true }
    val pending = create.invoke(null, 0, null, null, false) as BroadcastReceiver.PendingResult
    BroadcastReceiver::class.java.getMethod("setPendingResult", BroadcastReceiver.PendingResult::class.java).invoke(this, pending)
    call()
    // The shadow's finish() completes this future (the framework's own mFinished flag is never set under Robolectric).
    org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowBroadcastPendingResult>(pending)
        .future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
}
