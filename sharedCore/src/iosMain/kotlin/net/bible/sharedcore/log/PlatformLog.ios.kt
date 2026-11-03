package net.bible.sharedcore.log

import platform.Foundation.NSLog
import platform.Foundation.NSString
import platform.Foundation.NSLock
import kotlin.concurrent.AtomicReference

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
    recentIosLog.append(formatLogLineNow(level, tag, msg, tr))
    val text = if (tr == null) msg else "$msg\n${tr.stackTraceToString()}"
    nsLog("${level.name.first()}/$tag: $text")
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
actual fun nsLog(message: String) {
    val sink = IosLogSink.current()
    if (sink != null) sink(message) else NSLog("%@", message as NSString)
}

/**
 * Where [nsLog] writes when the Swift host has installed a sink. macOS 26 redacts an iOS app's NSLog text as
 * `<private>` (Console.app, `log show`), so the app installs a Swift `Logger` sink that logs `.public` at
 * launch (`iOSApp.init`, via `IosPlatformBootstrap.installLogSink`). Without a sink (tests, early startup) NSLog is used.
 */
object IosLogSink {
    private val sink = AtomicReference<((String) -> Unit)?>(null)

    fun current(): ((String) -> Unit)? = sink.value

    /** Installs [newSink] (null removes it) and returns the previous one, so a test can restore it. */
    fun install(newSink: ((String) -> Unit)?): ((String) -> Unit)? = sink.getAndSet(newSink)
}
