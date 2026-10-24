@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedcore.platform.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runMode
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Runs [block] on `Dispatchers.Main` while this (main) thread spins the main run loop, which drains
 * the main dispatch queue. The Kotlin/Native test runner calls tests on the main thread, so
 * `runTest { withContext(Dispatchers.Main) { } }` blocks the very queue it waits for and hangs.
 */
internal fun <T> runOnMainRunLoop(timeout: Duration = 20.seconds, block: suspend CoroutineScope.() -> T): T {
    check(NSThread.isMainThread) { "Expected the Kotlin/Native test runner to call tests on the main thread" }
    val result = CoroutineScope(Dispatchers.Main).async(block = block)
    val started = TimeSource.Monotonic.markNow()
    while (!result.isCompleted) {
        if (started.elapsedNow() > timeout) {
            result.cancel()
            fail("Main-thread test did not finish within $timeout")
        }
        NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, beforeDate = NSDate.dateWithTimeIntervalSinceNow(0.05))
    }
    return result.getCompleted()
}
