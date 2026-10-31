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
package net.bible.sharedui.host.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.sharedcore.event.EventSource
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #3919 review: the restore reload runs once per event, never two at a time. The "Copy from Cloud" swap posts
 * `workspaceRefreshRequired` per category and the settings enable flow posts `databaseRestored` after it; overlapping
 * `reloadAfterRestore` runs would leave the panes on discarded Window objects.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HostRestoreReloadSerialTest {
    private val mainThread = Executors.newSingleThreadExecutor { Thread(it, "test-main").apply { isDaemon = true } }.asCoroutineDispatcher()

    @BeforeTest fun before() = Dispatchers.setMain(mainThread)

    @AfterTest fun after() {
        Dispatchers.resetMain()
        mainThread.close()
    }

    private fun drainMain() = runBlocking { repeat(3) { withContext(Dispatchers.Main) { } } }

    @Test fun eventsArrivingTogetherReloadOneAfterTheOther() {
        val restored = EventSource<Unit>()
        val refreshRequired = EventSource<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var active = 0
        var maxActive = 0
        var runs = 0
        val gate = CompletableDeferred<Unit>()
        val subscription = installRestoreReload(listOf(restored, refreshRequired), scope) {
            active++
            maxActive = maxOf(maxActive, active)
            runs++
            gate.await()
            active--
        }
        try {
            refreshRequired.emit(Unit)
            refreshRequired.emit(Unit) // a second category's swap
            restored.emit(Unit)        // the settings flow's post
            drainMain()
            assertEquals(1, runs, "a reload started while another was still running")
            gate.complete(Unit)
            runBlocking { withTimeout(10_000) { scope.coroutineContext[Job]!!.children.toList().forEach { it.join() } } }
            assertEquals(3, runs)
            assertEquals(1, maxActive)

            subscription.cancel()
            restored.emit(Unit)
            refreshRequired.emit(Unit)
            drainMain()
            assertEquals(3, runs, "a cancelled subscription still reloads")
        } finally {
            subscription.cancel()
            scope.cancel()
        }
    }
}
