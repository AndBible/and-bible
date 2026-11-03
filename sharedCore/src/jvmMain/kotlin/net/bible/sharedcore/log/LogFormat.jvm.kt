package net.bible.sharedcore.log

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

internal actual fun logTimestampNow(): String = LocalDateTime.now().format(timestampFormat)

@Suppress("DEPRECATION") // Thread.threadId() needs Java 19; the toolchain is 17.
internal actual fun logThreadName(): String = Thread.currentThread().let { "${it.name}#${it.id}" }

private val homeAliasRegistered = run {
    System.getProperty("user.home")?.takeIf { it.length > 1 }?.let { LogPathAliases.add(it, "~") }
    true
}

internal fun ensureJvmLogAliases() = homeAliasRegistered
