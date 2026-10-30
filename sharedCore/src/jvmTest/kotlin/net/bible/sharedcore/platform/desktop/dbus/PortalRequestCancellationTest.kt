package net.bible.sharedcore.platform.desktop.dbus

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortalRequestCancellationTest {
    @Test
    fun cancellationDuringStartClosesActualReturnedPath() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = mutableListOf<String>()
        val job = async(Dispatchers.IO) {
            awaitPortalRequest<Unit>(
                expectedPath = "/expected/request",
                start = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)); "/actual/request" },
                close = { synchronized(closed) { closed.add(it) } },
                register = { AutoCloseable { } },
            )
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        job.cancel()
        release.countDown()
        job.cancelAndJoin()
        assertEquals(listOf("/actual/request"), synchronized(closed) { closed.toList() })
    }
}
