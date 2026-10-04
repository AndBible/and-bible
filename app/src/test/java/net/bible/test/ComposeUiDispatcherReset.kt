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

package net.bible.test

import android.os.Handler
import android.view.Choreographer
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlin.coroutines.ContinuationInterceptor

/**
 * Re-arms Compose's JVM-static main-thread dispatcher after an earlier Robolectric test left it stuck.
 *
 * [AndroidUiDispatcher.Main] is created once per JVM and survives from test to test, but Robolectric resets
 * the main Looper's queue and the Choreographer's callbacks between tests. When a test ends while the
 * dispatcher has a trampoline or frame dispatch scheduled, the Handler message or frame callback it posted
 * is dropped. Its `scheduledTrampolineDispatch`/`scheduledFrameDispatch` flags stay true, so it never posts
 * again. From then on, every `LaunchedEffect`, and every recomposition of a later test's
 * `setContent`/`buildActivity` host, waits forever. For example, a NavHost entry stays at STARTED and its
 * `BackHandler` is never registered.
 *
 * `createAndroidComposeRule` tests are immune because they drive their own clock. A test that builds a real
 * host with `Robolectric.buildActivity` and needs anything composed after the first frame calls this first.
 * It posts the dispatcher's own callback once more, as both a Handler message and a frame callback; the
 * dispatcher clears its flags when that runs. This touches only private fields, and a Compose upgrade that
 * renames them fails loudly here.
 */
fun resetComposeUiDispatcher() {
    val dispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor] as AndroidUiDispatcher
    fun <T> field(name: String): T {
        @Suppress("UNCHECKED_CAST")
        return AndroidUiDispatcher::class.java.getDeclaredField(name).apply { isAccessible = true }.get(dispatcher) as T
    }
    val callback: Any = field("dispatchCallback")
    field<Handler>("handler").post(callback as Runnable)
    field<Choreographer>("choreographer").postFrameCallback(callback as Choreographer.FrameCallback)
}
