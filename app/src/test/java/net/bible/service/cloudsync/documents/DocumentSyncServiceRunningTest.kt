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
package net.bible.service.cloudsync.documents

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import net.bible.android.TEST_SDK
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DocumentSyncServiceRunningTest {
    @After fun tearDown() { DocumentSync.resetSubscribersForTest() }

    /**
     * The Cloud documents screen builds a fresh subscription on every entry, so a subscriber that attaches
     * mid-drain only learns the drain is running from a later `true`. The drain must therefore re-assert
     * `true` on every op, not only once at its start.
     */
    @Test
    fun drainReassertsRunningTrueOnEveryOp_thenEndsWithFalse() {
        val seen = CopyOnWriteArrayList<Boolean>()
        var service: DocumentSyncService? = null
        val finished = CountDownLatch(1)
        val subscription = DocumentSync.runningChanged.subscribe {
            seen += it
            if (!it) finished.countDown()
        }
        try {
            val context = RuntimeEnvironment.getApplication()
            // Nonexistent books: each push is a no-op, but the drain still walks the three ops.
            DocumentSyncService.start(context, pushInitials = listOf("nope-a", "nope-b", "nope-c"), downloadInitials = emptyList())
            val intent = shadowOf(context).nextStartedService
            val created = Robolectric.buildService(DocumentSyncService::class.java, intent).create().get()
            service = created
            created.onStartCommand(intent, 0, 1)

            assertTrue("drain did not finish", finished.await(30, TimeUnit.SECONDS))
        } finally { subscription.cancel() }
        // The `false` edge is emitted BEFORE the drain's stopSelfSafe(). Returning here would let Robolectric
        // tear the application down while the drain coroutine (on Dispatchers.IO) is still calling
        // stopForeground, which NPEs on a null application and poisons whichever test runs next.
        // So wait for the drain coroutine itself, then check its teardown side effect.
        awaitDrainCompletion(service!!)
        assertTrue("drain must have stopped the service", shadowOf(service).isStoppedBySelf)
        // 1 (drain start) + 3 (one per op) trues, then the final false.
        assertEquals(listOf(true, true, true, true, false), seen.toList())
    }

    /** Joins every coroutine still running in the service's private scope (the drain job). */
    private fun awaitDrainCompletion(service: DocumentSyncService) {
        val scope = DocumentSyncService::class.java.getDeclaredField("scope").run {
            isAccessible = true
            get(service) as CoroutineScope
        }
        runBlocking {
            withTimeout(30_000) { scope.coroutineContext[Job]!!.children.toList().forEach { it.join() } }
        }
    }
}
