package net.bible.sharedui.assembly

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runUntilDate
import platform.Foundation.timeIntervalSince1970

/** Drives the real Native Main run loop while IO-owned startup completes, without blocking Main. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun <T> awaitStartupOnMain(block: suspend () -> T): T {
    val scope = MainScope()
    val result = scope.async { block() }
    val deadline = NSDate().timeIntervalSince1970 + 120.0
    try {
        while (!result.isCompleted) {
            check(NSDate().timeIntervalSince1970 < deadline) { "Timed out awaiting iOS startup" }
            NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
        }
        return result.getCompleted()
    } finally { result.cancel(); scope.cancel() }
}
