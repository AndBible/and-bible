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
package net.bible.service.cloudsync.desktop

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.sharedcore.platform.desktop.DesktopExitTasks
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvent
import net.bible.sharedcore.platform.desktop.DesktopLifecycleEvents
import net.bible.sharedcore.platform.desktop.sync.DesktopSyncScheduler
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSyncDriverTest {
    private val intervalSeconds = 300L

    private class Fixture(val scope: TestScope, val startSync: () -> Deferred<Boolean>) {
        val exitTasks = DesktopExitTasks()
        val events = DesktopLifecycleEvents()
        val ready = CompletableDeferred<Unit>()
        var closePasses = 0
        var startedPasses = 0
        var databasesReady = true
        var closeResult: Boolean? = null
    }

    private fun TestScope.fixture(startSync: () -> Deferred<Boolean>): Fixture = Fixture(this, startSync)

    private fun policy(f: Fixture) = BackgroundSyncPolicy(
        syncEnabled = { true }, replacing = { false }, intervalSeconds = { intervalSeconds },
        lastSynchronizedMs = { 0L }, nowMs = { Long.MAX_VALUE / 2 }, signedIn = { true }, signIn = { true },
        startSync = { f.startedPasses++; f.startSync() },
    )

    private fun driver(f: Fixture, closeWaitMs: Long = 9_000, external: () -> kotlinx.coroutines.Job? = { null }) = DesktopSyncDriver(
        policy = policy(f), closePass = { f.closePasses++; f.closeResult }, exitTasks = f.exitTasks, events = f.events,
        scope = f.scope.backgroundScope, awaitReady = { f.ready.await() }, closeWaitMs = closeWaitMs,
        databasesReady = { f.databasesReady }, externalPass = external,
    )

    @Test fun startPassRunsOnceAfterReady() = runTest {
        var starts = 0
        val f = fixture { starts++; CompletableDeferred(true) }
        driver(f)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(0, starts, "no pass before startup is ready")
        f.ready.complete(Unit)
        runCurrent()
        assertEquals(1, starts)
        advanceTimeBy((intervalSeconds - 1) * 1000)
        assertEquals(1, starts, "the start pass runs once; the next one waits for the interval")
    }

    @Test fun periodicPassesFollowPolicyInterval() = runTest {
        var starts = 0
        val f = fixture { starts++; CompletableDeferred(true) }
        driver(f); f.ready.complete(Unit)
        runCurrent()
        assertEquals(1, starts)
        advanceTimeBy(intervalSeconds * 1000 - 1); assertEquals(1, starts)
        advanceTimeBy(2); assertEquals(2, starts)
        advanceTimeBy(intervalSeconds * 1000); assertEquals(3, starts)
    }

    @Test fun passesNeverOverlap() = runTest {
        var running = 0; var maxRunning = 0; var starts = 0
        val f = fixture {
            val d = CompletableDeferred<Boolean>()
            starts++; running++; maxRunning = maxOf(maxRunning, running)
            backgroundScope.launch { delay(intervalSeconds * 1000 * 2 + 5); running--; d.complete(true) }
            d
        }
        driver(f); f.ready.complete(Unit)
        advanceTimeBy(intervalSeconds * 1000 * 10); runCurrent()
        assertEquals(1, maxRunning)
        assertTrue(starts in 2..5, "passes continue one after another, got $starts")
    }

    @Test fun closeWaitsForRunningPassUpTo10s() = runTest {
        val pass = CompletableDeferred<Boolean>() // a pass that runs past the bound
        val f = fixture { pass }
        driver(f); f.ready.complete(Unit)
        runCurrent()
        f.events.emit(DesktopLifecycleEvent.Background)
        val before = currentTime
        // The exit-task runner's own bound is 10 s; the driver stops waiting at 9 s so the whole run finishes inside it.
        assertTrue(f.exitTasks.runAll(10_000), "the task returns before the runner's bound")
        assertEquals(9_000L, currentTime - before)
        // Teardown: the running pass ends late; no close pass and no new pass may start behind it.
        pass.complete(true)
        advanceTimeBy(intervalSeconds * 1000 * 3); runCurrent()
        assertEquals(0, f.closePasses, "a close pass must not queue behind the timed-out pass")
        assertEquals(1, f.startedPasses, "no fresh pass after the exit began")
    }

    @Test fun closeReturnsAsSoonAsTheRunningPassEnds() = runTest {
        val pass = CompletableDeferred<Boolean>()
        val f = fixture { pass }
        driver(f); f.ready.complete(Unit)
        runCurrent()
        backgroundScope.launch { delay(2_000); pass.complete(true) }
        val before = currentTime
        assertTrue(f.exitTasks.runAll(10_000))
        assertEquals(2_000L, currentTime - before)
    }

    @Test fun closeRunsTheClosePassWhenIdleAndSkipsItWhenDatabasesAreNotReady() = runTest {
        val f = fixture { CompletableDeferred(true) }
        driver(f); f.ready.complete(Unit)
        runCurrent()
        f.exitTasks.runAll(10_000)
        assertEquals(1, f.closePasses)
        f.databasesReady = false
        f.exitTasks.runAll(10_000)
        assertEquals(1, f.closePasses, "closed or replaced databases: nothing to push")
    }

    @Test fun backgroundEventStopsPeriodicPasses() = runTest {
        var starts = 0
        val f = fixture { starts++; CompletableDeferred(true) }
        driver(f); f.ready.complete(Unit)
        runCurrent()
        f.events.emit(DesktopLifecycleEvent.Background)
        advanceTimeBy(intervalSeconds * 1000 * 5)
        assertEquals(1, starts)
    }

    @Test fun startPassWithoutNetworkDoesNotBlockUi() = runTest {
        var starts = 0
        val f = fixture { starts++; throw IOException("Unable to resolve host") }
        // Construction returns at once even though the pass can never succeed and readiness is still pending.
        driver(f)
        assertEquals(0, starts)
        f.ready.complete(Unit)
        runCurrent()
        assertEquals(1, starts)
        advanceTimeBy(intervalSeconds * 1000 + 1)
        assertEquals(2, starts, "an offline pass does not stop the periodic loop")
    }

    @Test fun aThrowingClosePassIsSwallowed() = runTest {
        val f = fixture { CompletableDeferred(true) }
        DesktopSyncDriver(
            policy(f), closePass = { error("boom") }, exitTasks = f.exitTasks, events = f.events,
            scope = backgroundScope, awaitReady = { f.ready.await() },
        )
        f.ready.complete(Unit); runCurrent()
        assertTrue(f.exitTasks.runAll(10_000))
    }

    /**
     * A restart after a close in the middle of an upload. The real hoisted runner (through [DesktopSyncScheduler]) drives a
     * two-database "upload" over temp files that fails after the first database and leaves a partial file of the second;
     * a second driver + scheduler over the same state (the next start) finishes the work and leaves no partial file.
     */
    @Test fun closeDuringUploadResumesNextStart() = runTest {
        val dir = Files.createTempDirectory("d3c-upload")
        try {
            val local = Files.createDirectories(dir.resolve("local")).also {
                Files.writeString(it.resolve("a.db"), "AAAA"); Files.writeString(it.resolve("b.db"), "BBBB")
            }
            val remote = Files.createDirectories(dir.resolve("remote"))
            var failMidUpload = true
            suspend fun upload(): Boolean {
                for (name in listOf("a.db", "b.db")) {
                    if (Files.exists(remote.resolve(name))) continue // already uploaded by an earlier run
                    val part = remote.resolve("$name.part")
                    Files.writeString(part, Files.readString(local.resolve(name)).take(2)) // half written
                    if (name == "b.db" && failMidUpload) delay(Long.MAX_VALUE) // the connection stalls; the user closes the app
                    Files.writeString(part, Files.readString(local.resolve(name)))
                    Files.move(part, remote.resolve(name))
                }
                return true
            }
            fun pass(scheduler: DesktopSyncScheduler) = BackgroundSyncPolicy(
                syncEnabled = { true }, replacing = { false }, intervalSeconds = { intervalSeconds }, lastSynchronizedMs = { 0L },
                nowMs = { Long.MAX_VALUE / 2 }, signedIn = { true }, signIn = { true },
                startSync = { scheduler.startDatabaseSync() }, stopSync = { scheduler.stop(it) },
            )

            // First run: the upload stalls half way, the close waits its bound and the process ends.
            val first = DesktopSyncScheduler(backgroundScope) { upload() }
            val ready = CompletableDeferred<Unit>()
            val exit = DesktopExitTasks()
            DesktopSyncDriver(pass(first), { null }, exit, DesktopLifecycleEvents(), backgroundScope, { ready.await() })
            ready.complete(Unit); runCurrent()
            assertTrue(exit.runAll(10_000))
            assertTrue(Files.exists(remote.resolve("a.db")))
            assertFalse(Files.exists(remote.resolve("b.db")), "b was not completed")
            assertTrue(Files.exists(remote.resolve("b.db.part")))

            // Next start: a new scheduler and driver over the same state finish the job.
            failMidUpload = false
            val second = DesktopSyncScheduler(backgroundScope) { upload() }
            val ready2 = CompletableDeferred<Unit>()
            DesktopSyncDriver(pass(second), { null }, DesktopExitTasks(), DesktopLifecycleEvents(), backgroundScope, { ready2.await() })
            ready2.complete(Unit); runCurrent()
            assertEquals("AAAA", Files.readString(remote.resolve("a.db")))
            assertEquals("BBBB", Files.readString(remote.resolve("b.db")))
            assertFalse(Files.exists(remote.resolve("b.db.part")))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test fun readinessNeedsASuccessfulOpenNotJustTheFlag() = runTest {
        var ready = false
        var openAttempts = 0
        var returned = false
        backgroundScope.launch {
            awaitDatabasesOpen({ ready }, { openAttempts++; if (openAttempts < 3) error("not migrated yet") }, pollMs = 500)
            returned = true
        }
        advanceTimeBy(5_000); runCurrent()
        assertEquals(0, openAttempts, "a clear flag means the container is not touched")
        ready = true
        advanceTimeBy(400); assertFalse(returned)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(3, openAttempts, "failed opens are retried")
        assertTrue(returned)
    }

    @Test fun readinessIsRecheckedAfterTheOpen() = runTest {
        var ready = true
        var opens = 0
        var returned = false
        backgroundScope.launch {
            // The first open closes the databases again (a replace started meanwhile): that is not "ready".
            awaitDatabasesOpen({ ready }, { if (opens++ == 0) ready = false }, pollMs = 100)
            returned = true
        }
        runCurrent()
        assertFalse(returned)
        ready = true
        advanceTimeBy(150); runCurrent()
        assertTrue(returned)
        assertEquals(2, opens)
    }

    @Test fun closeAlsoWaitsForAPassStartedOutsideTheDriver() = runTest {
        val external = CompletableDeferred<Boolean>()
        val f = fixture { CompletableDeferred(true) }
        driver(f, external = { external.takeIf { it.isActive } }); f.ready.complete(Unit)
        runCurrent()
        backgroundScope.launch { delay(3_000); external.complete(true) }
        val before = currentTime
        assertTrue(f.exitTasks.runAll(10_000))
        assertEquals(3_000L, currentTime - before, "waited for the external pass")
        assertEquals(1, f.closePasses, "then the close pass ran")
    }

    @Test fun aPassThatSawTheStopBeforeItBeganDoesNotCountAsTheRunningPass() = runTest {
        // A scope whose work is queued by hand, so the exit can arrive between launching a pass and its taking the lock.
        val queue = ArrayDeque<Runnable>()
        val manual = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { queue.add(block) }
        }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        val f = fixture { CompletableDeferred(true) }
        val ready = CompletableDeferred(Unit)
        DesktopSyncDriver(
            policy(f), { f.closePasses++; true }, f.exitTasks, f.events, kotlinx.coroutines.CoroutineScope(manual),
            awaitReady = { ready.await() }, databasesReady = { true },
        )
        queue.removeFirst().run()     // only the loop starts: it launches its first pass, which is queued, not yet run
        f.events.emit(DesktopLifecycleEvent.Background)
        val exit = backgroundScope.launch { f.exitTasks.runAll(10_000) }
        runCurrent()                  // the exit task joins the queued pass
        drain(); runCurrent(); drain(); runCurrent()
        exit.join()
        assertEquals(0, f.startedPasses, "the pass saw the stop and did nothing")
        assertEquals(1, f.closePasses, "so the close pass still ran")
    }

    @Test fun closePassSignsInWhenTheSessionIsNotSignedInYet() = runTest {
        var pushed = 0
        suspend fun run(signedIn: Boolean, signIn: Boolean?, changes: Boolean = true, enabled: Boolean = true) = runClosePass(
            enabled = { enabled }, signedIn = { signedIn }, signIn = { signIn }, hasChanges = { changes }, push = { pushed++; true },
        )
        assertEquals(true, run(signedIn = false, signIn = true)); assertEquals(1, pushed)
        assertEquals(true, run(signedIn = true, signIn = null)); assertEquals(2, pushed, "no sign-in needed when signed in")
        assertEquals(null, run(signedIn = false, signIn = false)); assertEquals(null, run(signedIn = false, signIn = null))
        assertEquals(null, run(signedIn = true, signIn = true, changes = false))
        assertEquals(null, run(signedIn = true, signIn = true, enabled = false))
        assertEquals(2, pushed)
    }
}
