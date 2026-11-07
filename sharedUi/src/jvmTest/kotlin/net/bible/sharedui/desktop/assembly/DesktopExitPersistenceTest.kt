package net.bible.sharedui.desktop.assembly

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DesktopExitPersistenceTest {
    @Test fun hookAfterNormalCloseDoesNotPersistAgain() {
        var hook: Thread? = null
        val saves = AtomicInteger()
        val persistence = DesktopExitPersistence(registerHook = { hook = it }, persist = { saves.incrementAndGet() })
        assertTrue(persistence.persistOnce())
        assertNotNull(hook).run()
        assertEquals(1, saves.get())
    }

    @Test fun concurrentHookWaitsForTheNormalCloseSaveInsteadOfSavingTwice() {
        var hook: Thread? = null
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val saves = AtomicInteger()
        val persistence = DesktopExitPersistence(registerHook = { hook = it }, persist = {
            saves.incrementAndGet()
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
        })
        val normal = Thread { persistence.persistOnce() }
        val shutdownFinished = CountDownLatch(1)
        normal.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val shutdown = Thread {
                assertNotNull(hook).run()
                shutdownFinished.countDown()
            }
            shutdown.start()
            assertTrue(!shutdownFinished.await(50, TimeUnit.MILLISECONDS), "the hook must join the in-flight save")
            release.countDown()
            shutdown.join(2_000)
            assertTrue(!shutdown.isAlive)
            assertEquals(1, saves.get())
        } finally {
            release.countDown()
            normal.join(2_000)
        }
    }

    @Test fun hookIsBoundedEvenWhenABlockingSaveIgnoresCoroutineCancellation() {
        var hook: Thread? = null
        val release = CountDownLatch(1)
        val exited = CountDownLatch(1)
        DesktopExitPersistence(registerHook = { hook = it }, persist = {
            release.await()
        }, afterShutdown = { exited.countDown() }, shutdownTimeoutMs = 50)
        val shutdown = Thread { assertNotNull(hook).run() }
        try {
            shutdown.start()
            shutdown.join(2_000)
            assertTrue(!shutdown.isAlive, "the shutdown hook must not wait forever for blocking I/O")
            assertEquals(0, exited.count, "native-thread exit must follow the bounded save")
        } finally {
            release.countDown()
            shutdown.join(2_000)
        }
    }

    @Test fun persistOnceReturnsFalseAtTheDeadlineWhenThePassHangs() {
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val persistence = DesktopExitPersistence(registerHook = {}, exitDeadlineMs = 200, persist = {
            try { release.await() } finally { finished.countDown() }
        })
        try {
            val start = System.nanoTime()
            assertFalse(persistence.persistOnce())
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1_500)
        } finally {
            release.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
        }
    }

    @Test fun shutdownHookStillWaitsFiveSeconds() {
        var hook: Thread? = null
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val finished = CountDownLatch(1)
        DesktopExitPersistence(registerHook = { hook = it }, persist = {
            entered.countDown()
            try { release.await() } finally { finished.countDown() }
        })
        val thread = Thread { hook!!.run() }
        try {
            thread.start()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            thread.join(1_000)
            assertTrue(thread.isAlive)
            thread.join(5_500)
            assertFalse(thread.isAlive)
        } finally {
            release.countDown()
            thread.join(2_000)
            assertTrue(finished.await(2, TimeUnit.SECONDS))
        }
    }

    @Test fun shutdownBeforeStartupDoesNotInitialiseDatabases() {
        var hook: Thread? = null
        DesktopExitPersistence(registerHook = { hook = it })
        assertNotNull(hook).run()
        assertTrue(!DesktopAppStartup.isStarted)
        assertTrue(!DesktopAppStartup.databasesOpen)
    }
}
