package net.bible.sharedcore.log

import platform.Foundation.NSLog
import platform.Foundation.NSString

internal actual fun platformLog(level: LogLevel, tag: String, msg: String, tr: Throwable?) {
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
fun nsLog(message: String) {
    NSLog("%@", message as NSString)
}
