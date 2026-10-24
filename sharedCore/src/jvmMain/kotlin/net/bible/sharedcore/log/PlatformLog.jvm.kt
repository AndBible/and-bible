package net.bible.sharedcore.log

internal actual fun platformLog(level: LogLevel, tag: String, msg: String, tr: Throwable?) {
    System.err.println("${level.name.first()}/$tag: $msg")
    tr?.printStackTrace()
}
