package net.bible.sharedcore.log

// Android logs through logcat, which adds its own threadtime columns; these only satisfy the common contract.
internal actual fun logTimestampNow(): String = java.time.LocalDateTime.now().toString()

internal actual fun logThreadName(): String = Thread.currentThread().name
