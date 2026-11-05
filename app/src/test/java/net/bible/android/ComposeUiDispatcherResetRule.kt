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
package net.bible.android

import org.junit.rules.ExternalResource

/**
 * Un-wedges Compose's process-wide `AndroidUiDispatcher.Main` before every `@Test`.
 *
 * `AndroidUiDispatcher.Main` is a JVM-static `lazy` (one per Robolectric SDK sandbox, i.e. shared by
 * every test class of that SDK in the one unit-test JVM). It de-duplicates its own work with two
 * flags, `scheduledTrampolineDispatch` and `scheduledFrameDispatch`: while a flag is `true` it
 * assumes a Handler message / Choreographer frame callback is already in flight and posts nothing.
 * Robolectric resets the main looper between tests and DROPS whatever was in flight, so a host
 * torn down with a dispatch pending (any `Robolectric.buildActivity(NavHostComposeActivity)` test)
 * leaves both flags stuck `true` with nothing that will ever clear them. Every later composition in
 * the JVM then never recomposes: state writes are never applied, `DisposableEffect`s never re-run,
 * and the NavHost's back handler stays disabled, so `onBackPressedDispatcher.onBackPressed()` pops
 * nothing. Evidence (fix batch 2, task 6): a second host in one JVM had both flags `true`, a first
 * host had both `false`; the same test is green alone and red after any earlier host test.
 *
 * Re-post the dispatcher's callbacks in `before()` rather than clearing its flags. Its queues can
 * still contain live work (notably GlobalSnapshotManager's process-wide collector), and clearing
 * `scheduledFrameDispatch` also tells the callback to skip retained frame waiters. Re-posting lets
 * the dispatcher drain its queues and clear its own flags, without relying on a new dispatch.
 *
 * ```
 * @get:Rule val composeDispatcherReset = ComposeUiDispatcherResetRule()
 * ```
 */
class ComposeUiDispatcherResetRule : ExternalResource() {
    override fun before() {
        net.bible.test.resetComposeUiDispatcher()
    }
}
