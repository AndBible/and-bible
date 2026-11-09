package net.bible.service.cloudsync.nextcloud

import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode
import java.io.IOException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.LEGACY)
class OperationAwaitTest {
    @Test fun returnsResult() {
        assertEquals(42, awaitOperation { 42 })
    }

    @Test fun unwrapsOriginalException() {
        val failure = IOException("expected")
        assertSame(failure, assertThrows(IOException::class.java) { awaitOperation<Unit> { throw failure } })
    }

    @Test fun timeoutDrainsLateCallbackBeforeReturning() {
        val completed = AtomicBoolean(false)
        assertThrows(TimeoutException::class.java) {
            awaitOperation(timeoutMillis = 50, cleanupMillis = 2000) {
                suspendCoroutine<Unit> { continuation ->
                    Thread {
                        Thread.sleep(150)
                        Handler(Looper.getMainLooper()).post {
                            completed.set(true)
                            continuation.resume(Unit)
                        }
                    }.start()
                }
            }
        }
        assertTrue(completed.get())
    }

    @Test fun cleanupExhaustionIsReported() {
        val continuation = java.util.concurrent.atomic.AtomicReference<kotlin.coroutines.Continuation<Unit>>()
        try {
            val failure = assertThrows(IllegalStateException::class.java) {
                awaitOperation(timeoutMillis = 50, cleanupMillis = 100) {
                    suspendCoroutine<Unit> { continuation.set(it) }
                }
            }
            assertTrue(failure.message!!.contains("did not terminate"))
        } finally {
            continuation.get()?.resume(Unit)
        }
    }
}
