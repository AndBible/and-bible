package net.bible.sharedcore.log

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSLocale
import platform.Foundation.NSThread

@OptIn(ExperimentalForeignApi::class)
private val timestampFormatter = NSDateFormatter().apply {
    locale = NSLocale("en_US_POSIX")
    dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
}

internal actual fun logTimestampNow(): String = timestampFormatter.stringFromDate(NSDate())

internal actual fun logThreadName(): String {
    val thread = NSThread.currentThread
    if (thread.isMainThread()) return "main"
    val name = thread.name?.takeIf { it.isNotBlank() }
    val id = thread.description?.let { Regex("number = (\\d+)").find(it)?.groupValues?.get(1) }
    return listOfNotNull(name ?: "thread", id?.let { "#$it" }).joinToString("")
}

/**
 * Shortens the app container (`/Users/<name>/Library/Containers/<id>/Data` for the iOS app on a Mac, the sandbox path
 * elsewhere) to `<container>` and a Mac user's home to `~`. Called once at startup; idempotent.
 */
fun registerIosLogPathAliases() {
    val container = NSHomeDirectory()
    LogPathAliases.add(container, "<container>")
    Regex("^(/Users/[^/]+)/Library/Containers/").find(container)?.let { LogPathAliases.add(it.groupValues[1], "~") }
}
