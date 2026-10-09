package net.bible.sharedcore.log

import platform.Foundation.NSLog

internal actual fun platformLog(level: LogLevel, tag: String, msg: String, tr: Throwable?) {
    val text = if (tr == null) msg else "$msg\n${tr.stackTraceToString()}"
    NSLog("%@", "${level.name.first()}/$tag: $text")
}
