package net.bible.sharedcore.log

import kotlin.concurrent.Volatile

/**
 * The line format of the iOS and desktop report logs (the in-memory buffer and the desktop log file), modelled on
 * Android's `logcat -v threadtime`: `2026-11-04 12:34:56.789 [main] D/Tag: message`, local time with milliseconds, the
 * thread, the level and the tag. A throwable's stack trace follows on the next lines. Absolute paths under an alias
 * (the user's home, the iOS app container) are shortened so a report holds no user name.
 */
internal fun formatLogLine(
    timestamp: String, thread: String, level: LogLevel, tag: String, msg: String, tr: Throwable?,
    aliases: List<Pair<String, String>>,
): String {
    val head = "$timestamp [$thread] ${level.name.first()}/$tag: $msg"
    val text = if (tr == null) head else head + "\n" + tr.stackTraceToString().trimEnd()
    return shortenPaths(text, aliases)
}

/** Replaces each alias path in [text] by its short form; only whole path segments match (`/home/bob` is not `/home/bobby`). */
internal fun shortenPaths(text: String, aliases: List<Pair<String, String>>): String {
    var out = text
    for ((path, alias) in aliases.filter { it.first.length > 1 }.sortedByDescending { it.first.length }) {
        val trimmed = path.trimEnd('/', '\\')
        var from = 0
        val sb = StringBuilder()
        while (true) {
            val at = out.indexOf(trimmed, from)
            if (at < 0) { sb.append(out, from, out.length); break }
            val end = at + trimmed.length
            val whole = end == out.length || out[end] == '/' || out[end] == '\\' || !out[end].isLetterOrDigit() && out[end] != '_' && out[end] != '-' && out[end] != '.'
            sb.append(out, from, at)
            sb.append(if (whole) alias else trimmed)
            from = end
        }
        out = sb.toString()
    }
    return out
}

/** Paths the log shortens, longest first; platforms register theirs at startup (JVM: `user.home`; iOS: the app container). */
internal object LogPathAliases {
    @Volatile private var aliases: List<Pair<String, String>> = emptyList()
    fun add(path: String, alias: String) { aliases = (aliases + (path to alias)).distinct() }
    fun snapshot(): List<Pair<String, String>> = aliases
}

/** Local `yyyy-MM-dd HH:mm:ss.SSS` now. */
internal expect fun logTimestampNow(): String

/** The calling thread's name and id, e.g. `main#1`. */
internal expect fun logThreadName(): String

/** A line for the current time and thread. */
internal fun formatLogLineNow(level: LogLevel, tag: String, msg: String, tr: Throwable?): String =
    formatLogLine(logTimestampNow(), logThreadName(), level, tag, msg, tr, LogPathAliases.snapshot())

/**
 * Facts every bug report header gives (version, build, OS, locale, web engine...): set once as they become known, read
 * by the report (and logged at startup). Insertion ordered; setting a key again replaces its value.
 */
class ReportFacts {
    private val lock = kotlinx.atomicfu.locks.SynchronizedObject()
    private val map = LinkedHashMap<String, String>()
    fun set(key: String, value: String) = kotlinx.atomicfu.locks.synchronized(lock) { map[key] = value; Unit }
    fun snapshot(): List<Pair<String, String>> = kotlinx.atomicfu.locks.synchronized(lock) { map.toList() }

    /** Writes every fact as an `Info` log line under [tag], so the log's first lines carry the same essentials as the report header. */
    fun logAll(tag: String) = snapshot().forEach { (k, v) -> Log.i(tag, "$k: $v") }

    companion object { val global = ReportFacts() }
}
