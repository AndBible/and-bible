package net.bible.sharedcore.log

import kotlin.concurrent.Volatile

enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

/** Platform log output. Android: android.util.Log; iOS: NSLog; JVM: stderr. */
internal expect fun platformLog(level: LogLevel, tag: String, msg: String, tr: Throwable?)

/**
 * Logging facade with the call shapes of `android.util.Log`, so domain code can drop the Android
 * import without other changes (spec L1a §3). Logging never throws.
 */
object Log {
    /** Test hook: when set, receives every call instead of the platform sink. Volatile: set on the test thread, read by logging worker threads. */
    @Volatile
    var sinkOverride: ((LogLevel, String, String, Throwable?) -> Unit)? = null

    private fun log(level: LogLevel, tag: String, msg: String, tr: Throwable?): Int {
        try {
            sinkOverride?.invoke(level, tag, msg, tr) ?: platformLog(level, tag, msg, tr)
        } catch (_: Throwable) {
        }
        return 0
    }

    fun v(tag: String, msg: String, tr: Throwable? = null) = log(LogLevel.VERBOSE, tag, msg, tr)
    fun d(tag: String, msg: String, tr: Throwable? = null) = log(LogLevel.DEBUG, tag, msg, tr)
    fun i(tag: String, msg: String, tr: Throwable? = null) = log(LogLevel.INFO, tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log(LogLevel.WARN, tag, msg, tr)
    fun w(tag: String, tr: Throwable) = log(LogLevel.WARN, tag, "", tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log(LogLevel.ERROR, tag, msg, tr)
    fun getStackTraceString(tr: Throwable?): String = tr?.stackTraceToString() ?: ""
}
