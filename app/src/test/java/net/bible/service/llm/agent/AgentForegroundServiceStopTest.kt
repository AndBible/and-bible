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

import android.content.Intent
import kotlinx.coroutines.Job
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.Selection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * F134: only [LiveRuns] (via a run's `finally`) and an explicit cancel stop [AgentForegroundService]. A session's
 * `StatusChanged(!isRunning)` must not: it fires when the winning run ends, while another run launched by the
 * service may still be live, and `onDestroy` -> `scope.cancel()` would kill that run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AgentForegroundServiceStopTest {
    private val ws = IdType()

    private fun startIntent(): Intent {
        val selection = Selection(bookInitials = null, startOrdinal = -1, startOffset = null, endOrdinal = -1, endOffset = null, bookmarks = emptyList())
        AgentForegroundService.startAgent(RuntimeEnvironment.getApplication(), IdType(), selection, ws)
        return shadowOf(RuntimeEnvironment.getApplication()).nextStartedService
    }

    private fun liveRuns(service: AgentForegroundService): LiveRuns =
        AgentForegroundService::class.java.getDeclaredField("liveRuns").run {
            isAccessible = true
            get(service) as LiveRuns
        }

    @Test fun aSessionStoppingDoesNotStopTheServiceWhileAnotherRunIsLive() {
        val intent = startIntent()
        val controller = Robolectric.buildService(AgentForegroundService::class.java, intent).create()
        val service = controller.get()
        val otherRun = Job()
        try {
            liveRuns(service).add(otherRun)
            // Unknown prompt: this run ends at once; the other run keeps the service alive.
            service.onStartCommand(intent, 0, 1)
            Thread.sleep(500)

            AgentSessionManager.emitChange(AgentSessionChange.StatusChanged(ws, false, AgentStopReason.CANCELLED))

            assertFalse("a StatusChanged must not stop the service while a launched run is live", shadowOf(service).isStoppedBySelf)
        } finally {
            otherRun.cancel()
            controller.destroy()
        }
    }

    @Test fun anExplicitCancelStillStopsTheService() {
        val intent = startIntent()
        val controller = Robolectric.buildService(AgentForegroundService::class.java, intent).create()
        val service = controller.get()
        try {
            service.onStartCommand(intent, 0, 1)
            service.onStartCommand(Intent(AgentForegroundService.CANCEL_AGENT), 0, 2)
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally {
            controller.destroy()
        }
    }
}
