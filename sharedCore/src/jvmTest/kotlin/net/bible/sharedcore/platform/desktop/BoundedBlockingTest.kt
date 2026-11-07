package net.bible.sharedcore.platform.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundedBlockingTest {
    @Test fun boundedBlockingReturnsFalseWithinBoundAndDoesNotInterrupt() {
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val start = System.nanoTime()
        try {
            assertFalse(boundedBlocking("blocked", 400) {
                try { release.await() } catch (e: InterruptedException) { interrupted.set(true) }
                interrupted.set(interrupted.get() || Thread.currentThread().isInterrupted)
                finished.countDown()
            })
            val elapsed = (System.nanoTime() - start) / 1_000_000
            assertTrue(elapsed < 600, "took $elapsed ms")
        } finally { release.countDown() }
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        assertFalse(interrupted.get())
    }

    @Test fun boundedBlockingReturnsTrueWhenBlockFinishes() {
        assertTrue(boundedBlocking("done", 1000) {})
    }

    @Test fun boundedBlockingLogsAndReturnsTrueWhenBlockThrows() {
        assertTrue(boundedBlocking("failure", 1000) { error("expected") })
    }
}
