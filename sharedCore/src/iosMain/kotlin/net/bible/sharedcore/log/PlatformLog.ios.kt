package net.bible.sharedcore.log

import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.NSLock

private val recentIosLog = RecentIosLog()
fun iosRecentLogSnapshot(): String = recentIosLog.snapshot()

/** Bounded process-local diagnostics. No disk work occurs on logging threads. */
internal class RecentIosLog(private val limit: Int = 256 * 1024) {
    private val lock = NSLock()
    private var text = ""
    fun append(line: String) {
        lock.lock()
        try { text = (text + line + "\n").takeLast(limit) } finally { lock.unlock() }
    }
    fun snapshot(): String {
        lock.lock()
        return try { text } finally { lock.unlock() }
    }
}

internal actual fun platformLog(level: LogLevel, tag: String, msg: String, tr: Throwable?) {
    val text = if (tr == null) msg else "$msg\n${tr.stackTraceToString()}"
    val line = "${level.name.first()}/$tag: $text"
    recentIosLog.append(line)
    nsLog(line)
}

/**
 * Writes [message] to the unified log through NSLog. NSLog is variadic, and a Kotlin String passed for `%@`
 * is not an Objective-C object there: NSLog crashes (SIGSEGV). The explicit `as NSString` conversion passes a
 * real NSString; `%s` would not crash but garbles non-ASCII text. [message] is never the format string, so
 * `%` in it is printed as is.
 */
// The compiler calls the cast impossible, but Kotlin/Native bridges String to NSString at runtime (verified on the
// iOS simulator: CI run 37995718911, probe 3).
@Suppress("CAST_NEVER_SUCCEEDS")
fun nsLog(message: String) {
    NSLog("%@", message as NSString)
}
