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

package net.bible.android.control.page.toolbar

import net.bible.android.control.page.window.WindowStateServiceImpl
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import net.bible.android.TEST_SDK
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.service.cloudsync.CloudSync
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.sharedcore.reading.ToolbarState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.objenesis.ObjenesisHelper
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the workspace-switch crash/ANR (A/B feedback round 5).
 *
 * `WindowRepository.loadFromDb` starts by clearing the active window and then restores each
 * window's page — and restoring a bible page emits `PageChange.BibleVerseChanged`, which this service
 * handles by rebuilding its snapshot from `windowControl.activeWindowPageManager`. Reading that
 * mid-load went through `WindowRepository.activeWindow`'s lazy getter, which saw
 * `initialized == false` and started ANOTHER `loadFromDb`. Measured on a real switch: 340 nested
 * loads, ~4.7 s of blocked main thread, and a window list full of duplicate windows — whose
 * duplicate ids then crashed the Compose reading view (two panes, one cached `BibleView`).
 *
 * So: while the repository has no active window, `refresh()` must not touch it at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ToolbarStateServiceLoadGuardTest {

    /**
     * A real-typed but never-constructed stand-in for a dependency the code under test must not
     * reach. `DocumentControl` and `SpeakControl` are final classes, so this module's subclass mock
     * maker cannot mock them, and Kotlin's non-null parameter checks reject a cast `null`. Objenesis
     * (on the test classpath via mockito) allocates an instance without running any constructor —
     * good enough precisely because `buildSnapshot()`, the only code that would touch these, is what
     * this test asserts never runs.
     */
    private inline fun <reified T : Any> neverTouched(): T = ObjenesisHelper.newInstance(T::class.java)

    /** The real [WindowStateServiceImpl] the service under test subscribes to; reset in [tearDown]. */
    private val windowStateService = WindowStateServiceImpl()

    /**
     * `ToolbarStateServiceImpl`'s `init` subscribes to [PassageChangeMediator.changes], the window
     * state service's `windowChanges`, [WorkspaceChanges.changes], [CloudSync.runningChanged] and
     * [SpeakChanges.changes] with no matching unsubscribe — by design, it is normally a
     * process-lifetime singleton. This test constructs one directly under plain
     * `android.app.Application`, so `TestBibleApplication.onTerminate()`'s owner resets never run for
     * it, and the subscribers would otherwise leak into later tests sharing this JVM.
     */
    @After
    fun tearDown() {
        PassageChangeMediator.resetSubscribersForTest()
        windowStateService.resetSubscribersForTest()
        WorkspaceChanges.resetSubscribersForTest()
        CloudSync.resetSubscribersForTest()
        SpeakChanges.resetSubscribersForTest()
    }

    @Test
    fun `refresh does not read the repository while a workspace is still loading`() {
        // A mock WindowRepository has no active window, so `initialized` — a final getter, which
        // the subclass mock maker does not intercept, i.e. the REAL one runs — reports false.
        // That is exactly the state loadFromDb leaves the repository in for the whole load.
        val repository = mock<WindowRepository>()
        val windowControl = mock<WindowControl>()
        whenever(windowControl.windowRepository).thenReturn(repository)

        val service = ToolbarStateServiceImpl(
            windowControl,
            neverTouched(),
            neverTouched(),
            neverTouched(),
            windowStateService,
        )

        // Without the guard this reaches windowControl.activeWindowPageManager -> activeWindow ->
        // initialize() -> loadFromDb(), i.e. the re-entrant load (here it blows up instead, since
        // there is no Koin/database in a unit test — either way, it must never get that far).
        service.refresh()

        assertEquals(ToolbarState.EMPTY, service.toolbar.value)
    }
}
