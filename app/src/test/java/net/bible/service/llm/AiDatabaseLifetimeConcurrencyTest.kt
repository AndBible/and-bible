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

package net.bible.service.llm

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList

class AiDatabaseLifetimeConcurrencyTest {
    @Test fun replaceCancelsEveryOwnedJobRegisteredFromManyThreads() = runBlocking {
        val lifetime = AiDatabaseLifetime()
        val scope = CoroutineScope(Dispatchers.Default)
        val jobs = CopyOnWriteArrayList<Job>()
        val done = CountDownLatch(8)
        repeat(8) {
            Thread {
                repeat(50) {
                    val job = scope.launch(start = CoroutineStart.LAZY) { delay(60_000) }
                    jobs += job
                    lifetime.ownUntilReplace(job)
                    job.start()
                }
                done.countDown()
            }.start()
        }
        assertTrue(done.await(20, java.util.concurrent.TimeUnit.SECONDS))
        withTimeout(20_000) { lifetime.replace { } }
        assertTrue(jobs.size == 400)
        assertTrue(jobs.all { it.isCancelled || it.isCompleted })
        // the owned set drained: a second replace has nothing to cancel and returns at once
        withTimeout(5_000) { lifetime.replace { } }
        scope.cancel()
    }
}
