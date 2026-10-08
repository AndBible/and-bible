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


package net.bible.service.llm.agent

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.Selection
import net.bible.service.common.CommonUtils
import net.bible.service.llm.AgentPrompt
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * F123: an `executePrompt` that loses `tryStart` must report it, must not run `onStarted` (regeneration deletes
 * the old page there), and must leave the winner's job bound. Workspace-level selection (no book, ordinal -1) so
 * no Bible module is needed. `executePrompt` hops to Main (context build, toast), so it runs on IO while this
 * thread idles the main looper; `runTest` would deadlock (memory robolectric-runtest-withcontext-main-deadlock).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ExecutePromptLostRaceTest {
    private val ws get() = CommonUtils.windowControl.windowRepository.id

    @After fun tearDown() {
        AgentSessionManager.clearSession(ws)
        shadowOf(Looper.getMainLooper()).idle()
        DatabaseResetter.resetDatabase()
    }

    private fun <T> onIoIdlingMain(block: suspend () -> T): T {
        val d = CoroutineScope(Dispatchers.IO).async { block() }
        val deadline = System.currentTimeMillis() + 30_000
        while (!d.isCompleted) {
            check(System.currentTimeMillis() < deadline) { "executePrompt did not return in 30 s" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        return d.getCompleted()
    }

    @Test fun aCallThatLosesTheRaceReportsItAndSkipsOnStarted() {
        val winner = Job()
        // The repository id is empty until initialized; executePrompt would otherwise initialize it mid-test.
        CommonUtils.windowControl.windowRepository.initialize()
        assertTrue(AgentSessionManager.getOrCreateSession(ws).tryStart(AgentContext(promptId = IdType()), winner))
        var started = false
        val result = onIoIdlingMain {
            AgentSessionManager.executePrompt(
                AgentPrompt(name = "t"),
                Selection(bookInitials = null, startOrdinal = -1, startOffset = null, endOrdinal = -1, endOffset = null, bookmarks = emptyList()),
                skipCache = true,
                onStarted = { started = true },
            )
        }
        assertFalse("a lost race must report false", result)
        assertFalse("onStarted must not run for a lost race", started)
        assertTrue("the winner's job stays bound", AgentSessionManager.getSession(ws)!!.job === winner)
        assertFalse(winner.isCancelled)
    }
}
